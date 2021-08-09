package part3_graphs

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, GraphDSL, RunnableGraph, Sink, Source}
import akka.stream.{BidiShape, ClosedShape, Materializer}

/**
 * User: patricio
 * Date: 9/8/21
 * Time: 03:04
 */
object BidirectionalFLows extends App {

  val system = ActorSystem("BidirectionalFlows")
  implicit val materializer: Materializer = Materializer(system)

  /*
  Example: crypto
   */

  def caesarEncrypt(n: Int)(string: String) = string.map(c => (c + n).toChar)

  def caesarDecrypt(n: Int)(string: String) = string.map(c => (c - n).toChar)

  println(caesarEncrypt(3)("Akka"))
  println(caesarDecrypt(3)("Dnnd"))

  // bidiFLow
  val bidiCryptoStaticGraph = GraphDSL.create() { implicit builder =>

    val encryptionFlowShape = builder.add(Flow[String].map(caesarEncrypt(3)))
    val decryptionFlowShape = builder.add(Flow[String].map(caesarDecrypt(3)))

    //BidiShape(encryptionFlowShape.in, encryptionFlowShape.out, decryptionFlowShape.in, decryptionFlowShape.out)
    BidiShape.fromFlows(encryptionFlowShape, decryptionFlowShape)
  }

  val unencryptedStrings = List("akka", "is", "awesome", "testing", "bidirectional", "flows")
  val source = Source(unencryptedStrings)

  val encryptedSource = Source(unencryptedStrings.map(caesarEncrypt(3)))

  val cryptoBidiGraph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val unencryptedSourceShape = builder.add(source)
    val encryptedSourceShape = builder.add(encryptedSource)
    val bidi = builder.add(bidiCryptoStaticGraph)
    val encryptedSyncShape = builder.add(Sink.foreach[String](string => println(s"Encrypted: $string")))
    val decryptedSyncShape = builder.add(Sink.foreach[String](string => println(s"Decrypted: $string")))

    unencryptedSourceShape ~> bidi.in1;
    bidi.out1 ~> encryptedSyncShape
    decryptedSyncShape <~ bidi.out2;
    bidi.in2 <~ encryptedSourceShape

    ClosedShape
  })

  cryptoBidiGraph.run()

  /**
   * - encrypting / decrypting
   * - encoding/decoding
   * - serializing/ deserializing
   */

}
