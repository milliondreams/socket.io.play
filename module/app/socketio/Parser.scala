package socketio

import scala.collection.immutable.ListMap
import util.matching.Regex

/**
 * Created with IntelliJ IDEA.
 * User: rohit
 * Date: 5/7/12
 * Time: 3:38 AM
 * To change this template use File | Settings | File Templates.
 */

case class Packet(val packetType: String="", val msgId: Int = 0, val ack: Boolean = false, val endpoint: String = "", val data: String = "")

object PacketTypes {
  final val DISCONNECT = "disconnect"
  final val CONNECT = "connect"
  final val HEARTBEAT = "heartbeat"
  final val MESSAGE = "message"
  final val JSON = "json"
  final val EVENT = "event"
  final val ACK = "ack"
  final val ERROR = "error"
  final val NOOP = "noop"

  //Using listmap as we need to maintain the original order
  val map = ListMap[String, Int](
    DISCONNECT -> 0,
    CONNECT -> 1,
    HEARTBEAT -> 2,
    MESSAGE -> 3,
    JSON -> 4,
    EVENT -> 5,
    ACK -> 6,
    ERROR -> 7,
    NOOP -> 8)

  val list: Array[String] = map.keySet.toArray

}

object Reasons {
  val map = Map[String, Int](
    "transport not supported" -> 0,
    "client not handshaken" -> 1,
    "unauthorized" -> 2);

  val list: Array[String] = map.keySet.toArray

}

object Advices {

  val map = Map[String, Int](
    "reconnect" -> 0
  )

  val list: Array[String] = map.keySet.toArray
}

object Parser {

  val regexp:Regex = """([^:]+):([0-9]+)?(\+)?:([^:]+)?:?([\s\S]*)?""".r

  def decodePacket(data: String) = {
    val regexp(ptype, pid, pack, pendpoint, pdata) = data

    Packet(
      packetType = PacketTypes.list(ptype.toInt),
      msgId = Option(pid).getOrElse("0").toInt,
      ack = !(Option(pack).getOrElse("").isEmpty),
      endpoint = Option(pendpoint).getOrElse(""),
      data = Option(pdata).getOrElse(""))
  }

  def encodePacket(packet:Packet) = {
    val msgId:String = if(packet.msgId != 0)  packet.msgId.toString() else ""

    PacketTypes.map(packet.packetType) + ":" + msgId + ":" + packet.endpoint + ":" + packet.data
  }

}



