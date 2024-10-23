package org.openjdk.bench.java.lang.foreign;

import org.openjdk.jmh.annotations.*;

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.util.concurrent.TimeUnit;
/*
 * Source: https://gist.github.com/Spasi/71d5cfa687a1dbe95b3fce608d31ae6b
 */
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgsAppend = { "--enable-native-access=ALL-UNNAMED", "-Djava.library.path=micro/native" })
public class FFMEscapeAnalysisTest {

    static {
        System.loadLibrary("eaTest");
    }

    // A shared library that exports the functions below
    private static final SymbolLookup LOOKUP = SymbolLookup.loaderLookup();

    // void noop_params0() {}
    private static final MethodHandle MH_NOOP_PARAMS0 = Linker.nativeLinker()
            .downcallHandle(FunctionDescriptor.ofVoid())
            .bindTo(LOOKUP.find("noop_params0").orElseThrow());

    // void noop_params1(void *param0) {}
    private static final MethodHandle MH_NOOP_PARAMS1 = Linker.nativeLinker()
            .downcallHandle(FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS
            ))
            .bindTo(LOOKUP.find("noop_params1").orElseThrow());

    // void noop_params2(void *param0, void *param1) {}
    private static final MethodHandle MH_NOOP_PARAMS2 = Linker.nativeLinker()
            .downcallHandle(FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS
            ))
            .bindTo(LOOKUP.find("noop_params2").orElseThrow());

    // void noop_params3(void *param0, void *param1, void *param2) {}
    private static final MethodHandle MH_NOOP_PARAMS3 = Linker.nativeLinker()
            .downcallHandle(FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS
            ))
            .bindTo(LOOKUP.find("noop_params3").orElseThrow());

    // void noop_params4(void *param0, void *param1, void *param2, void *param3) {}
    private static final MethodHandle MH_NOOP_PARAMS4 = Linker.nativeLinker()
            .downcallHandle(FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS
            ))
            .bindTo(LOOKUP.find("noop_params4").orElseThrow());

    // void noop_params5(int param0, int param1, void *param2, void *param3, void *param4) {}
    private static final MethodHandle MH_NOOP_PARAMS5 = Linker.nativeLinker()
            .downcallHandle(FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS
            ))
            .bindTo(LOOKUP.find("noop_params5").orElseThrow());

    @Benchmark
    public void noop_params0() {
        try {
            MH_NOOP_PARAMS0.invokeExact();
        } catch (Throwable t) {
            throw new AssertionError(t);
        }
    }

    @Benchmark
    public void noop_params1() {
        try {
            MH_NOOP_PARAMS1.invokeExact(
                    MemorySegment.ofAddress(0L)
            );
        } catch (Throwable t) {
            throw new AssertionError(t);
        }
    }

    @Benchmark
    public void noop_params2() {
        try {
            MH_NOOP_PARAMS2.invokeExact(
                    MemorySegment.ofAddress(0L),
                    MemorySegment.ofAddress(0L)
            );
        } catch (Throwable t) {
            throw new AssertionError(t);
        }
    }

    @Benchmark
    public void noop_params3() {
        try {
            MH_NOOP_PARAMS3.invokeExact(
                    MemorySegment.ofAddress(0L),
                    MemorySegment.ofAddress(0L),
                    MemorySegment.ofAddress(0L)
            );
        } catch (Throwable t) {
            throw new AssertionError(t);
        }
    }

    @Benchmark
    public void noop_params4() {
        try {
            MH_NOOP_PARAMS4.invokeExact(
                    MemorySegment.ofAddress(0L),
                    MemorySegment.ofAddress(0L),
                    MemorySegment.ofAddress(0L),
                    MemorySegment.ofAddress(0L)
            );
            /*var p = MemorySegment.ofAddress(0L);
            MH_NOOP_PARAMS4.invokeExact(p, p, p, p);*/
        } catch (Throwable t) {
            throw new AssertionError(t);
        }
    }

    @Benchmark
    public void noop_params5() {
        try {
            MH_NOOP_PARAMS5.invokeExact(
                    MemorySegment.ofAddress(0L),
                    MemorySegment.ofAddress(0L),
                    MemorySegment.ofAddress(0L),
                    MemorySegment.ofAddress(0L),
                    MemorySegment.ofAddress(0L)
            );
            /*var p = MemorySegment.ofAddress(0L);
            MH_NOOP_PARAMS5.invokeExact(p, p, p, p, p);*/
        } catch (Throwable t) {
            throw new AssertionError(t);
        }
    }
}