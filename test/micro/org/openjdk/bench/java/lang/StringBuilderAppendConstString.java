/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, Alibaba Group Holding Limited. All Rights Reserved.
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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * Benchmark for StringBuilder.append(String) with constant strings of lengths 2/4/8.
 * This tests the optimization that uses wider memory operations (T_SHORT/T_INT/T_LONG)
 * instead of byte-by-byte stores for constant strings.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
public class StringBuilderAppendConstString {

    // Constant strings of specific lengths for testing the optimization
    // Using Latin1 characters only (single byte per char in compact strings)
    private static final String STR_LEN_1 = "a";
    private static final String STR_LEN_2 = "ab";
    private static final String STR_LEN_3 = "abc";
    private static final String STR_LEN_4 = "abcd";
    private static final String STR_LEN_5 = "abcde";
    private static final String STR_LEN_6 = "abcdef";
    private static final String STR_LEN_7 = "abcdefg";
    private static final String STR_LEN_8 = "abcdefgh";

    // UTF16 strings (containing non-Latin1 characters)
    private static final String STR_UTF16_LEN_2 = "\u4e2d\u6587"; // 2 Chinese chars
    private static final String STR_UTF16_LEN_4 = "\u4e2d\u6587\u65e5\u672c"; // 4 Chinese chars
    private static final String STR_UTF16_LEN_8 = "\u4e2d\u6587\u65e5\u672c\u7b80\u4f53\u5b57\u7b26"; // 8 Chinese chars

    // Reusable StringBuilders
    private StringBuilder sbLatin1;
    private StringBuilder sbLatin2;
    private StringBuilder sbUtf16;

    // Non-constant strings (same content but not compile-time constants)
    private String strLen1;
    private String strLen2;
    private String strLen3;
    private String strLen4;
    private String strLen5;
    private String strLen6;
    private String strLen7;
    private String strLen8;

    @Setup
    public void setup() {
        sbLatin1 = new StringBuilder();
        sbLatin2 = new StringBuilder();
        sbUtf16 = new StringBuilder("\uFF11");

        strLen1 = new String(new char[]{'a'});
        strLen2 = new String(new char[]{'a', 'b'});
        strLen3 = new String(new char[]{'a', 'b', 'c'});
        strLen4 = new String(new char[]{'a', 'b', 'c', 'd'});
        strLen5 = new String(new char[]{'a', 'b', 'c', 'd', 'e'});
        strLen6 = new String(new char[]{'a', 'b', 'c', 'd', 'e', 'f'});
        strLen7 = new String(new char[]{'a', 'b', 'c', 'd', 'e', 'f', 'g'});
        strLen8 = new String(new char[]{'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'});
    }

    // ============= Constant String Append (Latin1) =============

    @Benchmark
    public int appendConstLen1() {
        StringBuilder buf = sbLatin1;
        buf.setLength(0);
        buf.append(STR_LEN_1);
        return buf.length();
    }

    @Benchmark
    public int appendConstLen2() {
        StringBuilder buf = sbLatin1;
        buf.setLength(0);
        buf.append(STR_LEN_2);
        return buf.length();
    }

    @Benchmark
    public int appendConstLen3() {
        StringBuilder buf = sbLatin1;
        buf.setLength(0);
        buf.append(STR_LEN_3);
        return buf.length();
    }

    @Benchmark
    public int appendConstLen4() {
        StringBuilder buf = sbLatin1;
        buf.setLength(0);
        buf.append(STR_LEN_4);
        return buf.length();
    }

    @Benchmark
    public int appendConstLen5() {
        StringBuilder buf = sbLatin1;
        buf.setLength(0);
        buf.append(STR_LEN_5);
        return buf.length();
    }

    @Benchmark
    public int appendConstLen6() {
        StringBuilder buf = sbLatin1;
        buf.setLength(0);
        buf.append(STR_LEN_6);
        return buf.length();
    }

    @Benchmark
    public int appendConstLen7() {
        StringBuilder buf = sbLatin1;
        buf.setLength(0);
        buf.append(STR_LEN_7);
        return buf.length();
    }

    @Benchmark
    public int appendConstLen8() {
        StringBuilder buf = sbLatin1;
        buf.setLength(0);
        buf.append(STR_LEN_8);
        return buf.length();
    }

