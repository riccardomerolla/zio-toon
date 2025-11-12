package io.github.riccardomerolla.ziotoon

import zio._

/** Built-in retry policies for common TOON error scenarios.
  *
  * Following ZIO best practices:
  *   - Retry policies are composable schedules
  *   - Exponential backoff with jitter
  *   - Smart retry based on error type
  *   - Bounded retries to prevent infinite loops
  *
  * ==Usage==
  *
  * {{{
  * import ToonRetryPolicy._
  *
  * // Retry decoding with exponential backoff
  * ToonDecoderService.decode(input)
  *   .retry(defaultRetry)
  *
  * // Smart retry based on error type
  * ToonDecoderService.decode(input)
  *   .retry(smartRetry)
  *
  * // Custom retry with logging
  * ToonDecoderService.decode(input)
  *   .retryN(3)
  *   .tapError(err => ZIO.logWarning(s"Retry failed: ${err.message}"))
  * }}}
  */
object ToonRetryPolicy {

  /** Default retry policy for TOON operations.
    *
    *   - Exponential backoff starting at 100ms
    *   - Maximum 5 retries
    *   - Backs off up to 10 seconds
    */
  val defaultRetry: Schedule[Any, ToonError, ToonError] =
    (Schedule.exponential(100.millis, 2.0) && Schedule.recurs(5))
      .as(identity[ToonError] _)
      .asInstanceOf[Schedule[Any, ToonError, ToonError]]

  /** Conservative retry policy with fewer retries.
    *
    *   - Exponential backoff starting at 50ms
    *   - Maximum 3 retries
    *   - Useful for non-critical operations
    */
  val conservativeRetry: Schedule[Any, ToonError, ToonError] =
    (Schedule.exponential(50.millis, 2.0) && Schedule.recurs(3))
      .as(identity[ToonError] _)
      .asInstanceOf[Schedule[Any, ToonError, ToonError]]

  /** Aggressive retry policy for critical operations.
    *
    *   - Exponential backoff starting at 10ms
    *   - Maximum 10 retries
    *   - Useful when operation must succeed
    */
  val aggressiveRetry: Schedule[Any, ToonError, ToonError] =
    (Schedule.exponential(10.millis, 2.0) && Schedule.recurs(10))
      .as(identity[ToonError] _)
      .asInstanceOf[Schedule[Any, ToonError, ToonError]]

  /** Smart retry policy that only retries recoverable errors.
    *
    * Does NOT retry:
    *   - Parse errors (syntax is wrong)
    *   - Indentation errors (structure is wrong)
    *   - Missing colon errors (malformed)
    *
    * These errors won't be fixed by retrying.
    */
  val smartRetry: Schedule[Any, ToonError, ToonError] =
    (Schedule.recurWhile[ToonError] {
      case _: ToonError.ParseError         => false
      case _: ToonError.SyntaxError        => false
      case _: ToonError.IndentationError   => false
      case _: ToonError.MissingColon       => false
      case _: ToonError.InvalidEscape      => false
      case _: ToonError.UnterminatedString => false
      case _                               => true // Retry count/width mismatches which might be transient
    } && (Schedule.exponential(100.millis, 2.0) && Schedule.recurs(3)))
      .as(identity[ToonError] _)
      .asInstanceOf[Schedule[Any, ToonError, ToonError]]

  /** Retry policy with exponential backoff and jitter.
    *
    * Jitter prevents thundering herd when many operations retry simultaneously.
    *
    *   - Exponential backoff with random jitter
    *   - Maximum 5 retries
    *   - Delays between 50ms and computed exponential value
    */
  val jitteredRetry: Schedule[Any, ToonError, ToonError] =
    (Schedule.exponential(100.millis, 2.0) && Schedule.recurs(5))
      .jittered
      .as(identity[ToonError] _)
      .asInstanceOf[Schedule[Any, ToonError, ToonError]]

