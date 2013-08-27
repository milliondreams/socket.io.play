package controllers


import play.api.libs.json._


object MySocketIOController extends SocketIOController {

  def processMessage(sessionId: String, packet: Packet) {
    packet.packetType match {

      case ("message") =>
        println("Processed request for sessionId: " + packet.data)
        Enqueue("Processed request for sessionId: " + packet.data)

      case ("connect") =>
        println("Processed request for sessionId: " + packet.data)

      case ("json") =>
        println("Processed request for sessionId: " + packet.data)
        Enqueue("Processed request for sessionId: " + packet.data)

      case ("event") =>
        println("Processed request for sessionId: " + packet.data)
        val parsedData: JsValue = Json.parse(packet.data)
        (parsedData \ "name").asOpt[String] match {
          case Some("my other event") =>
            val data = parsedData \ "args"
            println( """"my other event" just happened for client: """ + sessionId + " data sent: " + data)
            enqueueJsonMsg(sessionId, "Processed request for sessionId: " + data)

          case _ =>
            println("Unkown event happened.")
        }
    }

  }

}
