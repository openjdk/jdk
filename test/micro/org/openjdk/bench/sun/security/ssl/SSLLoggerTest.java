package org.openjdk.bench.sun.security.ssl;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import sun.security.ssl.SSLLogger;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Fork(value = 1, jvmArgs = {"--add-exports", "java.base/sun.security.ssl=ALL-UNNAMED"})
public class SSLLoggerTest {

    @Param({"1", "10"})
    private int numStrings;

    @Benchmark
    public void testLog() {
        if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE)) {
            SSLLogger.info(createMessage(numStrings));
	    }
    }

//    @Benchmark
//    public void testLogWithSupplier() {
//        if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
//            SSLLogger.info(() -> createMessage(numStrings));
//        }
//    }

    private static String createMessage(int numStrings) {
        return IntStream.range(0, numStrings).mapToObj(i -> { return "test" + i; }).collect(Collectors.toList()).toString();
    }
}
