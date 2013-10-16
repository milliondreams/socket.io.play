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

  val clientTimeout: Timeout

  val xhrMap = collection.mutable.Map.empty[String, ActorRef]
  val wsMap  = collection.mutable.Map.empty[String, ActorRef]

  val xhrRevMap = collection.mutable.Map.empty[ActorRef, String]
  val wsRevMap  = collection.mutable.Map.empty[ActorRef, String]

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

  def enqueueMsg(sessionId: String, msg: String) = enqueue(sessionId, msg, MESSAGE)

  def enqueueEvent(sessionId: String, event: String) = enqueue(sessionId, event, EVENT)

  def enqueueJsonMsg(sessionId: String, msg: String) = enqueue(sessionId, msg, PacketTypes.JSON)

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
    val t = clientTimeout.duration.toSeconds.toString
    Ok(sessionId + ":" + t + ":" + t +":xhr-polling")
  }

  def wsHandler(sessionId: String) = WebSocket.using[String] {
    implicit request =>
      if (wsMap contains sessionId) {
        handleConnectionFailure(Json.stringify(Json.toJson(Map("error" -> "Invalid Session ID"))))
      } else {
        println("creating new websocket actor")
        val channel = Enumerator.imperative[String]()
        val wsActor = Akka.system.actorOf(Props(new WSActor(channel, processMessage, wsMap, wsRevMap, clientTimeout)))
        wsMap    += (sessionId -> wsActor)
        wsRevMap += (wsActor -> sessionId)
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
        val xhrActor = Akka.system.actorOf(Props(new XHRActor(processMessage, xhrMap, xhrRevMap, clientTimeout)))
        xhrMap    += (sessionId -> xhrActor)
        xhrRevMap += (xhrActor -> sessionId)
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

class XHRActor(processMessage: (String, Packet) => Unit, xhrMap: collection.mutable.Map[String, ActorRef], xhrRevMap: collection.mutable.Map[ActorRef, String], clientTimeout: Timeout) extends Actor {
  
  var cancellable: Cancellable = _
  var eventQueue = List.empty[String]

  def enqueue: Receive = {
    case Enqueue(x) => eventQueue = eventQueue :+ x
  }

  def sendEventOrNoop(s: ActorRef) {
    eventQueue match {
      case x :: xs => eventQueue = xs; s ! x
      case Nil => context.system.scheduler.scheduleOnce((clientTimeout.duration.toSeconds - 1).seconds) {
        eventQueue match {
          case x :: xs => eventQueue = xs; s ! x
          case Nil => 
            s ! "8::"
            cancellable.cancel
            cancellable = context.system.scheduler.scheduleOnce((clientTimeout.duration.toSeconds + 1).seconds) {
              self ! Die
            }
        }
      }
    }
  }

  def quit(s: ActorRef) {
    s ! "0::"
    quit
  }

  def quit {
    xhrMap    -= xhrRevMap(self)
    xhrRevMap -= self
    context.stop(self)
  }

  def receive = enqueue orElse beforeConnected

  def beforeConnected: Receive = {
    case x =>
      sender ! "1::"
      context.become(afterConnected orElse enqueue)
      cancellable = context.system.scheduler.scheduleOnce((clientTimeout.duration.toSeconds + 1).seconds) {
        self ! Die
      }
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

        case DISCONNECT => quit(s)
          
        case _ =>
          val sessionId = xhrRevMap(self)
          processMessage(sessionId, packet)
          s ! "1"

      }
    }

    case Die => quit
  }
}

class WSActor(channel: PushEnumerator[String], processMessage: (String, Packet) => Unit, wsMap: collection.mutable.Map[String, ActorRef], wsRevMap: collection.mutable.Map[ActorRef, String], clientTimeout: Timeout) extends Actor {

  var cancellable: Cancellable = _

  def enqueue: Receive = {
    case Enqueue(x) => channel.push(x)
  }

  def sendBeat = channel.push("2::")

  def quit {
    channel.close()
    wsMap    -= wsRevMap(self)
    wsRevMap -= self
    context.stop(self)
  }

  def receive = beforeConnected

  def beforeConnected: Receive = {
    case _ =>
      channel.push("1::")
      val t = clientTimeout.duration.toSeconds - 1
      context.system.scheduler.schedule(t.seconds, t.seconds)(sendBeat)
      cancellable = context.system.scheduler.scheduleOnce((clientTimeout.duration.toSeconds + 1).seconds) {
        self ! Die
      }
      context.become(afterConnected orElse enqueue)
  }

  def afterConnected: Receive = {

    case ProcessPacket(socketData) => {
      val packet = Parser.decodePacket(socketData)

      packet.packetType match {
        
        case DISCONNECT => quit
          
        
        case HEARTBEAT => 
          cancellable.cancel
          cancellable = context.system.scheduler.scheduleOnce((clientTimeout.duration.toSeconds + 1).seconds) {
            self ! Die
          }
        
        case _ =>
          val sessionId = wsRevMap(self)
          processMessage(sessionId, packet)

      }
    }

    case Die => quit

  }
}

case object EventOrNoop

case object Die

case class ProcessPacket(x: String)

case class Enqueue(x: String)
