ThisBuild / scalaVersion := "3.3.1"
ThisBuild / organization := "io.github.riccardomerolla"
ThisBuild / organizationName := "Riccardo Merolla"
ThisBuild / organizationHomepage := Some(url("https://github.com/riccardomerolla"))

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/riccardomerolla/zio-toon"),
    "scm:git@github.com:riccardomerolla/zio-toon.git"
  )
)

ThisBuild / developers := List(
  Developer(
    id = "riccardomerolla",
    name = "Riccardo Merolla",
    email = "riccardo.merolla@gmail.com",
    url = url("https://github.com/riccardomerolla")
  )
)

ThisBuild / licenses := List(
  "MIT" -> url("https://github.com/riccardomerolla/zio-toon/blob/main/LICENSE")
)

ThisBuild / homepage := Some(url("https://github.com/riccardomerolla/zio-toon"))

// Remove any "SNAPSHOT" in the version for proper Sonatype releases
ThisBuild / versionScheme := Some("early-semver")

// Sonatype publishing configuration for Sonatype Central (s01)
ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"
ThisBuild / publishTo := {
  val nexus = "https://s01.oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}
ThisBuild / publishMavenStyle := true

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