    // ============= Non-constant String Append (Latin1) =============

    @Benchmark
    public int appendNonConstLen1() {
        StringBuilder buf = sbLatin1;
        buf.setLength(0);
        buf.append(strLen1);
        return buf.length();
    }

    @Benchmark
    public int appendNonConstLen2() {
        StringBuilder buf = sbLatin1;
        buf.setLength(0);
        buf.append(strLen2);
        return buf.length();
    }

    @Benchmark
    public int appendNonConstLen3() {
        StringBuilder buf = sbLatin1;
        buf.setLength(0);
        buf.append(strLen3);
        return buf.length();
    }

    @Benchmark
    public int appendNonConstLen4() {
        StringBuilder buf = sbLatin1;
        buf.setLength(0);
        buf.append(strLen4);
        return buf.length();
    }

    @Benchmark
    public int appendNonConstLen5() {
        StringBuilder buf = sbLatin1;
        buf.setLength(0);
        buf.append(strLen5);
        return buf.length();
    }

    @Benchmark
    public int appendNonConstLen6() {
        StringBuilder buf = sbLatin1;
        buf.setLength(0);
        buf.append(strLen6);
        return buf.length();
    }

    @Benchmark
    public int appendNonConstLen7() {
        StringBuilder buf = sbLatin1;
        buf.setLength(0);
        buf.append(strLen7);
        return buf.length();
    }

    @Benchmark
    public int appendNonConstLen8() {
        StringBuilder buf = sbLatin1;
        buf.setLength(0);
        buf.append(strLen8);
        return buf.length();
    }

    // ============= Constant String Append (UTF16) =============

    @Benchmark
    public int appendConstUtf16Len2() {
        StringBuilder buf = sbUtf16;
        buf.setLength(0);
        buf.append(STR_UTF16_LEN_2);
        return buf.length();
    }

    @Benchmark
    public int appendConstUtf16Len4() {
        StringBuilder buf = sbUtf16;
        buf.setLength(0);
        buf.append(STR_UTF16_LEN_4);
        return buf.length();
    }

    @Benchmark
    public int appendConstUtf16Len8() {
        StringBuilder buf = sbUtf16;
        buf.setLength(0);
        buf.append(STR_UTF16_LEN_8);
        return buf.length();
    }

    // ============= Multiple Append Operations =============

    @Benchmark
    public int appendMultiConstLen2() {
        StringBuilder buf = sbLatin1;
        buf.setLength(0);
        buf.append(STR_LEN_2);
        buf.append(STR_LEN_2);
        buf.append(STR_LEN_2);
        buf.append(STR_LEN_2);
        return buf.length();
    }

    @Benchmark
    public int appendMultiConstLen4() {
        StringBuilder buf = sbLatin1;
        buf.setLength(0);
        buf.append(STR_LEN_4);
        buf.append(STR_LEN_4);
        return buf.length();
    }

    @Benchmark
    public int appendMultiConstLen8() {
        StringBuilder buf = sbLatin1;
        buf.setLength(0);
        buf.append(STR_LEN_8);
        buf.append(STR_LEN_8);
        return buf.length();
    }

    // ============= Mixed Length Appends =============

    @Benchmark
    public int appendMixedConst() {
        StringBuilder buf = sbLatin1;
        buf.setLength(0);
        buf.append(STR_LEN_2);
        buf.append(STR_LEN_4);
        buf.append(STR_LEN_8);
        return buf.length();
    }

    // ============= Repeated Appends =============

    @Benchmark
    public int appendRepeatConstLen2() {
        StringBuilder buf = sbLatin1;
        buf.setLength(0);
        for (int i = 0; i < 8; i++) {
            buf.append(STR_LEN_2);
        }
        return buf.length();
    }

    @Benchmark
    public int appendRepeatConstLen4() {
        StringBuilder buf = sbLatin1;
        buf.setLength(0);
        for (int i = 0; i < 4; i++) {
            buf.append(STR_LEN_4);
        }
        return buf.length();
    }

    @Benchmark
    public int appendRepeatConstLen8() {
        StringBuilder buf = sbLatin1;
        buf.setLength(0);
        for (int i = 0; i < 2; i++) {
            buf.append(STR_LEN_8);
        }
        return buf.length();
    }
}
