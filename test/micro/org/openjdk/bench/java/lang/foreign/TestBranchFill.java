package org.openjdk.bench.java.lang.foreign;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.foreign.MemorySegment;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3)
public class TestBranchFill {

    @Param({"false", "true"})
    private boolean shuffle;
    private MemorySegment[] segments;
    @Param({ "1024", "128000"})
    private int samples;
    private byte[] segmentSequence;

    @Setup
    public void setup() {
        segments = new MemorySegment[8];
        // still allocates 8 different arrays
        for (int i = 0; i < 8; i++) {
            // we always pay the most of the cost here, for fun
            byte[] a = shuffle? new byte[i + 1] : new byte[8];
            segments[i] = MemorySegment.ofArray(a);
        }
        segmentSequence = new byte[samples];
        var rnd = new Random(42);
        for(int i = 0; i < samples; i++) {
            // if shuffle == false always fall into the "worst" case of populating 8 bytes
            segmentSequence[i] = (byte) rnd.nextInt(0, 8);
        }
    }

    @Benchmark
    public void heap_segment_fill() {
        var segments = this.segments;
        for (int nextIndex : segmentSequence) {
            fill(segments[nextIndex]);
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void fill(MemorySegment segment) {
        segment.fill((byte) 0);
    }

}