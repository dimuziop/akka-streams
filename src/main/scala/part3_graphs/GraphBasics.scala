package part3_graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Balance, Broadcast, Flow, GraphDSL, RunnableGraph, Sink, Source, Zip, Merge}
import akka.stream.{ClosedShape, Materializer}

import scala.language.postfixOps

/**
 * User: patricio
 * Date: 2/8/21
 * Time: 08:23
 */
object GraphBasics extends App {

  val system = ActorSystem("GraphBasics")
  implicit val materializer = Materializer(system)

  val input = Source(1 to 1000)
  val incrementer = Flow[Int].map(x => x + 1)
  val multiplier = Flow[Int].map(x => x * 10)

  val output = Sink.foreach[(Int, Int)](println)

  // step 1 - performing set up
  val graph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] => // builder = Mutable data structure
    import GraphDSL.Implicits._

    // step 2 -- adding the components
    val broadcast = builder.add(Broadcast[Int](2))
    val zip = builder.add(Zip[Int, Int])

    // step 3 -- tying up all the components
    input ~> broadcast
    broadcast.out(0) ~> incrementer ~> zip.in0
    broadcast.out(1) ~> multiplier ~> zip.in1

    zip.out ~> output

    //step 4 return the closed shape
    ClosedShape
    // shape
  } // graph
  ) // runnable graph

  //graph.run() // run and materialized it
  /**
   * exercise 1: feed a source into 2 sinks at the same time (hint: use a broadcast)
   */

  val exercise1 = RunnableGraph.fromGraph(GraphDSL.create(){ implicit builder: GraphDSL.Builder[NotUsed] =>
    import GraphDSL.Implicits._
    val broadcast = builder.add(Broadcast[Int](2))
    val sink1 = Sink.foreach[Int](x => println(s"Sink no 1: $x"))
    val sink2 = Sink.foreach[Int](x => println(s"Sink no 2: $x"))
    input ~> broadcast ~> sink1
             broadcast ~> sink2 // implicit por numbering :: NO SCALA IMPLICITS

    /*broadcast.out(0) ~> sink1
    broadcast.out(1) ~> sink2*/

    ClosedShape

  })

  //exercise1.run()

  /**
   * exercise 2: balance
   */

  import scala.concurrent.duration._
  val fastSource = input.throttle(5, 1 second)
  val slowSource = input.throttle(2, 1 second)
  val sink1 = Sink.foreach[Int](x => println(s"Sink no 1: $x"))
  val sink2 = Sink.foreach[Int](x => println(s"Sink no 2: $x"))
  val sink3 = Sink.fold[Int, Int](0)((prev, _) => {
    println(s"Sink 3 number of elements: $prev")
    prev + 1
  })

  val sink4 = Sink.fold[Int, Int](0)((prev, _) => {
    println(s"Sink 4 number of elements: $prev")
    prev + 1
  })


  val exercise2 = RunnableGraph.fromGraph(GraphDSL.create(){ implicit builder: GraphDSL.Builder[NotUsed] =>

    import GraphDSL.Implicits._

    val merged = builder.add(Merge[Int](2))
    val balanced = builder.add(Balance[Int](2))

    fastSource ~> merged ~>  balanced ~> sink3
    slowSource ~> merged
    balanced ~> sink4

    ClosedShape

  })

  exercise2.run()

}
