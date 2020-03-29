name := "jsoniter-scala-null-issue"

version := "0.1"

scalaVersion := "2.13.1"

val jsoniterScalaVersion = "2.1.9"
val fs2Version = "2.3.0"
val circeVersion = "0.13.0"

libraryDependencies ++= Seq(
  "co.fs2" %% "fs2-core" % "2.3.0",
  "co.fs2" %% "fs2-io" % "2.3.0"
)

libraryDependencies ++= Seq(
  "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core"   % jsoniterScalaVersion,
  "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % jsoniterScalaVersion // % "compile-internal" // or "provided", but it is required only in compile-time
)

libraryDependencies ++= Seq(
  "io.circe" %% "circe-fs2",
  "io.circe" %% "circe-generic"
).map(_ % circeVersion)

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.8" % Test
