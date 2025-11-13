# zio-toon

A Scala 3 / ZIO 2.x implementation of TOON (Token-Oriented Object Notation), a compact serialization format optimized to reduce token usage when interacting with Large Language Models (LLMs).

**Built with ZIO Best Practices**: Effect-oriented programming, service pattern, type-safe error handling, composable dependency injection, streaming support, and retry policies.

## What is TOON?

TOON is a line-oriented, indentation-based text format that encodes the JSON data model with explicit structure and minimal quoting. It typically reduces token usage by 30-60% compared to JSON, making it ideal for LLM interactions where context window and token costs matter.

### Key Features

- **Token-efficient**: 30-60% fewer tokens than JSON
- **Explicit schema**: Array lengths and field names declared once
- **Minimal syntax**: Eliminates redundant punctuation (braces, brackets, quotes)
- **Tabular arrays**: CSV-like format for uniform data sets
- **Human-readable**: Indentation-based structure similar to YAML
- **Type-safe**: Full Scala 3 implementation with ADTs
- **ZIO-first**: Service pattern, ZLayer DI, typed errors, effect composition
- **JSON Integration**: Bidirectional conversion with token savings calculation
- **Schema Support**: Automatic encoding/decoding with zio-schema
- **Streaming**: Memory-efficient processing with ZStream
- **Retry Policies**: Built-in resilience strategies
- **Benchmarks**: JMH performance testing included

## Installation

Add to your `build.sbt`:

```scala
libraryDependencies += "io.github.riccardomerolla" %% "zio-toon" % "0.1.0-SNAPSHOT"
```

**Dependencies:**
- Scala 3.3.1+
- ZIO 2.1.22
- zio-streams 2.1.22
- zio-schema 1.7.5
- zio-json 0.7.45

## Quick Start

### Pure API (No ZIO Required)

For simple use cases without ZIO effects:

```scala
import io.github.riccardomerolla.ziotoon._
import ToonValue._

// Create a ToonValue
val person = obj(
  "name" -> str("Alice"),
  "age" -> num(30),
  "active" -> bool(true)
)

// Pure encoding - returns String
val toonString = Toon.encode(person)
println(toonString)
// Output:
// name: Alice
// age: 30
// active: true

// Pure decoding - returns Either[ToonError, ToonValue]
val result = Toon.decode(toonString)
result match {
  case Right(value) => println(s"Decoded: $value")
  case Left(error) => println(s"Error: ${error.message}")
}
```

### Effect API (ZIO Services)

For applications using ZIO:

```scala
import io.github.riccardomerolla.ziotoon._
import ToonValue._
import zio._

// Define your application logic using services
val program = for {
  // Services are accessed from the environment
  encoded <- Toon.encode(obj("name" -> str("Bob")))
  _       <- Console.printLine(s"Encoded: $encoded")
  
  decoded <- Toon.decode(encoded)
  _       <- Console.printLine(s"Decoded: $decoded")
} yield decoded

// Provide services at application entry point
object MyApp extends ZIOAppDefault {
  def run = program.provide(Toon.live)
}
```

### Custom Configuration

```scala
// Custom encoder/decoder configuration
val customConfig = Toon.configured(
  encoderConfig = EncoderConfig(
    indentSize = 4,
    delimiter = Delimiter.Tab
  ),
  decoderConfig = DecoderConfig(
    strictMode = false,
    indentSize = 4
  )
)

// Use custom configuration
program.provide(customConfig)
```

## Core Features

### 1. Tabular Arrays - The Killer Feature

TOON excels at encoding arrays of uniform objects:

```scala
val users = arr(
  obj("id" -> num(1), "name" -> str("Alice"), "role" -> str("admin")),
  obj("id" -> num(2), "name" -> str("Bob"), "role" -> str("user")),
  obj("id" -> num(3), "name" -> str("Charlie"), "role" -> str("developer"))
)

Toon.encode(obj("users" -> users))
```

**Output:**
```
users[3]{id,name,role}:
  1,Alice,admin
  2,Bob,user
  3,Charlie,developer
```

**Compare with JSON (222 chars vs 101 chars - 55% reduction!):**
```json
{"users":[{"id":1,"name":"Alice","role":"admin"},{"id":2,"name":"Bob","role":"user"},{"id":3,"name":"Charlie","role":"developer"}]}
```

### 2. JSON Integration

