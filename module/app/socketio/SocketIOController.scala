package socketio

import scala.concurrent._
import scala.concurrent.duration._
import scala.util.matching.Regex

import play.api._
import play.api.libs.json._
import play.api.libs.json.JsValue
import play.api.libs.iteratee._
import play.api.libs.concurrent._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import play.api.Play.current

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout

import socketio.PacketTypes._


trait SocketIOController extends Controller {

  val socketIOActor: ActorRef

  implicit val timeout = Timeout(10 second)

  val pRegex: Regex = """([^/?]+)?/?([^/?]+)?\??(.+)?""".r

  def handler(socketUrl: String) = {
    val pRegex(transport, sessionId, query) = socketUrl

    Option(transport) match {
      case None => initSession
      case Some("websocket") => websocketSetup(sessionId)
      case _	=> throw new Exception("Unable to match transport")
    }
  }

  def initSession = Action {
    val sessionId = java.util.UUID.randomUUID().toString()
    println(sessionId)
    Ok(sessionId + ":20:15:websocket")
  }

  def websocketSetup (sessionId: String) = WebSocket.async[String] {
    request =>
      (socketIOActor ? Join(sessionId)).map {
        case ConnectionEstablished(enumerator) =>
          handleConnectionSetup(sessionId, enumerator)

        case NotifyConnectFailure(error) =>
          handleConnectionFailure(error)
      }
  }

  def handleConnectionSetup (sessionId: String, enumerator: Enumerator[String]):
  (Iteratee[String, Unit], Enumerator[String]) = {
    println("ConnectionEstablished")
    // Create an Iteratee to consume the feed
    val iteratee = Iteratee.foreach[String] {
      socketData =>

        println("Got this -- " + socketData)

        socketIOActor ! ProcessRawSocketData(sessionId, socketData)

    }.mapDone {
      _ =>
        println("Quit!!!")
        socketIOActor ! Quit(sessionId)
    }

    socketIOActor ! NotifyConnected(sessionId, "")

    (iteratee, enumerator)
  }

  def handleConnectionFailure (error: String): (Iteratee[String, Unit], Enumerator[String]) = {
    // Connection error

    // A finished Iteratee sending EOF
    val iteratee = Done[String, Unit]((), Input.EOF)

    // Send an error and close the socket
    val enumerator = Enumerator[String](error).andThen(Enumerator.enumInput(Input.EOF))

    (iteratee, enumerator)
  }
}

trait SocketIOActor extends Actor {

  var sessions = Map.empty[String, SocketIOSession]
  val timeout = 10 second

  def processMessage: PartialFunction[(String, (String, String, Any)), Unit]

  def receive = {

    case Join(sessionId) => {
      println(sessionId)
      val channel = Enumerator.imperative[String]()
      if (sessions.contains(sessionId)) {
        sender ! NotifyConnectFailure(Json.stringify(Json.toJson(Map("error" -> "Invalid Session ID"))))
      } else {
        val heartbeatSchedule = Akka.system.scheduler.scheduleOnce(timeout, self, Heartbeat(sessionId))
        sessions = sessions + (sessionId -> SocketIOSession(channel, heartbeatSchedule))
        sender ! ConnectionEstablished(channel)
      }
    }

    case ProcessRawSocketData(sessionId, socketData) => {
      val packet = Parser.decodePacket(socketData)

      println(packet)

      packet.packetType match {
        case CONNECT => {
          self ! NotifyConnected(sessionId, packet.endpoint)
        }

        case HEARTBEAT => {
          /*do nothing */

          //TODO: implement disconnect on haeartbeat failure
        }

        case MESSAGE => {
          self ! ReceiveMessage(sessionId, packet.endpoint, packet.data)
        }

        case JSON => {
          self ! ReceiveJsonMessage(sessionId, packet.endpoint, Json.parse(packet.data))
        }

        case EVENT => {
          val jdata: JsValue = Json.parse(packet.data)
          self ! ReceiveEvent(sessionId, packet.endpoint, (jdata \ "name").asOpt[String].getOrElse("UNNAMED_EVENT"), jdata \ "args")
        }

        case DISCONNECT => {
          //self ! NotifyDisconnect(sessionId, packet.endpoint)
        }

      }

    }

    case NotifyConnected(sessionId, namespace) => {
      sendPacket(sessionId, Packet(packetType = CONNECT, endpoint = namespace))
      processMessage("connected", (sessionId, namespace, ""))
    }

    case ReceiveMessage(sessionId, namespace, msg) => {
      println("RECEIVED---" + sessionId + "---" + msg)
      processMessage("message", (sessionId, namespace, msg))
    }


    case ReceiveEvent(sessionId, namespace, eventName, eventData) => {
      println(sessionId + "---" + eventName + " -- " + eventData)
      processMessage(eventName, (sessionId, namespace, eventData))
    }

    case ReceiveJsonMessage(sessionId, namespace, json) => {
      println(sessionId + "---" + json)
      if (processMessage.isDefinedAt(("message", (sessionId, namespace, json)))) {
        processMessage("message", (sessionId, namespace, json))
      } else {
        processMessage("message", (sessionId, namespace, Json.stringify(json)))
      }
    }

    case SendMessage(sessionId, packet) => {
      println("Sending connect response -- " + packet)
      sendPacket(sessionId, packet)
    }

    case Heartbeat(sessionId) => {
      sendPacket(sessionId, Packet(packetType = HEARTBEAT))
    }

    case Quit(sessionId) => {
      if (sessions.contains(sessionId)) {
        val session = sessions.get(sessionId).get
        session.schedule.cancel()
        session.channel.close()
        sessions = sessions - sessionId
        println(sessionId + "--- QUIT")
      }
    }

  }