  /** Retry policy with fixed delays.
    *
    *   - Fixed 200ms delay between retries
    *   - Maximum 3 retries
    *   - Useful when you want predictable timing
    */
  val fixedDelayRetry: Schedule[Any, ToonError, ToonError] =
    (Schedule.fixed(200.millis) && Schedule.recurs(3))
      .as(identity[ToonError] _)
      .asInstanceOf[Schedule[Any, ToonError, ToonError]]

  /** Retry policy with custom logging.
    *
    * Logs each retry attempt with error details.
    *
    * @param maxRetries
    *   Maximum number of retries
    * @return
    *   Schedule that logs retry attempts
    */
  def retryWithLogging(maxRetries: Int = 5): Schedule[Any, ToonError, ToonError] =
    (Schedule.exponential(100.millis, 2.0) && Schedule.recurs(maxRetries))
      .tapInput[Any, ToonError] { (err: ToonError) =>
        ZIO.logWarning(s"Retry after error: ${err.message}")
      }
      .as(identity[ToonError] _)
      .asInstanceOf[Schedule[Any, ToonError, ToonError]]

  /** Create a custom retry policy.
    *
    * @param initialDelay
    *   Initial delay before first retry
    * @param factor
    *   Exponential backoff factor
    * @param maxRetries
    *   Maximum number of retries
    * @param retryPredicate
    *   Predicate to determine if error should be retried
    * @return
    *   Custom schedule
    */
  def custom(
      initialDelay: Duration = 100.millis,
      factor: Double = 2.0,
      maxRetries: Int = 5,
      retryPredicate: ToonError => Boolean = _ => true,
    ): Schedule[Any, ToonError, ToonError] =
    (Schedule.recurWhile[ToonError](retryPredicate) &&
      (Schedule.exponential(initialDelay, factor) && Schedule.recurs(maxRetries)))
      .as(identity[ToonError] _)
      .asInstanceOf[Schedule[Any, ToonError, ToonError]]
}

/** Extension methods for adding retry policies to TOON effects.
  *
  * ==Usage==
  *
  * {{{
  * import ToonRetryPolicy.ToonRetryOps
  *
  * ToonDecoderService.decode(input)
  *   .retryDefault
  *   .timeoutFail(ToonError.ParseError("Timeout"))(30.seconds)
  * }}}
  */
object ToonRetryOps {

  implicit class ToonEffectRetryOps[R, A](effect: ZIO[R, ToonError, A]) {

    /** Retry with default policy.
      */
    def retryDefault: ZIO[R, ToonError, A] =
      effect.retry(ToonRetryPolicy.defaultRetry)

    /** Retry with conservative policy.
      */
    def retryConservative: ZIO[R, ToonError, A] =
      effect.retry(ToonRetryPolicy.conservativeRetry)

    /** Retry with aggressive policy.
      */
    def retryAggressive: ZIO[R, ToonError, A] =
      effect.retry(ToonRetryPolicy.aggressiveRetry)

    /** Retry with smart policy (only recoverable errors).
      */
    def retrySmart: ZIO[R, ToonError, A] =
      effect.retry(ToonRetryPolicy.smartRetry)

    /** Retry with jittered backoff.
      */
    def retryJittered: ZIO[R, ToonError, A] =
      effect.retry(ToonRetryPolicy.jitteredRetry)

    /** Retry with fixed delay.
      */
    def retryFixed: ZIO[R, ToonError, A] =
      effect.retry(ToonRetryPolicy.fixedDelayRetry)

    /** Retry with logging of attempts.
      */
    def retryWithLogging(maxRetries: Int = 5): ZIO[R, ToonError, A] =
      effect.retry(ToonRetryPolicy.retryWithLogging(maxRetries))

    /** Retry with timeout.
      *
      * @param timeout
      *   Maximum time to spend retrying
      * @param timeoutError
      *   Error to fail with on timeout
      */
    def retryWithTimeout(
        timeout: Duration,
        timeoutError: ToonError = ToonError.ParseError("Operation timed out"),
      ): ZIO[R, ToonError, A] =
      effect
        .retry(ToonRetryPolicy.defaultRetry)
        .timeoutFail(timeoutError)(timeout)
  }
}