Seamless conversion between JSON and TOON with token savings calculation:

```scala
import io.github.riccardomerolla.ziotoon._

val program = for {
  // Convert JSON to TOON
  value <- ToonJsonService.fromJson("""{"name":"Alice","age":30}""")
  toon <- ToonEncoderService.encode(value)
  _ <- Console.printLine(s"TOON: $toon")
  
  // Calculate token savings
  savings <- ToonJsonService.calculateSavings(value)
  _ <- Console.printLine(f"Saved ${savings.savingsPercent}%.1f%% tokens")
  
  // Convert back to JSON
  json <- ToonJsonService.toJson(value)
  _ <- Console.printLine(s"JSON: $json")
} yield ()

// Run with layers
program.provide(
  ToonJsonService.live,
  ToonEncoderService.live,
  ToonDecoderService.live
)
```

**TokenSavings** provides:
- `jsonSize: Int` - JSON character count
- `toonSize: Int` - TOON character count  
- `savings: Int` - Absolute character difference
- `savingsPercent: Double` - Percentage saved

### 3. Schema-Based Type-Safe Encoding

Automatically encode/decode case classes using zio-schema:

```scala
import io.github.riccardomerolla.ziotoon._
import zio._
import zio.schema._

case class Person(name: String, age: Int, email: String)

object Person {
  given schema: Schema[Person] = DeriveSchema.gen[Person]
}

val program = for {
  // Encode case class to TOON
  person = Person("Alice", 30, "alice@example.com")
  toonString <- ToonSchemaService.encode(person)
  _ <- Console.printLine(toonString)
  
  // Decode TOON to case class
  decoded <- ToonSchemaService.decode[Person](toonString)
  _ <- Console.printLine(s"Decoded: $decoded")
} yield decoded

// Provide layers (ToonSchemaService depends on ToonJsonService)
program.provide(
  ToonJsonService.live,
  ToonSchemaService.live
)
```

### 4. Streaming Support

Memory-efficient processing of large documents with ZStream:

```scala
import io.github.riccardomerolla.ziotoon._
import zio.stream._

val program = for {
  // Stream encode - process values one by one
  values = ZStream(
    obj("id" -> num(1), "name" -> str("Alice")),
    obj("id" -> num(2), "name" -> str("Bob")),
    obj("id" -> num(3), "name" -> str("Charlie"))
  )
  
  encoded <- ToonStreamService.encodeStream(values).runCollect
  _ <- Console.printLine(s"Encoded ${encoded.size} items")
  
  // Stream decode - process line by line
  input = ZStream.fromIterable(encoded)
  decoded <- ToonStreamService.decodeStream(input).runCollect
  _ <- Console.printLine(s"Decoded ${decoded.size} items")
  
  // Round-trip validation
  roundTripped <- ToonStreamService.roundTripStream(values).runCollect
  _ <- Console.printLine(s"Round-trip: ${roundTripped.size} items")
} yield ()

// Provide streaming layers
program.provide(
  ToonStreamService.live,
  ToonEncoderService.live,
  ToonDecoderService.live
)
```

**Stream Operations:**
- `encodeStream` - Encode stream of values to TOON strings
- `decodeStream` - Decode stream of TOON strings to values
- `encodeArrayStream` - Encode stream as single TOON array
- `decodeLineStream` - Decode line-delimited TOON documents
- `roundTripStream` - Validate encode/decode round-trip

### 5. Retry Policies

Built-in resilience strategies for error handling:

```scala
import io.github.riccardomerolla.ziotoon._
import ToonRetryPolicy._

// Retry with default exponential backoff
val safeProgram = ToonDecoderService.decode(unreliableInput)
  .retry(defaultRetry)

// Smart retry - only retries recoverable errors
val smartProgram = ToonDecoderService.decode(input)
  .retry(smartRetry) // Skips parse errors, retries transient failures

// Using extension methods
import ToonRetryOps._

val program = ToonDecoderService.decode(input)
  .retryDefault              // 5 retries with exponential backoff
  .timeoutFail(TimeoutError)(30.seconds)
  .catchAll {
    case ToonError.ParseError(msg) => fallbackValue
    case other => ZIO.fail(other)
  }
```

