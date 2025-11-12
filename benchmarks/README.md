# ZIO-Toon Benchmarks

Performance benchmarks for TOON encoding and decoding using JMH (Java Microbenchmark Harness).

## Running Benchmarks

### Run All Benchmarks
```bash
sbt "benchmarks/Jmh/run"
```

### Run Specific Benchmark
```bash
sbt "benchmarks/Jmh/run ToonEncoderBenchmark"
sbt "benchmarks/Jmh/run ToonDecoderBenchmark"
sbt "benchmarks/Jmh/run ToonRoundTripBenchmark"
sbt "benchmarks/Jmh/run ToonVsJsonBenchmark"
```

### Custom Parameters
```bash
# 5 warmup iterations, 10 measurement iterations, 2 forks, 4 threads
sbt "benchmarks/Jmh/run -i 10 -wi 5 -f 2 -t 4"
```

## Benchmark Suites

### ToonEncoderBenchmark
Measures encoding performance for different data structures:
- Small objects (3 fields)
- Medium nested objects
- Large arrays (100 elements)
- Tabular arrays (50 rows)
- Single primitives
- Service-based encoding

### ToonDecoderBenchmark
Measures decoding performance:
- Small objects
- Medium nested objects
- Tabular arrays (10 rows)
- Single primitives
- Service-based decoding

### ToonRoundTripBenchmark
Measures full encode-decode cycles:
- Pure round-trip (direct encoder/decoder)
- Service-based round-trip (via ZIO)

### ToonVsJsonBenchmark
Compares TOON vs JSON performance:
- TOON encoding speed
- JSON encoding speed
- Token savings calculation overhead

## Interpreting Results

JMH provides two main metrics:

1. **Throughput** (ops/sec) - Higher is better
   - How many operations per second

2. **Average Time** (μs/op) - Lower is better
   - Average time per operation in microseconds

### Example Output
```
Benchmark                                Mode  Cnt    Score    Error  Units
ToonEncoderBenchmark.encodeSmallObject  thrpt    5  125.432 ± 3.215  ops/ms
ToonEncoderBenchmark.encodeSmallObject   avgt    5    7.992 ± 0.205   us/op
```

This means:
- Throughput: ~125,000 operations per second
- Average: ~8 microseconds per operation

## Performance Tips

1. **Warmup is Important**
   - JVM JIT needs time to optimize code
   - Default 3 warmup iterations should be sufficient
   - Increase for more stable results

2. **Forks**
   - Multiple JVM forks reduce noise
   - Default 1 fork is usually enough
   - Use 2-3 for publication-quality results

3. **Threads**
   - Single-threaded by default
   - Increase for concurrent scenarios
   - Use `-t 4` for multi-threaded benchmarks

4. **Iterations**
   - More iterations = more accuracy
   - Balance accuracy vs. time
   - 5-10 measurement iterations recommended

## Baseline Performance

Expected performance on modern hardware (rough estimates):

- **Small Object Encoding**: 100,000+ ops/sec
- **Small Object Decoding**: 50,000+ ops/sec
- **Round-trip**: 25,000+ ops/sec
- **Large Array (100 elements)**: 5,000+ ops/sec

*Actual performance depends on hardware, JVM, and data complexity.*

## Adding New Benchmarks

1. Create new `@Benchmark` method in existing class
2. Or create new benchmark class with `@State` annotation
3. Mark with `@BenchmarkMode`, `@OutputTimeUnit`, etc.
4. Run with `sbt "benchmarks/Jmh/run YourBenchmark"`

Example:
```scala
@Benchmark
def myNewBenchmark(): String = {
  encoder.encode(myTestData)
}
```

## Profiling

### Generate Flamegraphs
```bash
sbt "benchmarks/Jmh/run -prof jfr"
```

### Track Allocations
```bash
sbt "benchmarks/Jmh/run -prof gc"
```

### CPU Profiling
```bash
sbt "benchmarks/Jmh/run -prof perfasm"
```

## Continuous Monitoring

Consider integrating benchmarks into CI/CD:
1. Run benchmarks on each release
2. Compare against baseline
3. Alert on performance regressions
4. Track trends over time

## Resources

- [JMH Documentation](https://github.com/openjdk/jmh)
- [sbt-jmh Plugin](https://github.com/ktoso/sbt-jmh)
- [JMH Samples](https://hg.openjdk.java.net/code-tools/jmh/file/tip/jmh-samples/src/main/java/org/openjdk/jmh/samples/)

