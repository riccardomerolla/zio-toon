package io.github.riccardomerolla.ziotoon.benchmarks

import org.openjdk.jmh.annotations._
import java.util.concurrent.TimeUnit
import io.github.riccardomerolla.ziotoon._
import ToonValue._
import zio._

/**
 * JMH benchmarks for TOON encoding performance.
 * 
 * Run with: sbt "benchmarks/Jmh/run -i 3 -wi 3 -f 1"
 * 
 * Measures:
 * - Throughput (ops/second)
 * - Average time per operation
 * - Memory allocation
 */
@State(org.openjdk.jmh.annotations.Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput, Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class ToonEncoderBenchmark {
  
  val encoder = new ToonEncoder(EncoderConfig.default)
  val runtime = Runtime.default
  
  // Small object
  val smallObject = obj(
    "name" -> str("Alice"),
    "age" -> num(30),
    "active" -> bool(true)
  )
  
  // Medium object with nested structure
  val mediumObject = obj(
    "id" -> num(123),
    "user" -> obj(
      "name" -> str("Bob"),
      "email" -> str("bob@example.com"),
      "profile" -> obj(
        "bio" -> str("Software developer"),
        "location" -> str("San Francisco")
      )
    ),
    "tags" -> arr(str("scala"), str("zio"), str("functional")),
    "metadata" -> obj(
      "created" -> str("2025-01-01"),
      "updated" -> str("2025-01-15")
    )
  )
  
  // Large array
  val largeArray = Arr(Chunk.fromIterable(
    (1 to 100).map(i => obj(
      "id" -> num(i),
      "name" -> str(s"Item $i"),
      "value" -> num(i * 1.5)
    ))
  ))
  
  // Tabular array
  val tabularArray = Arr(Chunk.fromIterable(
    (1 to 50).map(i => obj(
      "id" -> num(i),
      "name" -> str(s"User $i"),
      "email" -> str(s"user$i@example.com"),
      "age" -> num(20 + (i % 50))
    ))
  ))
  
  @Benchmark
  def encodeSmallObject(): String = {
    encoder.encode(smallObject)
  }
  
  @Benchmark
  def encodeMediumObject(): String = {
    encoder.encode(mediumObject)
  }
  
  @Benchmark
  def encodeLargeArray(): String = {
    encoder.encode(largeArray)
  }
  
  @Benchmark
  def encodeTabularArray(): String = {
    encoder.encode(tabularArray)
  }
  
  @Benchmark
  def encodePrimitive(): String = {
    encoder.encode(str("Hello, World!"))
  }
  
  @Benchmark
  def encodeWithService(): String = {
    val program = ToonEncoderService.encode(mediumObject)
      .provide(ToonEncoderService.live)
    
    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.run(program).getOrThrowFiberFailure()
    }
  }
}

/**
 * JMH benchmarks for TOON decoding performance.
 */
@State(org.openjdk.jmh.annotations.Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput, Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class ToonDecoderBenchmark {
  
  val decoder = new ToonDecoder(DecoderConfig.default)
  val runtime = Runtime.default
  
  // Encoded samples
  val encodedSmallObject = """name: Alice
age: 30
active: true"""
  
  val encodedMediumObject = """id: 123
user:
  name: Bob
  email: bob@example.com
  profile:
    bio: "Software developer"
    location: "San Francisco"
tags[3]: scala,zio,functional
metadata:
  created: 2025-01-01
  updated: 2025-01-15"""
  
  val encodedTabularArray = """users[10]{id,name,age}:
  1,Alice,25
  2,Bob,30
  3,Charlie,35
  4,Diana,28
  5,Eve,32
  6,Frank,27
  7,Grace,29
  8,Henry,31
  9,Ivy,26
  10,Jack,33"""
  
  val encodedPrimitive = "\"Hello, World!\""
  
  @Benchmark
  def decodeSmallObject(): Either[ToonError, ToonValue] = {
    decoder.decode(encodedSmallObject)
  }
  
  @Benchmark
  def decodeMediumObject(): Either[ToonError, ToonValue] = {
    decoder.decode(encodedMediumObject)
  }
  
  @Benchmark
  def decodeTabularArray(): Either[ToonError, ToonValue] = {
    decoder.decode(encodedTabularArray)
  }
  
  @Benchmark
  def decodePrimitive(): Either[ToonError, ToonValue] = {
    decoder.decode(encodedPrimitive)
  }
  
  @Benchmark
  def decodeWithService(): ToonValue = {
    val program = ToonDecoderService.decode(encodedMediumObject)
      .provide(ToonDecoderService.live)
    
    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.run(program).getOrThrowFiberFailure()
    }
  }
}

/**
 * JMH benchmarks for round-trip performance.
 */
@State(org.openjdk.jmh.annotations.Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput, Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class ToonRoundTripBenchmark {
  
  val encoder = new ToonEncoder(EncoderConfig.default)
  val decoder = new ToonDecoder(DecoderConfig.default)
  val runtime = Runtime.default
  
  val testObject = obj(
    "id" -> num(123),
    "name" -> str("Test"),
    "nested" -> obj(
      "field1" -> str("value1"),
      "field2" -> num(42)
    ),
    "array" -> arr(str("a"), str("b"), str("c"))
  )
  
  @Benchmark
  def roundTripPure(): Either[ToonError, ToonValue] = {
    val encoded = encoder.encode(testObject)
    decoder.decode(encoded)
  }
  
  @Benchmark
  def roundTripWithService(): ToonValue = {
    val program = for {
      encoded <- ToonEncoderService.encode(testObject)
      decoded <- ToonDecoderService.decode(encoded)
    } yield decoded
    
    val layer = ToonEncoderService.live ++ ToonDecoderService.live
    
    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.run(program.provide(layer)).getOrThrowFiberFailure()
    }
  }
}

/**
 * JMH benchmarks comparing TOON with JSON.
 */
@State(org.openjdk.jmh.annotations.Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput, Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class ToonVsJsonBenchmark {
  
  val encoder = new ToonEncoder(EncoderConfig.default)
  val runtime = Runtime.default
  
  val testData = obj(
    "users" -> Arr(Chunk.fromIterable(
      (1 to 20).map(i => obj(
        "id" -> num(i),
        "name" -> str(s"User $i"),
        "email" -> str(s"user$i@example.com"),
        "active" -> bool(i % 2 == 0)
      ))
    ))
  )
  
  @Benchmark
  def encodeToon(): String = {
    encoder.encode(testData)
  }
  
  @Benchmark
  def encodeJson(): String = {
    val program = ToonJsonService.toJson(testData)
      .provide(ToonJsonService.live)
    
    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.run(program).getOrThrowFiberFailure()
    }
  }
  
  @Benchmark
  def calculateSavings(): TokenSavings = {
    val program = ToonJsonService.calculateSavings(testData)
      .provide(ToonJsonService.live ++ ToonEncoderService.live)
    
    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.run(program).getOrThrowFiberFailure()
    }
  }
}