**Available Policies:**
- `defaultRetry` - 5 retries, exponential backoff (100ms start)
- `conservativeRetry` - 3 retries, exponential backoff (50ms start)
- `aggressiveRetry` - 10 retries, exponential backoff (10ms start)
- `smartRetry` - Only retries recoverable errors
- `jitteredRetry` - Exponential backoff with jitter
- `fixedDelayRetry` - Fixed 200ms delay between retries
- `custom()` - Build your own policy

**Extension Methods:**
- `.retryDefault` - Apply default retry
- `.retrySmart` - Apply smart retry
- `.retryWithTimeout(duration)` - Retry with timeout

### 6. Performance Benchmarks

JMH benchmarks included for performance testing:

```bash
# Run all benchmarks
sbt "benchmarks/Jmh/run"

# Run specific benchmark
sbt "benchmarks/Jmh/run ToonEncoderBenchmark"

# Custom parameters (5 warmup, 10 iterations, 2 forks)
sbt "benchmarks/Jmh/run -wi 5 -i 10 -f 2"
```

**Benchmark Suites:**
- `ToonEncoderBenchmark` - Encoding performance
- `ToonDecoderBenchmark` - Decoding performance
- `ToonRoundTripBenchmark` - Full encode/decode cycles
- `ToonVsJsonBenchmark` - TOON vs JSON comparison

## API Reference

### Services

#### ToonEncoderService
Encodes `ToonValue` to TOON format strings.

```scala
trait ToonEncoderService {
  def encode(value: ToonValue): UIO[String]
}

// Usage
ToonEncoderService.encode(value)
  .provide(ToonEncoderService.live)
```

#### ToonDecoderService
Decodes TOON format strings to `ToonValue`.

```scala
trait ToonDecoderService {
  def decode(input: String): IO[ToonError, ToonValue]
}

// Usage with error handling
ToonDecoderService.decode(toonString)
  .catchAll {
    case ToonError.ParseError(msg) => 
      ZIO.logError(s"Parse error: $msg") *> ZIO.succeed(ToonValue.Null)
    case ToonError.IndentationError(msg, line) =>
      ZIO.logError(s"Bad indent at line $line") *> ZIO.fail(error)
  }
  .provide(ToonDecoderService.live)
```

#### ToonJsonService
Bidirectional conversion between JSON and TOON.

```scala
trait ToonJsonService {
  def toJson(value: ToonValue): UIO[String]
  def fromJson(json: String): IO[String, ToonValue]
  def calculateSavings(value: ToonValue): URIO[ToonEncoderService, TokenSavings]
  def toPrettyJson(value: ToonValue, indent: Int): UIO[String]
}
```

#### ToonSchemaService
Type-safe encoding/decoding with zio-schema.

```scala
trait ToonSchemaService {
  def encode[A: Schema](value: A): IO[String, String]
  def decode[A: Schema](toonString: String): IO[String, A]
  def encodeWithConfig[A: Schema](value: A, config: EncoderConfig): IO[String, String]
  def decodeWithConfig[A: Schema](toonString: String, config: DecoderConfig): IO[String, A]
}
```

#### ToonStreamService
Memory-efficient streaming operations.

```scala
trait ToonStreamService {
  def encodeStream(values: ZStream[Any, Nothing, ToonValue]): ZStream[Any, Nothing, String]
  def decodeStream(input: ZStream[Any, Nothing, String]): ZStream[Any, ToonError, ToonValue]
  def encodeArrayStream(elements: ZStream[Any, Nothing, ToonValue], key: Option[String]): ZStream[Any, Nothing, String]
  def decodeLineStream(lines: ZStream[Any, Nothing, String]): ZStream[Any, ToonError, ToonValue]
  def roundTripStream(values: ZStream[Any, Nothing, ToonValue]): ZStream[Any, ToonError, ToonValue]
}
```

### Value Types

```scala
sealed trait ToonValue

object ToonValue {
  // Primitives
  case class Str(value: String) extends ToonValue
  case class Num(value: Double) extends ToonValue
  case class Bool(value: Boolean) extends ToonValue
  case object Null extends ToonValue
  
  // Containers
  case class Obj(fields: Chunk[(String, ToonValue)]) extends ToonValue
  case class Arr(elements: Chunk[ToonValue]) extends ToonValue
  
  // Helper constructors
  def str(s: String): Str = Str(s)
  def num(n: Double): Num = Num(n)
  def bool(b: Boolean): Bool = Bool(b)
  def obj(fields: (String, ToonValue)*): Obj = Obj(Chunk.fromIterable(fields))
  def arr(elements: ToonValue*): Arr = Arr(Chunk.fromIterable(elements))
}
```

