/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.nio;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Fork(3)
public class CharsetCanEncode {

    private static final char ALEF_CHAR = '\u05d0';
    private static final String ALEF_STRING = "\u05d0";

    // sun.nio.cs.US_ASCII
    private CharsetEncoder ascii = Charset.forName("US-ASCII").newEncoder();

    // sun.nio.cs.ISO_8859_1
    private CharsetEncoder iso88591 = Charset.forName("ISO-8859-1").newEncoder();

    // sun.nio.cs.SingleByte
    private CharsetEncoder iso88592 = Charset.forName("ISO-8859-2").newEncoder();

    // sun.nio.cs.DoubleByte
    private CharsetEncoder shiftjis = Charset.forName("Shift_JIS").newEncoder();

    // sun.nio.cs.UTF_8
    private CharsetEncoder utf8 = Charset.forName("UTF-8").newEncoder();

    // sun.nio.cs.UTF_16LE
    private CharsetEncoder utf16le = Charset.forName("UTF-16LE").newEncoder();

    @Benchmark
    public boolean asciiCanEncodeCharYes() {
        return ascii.canEncode('D');
    }

    @Benchmark
    public boolean asciiCanEncodeStringYes() {
        return ascii.canEncode("D");
    }

    @Benchmark
    public boolean asciiCanEncodeCharNo() {
        return ascii.canEncode(ALEF_CHAR);
    }

    @Benchmark
    public boolean asciiCanEncodeStringNo() {
        return ascii.canEncode(ALEF_STRING);
    }

    @Benchmark
    public boolean iso88591CanEncodeCharYes() {
        return iso88591.canEncode('D');
    }

    @Benchmark
    public boolean iso88591CanEncodeStringYes() {
        return iso88591.canEncode("D");
    }

    @Benchmark
    public boolean iso88591CanEncodeCharNo() {
        return iso88591.canEncode(ALEF_CHAR);
    }

    @Benchmark
    public boolean iso88591CanEncodeStringNo() {
        return iso88591.canEncode(ALEF_STRING);
    }

    @Benchmark
    public boolean iso88592CanEncodeCharYes() {
        return iso88592.canEncode('D');
    }

    @Benchmark
    public boolean iso88592CanEncodeStringYes() {
        return iso88592.canEncode("D");
    }

    @Benchmark
    public boolean iso88592CanEncodeCharNo() {
        return iso88592.canEncode(ALEF_CHAR);
    }

    @Benchmark
    public boolean iso88592CanEncodeStringNo() {
        return iso88592.canEncode(ALEF_STRING);
    }

    @Benchmark
    public boolean shiftjisCanEncodeCharYes() {
        return shiftjis.canEncode('D');
    }

    @Benchmark
    public boolean shiftjisCanEncodeStringYes() {
        return shiftjis.canEncode("D");
    }

    @Benchmark
    public boolean shiftjisCanEncodeCharNo() {
        return shiftjis.canEncode(ALEF_CHAR);
    }

    @Benchmark
    public boolean shiftjisCanEncodeStringNo() {
        return shiftjis.canEncode(ALEF_STRING);
    }

    @Benchmark
    public boolean utf8CanEncodeCharYes() {
        return utf8.canEncode('D');
    }

    @Benchmark
    public boolean utf8CanEncodeStringYes() {
        return utf8.canEncode("D");
    }

    @Benchmark
    public boolean utf8CanEncodeCharNo() {
        return utf8.canEncode(Character.MIN_SURROGATE);
    }

    @Benchmark
    public boolean utf8CanEncodeStringNo() {
        return utf8.canEncode(String.valueOf(Character.MIN_SURROGATE));
    }

    @Benchmark
    public boolean utf16leCanEncodeCharYes() {
        return utf16le.canEncode('D');
    }

    @Benchmark
    public boolean utf16leCanEncodeStringYes() {
        return utf16le.canEncode("D");
    }

    @Benchmark
    public boolean utf16leCanEncodeCharNo() {
        return utf16le.canEncode(Character.MIN_SURROGATE);
    }

    @Benchmark
    public boolean utf16leCanEncodeStringNo() {
        return utf16le.canEncode(String.valueOf(Character.MIN_SURROGATE));
    }
}
