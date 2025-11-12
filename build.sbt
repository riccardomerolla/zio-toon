ThisBuild / scalaVersion := "3.3.1"
ThisBuild / organization := "io.github.riccardomerolla"
ThisBuild / organizationName := "Riccardo Merolla"
ThisBuild / organizationHomepage := Some(url("https://github.com/riccardomerolla"))

inThisBuild(List(
  organization := "io.github.riccardomerolla",
  homepage := Some(url("https://github.com/riccardomerolla/zio-toon")),
  licenses := List(
    "MIT" -> url("https://github.com/riccardomerolla/zio-toon/blob/main/LICENSE")
  ),
  developers := List(
    Developer(
      id = "riccardomerolla",
      name = "Riccardo Merolla",
      email = "riccardo.merolla@gmail.com",
      url = url("https://github.com/riccardomerolla")
    )
  )
))

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/riccardomerolla/zio-toon"),
    "scm:git@github.com:riccardomerolla/zio-toon.git"
  )
)

// Remove any "SNAPSHOT" in the version for proper Sonatype releases
ThisBuild / versionScheme := Some("early-semver")

lazy val root = (project in file("."))
  .settings(
    name := "zio-toon",
    description := "A Scala 3 / ZIO 2.x implementation of TOON (Token-Oriented Object Notation), a compact serialization format optimized to reduce token usage when interacting with LLMs",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % "2.1.22",
      "dev.zio" %% "zio-streams" % "2.1.22",
      "dev.zio" %% "zio-schema" % "1.7.5",
      "dev.zio" %% "zio-schema-derivation" % "1.7.5",
      "dev.zio" %% "zio-schema-json" % "1.7.5",
      "dev.zio" %% "zio-json" % "0.7.45",
      "dev.zio" %% "zio-test" % "2.1.22" % Test,
      "dev.zio" %% "zio-test-sbt" % "2.1.22" % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

// Benchmark subproject with JMH
lazy val benchmarks = (project in file("benchmarks"))
  .enablePlugins(JmhPlugin)
  .dependsOn(root)
  .settings(
    name := "zio-toon-benchmarks",
    publish / skip := true,
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % "2.1.22"
    )
  )