### Error Types

```scala
sealed trait ToonError {
  def message: String
}

object ToonError {
  case class ParseError(message: String) extends ToonError
  case class SyntaxError(message: String, line: Int) extends ToonError
  case class IndentationError(message: String, line: Int) extends ToonError
  case class MissingColon(line: Int) extends ToonError
  case class InvalidEscape(message: String, line: Int) extends ToonError
  case class InvalidNumber(value: String, line: Int) extends ToonError
  case class UnterminatedString(line: Int) extends ToonError
  case class CountMismatch(expected: Int, actual: Int, field: String, line: Int) extends ToonError
  case class WidthMismatch(expected: Int, actual: Int, line: Int) extends ToonError
}
```

### Configuration

```scala
// Encoder configuration
case class EncoderConfig(
  indentSize: Int = 2,              // Spaces per indentation level
  delimiter: Delimiter = Delimiter.Comma  // Array delimiter (Comma or Tab)
)

// Decoder configuration  
case class DecoderConfig(
  strictMode: Boolean = true,       // Enforce strict indentation
  indentSize: Int = 2               // Expected spaces per level
)
```

### Layers

Provide services at your application entry point:

```scala
// Basic TOON encoding/decoding
val toonLayer = Toon.live

// With JSON integration
val jsonLayer = ToonEncoderService.live ++ ToonDecoderService.live ++ ToonJsonService.live

// With schema support (depends on JSON)
val schemaLayer = ToonJsonService.live >>> ToonSchemaService.live

// With streaming
val streamLayer = 
  (ToonEncoderService.live ++ ToonDecoderService.live) >+> ToonStreamService.live

// Complete integration
val fullLayer = 
  ToonEncoderService.live ++ 
  ToonDecoderService.live ++ 
  ToonJsonService.live ++
  (ToonEncoderService.live ++ ToonDecoderService.live) >+> ToonStreamService.live
```

## TOON Format Examples

### Basic Types

```
// String
name: Alice

// Number
age: 30

// Boolean
active: true

// Null
email: null
```

### Objects

```
person:
  name: Alice
  age: 30
  address:
    street: 123 Main St
    city: San Francisco
    zip: 94102
```

### Arrays

```
// Simple array
numbers[5]: 1,2,3,4,5

// Array of objects (tabular format)
users[3]{id,name,role}:
  1,Alice,admin
  2,Bob,user
  3,Charlie,developer

// Nested structures
teams[2]:
  [0]:
    name: Engineering
    members[2]{name,role}:
      Alice,Lead
      Bob,Developer
  [1]:
    name: Design
    members[1]{name,role}:
      Charlie,Designer
```

## ZIO Best Practices Demonstrated

✅ **Effects as Blueprints** - All ZIO effects are pure descriptions  
✅ **Service Pattern** - Traits with Live implementations  
✅ **ZLayer DI** - Composable dependency injection  
✅ **Type-Safe Errors** - All errors in error channel, never thrown  
✅ **Exhaustive Error Handling** - Pattern matching covers all cases  
✅ **No Side Effects** - All I/O wrapped in ZIO effects  
✅ **Streaming** - ZStream for memory-efficient processing  
✅ **Retry Policies** - Schedule-based resilience  
✅ **Resource Safety** - Automatic cleanup with ZLayer scopes  
✅ **Effect Composition** - for-comprehension and operators  

## Testing

Run tests:

```bash
# All tests
sbt test

# Specific test suite
sbt "testOnly io.github.riccardomerolla.ziotoon.ToonEncoderSpec"

# With benchmarks
sbt "benchmarks/Jmh/run"
```

**Test Coverage:**
- 93+ unit tests
- Property-based tests with generators
- Streaming tests
- Retry policy tests
- Integration tests
- JMH performance benchmarks

## Specification

This implementation follows the official [TOON Format Specification v2.0](https://github.com/toon-format/spec).

## Contributing

Contributions welcome! Please:
1. Follow ZIO best practices
2. Add tests for new features
3. Update documentation
4. Run `sbt test` before submitting

## License

MIT License - see LICENSE file for details.

## Links

- [TOON Specification](https://github.com/toon-format/spec)
- [ZIO Documentation](https://zio.dev)
- [Scala 3 Documentation](https://docs.scala-lang.org/scala3/)
