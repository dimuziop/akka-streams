name := "akka_streams"

version := "0.1"

scalaVersion := "2.13.6"


lazy val akkaVersion = "2.6.15"
lazy val scalaTestVersion = "3.2.9"
lazy val onionsVersion = "1.0.1"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "org.scalactic" %% "scalactic" % scalaTestVersion,
  "org.scalatest" %% "scalatest" % scalaTestVersion % Test,
  "com.lightbend.akka" %% "akka-stream-alpakka-mqtt" % "3.0.2",
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "org.openjdk.jol" % "jol-core" % "0.16"
)

/*libraryDependencies ++= Seq(
  "net.team2xh" %% "onions" % onionsVersion,
  "net.team2xh" %% "scurses" % onionsVersion
)*/
