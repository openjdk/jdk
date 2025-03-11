/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.openjdk.bench.java.util.logging;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MutableCallSite;
import java.lang.invoke.SwitchPoint;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import sun.security.ssl.SSLLogger;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Measurement(iterations = 5, time = 1)
public class LogTestMini {

    @Param({"1", "10"})
    private int numStrings;

    public static final boolean isOn = false;
    public static volatile boolean isOnVolatile = false;


    //private static final MethodHandle PRED_F = MethodHandles.constant(boolean.class, false);
    //private static final MethodHandle PRED_T = MethodHandles.constant(boolean.class, true);
    //private static final MutableCallSite ISDORMANT_CALL_SITE = new MutableCallSite(PRED_F);
    private static final SwitchPoint loggingRequested = new SwitchPoint();

    @Setup
    public void beforeRun() {
    }

    @Benchmark
    @Fork(value = 2)
    public void static_final_boolean_false() {
       if (isOn) {
           createMessage(numStrings);
       }
    }

    @Benchmark
    @Fork(value = 2)
    public void static_volatile_boolean_false() {
        if (isOnVolatile) {
            createMessage(numStrings);
        }
    }

//    @Benchmark
//    @Fork(value = 2)
//    public void mutatableCallSite() {
//        if (ISDORMANT_CALL_SITE.getTarget() == PRED_T) {
//            createMessage(numStrings);
//        }
//    }

    @Benchmark
    @Fork(value = 2)
    public void switchPointOff() {
        if (loggingRequested.hasBeenInvalidated()) {
            createMessage(numStrings);
        }
    }

//    @Benchmark
//    @Fork(value = 2, jvmArgsAppend = { "--add-exports=java.base/sun.security.ssl=ALL-UNNAMED", "-Djavax.net.debug" })
//    public void sslLogger_OFF_LEVEL() {
//       SSLLogger.info(createMessage(numStrings).get());
//    }
//
//    @Benchmark
//    @Fork(value = 2, jvmArgsAppend = { "--add-exports=java.base/sun.security.ssl=ALL-UNNAMED", "-Djavax.net.debug" })
//    public void sslLogger_OFF_LEVEL_withSupplier() {
//        SSLLogger.info(createMessage(numStrings));
//    }
//
//    @Benchmark
//    @Fork(value = 2, jvmArgsAppend = { "--add-exports=java.base/sun.security.ssl=ALL-UNNAMED", "-Djavax.net.debug", "-DcLevel" })
//    public void sslLogger_INFO_LEVEL() {
//        SSLLogger.info(createMessage(numStrings).get());
//    }
//
//    @Benchmark
//    @Fork(value = 2, jvmArgsAppend = { "--add-exports=java.base/sun.security.ssl=ALL-UNNAMED", "-Djavax.net.debug", "-DcLevel" })
//    public void sslLogger_INFO_LEVEL_withSupplier() {
//        SSLLogger.info(createMessage(numStrings));
//    }
//
//    @Benchmark
//    @Fork(value = 2)
//    public void jfrLogger_off() {
//        var e = new StringEvent();
//	if (e.shouldCommit()) {
//            e.setMessage(createMessage(numStrings).get());
//            e.commit();
//        }
//    }
//
//    @Benchmark
//    @Fork(value = 2, jvmArgsAppend = { "-XX:+FlightRecorder", "-XX:StartFlightRecording" })
//    public void jfrLogger_enabled() {
//        var e = new StringEvent();
//        if (e.shouldCommit()) {
//            e.setMessage(createMessage(numStrings).get());
//            e.commit();
//        }
//    }

    private static Supplier<String> createMessage(int numStrings) {
         return () -> IntStream.range(0, numStrings).mapToObj( i -> { return "test" + i;}).collect(Collectors.toList()).toString();
    }

    @Name("StringEvent")
    @Label("String Event")
    @Category("Perf")
    class StringEvent extends Event {

        private String message;

        public StringEvent() {
        }

        @Label("Message")
        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
