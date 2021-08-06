package part3_graphs

import akka.actor.ActorSystem
import akka.stream.javadsl.RunnableGraph
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Sink, Source, ZipWith}
import akka.stream.{ClosedShape, FanOutShape2, Materializer, UniformFanInShape}

import java.util.{Date, UUID}

/**
 * User: patricio
 * Date: 6/8/21
 * Time: 12:05
 */
object MoreOpenGraphs extends App {

  val system = ActorSystem("MoreOpenGraphs")
  /*
  Example: Max3 Operator
  - 3 inputs of type int
  - the maximum of the 3
   */

  val max3StaticGraph = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    val max1 = builder.add(ZipWith[Int, Int, Int]((a, b) => Math.max(a, b)))
    val max2 = builder.add(ZipWith[Int, Int, Int]((a, b) => Math.max(a, b)))

    max1.out ~> max2.in0

    UniformFanInShape(max2.out, max1.in0, max1.in1, max2.in1)
  }

  val source = Source(1 to 10)
  val source2 = Source(1 to 10).map(_ => 5)
  val source3 = Source((1 to 10).reverse)

  val maxSink = Sink.foreach[Int](x => println(s"Max is: $x"))

  val max3RunnableGraph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    val max3Shape = builder.add(max3StaticGraph)
    source ~> max3Shape.in(0)
    source2 ~> max3Shape.in(1)
    source3 ~> max3Shape.in(2)
    max3Shape.out ~> maxSink

    ClosedShape
  })
  implicit val materializer: Materializer = Materializer(system)
  max3RunnableGraph.run(materializer)

  // same for UniformFanOutShape
  /*
  Non-uniform fan out shape
  Processing bank transactions > 10000

  Streams component for txns
  - output1: let the transactions go through
  - output2: suspicions txn idx
   */


  case class Transaction(id: String, source: String, recipient: String, amount: Int, date: Date)

  val transactionSource = Source(List(
    Transaction(UUID.randomUUID().toString, "Homero", "Bart", 100, new Date),
    Transaction(UUID.randomUUID().toString, "Marge", "Barney", 100000, new Date),
    Transaction(UUID.randomUUID().toString, "Lisa", "Dr. Hibert", 7000, new Date)
  ))

  val bankProcessor = Sink.foreach[Transaction](println)
  val suspiciousAnalysisService = Sink.foreach[String](txnId => println(s"Suspicious transaction ID: $txnId"))

  val suspiciousTxnStaticGraph = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    val broadcast = builder.add(Broadcast[Transaction](2))
    val suspiciousTxnFilter = builder.add(Flow[Transaction].filter(txn => txn.amount > 10000))
    val txnIdExtractor = builder.add(Flow[Transaction].map[String](txn => txn.id))

    broadcast.out(0) ~> suspiciousTxnFilter ~> txnIdExtractor

    new FanOutShape2(broadcast.in, broadcast.out(1), txnIdExtractor.out)
  }

  val suspiciousTXRunnableGraph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    val suspiciousTxnShape = builder.add(suspiciousTxnStaticGraph)
    transactionSource ~> suspiciousTxnShape.in
    suspiciousTxnShape.out0 ~> bankProcessor
    suspiciousTxnShape.out1 ~> suspiciousAnalysisService
    ClosedShape
  })

  suspiciousTXRunnableGraph.run(materializer)

}
