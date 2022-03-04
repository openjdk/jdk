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
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.io.UnsupportedEncodingException;

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
    public static class Algorithm {
        private final static byte[] alphabet;

        static {
            try {
                alphabet = "abcdefghijklmnopqrstuvwxyz".getBytes("US-ASCII");
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }

        @Param({"0", "1", "10", "100", "1000", "10000"})
        private int size;

        private byte[] value;

        @Setup
        public void setup() throws UnsupportedEncodingException {
            value = new byte[size];
            for (int i = 0; i < size; i++) {
                value[i] = alphabet[ThreadLocalRandom.current().nextInt(alphabet.length)];
            }
        }

        @Benchmark
        public int scalar() {
            int h = 0;
            int i = 0;
            for (; i < value.length; i++) {
                h = 31 * h + (value[i] & 0xff);
            }
            return h;
        }

        @Benchmark
        public int scalarUnrolled8() {
            int h = 0;
            int i = 0;
            for (; i < (value.length & ~(8 - 1)); i += 8) {
                h = -1807454463 * h                   +
                     1742810335 * (value[i+0] & 0xff) +
                      887503681 * (value[i+1] & 0xff) +
                       28629151 * (value[i+2] & 0xff) +
                         923521 * (value[i+3] & 0xff) +
                          29791 * (value[i+4] & 0xff) +
                            961 * (value[i+5] & 0xff) +
                             31 * (value[i+6] & 0xff) +
                              1 * (value[i+7] & 0xff);
            }
            for (; i < value.length; i++) {
                h = 31 * h + (value[i] & 0xff);
            }
            return h;
        }

        @Benchmark
        public int scalarUnrolled16() {
            int h = 0;
            int i = 0;
            for (; i < (value.length & ~(16 - 1)); i += 16) {
                h =  1353309697 * h                    +
                     -510534177 * (value[i+ 0] & 0xff) +
                     1507551809 * (value[i+ 1] & 0xff) +
                     -505558625 * (value[i+ 2] & 0xff) +
                     -293403007 * (value[i+ 3] & 0xff) +
                      129082719 * (value[i+ 4] & 0xff) +
                    -1796951359 * (value[i+ 5] & 0xff) +
                     -196513505 * (value[i+ 6] & 0xff) +
                    -1807454463 * (value[i+ 7] & 0xff) +
                     1742810335 * (value[i+ 8] & 0xff) +
                      887503681 * (value[i+ 9] & 0xff) +
                       28629151 * (value[i+10] & 0xff) +
                         923521 * (value[i+11] & 0xff) +
                          29791 * (value[i+12] & 0xff) +
                            961 * (value[i+13] & 0xff) +
                             31 * (value[i+14] & 0xff) +
                              1 * (value[i+15] & 0xff);
            }
            for (; i < value.length; i++) {
                h = 31 * h + (value[i] & 0xff);
            }
            return h;
        }
    }
}
