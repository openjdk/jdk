/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.lang.foreign;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.nio.charset.StandardCharsets.UTF_8;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgs = { "--enable-native-access=ALL-UNNAMED", "-Djava.library.path=micro/native" })
public class ToJavaStringTest {

    private MemorySegment strSegment;
    private int length;

    @Param({"5", "20", "100", "200", "451"})
    int size;

    static {
        System.loadLibrary("ToJavaString");
    }

    @Setup
    public void setup() {
        var arena = Arena.ofAuto();
        while (LOREM.length() < size) {
            LOREM += LOREM;
        }
        var s = LOREM.substring(0, size);
        strSegment = arena.allocateFrom(s);
        length = s.getBytes(UTF_8).length;
    }

    @Benchmark
    public String segment_getString() {
        return strSegment.getString(0);
    }

    @Benchmark
    public String segment_getStringLength() {
        return strSegment.getString(0, UTF_8, length);
    }

    @Benchmark
    public String jni_readString() {
        return readString(strSegment.address());
    }

    @Benchmark
    public String segment_copyStringBytes() {
        byte[] bytes = new byte[length];
        MemorySegment.copy(strSegment, JAVA_BYTE, 0, bytes, 0, length);
        return new String(bytes, UTF_8);
    }

    static native String readString(long addr);

    static String LOREM = """
                Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et
                 dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip
                 ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu
                 fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt
                 mollit anim id est laborum.
                """;

}
