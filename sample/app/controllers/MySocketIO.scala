package controllers

import scala.concurrent.duration._

import play.api._
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent._
import play.api.Play.current

import akka.actor._

import socketio.{SocketIOController, SocketIOActor}


/**
 * Created with IntelliJ IDEA.
 * User: rohit
 * Date: 5/14/12
 * Time: 10:11 AM
 * To change this template use File | Settings | File Templates.
 */

object MySocketIOController extends SocketIOController {
  lazy val socketIOActor: ActorRef = {
    Akka.system.actorOf(Props[MySocketIO])
  }
}


class MySocketIO extends SocketIOActor {
  def processMessage: PartialFunction[(String, (String, String, Any)), Unit] = {

    //Process regular message
    case ("message", (sessionId: String, namespace: String, msg: String)) => {
      println(sessionId + " in my Socket --- " + msg)
      //DO your message processing here! Like saving the msg
      send(sessionId, namespace, msg)
      broadcast(namespace, msg)
    }

    //Process JSON message
    case ("message", (sessionId: String, namespace: String, msg: JsValue)) => {
      println(sessionId + " handling JSON message in my Socket --- " + Json.stringify(msg))
      sendJson(sessionId, namespace, msg)
    }

    //Handle event
    case ("someEvt", (sessionId: String, namespace: String, eventData: JsValue)) => {
      println(sessionId + " handling Event in my Socket --- " + Json.stringify(eventData))
      emit(sessionId, namespace,
        Json.stringify(
          Json.toJson(Map(
            "name" -> Json.toJson("someEvt"),
            "args" -> eventData
          )
          )
        )
      )

    }

    case ("connected", (sessionId: String, namespace: String, msg: String)) =>{
      println("New session created . .  .")
      send(sessionId, "welcome");

    }

  }
}
