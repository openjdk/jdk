package micro.org.openjdk.bench.java.util;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @see <a href="https://bugs.openjdk.java.net/browse/JDK-8274715">JDK-8274715</a>
 */
@Fork(value = 3)
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
public class NCopiesBenchmarks {
    @Param({"10", "50", "100"})
    int size;

    private List<Object> list;

    @Setup
    public void prepare() {
        list = Collections.nCopies(size, new Object());
    }

    @Benchmark
    public void forEach(Blackhole bh) {
        list.forEach(bh::consume);
    }

}
