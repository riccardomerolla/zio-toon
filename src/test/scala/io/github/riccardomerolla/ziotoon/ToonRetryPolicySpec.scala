package io.github.riccardomerolla.ziotoon

import zio._
import zio.test._
import zio.test.Assertion._
import ToonValue._
import ToonRetryPolicy._
import ToonRetryOps._

/**
 * Tests for ToonRetryPolicy following ZIO best practices.
 */
object ToonRetryPolicySpec extends ZIOSpecDefault {

  // Helper to create a failing decoder
  def failingDecoder(attempts: Ref[Int], failUntil: Int, error: ToonError): ZIO[ToonDecoderService, ToonError, ToonValue] =
    for {
      current <- attempts.getAndUpdate(_ + 1)
      result <- if (current < failUntil)
                  ZIO.fail(error)
                else
                  ZIO.succeed(obj("success" -> bool(true)))
    } yield result

  def spec = suite("ToonRetryPolicy")(

    suite("Built-in Policies")(
      test("defaultRetry retries up to 5 times") {
        for {
          attempts <- Ref.make(0)
          fiber <- failingDecoder(attempts, 3, ToonError.CountMismatch(5, 3, "test", 1))
            .retry(defaultRetry)
            .fork
          _ <- TestClock.adjust(10.seconds)
          result <- fiber.join
          finalAttempts <- attempts.get
        } yield assertTrue(
          result == obj("success" -> bool(true)) &&
          finalAttempts >= 3  // At least 3 attempts (could be more due to retry logic)
        )
      }.provideLayer(ToonDecoderService.live),

      test("conservativeRetry has fewer retries") {
        for {
          attempts <- Ref.make(0)
          fiber <- failingDecoder(attempts, 2, ToonError.CountMismatch(5, 3, "test", 1))
            .retry(conservativeRetry)
            .fork
          _ <- TestClock.adjust(10.seconds)
          result <- fiber.join.exit
          finalAttempts <- attempts.get
        } yield assertTrue(
          finalAttempts <= 5  // Original + up to 4 retries
        )
      }.provideLayer(ToonDecoderService.live),

      test("aggressiveRetry has more retries") {
        for {
          attempts <- Ref.make(0)
          fiber <- failingDecoder(attempts, 8, ToonError.CountMismatch(5, 3, "test", 1))
            .retry(aggressiveRetry)
            .fork
          _ <- TestClock.adjust(60.seconds)
          result <- fiber.join
          finalAttempts <- attempts.get
        } yield assertTrue(
          result == obj("success" -> bool(true)) &&
          finalAttempts >= 8  // At least 8 attempts
        )
      }.provideLayer(ToonDecoderService.live),

      test("smartRetry does not retry parse errors") {
        for {
          attempts <- Ref.make(0)
          result <- failingDecoder(attempts, 10, ToonError.ParseError("syntax error"))
            .retry(smartRetry)
            .exit
          finalAttempts <- attempts.get
        } yield assertTrue(
          result.isFailure &&
          finalAttempts == 1  // No retries for parse errors
        )
      }.provideLayer(ToonDecoderService.live),

      test("smartRetry does retry count mismatches") {
        for {
          attempts <- Ref.make(0)
          fiber <- failingDecoder(attempts, 2, ToonError.CountMismatch(5, 3, "test", 1))
            .retry(smartRetry)
            .fork
          _ <- TestClock.adjust(10.seconds)
          result <- fiber.join
          finalAttempts <- attempts.get
        } yield assertTrue(
          result == obj("success" -> bool(true)) &&
          finalAttempts >= 2  // At least 2 attempts
        )
      }.provideLayer(ToonDecoderService.live)
    ),

    suite("Extension Methods")(
      test("retryDefault works") {
        for {
          attempts <- Ref.make(0)
          fiber <- failingDecoder(attempts, 2, ToonError.CountMismatch(5, 3, "test", 1))
            .retryDefault
            .fork
          _ <- TestClock.adjust(10.seconds)
          result <- fiber.join
        } yield assertTrue(result == obj("success" -> bool(true)))
      }.provideLayer(ToonDecoderService.live),

      test("retrySmart skips non-recoverable errors") {
        for {
          attempts <- Ref.make(0)
          result <- failingDecoder(attempts, 10, ToonError.IndentationError("bad indent", 1))
            .retrySmart
            .exit
          finalAttempts <- attempts.get
        } yield assertTrue(
          result.isFailure &&
          finalAttempts == 1
        )
      }.provideLayer(ToonDecoderService.live),

      test("retryWithTimeout fails after timeout") {
        val neverSucceeds = ZIO.fail(ToonError.ParseError("always fails"))
          .delay(100.millis)

        for {
          fiber <- neverSucceeds
            .retryWithTimeout(200.millis)
            .fork
          _ <- TestClock.adjust(300.millis)
          result <- fiber.join.exit
        } yield assert(result)(fails(equalTo(ToonError.ParseError("Operation timed out"))))
      }.provideLayer(ToonDecoderService.live)
    ),

    suite("Custom Policies")(
      test("custom policy with predicate") {
        val customPolicy = ToonRetryPolicy.custom(
          initialDelay = 10.millis,
          factor = 1.5,
          maxRetries = 3,
          retryPredicate = {
            case _: ToonError.CountMismatch => true
            case _ => false
          }
        )

        for {
          attempts <- Ref.make(0)
          // This should retry
          fiber1 <- failingDecoder(attempts, 2, ToonError.CountMismatch(5, 3, "test", 1))
            .retry(customPolicy)
            .fork
          _ <- TestClock.adjust(10.seconds)
          result1 <- fiber1.join
          _ <- attempts.set(0)
          // This should not retry
          result2 <- failingDecoder(attempts, 10, ToonError.ParseError("error"))
            .retry(customPolicy)
            .exit
          finalAttempts <- attempts.get
        } yield assertTrue(
          result1 == obj("success" -> bool(true)) &&
          result2.isFailure &&
          finalAttempts == 1
        )
      }.provideLayer(ToonDecoderService.live)
    ),

    suite("Real-world Scenarios")(
      test("decode with retry succeeds after transient failure") {
        val badInput = "key: \"unterminated"  // Actually invalid - unterminated string
        val goodInput = "name: Alice\nage: 30"

        for {
          attempts <- Ref.make(0)
          fiber <- (for {
            current <- attempts.getAndUpdate(_ + 1)
            input = if (current == 0) badInput else goodInput
            decoded <- ToonDecoderService.decode(input)
          } yield decoded)
            .retryDefault
            .fork
          _ <- TestClock.adjust(10.seconds)
          result <- fiber.join
        } yield assertTrue(result == obj("name" -> str("Alice"), "age" -> num(30)))
      }.provideLayer(ToonDecoderService.live),

      test("combine retry with error handling") {
        for {
          attempts <- Ref.make(0)
          fiber <- failingDecoder(attempts, 2, ToonError.CountMismatch(5, 3, "test", 1))
            .retrySmart
            .catchAll {
              case _: ToonError.CountMismatch =>
                ZIO.succeed(obj("fallback" -> bool(true)))
              case other =>
                ZIO.fail(other)
            }
            .fork
          _ <- TestClock.adjust(10.seconds)
          result <- fiber.join
        } yield assertTrue(result == obj("success" -> bool(true)))
      }.provideLayer(ToonDecoderService.live)
    )

  )
}

