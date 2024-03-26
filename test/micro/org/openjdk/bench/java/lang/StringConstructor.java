/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.java.lang;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 3)
@Measurement(iterations = 5, time = 3)
@Fork(3)
public class StringConstructor {

    private static final char INTEROBANG = 0x2030;

    // Fixed offset to use for ranged newStrings
    public final int offset = 1;

    @Param({"7", "64"})
    public int size;

    private byte[] array;
    private char[] chars;
    private char[] charsMixedBegin;
    private char[] charsMixedSmall;
    private char[] charsMixedEnd;
    private int[] codePointsLatin1;
    private int[] codePointsMixedBegin;
    private int[] codePointsMixedSmall;

    private static int[] intCopyOfChars(char[] chars, int newLength) {
        int[] res = new int[newLength];
        for (int i = 0; i < Math.min(chars.length, newLength); i++)
            res[i] = chars[i];
        return res;
    }

    @Setup
    public void setup() {
        String s = "a".repeat(size);
        array = s.getBytes(StandardCharsets.UTF_8);
        chars = s.toCharArray();
        charsMixedBegin = Arrays.copyOf(chars, array.length);
        charsMixedBegin[0] = INTEROBANG;
        charsMixedSmall = Arrays.copyOf(chars, array.length);
        charsMixedSmall[Math.min(charsMixedSmall.length - 1, 7)] = INTEROBANG;
        charsMixedEnd = new char[size + 7];
        Arrays.fill(charsMixedEnd, 'a');
        charsMixedEnd[charsMixedEnd.length - 1] = INTEROBANG;

        codePointsLatin1 = intCopyOfChars(chars, array.length);
        codePointsMixedBegin = intCopyOfChars(chars, array.length);
        codePointsMixedBegin[0] = INTEROBANG;
        codePointsMixedSmall = intCopyOfChars(chars, array.length);
        codePointsMixedSmall[Math.min(codePointsMixedSmall.length - 1, 7)] = INTEROBANG;
    }

    @Benchmark
    public String newStringFromBytes() {
        return new String(array);
    }

    @Benchmark
    public String newStringFromBytesRanged() {
        return new String(array, offset, array.length - offset);
    }

    @Benchmark
    public String newStringFromBytesRangedWithCharsetUTF8() {
        return new String(array, offset, array.length - offset, StandardCharsets.UTF_8);
    }

    @Benchmark
    public String newStringFromBytesWithCharsetUTF8() {
        return new String(array, StandardCharsets.UTF_8);
    }

    @Benchmark
    public String newStringFromBytesWithCharsetNameUTF8() throws Exception {
        return new String(array, StandardCharsets.UTF_8.name());
    }

    @Benchmark
    public String newStringFromCharsLatin1() {
        return new String(chars);
    }

    @Benchmark
    public String newStringFromCharsMixedBegin() {
        return new String(charsMixedBegin);
    }

    @Benchmark
    public String newStringFromCharsMixedSmall() {
        return new String(charsMixedSmall);
    }

    @Benchmark
    public String newStringFromCharsMixedEnd() {
        return new String(charsMixedEnd);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void newStringFromCharsMixedAll(Blackhole bh) {
        bh.consume(new String(charsMixedBegin));
        bh.consume(new String(charsMixedSmall));
        bh.consume(new String(chars));
    }

    @Benchmark
    public String newStringFromCodePointRangedLatin1() {
        return new String(codePointsLatin1, 0, codePointsLatin1.length);
    }

    @Benchmark
    public String newStringFromCodePointRangedMixedBegin() {
        return new String(codePointsMixedBegin, 0, codePointsMixedBegin.length);
    }

    @Benchmark
    public String newStringFromCodePointRangedMixedSmall() {
        return new String(codePointsMixedSmall, 0, codePointsMixedSmall.length);
    }
}
