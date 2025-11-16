package io.github.riccardomerolla.ziotoon.cli

import io.github.riccardomerolla.ziotoon._
import zio._
import zio.test._
import zio.test.Assertion._
import zio.test.TestConsole

import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path }
import java.util.Comparator

object MainSpec extends ZIOSpecDefault {

  private val sampleJson =
    """{"user":{"id":42,"name":"Toonie"},"tags":["cli","test"],"active":true}"""

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("Main CLI")(
      test("encode command produces TOON matching original JSON") {
        withTempDir { dir =>
          val input  = dir.resolve("input.json")
          val output = dir.resolve("output.toon")
          for {
            _        <- ZIO.attempt(Files.writeString(input, sampleJson, StandardCharsets.UTF_8))
            result   <- Main.cliApp
                          .run(List("encode", "--output", output.toString, input.toString))
                          .provideSomeLayer[TestEnvironment](ToonJsonService.live)
                          .mapError(err => new RuntimeException(err.toString))
            encoded  <- ZIO.attempt(Files.readString(output, StandardCharsets.UTF_8))
            decoded  <- ZIO
                          .fromEither(new ToonDecoder().decode(encoded))
                          .mapError(err => new RuntimeException(err.message))
            expected <- ToonJsonService
                          .fromJson(sampleJson)
                          .mapError(err => new RuntimeException(err))
                          .provideSomeLayer[TestEnvironment](ToonJsonService.live)
          } yield assertTrue(result.contains(())) && assertTrue(decoded == expected)
        }
      },
      test("decode command produces JSON matching original TOON") {
        withTempDir { dir =>
          val input  = dir.resolve("input.toon")
          val output = dir.resolve("decoded.json")
          for {
            value       <- ToonJsonService
                             .fromJson(sampleJson)
                             .mapError(err => new RuntimeException(err))
                             .provideSomeLayer[TestEnvironment](ToonJsonService.live)
            toonPayload <- ZIO.succeed(new ToonEncoder().encode(value))
            _           <- ZIO.attempt(Files.writeString(input, toonPayload, StandardCharsets.UTF_8))
            result      <- Main.cliApp
                             .run(List("decode", "--output", output.toString, input.toString))
                             .provideSomeLayer[TestEnvironment](ToonJsonService.live)
                             .mapError(err => new RuntimeException(err.toString))
            json        <- ZIO.attempt(Files.readString(output, StandardCharsets.UTF_8))
            decoded     <- ToonJsonService
                             .fromJson(json)
                             .mapError(err => new RuntimeException(err))
                             .provideSomeLayer[TestEnvironment](ToonJsonService.live)
          } yield assertTrue(result.contains(())) && assertTrue(decoded == value)
        }
      },
      test("encode --stats emits token telemetry to console") {
        withTempDir { dir =>
          val input  = dir.resolve("stats-input.json")
          val output = dir.resolve("stats-output.toon")
          for {
            _      <- ZIO.attempt(Files.writeString(input, sampleJson, StandardCharsets.UTF_8))
            _      <- TestConsole.clearOutput
            _      <- Main.cliApp
                        .run(
                          List(
                            "encode",
                            "--output",
                            output.toString,
                            "--stats",
                            "true",
                            input.toString,
                          )
                        )
                        .provideSomeLayer[TestEnvironment](ToonJsonService.live)
            lines  <- TestConsole.output
          } yield assertTrue(lines.exists(_.contains("[stats] tokenizer=")))
        }
      },
      test("encode --optimize triggers optimizer stats even without --stats flag") {
        withTempDir { dir =>
          val input  = dir.resolve("opt-input.json")
          val output = dir.resolve("opt-output.toon")
          for {
            _      <- ZIO.attempt(Files.writeString(input, sampleJson, StandardCharsets.UTF_8))
            _      <- TestConsole.clearOutput
            _      <- Main.cliApp
                        .run(
                          List(
                            "encode",
                            "--output",
                            output.toString,
                            "--optimize",
                            "true",
                            input.toString,
                          )
                        )
                        .provideSomeLayer[TestEnvironment](ToonJsonService.live)
            lines  <- TestConsole.output
          } yield assertTrue(lines.exists(_.contains("[stats] tokenizer=")))
        }
      },
    )

  private def withTempDir[R, E, A](use: Path => ZIO[R, E, A]): ZIO[R, E | Throwable, A] =
    ZIO.acquireReleaseWith(ZIO.attempt(Files.createTempDirectory("zio-toon-cli-test")))(cleanupDirectory)(use)

  private def cleanupDirectory(path: Path): ZIO[Any, Nothing, Unit] =
    ZIO
      .attemptBlocking {
        if (Files.exists(path)) {
          val stream = Files.walk(path)
          try stream.sorted(Comparator.reverseOrder()).forEach(Files.deleteIfExists)
          finally stream.close()
        }
      }
      .ignore
}
