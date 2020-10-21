/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

import java.util.Random;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.util.concurrent.TimeUnit;

/**
 * This benchmark can be used to measure performance between StringLatin1 and StringUTF16 in terms of
 * performance of the indexOf(char) and indexOf(String) methods which are intrinsified.
 * On x86 the behaviour of the indexOf method is contingent upon the length of the string
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class StringIndexOfChar {
    private static final int loops = 100000;
    private static final Random rng = new Random(1999);
    private static final int pathCnt = 1000;
    private static final String [] latn1_short        = new String[pathCnt];
    private static final String [] latn1_sse4         = new String[pathCnt];
    private static final String [] latn1_avx2         = new String[pathCnt];
    private static final String [] latn1_mixedLength  = new String[pathCnt];
    private static final String [] utf16_short        = new String[pathCnt];
    private static final String [] utf16_sse4         = new String[pathCnt];
    private static final String [] utf16_avx2         = new String[pathCnt];
    private static final String [] utf16_mixedLength  = new String[pathCnt];
    static {
        for (int i = 0; i < pathCnt; i++) {
            latn1_short[i] = makeRndString(false, 15);
            latn1_sse4[i]  = makeRndString(false, 16);
            latn1_avx2[i]  = makeRndString(false, 32);
            utf16_short[i] = makeRndString(true, 7);
            utf16_sse4[i]  = makeRndString(true, 8);
            utf16_avx2[i]  = makeRndString(true, 16);
            latn1_mixedLength[i] = makeRndString(false, rng.nextInt(65));
            utf16_mixedLength[i] = makeRndString(true, rng.nextInt(65));
        }
    }

    private static String makeRndString(boolean isUtf16, int length) {
        StringBuilder sb = new StringBuilder(length);
        if(length > 0){
            sb.append(isUtf16?'\u2026':'b'); // ...

            for (int i = 1; i < length-1; i++) {
                sb.append((char)('b' + rng.nextInt(26)));
            }

            sb.append(rng.nextInt(3) >= 1?'a':'b');//66.6% of time 'a' is in string
        }
        return sb.toString();
    }


    @Benchmark
    public void latin1_mixed_char() {
        int ret = 0;
        for (String what : latn1_mixedLength) {
            ret += what.indexOf('a');
        }
    }

    @Benchmark
    public void utf16_mixed_char() {
        int ret = 0;
        for (String what : utf16_mixedLength) {
            ret += what.indexOf('a');
        }
    }

    @Benchmark
    public void latin1_mixed_String() {
        int ret = 0;
        for (String what : latn1_mixedLength) {
            ret += what.indexOf("a");
        }
    }

    @Benchmark
    public void utf16_mixed_String() {
        int ret = 0;
        for (String what : utf16_mixedLength) {
            ret += what.indexOf("a");
        }
    }

    ////////// more detailed code path dependent tests //////////

    @Benchmark
    public void latin1_Short_char() {
        int ret = 0;
        for (String what : latn1_short) {
            ret += what.indexOf('a');
        }
    }

    @Benchmark
    public void latin1_SSE4_char() {
        int ret = 0;
        for (String what : latn1_sse4) {
            ret += what.indexOf('a');
        }
    }

    @Benchmark
    public void latin1_AVX2_char() {
        int ret = 0;
        for (String what : latn1_avx2) {
            ret += what.indexOf('a');
        }
    }

    @Benchmark
    public int utf16_Short_char() {
        int ret = 0;
        for (String what : utf16_short) {
            ret += what.indexOf('a');
        }
        return ret;
    }

    @Benchmark
    public int utf16_SSE4_char() {
        int ret = 0;
        for (String what : utf16_sse4) {
            ret += what.indexOf('a');
        }
        return ret;
    }

    @Benchmark
    public int utf16_AVX2_char() {
        int ret = 0;
        for (String what : utf16_avx2) {
            ret += what.indexOf('a');
        }
        return ret;
    }

    @Benchmark
    public int latin1_Short_String() {
        int ret = 0;
        for (String what : latn1_short) {
            ret += what.indexOf("a");
        }
        return ret;
    }

    @Benchmark
    public int latin1_SSE4_String() {
        int ret = 0;
        for (String what : latn1_sse4) {
            ret += what.indexOf("a");
        }
        return ret;
    }

    @Benchmark
    public int latin1_AVX2_String() {
        int ret = 0;
        for (String what : latn1_avx2) {
            ret += what.indexOf("a");
        }
        return ret;
    }

    @Benchmark
    public int utf16_Short_String() {
        int ret = 0;
        for (String what : utf16_short) {
            ret += what.indexOf("a");
        }
        return ret;
    }

    @Benchmark
    public int utf16_SSE4_String() {
        int ret = 0;
        for (String what : utf16_sse4) {
            ret += what.indexOf("a");
        }
        return ret;
    }

    @Benchmark
    public int utf16_AVX2_String() {
        int ret = 0;
        for (String what : utf16_avx2) {
            ret += what.indexOf("a");
        }
        return ret;
    }
}
