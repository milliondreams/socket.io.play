package controllers

import scala.concurrent.duration._
import scala.util.matching.Regex

import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import play.api.Play.current

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout

import PacketTypes._

trait SocketIOController extends Controller {

  val xhrMap = collection.mutable.Map.empty[String, ActorRef]
  val wsMap = collection.mutable.Map.empty[String, ActorRef]

  def processMessage(sessionId: String, packet: Packet): Unit

  implicit val timeout = Timeout(10 second)

  val pRegex: Regex = """([^/?]+)?/?([^/?]+)?\??(.+)?""".r

  def handler(socketUrl: String) = {
    val pRegex(transport, sessionId, query) = socketUrl

    Option(transport) match {
      case None => initSession
      case Some("websocket") => wsHandler(sessionId)
      case Some("xhr-polling") => xhrHandler(sessionId)
      case _ => throw new Exception("Unable to match transport")
    }
  }

  def enqueueMsg(sessionId: String, msg: String) = enqueue(sessionId, MESSAGE, msg)

  def enqueueEvent(sessionId: String, event: String) = enqueue(sessionId, EVENT, event)

  def enqueueJsonMsg(sessionId: String, msg: String) = enqueue(sessionId, PacketTypes.JSON, msg)

  def enqueue(sessionId: String, payload: String, payloadType: String) {
    if (wsMap contains sessionId)
      wsMap(sessionId) ! Enqueue(Parser.encodePacket(Packet(packetType = payloadType, endpoint = "", data = payload)))
    else if (xhrMap contains sessionId)
      xhrMap(sessionId) ! Enqueue(Parser.encodePacket(Packet(packetType = payloadType, endpoint = "", data = payload)))
  }

  def broadcastMsg(msg: String) = {
    xhrMap.keysIterator.foreach(enqueueMsg(_, msg))
    wsMap.keysIterator.foreach(enqueueMsg(_, msg))
  }

  def broadcastEvent(event: String) = {
    xhrMap.keysIterator.foreach(enqueueEvent(_, event))
    wsMap.keysIterator.foreach(enqueueEvent(_, event))
  }

  def broadcastJsonMsg(msg: String) = {
    xhrMap.keysIterator.foreach(enqueueJsonMsg(_, msg))
    wsMap.keysIterator.foreach(enqueueJsonMsg(_, msg))
  }

  def initSession = Action {
    val sessionId = java.util.UUID.randomUUID().toString
    System.err.println("Strating new session: " + sessionId)
    Ok(sessionId + ":20:15:websocket,xhr-polling")
  }

  def wsHandler(sessionId: String) = WebSocket.using[String] {
    implicit request =>
      if (wsMap contains sessionId) {
        handleConnectionFailure(Json.stringify(Json.toJson(Map("error" -> "Invalid Session ID"))))
      } else {
        println("creating new websocket actor")
        val channel = Enumerator.imperative[String]()
        val wsActor = Akka.system.actorOf(Props(new WSActor(channel, processMessage, wsMap)))
        wsMap += (sessionId -> wsActor)
        handleConnectionSetup(sessionId, channel)
      }
  }

  def handleConnectionSetup(sessionId: String, enumerator: Enumerator[String]):
  (Iteratee[String, Unit], Enumerator[String]) = {
    val iteratee = Iteratee.foreach[String] {
      socketData =>
        wsMap(sessionId) ! ProcessPacket(socketData)
    }.mapDone {
      _ =>
        println("all done quit.")
    }
    wsMap(sessionId) ! EventOrNoop
    (iteratee, enumerator)
  }

  def handleConnectionFailure(error: String): (Iteratee[String, Unit], Enumerator[String]) = {
    // Connection error

    // A finished Iteratee sending EOF
    val iteratee = Done[String, Unit]((), Input.EOF)

    // Send an error and close the socket
    val enumerator = Enumerator[String](error).andThen(Enumerator.enumInput(Input.EOF))

    (iteratee, enumerator)
  }

  def xhrHandler(sessionId: String) = Action {
    implicit request =>
      if (xhrMap contains sessionId) {
        val body: AnyContent = request.body
        body.asText match {
          case None => Async {
            val res = xhrMap(sessionId) ? EventOrNoop
            res.map {
              x: Any =>
                Ok(x.toString)
            }
          }

          case Some(x) => Async {
            val res = ask(xhrMap(sessionId), ProcessPacket(x))
            res.map {
              x: Any =>
                Ok(x.toString)
            }
          }
        }
      } else {
        val xhrActor = Akka.system.actorOf(Props(new XHRActor(processMessage, xhrMap)))
        xhrMap += (sessionId -> xhrActor)
        Async {
          val res = xhrActor ? EventOrNoop
          res.map {
            x: Any =>
              Ok(x.toString)
          }
        }
      }
  }
}

class XHRActor(val processMessage: (String, Packet) => Unit, val xhrMap: collection.mutable.Map[String, ActorRef]) extends Actor {
  var eventQueue = List.empty[String]

  def enqueue: Receive = {
    case Enqueue(x) => eventQueue = eventQueue :+ x
  }

  def sendEventOrNoop(s: ActorRef) {
    eventQueue match {
      case x :: xs => eventQueue = xs; s ! x
      case Nil => context.system.scheduler.scheduleOnce(9.seconds) {
        eventQueue match {
          case x :: xs => eventQueue = xs; s ! x
          case Nil => s ! "8::"
        }
      }
    }
  }

  def receive = enqueue orElse beforeConnected

  def beforeConnected: Receive = {
    case x =>
      sender ! "1::"
      context.become(afterConnected orElse enqueue)
  }

  def afterConnected: Receive = {

    case EventOrNoop => {
      val s = sender
      sendEventOrNoop(s)
    }

    case ProcessPacket(socketData) => {
      val s = sender
      val packet = Parser.decodePacket(socketData)

      packet.packetType match {
        case DISCONNECT =>
          s ! "0::"
          context.stop(self)

        case HEARTBEAT => sendEventOrNoop(s)

        case _ =>
          val sessionId = xhrMap.find {
            case (_, y) => y == self
          }.get._1
          processMessage(sessionId, packet)
          sendEventOrNoop(s)

      }
    }
  }
}

class WSActor(channel: PushEnumerator[String], processMessage: (String, Packet) => Unit, wsMap: collection.mutable.Map[String, ActorRef]) extends Actor {

  def enqueue: Receive = {
    case Enqueue(x) => channel.push(x)
  }

  def sendBeat = channel.push("2::")

  def receive = beforeConnected

  def beforeConnected: Receive = {
    case _ =>
      channel.push("1::")
      context.system.scheduler.schedule(10.seconds, 10.seconds)(sendBeat)
      context.become(afterConnected orElse enqueue)
  }

  def afterConnected: Receive = {

    case ProcessPacket(socketData) => {
      val packet = Parser.decodePacket(socketData)

      packet.packetType match {
        case DISCONNECT =>
          channel.close()
          wsMap.remove {
            wsMap.find((p: (String, ActorRef)) => p._2 == self).get._1
          }
          context.stop(self)

        case HEARTBEAT => {}

        case _ =>
          val sessionId = wsMap.find {
            case (_, y) => y == self
          }.get._1
          processMessage(sessionId, packet)

      }
    }
  }
}

case object EventOrNoop

case class ProcessPacket(x: String)

case class Enqueue(x: String)