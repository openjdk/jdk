/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Arrays;
import java.util.Random;
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
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3)
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
    @Warmup(iterations = 5, time = 1)
    @Measurement(iterations = 5, time = 1)
    @Fork(value = 3, jvmArgs = {"--add-exports", "java.base/java.lang=ALL-UNNAMED", "--add-opens", "java.base/java.lang=ALL-UNNAMED"})
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

        @Param({"1", "10", "100", "10000"})
        private int size;

        private byte[] latin1;
        private byte[] utf16;

        @Setup
        public void setup() throws UnsupportedEncodingException, ClassNotFoundException, NoSuchMethodException, Throwable {
            Random rnd = new Random(42);

            char[] str = new char[size];
            for (int i = 0; i < size; i++) {
                str[i] = alphabet.charAt(rnd.nextInt(alphabet.length()));
            }
            latin1 = new String(str).getBytes("US-ASCII");
            utf16 = new String(str).getBytes("UTF-16");
            // strip out byte order byte(s)
            utf16 = Arrays.copyOfRange(utf16, utf16.length - str.length * 2, utf16.length);
        }

        @Benchmark
        public int defaultLatin1() throws Throwable {
            return (int)defaultLatin1HashCodeMH.invokeExact(latin1);
        }

        @Benchmark
        public int defaultUTF16() throws Throwable {
            return (int)defaultUTF16HashCodeMH.invokeExact(utf16);
        }
    }
}