  //Helper method for easy send message                                                \
  def send(sessionId: String, msg: String) {
    send(sessionId, "", msg)
  }

  def send(sessionId: String, namespace: String, msg: String) {
    sendPacket(sessionId, Packet(packetType = MESSAGE, endpoint = namespace, data = msg))
  }

  //Helper method for easy send Json message                                                \
  def sendJson(sessionId: String, msg: JsValue) {
    sendJson(sessionId, "", msg)
  }

  def sendJson(sessionId: String, namespace: String, msg: JsValue) {
    sendPacket(sessionId, Packet(packetType = JSON, endpoint = namespace, data = Json.stringify(msg)))
  }

  //Helper method for easy emit event                                                \
  def emit(sessionId: String, msg: String) {
    emit(sessionId, "", msg)
  }

  def emit(sessionId: String, namespace: String, msg: String) {
    sendPacket(sessionId, Packet(packetType = EVENT, endpoint = namespace, data = msg))
  }

  def emit(sessionId: String, msg: JsValue) {
    emit(sessionId, "", msg)
  }

  def emit(sessionId: String, namespace: String, msg: JsValue) {
    emit(sessionId, namespace, Json.stringify(msg))
  }

  //Implement broadcast
  def broadcast(namespace: String, msg: String) {
    //TODO: Handle basic namespace restriction, i.e. the message should not be sent to clients/sessions that are not connected to that namespace
    sessions.keySet.foreach({
      send(_, namespace, msg)
    })
  }

  def sendPacket(sessionId: String, packet: Packet) {
    val session = sessions.get(sessionId).get //TODO: Should be getOrElse, sending non existing socket IO data to dead letter queue
    session.channel.push(Parser.encodePacket(packet))
    session.schedule.cancel()
    session.schedule = Akka.system.scheduler.scheduleOnce(timeout) {
      self ! Heartbeat(sessionId)
    }

  }
}

case class Join(sessionId: String)

case class Heartbeat(sessionId: String)

case class NotifyConnected(sessionId: String, namespace: String)

case class ReceiveMessage(sessionId: String, namespace: String, message: String)

case class SendMessage(sessionId: String, packet: Packet)

case class ReceiveJsonMessage(sessionId: String, namespace: String, message: JsValue)

case class SendJsonMessage(sessionId: String, packet: Packet)

case class ReceiveEvent(sessionId: String, namespace: String, eventType: String, message: JsValue)

case class SendEvent(sessionId: String, packet: Packet)

//case class NotifyDisconnect(sessionId:String, namespace:String)

case class Quit(sessionId: String)

case class NotifyConnectFailure(message: String)

case class ConnectionEstablished(enumerator: Enumerator[String])

case class SocketIOSession(val channel: PushEnumerator[String], var schedule: Cancellable)

case class ProcessRawSocketData(sessionId: String, socketData: String)



