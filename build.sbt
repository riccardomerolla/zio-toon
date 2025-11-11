ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.1"
ThisBuild / organization := "io.github.riccardomerolla"

lazy val root = (project in file("."))
  .settings(
    name := "toon4s",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % "2.1.9",
      "dev.zio" %% "zio-streams" % "2.1.9",
      "dev.zio" %% "zio-schema" % "1.5.0",
      "dev.zio" %% "zio-schema-derivation" % "1.5.0",
      "dev.zio" %% "zio-test" % "2.1.9" % Test,
      "dev.zio" %% "zio-test-sbt" % "2.1.9" % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )
