package controllers

import scala.concurrent.duration._

import play.api._
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent._
import play.api.Play.current

import akka.actor._

import PacketTypes._

/**
 * Created with IntelliJ IDEA.
 * User: rohit
 * Date: 5/14/12
 * Time: 10:11 AM
 * To change this template use File | Settings | File Templates.
 */

object MySocketIOController extends SocketIOController {

  def processMessage(sessionId: String, packet: Packet) {

    packet.packetType match {
      //Process regular message
      case (MESSAGE) => {
        println("Processed request for sessionId: " + packet.data)
        //DO your message processing here
        Enqueue("Processed request for sessionId: " + packet.data)
      }

      //Process JSON message
      case (JSON) => {
        println("Processed request for sessionId: " + packet.data)
        //
        Enqueue("Processed request for sessionId: " + packet.data)
      }

      //Handle event
      case (EVENT) => {
        println("Processed request for sessionId: " + packet.data)
        // "Processed request for sessionId: " + eventData
        val parsedData: JsValue = Json.parse(packet.data)
        (parsedData \ "name").asOpt[String] match {
          case Some("my other event") => {
            val data = parsedData \ "args"
            //process data
            println(""""my other event" just happened for client: """ + sessionId + " data sent: " + data)
            enqueueJsonMsg(sessionId, "Processed request for sessionId: " + data)
          }
          case _ => 
            println("Unkown event happened.")
        } 
      }

    }

  }
}
