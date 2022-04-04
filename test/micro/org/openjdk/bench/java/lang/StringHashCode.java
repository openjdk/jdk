/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

/**
 * Performance test of String.hashCode() function
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class StringHashCode {

    private String hashcode;
    private String hashcode0;
    private String empty;

    @Setup
    public void setup() {
        hashcode = "abcdefghijkl";
        hashcode0 = new String(new char[]{72, 90, 100, 89, 105, 2, 72, 90, 100, 89, 105, 2});
        empty = new String();
    }

    /**
     * Benchmark testing String.hashCode() with a regular 12 char string with
     * the result possibly cached in String
     */
    @Benchmark
    public int cached() {
        return hashcode.hashCode();
    }

    /**
     * Benchmark testing String.hashCode() with a 12 char string with the
     * hashcode = 0 forcing the value to always be recalculated.
     */
    @Benchmark
    public int notCached() {
        return hashcode0.hashCode();
    }

    /**
     * Benchmark testing String.hashCode() with the empty string. Since the
     * empty String has hashCode = 0, this value is always recalculated.
     */
    @Benchmark
    public int empty() {
        return empty.hashCode();
    }

    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @State(Scope.Thread)
    @Fork(jvmArgsAppend = {"--add-exports", "java.base/java.lang=ALL-UNNAMED", "--add-opens", "java.base/java.lang=ALL-UNNAMED"})
    public static class Algorithm {

        private final static String alphabet = "abcdefghijklmnopqrstuvwxyz";

        private final static MethodHandle defaultLatin1HashCodeMH;
        private final static MethodHandle defaultUTF16HashCodeMH;

        static {
            try {
                Class<?> stringLatin1 = Class.forName("java.lang.StringLatin1");
                Method stringLatin1HashCode = stringLatin1.getDeclaredMethod("hashCode", byte[].class);
                stringLatin1HashCode.setAccessible(true);

                defaultLatin1HashCodeMH = MethodHandles.lookup().unreflect(stringLatin1HashCode);

                Class<?> stringUTF16 = Class.forName("java.lang.StringUTF16");
                Method stringUTF16HashCode = stringUTF16.getDeclaredMethod("hashCode", byte[].class);
                stringUTF16HashCode.setAccessible(true);

                defaultUTF16HashCodeMH = MethodHandles.lookup().unreflect(stringUTF16HashCode);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Param({"0", "1", "10", "100", "1000", "10000"})
        private int size;

        private byte[] latin1;
        private byte[] utf16;

        @Setup
        public void setup() throws UnsupportedEncodingException, ClassNotFoundException, NoSuchMethodException, Throwable {
            char[] str = new char[size];
            for (int i = 0; i < size; i++) {
                str[i] = alphabet.charAt(ThreadLocalRandom.current().nextInt(alphabet.length()));
            }
            latin1 = new String(str).getBytes("US-ASCII");
            utf16 = new String(str).getBytes("UTF-16");
        }

        @Benchmark
        public int defaultLatin1() throws Throwable {
            return (int)defaultLatin1HashCodeMH.invokeExact(latin1);
        }

        @Benchmark
        public int scalarLatin1() {
            int h = 0;
            int i = 0, len = latin1.length;
            for (; i < len; i++) {
                h = 31 * h + (latin1[i] & 0xff);
            }
            return h;
        }

        @Benchmark
        public int scalarLatin1Unrolled8() {
            int h = 0;
            int i = 0, len = latin1.length;
            for (; i < (len & ~(8 - 1)); i += 8) {
                h = -1807454463 * h                   +
                     1742810335 * (latin1[i+0] & 0xff) +
                      887503681 * (latin1[i+1] & 0xff) +
                       28629151 * (latin1[i+2] & 0xff) +
                         923521 * (latin1[i+3] & 0xff) +
                          29791 * (latin1[i+4] & 0xff) +
                            961 * (latin1[i+5] & 0xff) +
                             31 * (latin1[i+6] & 0xff) +
                              1 * (latin1[i+7] & 0xff);
            }
            for (; i < len; i++) {
                h = 31 * h + (latin1[i] & 0xff);
            }
            return h;
        }

        @Benchmark
        public int scalarLatin1Inverted() {
            int h = 0;
            int len = latin1.length, i = len - 1;
            int coef = 1;
            for (; i >= 0; i -= 1) {
                h = h + coef * (latin1[i] & 0xff);
                coef = coef * 31;
            }
            return h;
        }

        @Benchmark
        public int scalarLatin1InvertedUnrolled8() {
            int h = 0;
            int len = latin1.length, i = len - 1;
            int coef = 1;
            for (int bound = len - (len % (8*(1-0))); i >= bound /* align on 8 elements */; i -= 1) {
                h = h + coef * (latin1[i] & 0xff);
                coef = coef * 31;
            }
            if (i-(8*(1-0)-1) >= 0) {
                int h0 = 0;
                int h1 = 0;
                int h2 = 0;
                int h3 = 0;
                int h4 = 0;
                int h5 = 0;
                int h6 = 0;
                int h7 = 0;
                int coef0 = 31*31*31*31*31*31*31*coef;
                int coef1 = 31*31*31*31*31*31*coef;
                int coef2 = 31*31*31*31*31*coef;
                int coef3 = 31*31*31*31*coef;
                int coef4 = 31*31*31*coef;
                int coef5 = 31*31*coef;
                int coef6 = 31*coef;
                int coef7 = coef;
                for (; i-(8-1) >= 0; i -= 8) {
                    h0 += coef0 * (latin1[i-(8-1)+0] & 0xff);
                    h1 += coef1 * (latin1[i-(8-1)+1] & 0xff);
                    h2 += coef2 * (latin1[i-(8-1)+2] & 0xff);
                    h3 += coef3 * (latin1[i-(8-1)+3] & 0xff);
                    h4 += coef4 * (latin1[i-(8-1)+4] & 0xff);
                    h5 += coef5 * (latin1[i-(8-1)+5] & 0xff);
                    h6 += coef6 * (latin1[i-(8-1)+6] & 0xff);
                    h7 += coef7 * (latin1[i-(8-1)+7] & 0xff);
                    coef0 = 31*31*31*31*31*31*31*31 * coef0;
                    coef1 = 31*31*31*31*31*31*31*31 * coef1;
                    coef2 = 31*31*31*31*31*31*31*31 * coef2;
                    coef3 = 31*31*31*31*31*31*31*31 * coef3;
                    coef4 = 31*31*31*31*31*31*31*31 * coef4;
                    coef5 = 31*31*31*31*31*31*31*31 * coef5;
                    coef6 = 31*31*31*31*31*31*31*31 * coef6;
                    coef7 = 31*31*31*31*31*31*31*31 * coef7;
                }
                h += h0 + h1 + h2 + h3 + h4 + h5 + h6 + h7;
            }
            return h;
        }

        @Benchmark
        public int scalarLatin1InvertedUnrolled32() {
            int h = 0;
            int len = latin1.length, i = len - 1;
            int coef = 1;
            for (int bound = len - (len % 32); i >= bound /* align on 32 elements */; i -= 1) {
                h = h + coef * (latin1[i] & 0xff);
                coef = coef * 31;
            }
            if (i-(32-1) >= 0) {
                int h0  = 0;
                int h1  = 0;
                int h2  = 0;
                int h3  = 0;
                int h4  = 0;
                int h5  = 0;
                int h6  = 0;
                int h7  = 0;
                int h8  = 0;
                int h9  = 0;
                int h10 = 0;
                int h11 = 0;
                int h12 = 0;
                int h13 = 0;
                int h14 = 0;
                int h15 = 0;
                int h16 = 0;
                int h17 = 0;
                int h18 = 0;
                int h19 = 0;
                int h20 = 0;
                int h21 = 0;
                int h22 = 0;
                int h23 = 0;
                int h24 = 0;
                int h25 = 0;
                int h26 = 0;
                int h27 = 0;
                int h28 = 0;
                int h29 = 0;
                int h30 = 0;
                int h31 = 0;
                int coef0  = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*coef;
                int coef1  = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*coef;
                int coef2  = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*coef;
                int coef3  = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*coef;
                int coef4  = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*coef;
                int coef5  = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*coef;
                int coef6  = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*coef;
                int coef7  = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*coef;
                int coef8  = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*coef;
                int coef9  = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*coef;
                int coef10 = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*coef;
                int coef11 = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*coef;
                int coef12 = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*coef;
                int coef13 = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*coef;
                int coef14 = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*coef;
                int coef15 = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*coef;
                int coef16 = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*coef;
                int coef17 = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*coef;
                int coef18 = 31*31*31*31*31*31*31*31*31*31*31*31*31*coef;
                int coef19 = 31*31*31*31*31*31*31*31*31*31*31*31*coef;
                int coef20 = 31*31*31*31*31*31*31*31*31*31*31*coef;
                int coef21 = 31*31*31*31*31*31*31*31*31*31*coef;
                int coef22 = 31*31*31*31*31*31*31*31*31*coef;
                int coef23 = 31*31*31*31*31*31*31*31*coef;
                int coef24 = 31*31*31*31*31*31*31*coef;
                int coef25 = 31*31*31*31*31*31*coef;
                int coef26 = 31*31*31*31*31*coef;
                int coef27 = 31*31*31*31*coef;
                int coef28 = 31*31*31*coef;
                int coef29 = 31*31*coef;
                int coef30 = 31*coef;
                int coef31 = coef;
                for (; i-(32-1) >= 0; i -= 32) {
                    h0  += coef0  * (latin1[i-(32-1)+0] & 0xff);
                    h1  += coef1  * (latin1[i-(32-1)+1] & 0xff);
                    h2  += coef2  * (latin1[i-(32-1)+2] & 0xff);
                    h3  += coef3  * (latin1[i-(32-1)+3] & 0xff);
                    h4  += coef4  * (latin1[i-(32-1)+4] & 0xff);
                    h5  += coef5  * (latin1[i-(32-1)+5] & 0xff);
                    h6  += coef6  * (latin1[i-(32-1)+6] & 0xff);
                    h7  += coef7  * (latin1[i-(32-1)+7] & 0xff);
                    h8  += coef8  * (latin1[i-(32-1)+8] & 0xff);
                    h9  += coef9  * (latin1[i-(32-1)+9] & 0xff);
                    h10 += coef10 * (latin1[i-(32-1)+10] & 0xff);
                    h11 += coef11 * (latin1[i-(32-1)+11] & 0xff);
                    h12 += coef12 * (latin1[i-(32-1)+12] & 0xff);
                    h13 += coef13 * (latin1[i-(32-1)+13] & 0xff);
                    h14 += coef14 * (latin1[i-(32-1)+14] & 0xff);
                    h15 += coef15 * (latin1[i-(32-1)+15] & 0xff);
                    h16 += coef16 * (latin1[i-(32-1)+16] & 0xff);
                    h17 += coef17 * (latin1[i-(32-1)+17] & 0xff);
                    h18 += coef18 * (latin1[i-(32-1)+18] & 0xff);
                    h19 += coef19 * (latin1[i-(32-1)+19] & 0xff);
                    h20 += coef20 * (latin1[i-(32-1)+20] & 0xff);
                    h21 += coef21 * (latin1[i-(32-1)+21] & 0xff);
                    h22 += coef22 * (latin1[i-(32-1)+22] & 0xff);
                    h23 += coef23 * (latin1[i-(32-1)+23] & 0xff);
                    h24 += coef24 * (latin1[i-(32-1)+24] & 0xff);
                    h25 += coef25 * (latin1[i-(32-1)+25] & 0xff);
                    h26 += coef26 * (latin1[i-(32-1)+26] & 0xff);
                    h27 += coef27 * (latin1[i-(32-1)+27] & 0xff);
                    h28 += coef28 * (latin1[i-(32-1)+28] & 0xff);
                    h29 += coef29 * (latin1[i-(32-1)+29] & 0xff);
                    h30 += coef30 * (latin1[i-(32-1)+30] & 0xff);
                    h31 += coef31 * (latin1[i-(32-1)+31] & 0xff);
                    coef0  = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31 * coef0;
                    coef1  = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31 * coef1;
                    coef2  = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31 * coef2;
                    coef3  = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31 * coef3;
                    coef4  = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31 * coef4;
                    coef5  = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31 * coef5;
                    coef6  = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31 * coef6;
                    coef7  = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31 * coef7;
                    coef8  = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31 * coef8;
                    coef9  = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31 * coef9;
                    coef10 = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31 * coef10;
                    coef11 = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31 * coef11;
                    coef12 = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31 * coef12;
                    coef13 = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31 * coef13;
                    coef14 = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31 * coef14;
                    coef15 = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31 * coef15;
                    coef16 = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31 * coef16;
                    coef17 = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31 * coef17;
                    coef18 = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31 * coef18;
                    coef19 = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31 * coef19;
                    coef20 = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31 * coef20;
                    coef21 = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31 * coef21;
                    coef22 = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31 * coef22;
                    coef23 = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31 * coef23;
                    coef24 = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31 * coef24;
                    coef25 = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31 * coef25;
                    coef26 = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31 * coef26;
                    coef27 = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31 * coef27;
                    coef28 = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31 * coef28;
                    coef29 = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31 * coef29;
                    coef30 = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31 * coef30;
                    coef31 = 31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31*31 * coef31;
                }
                h += h0  + h1  + h2  + h3  + h4  + h5  + h6  + h7
                  +  h8  + h9  + h10 + h11 + h12 + h13 + h14 + h15
                  +  h16 + h17 + h18 + h19 + h20 + h21 + h22 + h23
                  +  h24 + h25 + h26 + h27 + h28 + h29 + h30 + h31;
            }
            return h;
        }

        @Benchmark
        public int defaultUTF16() throws Throwable {
            return (int)defaultUTF16HashCodeMH.invokeExact(utf16);
        }

        char getCharUTF16(byte[] value, int index) {
            index <<= 1;
            // assuming little endian
            return (char)(((value[index++] & 0xff) << 0) |
                          ((value[index]   & 0xff) << 8));
        }

        @Benchmark
        public int scalarUTF16() {
            int h = 0;
            int i = 0, len = utf16.length / 2;
            for (; i < len; i++) {
                h = 31 * h + getCharUTF16(utf16, i);
            }
            return h;
        }

        @Benchmark
        public int scalarUTF16Unrolled8() {
            int h = 0;
            int i = 0, len = utf16.length / 2;
            for (; i < (len & ~(8 - 1)); i += 8) {
                h = -1807454463 * h                   +
                     1742810335 * getCharUTF16(utf16, i+0) +
                      887503681 * getCharUTF16(utf16, i+1) +
                       28629151 * getCharUTF16(utf16, i+2) +
                         923521 * getCharUTF16(utf16, i+3) +
                          29791 * getCharUTF16(utf16, i+4) +
                            961 * getCharUTF16(utf16, i+5) +
                             31 * getCharUTF16(utf16, i+6) +
                              1 * getCharUTF16(utf16, i+7);
            }
            for (; i < len; i++) {
                h = 31 * h + getCharUTF16(utf16, i);
            }
            return h;
        }

        @Benchmark
        public int scalarUTF16Unrolled16() {
            int h = 0;
            int i = 0, len = utf16.length / 2;
            for (; i < (len & ~(16 - 1)); i += 16) {
                h =  1353309697 * h                    +
                     -510534177 * getCharUTF16(utf16, i+0) +
                     1507551809 * getCharUTF16(utf16, i+1) +
                     -505558625 * getCharUTF16(utf16, i+2) +
                     -293403007 * getCharUTF16(utf16, i+3) +
                      129082719 * getCharUTF16(utf16, i+4) +
                    -1796951359 * getCharUTF16(utf16, i+5) +
                     -196513505 * getCharUTF16(utf16, i+6) +
                    -1807454463 * getCharUTF16(utf16, i+7) +
                     1742810335 * getCharUTF16(utf16, i+8) +
                      887503681 * getCharUTF16(utf16, i+9) +
                       28629151 * getCharUTF16(utf16, i+10) +
                         923521 * getCharUTF16(utf16, i+11) +
                          29791 * getCharUTF16(utf16, i+12) +
                            961 * getCharUTF16(utf16, i+13) +
                             31 * getCharUTF16(utf16, i+14) +
                              1 * getCharUTF16(utf16, i+15);
            }
            for (; i < len; i++) {
                h = 31 * h + getCharUTF16(utf16, i);
            }
            return h;
        }
    }
}
