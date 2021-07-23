package playground

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source

/**
 * User: patricio
 * Date: 23/7/21
 * Time: 15:49
 */
object Playground extends App {

  implicit val actorSystem = ActorSystem("Playground")
  implicit val actorMaterializer = Materializer(actorSystem)

  Source.single("hello, Streams!").to(Sink.foreach(println)).run()

}
