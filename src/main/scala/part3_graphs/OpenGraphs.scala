package part3_graphs

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Concat, Flow, GraphDSL, Sink, Source}
import akka.stream.{FlowShape, Materializer, SinkShape, SourceShape}

/**
 * User: patricio
 * Date: 2/8/21
 * Time: 11:18
 */
object OpenGraphs extends App {

  val system = ActorSystem("OpenGraph")
  implicit val materializer = Materializer(system)

  /*
    A composite source that concatenates 2 sources
    - emits ALL the elements from the first source
    - then ALL the elements from the second
   */

  val firstSource = Source(1 to 10)
  val secondSource = Source(42 to 1000)

  val sourceGraph = Source.fromGraph(GraphDSL.create(){ implicit builder =>
    import GraphDSL.Implicits._

    val concat = builder.add(Concat[Int](2))
    firstSource ~> concat
    secondSource ~> concat

    SourceShape(concat.out)

  })

  //sourceGraph.to(Sink.foreach(println)).run()

  /*
  Complex sink
   */
  val sink1 = Sink.foreach[Int](x => println(s"Meaningful sink 1: $x"))
  val sink2 = Sink.foreach[Int](x => println(s"Meaningful sink 2: $x"))

  val sinkGraph = Sink.fromGraph(GraphDSL.create(){ implicit builder =>
    import GraphDSL.Implicits._

    val broadcast = builder.add(Broadcast[Int](2))

    broadcast ~> sink1
    broadcast ~> sink2

    SinkShape(broadcast.in)
  })

  //firstSource.to(sinkGraph).run()

  /**
   * Challenge - complex flow?
   * Write your own flow that's composed of two other flows
   * - one that adds 1 to a number
   * - one that does number * 10
   */

  val addOneFLow = Flow[Int].map(_ + 1)
  val plusTen = Flow[Int].map(_ * 10)

  val complexFLow = Flow.fromGraph(GraphDSL.create(){ implicit builder =>

    import GraphDSL.Implicits._

    val flow1 = builder.add(addOneFLow)
    val flow2 = builder.add(plusTen)

    flow1 ~> flow2

    FlowShape(flow1.in, flow2.out)

  })

  //firstSource.via(complexFLow).to(Sink.foreach(println)).run()

  /**
  Exercise: flow from a sink and a source?
   */

  val fromSourceFlow = Flow.fromGraph(GraphDSL.create() { implicit builder =>

    import GraphDSL.Implicits._
    val source = builder.add(firstSource)
    val flowShape = builder.add(plusTen)
    val sinkShape = builder.add(sinkGraph)

    source ~> flowShape

    FlowShape(sinkShape.in, flowShape.out)
  })


  val f = Flow.fromSinkAndSourceCoupled(Sink.foreach[String](println), Source(1 to 10))


}
