package controllers

import scala.concurrent.duration._

import play.api._
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent._
import play.api.Play.current

import akka.actor._

/**
 * Created with IntelliJ IDEA.
 * User: rohit
 * Date: 5/14/12
 * Time: 10:11 AM
 * To change this template use File | Settings | File Templates.
 */

object MySocketIOController extends SocketIOController {

  def processMessage: PartialFunction[(String, (String, String, Any)), String] = {

    //Process regular message
    case ("message", (sessionId: String, namespace: String, msg: String)) => {
      println("Processed request for sessionId: " + msg)
      //DO your message processing here! Like saving the msg
      //send(sessionId, namespace, msg)
      //broadcast(namespace, msg)
      "Processed request for sessionId: " + msg
    }

    //Process JSON message
    case ("message", (sessionId: String, namespace: String, msg: JsValue)) => {
      println("Processed request for sessionId: " + Json.stringify(msg))
      "Processed request for sessionId: " + msg
    }

    //Handle event
    case ("event", (sessionId: String, namespace: String, eventData: JsValue)) => {
      println("Processed request for sessionId: " + eventData)
      "Processed request for sessionId: " + eventData
    }
  }
}
