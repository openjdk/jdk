package org.openjdk.bench.java.lang.foreign;

import org.openjdk.jmh.annotations.*;

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.util.concurrent.TimeUnit;

/*
Windows 11, Ryzen 7950X3D, JDK 24-beta+20 (similar results on JDK 23)
Benchmark                                              Mode  Cnt      Score      Error   Units
FFMEscapeAnalysisTest.noop_params0                     avgt    3      3,133 ?    1,954   ns/op
FFMEscapeAnalysisTest.noop_params0:gc.alloc.rate       avgt    3      0,007 ?    0,001  MB/sec
FFMEscapeAnalysisTest.noop_params0:gc.alloc.rate.norm  avgt    3     ? 10??               B/op
FFMEscapeAnalysisTest.noop_params0:gc.count            avgt    3        ? 0             counts
FFMEscapeAnalysisTest.noop_params1                     avgt    3      3,051 ?    0,051   ns/op
FFMEscapeAnalysisTest.noop_params1:gc.alloc.rate       avgt    3      0,007 ?    0,001  MB/sec
FFMEscapeAnalysisTest.noop_params1:gc.alloc.rate.norm  avgt    3     ? 10??               B/op
FFMEscapeAnalysisTest.noop_params1:gc.count            avgt    3        ? 0             counts
FFMEscapeAnalysisTest.noop_params2                     avgt    3      3,048 ?    0,218   ns/op
FFMEscapeAnalysisTest.noop_params2:gc.alloc.rate       avgt    3      0,007 ?    0,001  MB/sec
FFMEscapeAnalysisTest.noop_params2:gc.alloc.rate.norm  avgt    3     ? 10??               B/op
FFMEscapeAnalysisTest.noop_params2:gc.count            avgt    3        ? 0             counts
FFMEscapeAnalysisTest.noop_params3                     avgt    3      3,110 ?    1,973   ns/op
FFMEscapeAnalysisTest.noop_params3:gc.alloc.rate       avgt    3      2,368 ?   74,631  MB/sec
FFMEscapeAnalysisTest.noop_params3:gc.alloc.rate.norm  avgt    3      0,008 ?    0,253    B/op
FFMEscapeAnalysisTest.noop_params3:gc.count            avgt    3        ? 0             counts
FFMEscapeAnalysisTest.noop_params4                     avgt    3     10,313 ?    3,615   ns/op
FFMEscapeAnalysisTest.noop_params4:gc.alloc.rate       avgt    3  14796,598 ? 5131,809  MB/sec
FFMEscapeAnalysisTest.noop_params4:gc.alloc.rate.norm  avgt    3    160,000 ?    0,001    B/op
FFMEscapeAnalysisTest.noop_params4:gc.count            avgt    3     20,000             counts
FFMEscapeAnalysisTest.noop_params4:gc.time             avgt    3     15,000                 ms
FFMEscapeAnalysisTest.noop_params5                     avgt    3     12,156 ?    4,828   ns/op
FFMEscapeAnalysisTest.noop_params5:gc.alloc.rate       avgt    3  15692,588 ? 6152,349  MB/sec
FFMEscapeAnalysisTest.noop_params5:gc.alloc.rate.norm  avgt    3    200,000 ?    0,001    B/op
FFMEscapeAnalysisTest.noop_params5:gc.count            avgt    3     19,000             counts
FFMEscapeAnalysisTest.noop_params5:gc.time             avgt    3     16,000                 ms
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