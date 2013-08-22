package controllers

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

import PacketTypes._

trait SocketIOController extends Controller {

  val xhrMap = collection.mutable.Map.empty[String, ActorRef]
  def processMessage: PartialFunction[(String, (String, String, Any)), String]

  implicit val timeout = Timeout(10 second)

  val pRegex: Regex = """([^/?]+)?/?([^/?]+)?\??(.+)?""".r

  def handler(socketUrl: String) = {
    val pRegex(transport, sessionId, query) = socketUrl

    Option(transport) match {
      case None => initSession
      case Some("xhr-polling") => xhrHandler(sessionId)
      case _ => throw new Exception("Unable to match transport")
    }
  }

  def enqueueMsg(sessionId: String, msg: String) = {
    xhrMap(sessionId) ! Enqueue(Parser.encodePacket(Packet(packetType = MESSAGE, endpoint = "", data = msg)))
  }

  def enqueueEvent(sessionId: String, event: String) = {
    xhrMap(sessionId) ! Enqueue(Parser.encodePacket(Packet(packetType = EVENT, endpoint = "", data = event)))
  }

  def enqueueJsonMsg(sessionId: String, msg: String) = {
    xhrMap(sessionId) ! Enqueue(Parser.encodePacket(Packet(packetType = JSON, endpoint = "", data = msg)))
  }

  def initSession = Action {
    val sessionId = java.util.UUID.randomUUID().toString()
    val xhrActor = Akka.system.actorOf(Props(new XHRActor(processMessage)))
    System.err.println("Strating new session: " + sessionId)
    xhrMap += (sessionId -> xhrActor)
    Ok(sessionId + ":60:60:xhr-polling")
  }

  def xhrHandler(sessionId: String) = Action { implicit request =>
    if (xhrMap contains sessionId) {
      val body: AnyContent = request.body
      body.asText match {
        case None => Async {
          val res = (xhrMap(sessionId) ? EventOrNoop)
          res.map { x: Any =>
            Ok(x.toString)
          }
        }

        case Some(x) => Async {
          val res = ask(xhrMap(sessionId), ProcessPacket(x))
          res.map { x: Any =>
            Ok(x.toString)
          }
        }
      }
    } else {
      Ok("0::")
    }
  }
}

class XHRActor(val processMessage: PartialFunction[(String, (String, String, Any)), String]) extends Actor {
  var eventQueue = List.empty[String]

  def receive = beforeConnected orElse enqueue

  def enqueue: Receive = {
    case Enqueue(x) => eventQueue :+ x
  }

  def beforeConnected: Receive = {
    case x =>
      sender ! "1::"
      context.become(afterConnected orElse enqueue)
  }

  def afterConnected: Receive = {

    case EventOrNoop => {
      val s = sender
      eventQueue match {
        case x :: xs =>
          eventQueue = xs; s ! x
        case Nil => context.system.scheduler.scheduleOnce(8.seconds, s, "8::")
      }

    }

    case ProcessPacket(socketData) => {
      val s = sender
      val packet = Parser.decodePacket(socketData)

      packet.packetType match {

        case HEARTBEAT => {
          eventQueue match {
            case x :: xs =>
              eventQueue = xs; s ! x
            case Nil => context.system.scheduler.scheduleOnce(8.seconds, s, "8::")
          }
        }

        case MESSAGE => {
          s ! "Processed request for sessionId: " + packet.data
        }

        case JSON => {
          s ! "Processed request for sessionId: " + packet.data
        }

        case EVENT => {
          val jdata: JsValue = Json.parse(packet.data)
          s ! "Processed request for sessionId: " + (jdata \ "args")
        }

        case DISCONNECT => {
          s ! "0::"
        }
      }
    }
  }
}

case object EventOrNoop

case class ProcessPacket(x: String)

case class Enqueue(x: String)