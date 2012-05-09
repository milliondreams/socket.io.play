package controllers

import akka.actor._
import akka.util.duration._

import play.api._
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent._

import play.api.Play.current

import akka.util.Timeout
import akka.pattern.ask
import com.sun.xml.internal.ws.handler.ClientMessageHandlerTube
import socketio.{Packet, PacketTypes, Parser}

object SocketIO extends Controller {

  implicit val timeout = Timeout(10 second)

  lazy val socketIOActor = {
    Akka.system.actorOf(Props[SocketIOActor])
  }

  def init = Action {
    val sessionId = java.util.UUID.randomUUID().toString()
    println(sessionId)
    Ok(sessionId + ":20:15:websocket")
  }

  def socketSetup(sessionId: String) = WebSocket.async[String] {
    request =>
      (socketIOActor ? Join(sessionId)).asPromise.map {

        case Connected(enumerator) =>

          println("Connected")
          // Create an Iteratee to consume the feed
          val iteratee = Iteratee.foreach[String] { event =>

            println("Got this -- " + event)

            val packet = Parser.decodePacket(event)

            println(packet)

            packet.packetType match {
              case PacketTypes.HEARTBEAT => {/*do nothing */
                println("HEARTBEAT")
              }
              case _ => {
                println("Talking -- " + event)
                socketIOActor ! ServerMessage(sessionId, event)
              }
            }
          }.mapDone {
            _ =>
              println("Quit!!!")
              socketIOActor ! Quit(sessionId)
          }

          //println("Sending connect response")
          socketIOActor ! ClientMessage(sessionId, Packet(packetType = PacketTypes.CONNECT))
          //socketIOActor ! ClientMessage(sessionId, Packet(packetType = PacketTypes.MESSAGE, data = "{\"name\":\"eventConnect\",\"args\":[{\"message\":\"welcome\"}]}"))

          (iteratee, enumerator)

        case CannotConnect(error) =>

          // Connection error

          // A finished Iteratee sending EOF
          val iteratee = Done[String, Unit]((), Input.EOF)

          // Send an error and close the socket
          val enumerator = Enumerator[String](error).andThen(Enumerator.enumInput(Input.EOF))

          (iteratee, enumerator)

      }
  }

}

class SocketIOActor extends Actor {

  var sessions = Map.empty[String, SocketIOSession]
  val timeout = 10 second


  def receive = {
    case Join(sessionId) => {
      println(sessionId)
      val channel = Enumerator.imperative[String]()
      if (sessions.contains(sessionId)) {
        sender ! CannotConnect(Json.stringify(Json.toJson(Map("error" -> "Invalid Session ID"))))
      } else {
        val heartbeatSchedule = Akka.system.scheduler.scheduleOnce(timeout, self, Heartbeat(sessionId))
        sessions = sessions + (sessionId -> SocketIOSession(channel, heartbeatSchedule))
        sender ! Connected(channel)
      }
    }
    case ServerMessage(sessionId, event) => {
      println(sessionId + "---" + event)
      //DO your message processing here! Like saving the data
      val id = math.round(math.random * 1000)
      notify(sessionId,
        Parser.encodePacket(
          Packet(
            packetType = PacketTypes.MESSAGE,
            data = Json.stringify(Json.toJson(Map("id" -> id)))
          )
        )
      )


    }

    case Quit(sessionId) => {
      sessions = sessions - sessionId
      println(sessionId + "--- QUIT")
    }

    case ClientMessage(sessionId, message) => {
      println("Sending connect response -- " + message)
      notify(sessionId, Parser.encodePacket(message))
    }

    case Heartbeat(sessionId) => {
      notify(sessionId, Parser.encodePacket(Packet(packetType = PacketTypes.HEARTBEAT)))

    }
  }

  def notify(sessionId:String, message:String) {
    println("Sending message -- " + message)
    val session = sessions.get(sessionId).get
    session.channel.push(message)
    session.schedule.cancel()
    session.schedule = Akka.system.scheduler.scheduleOnce(timeout){
      self ! Heartbeat(sessionId)
    }

  }
}

case class Join(sessionId: String)

case class Heartbeat(sessionId: String)

case class ServerMessage(sessionId:String, message:String)

case class ClientMessage(sessionId:String, message:Packet)

case class Quit(sessionId: String)

case class CannotConnect(message: String)

case class Connected(enumerator: Enumerator[String])

case class SocketIOSession(val channel:PushEnumerator[String], var schedule:Cancellable)