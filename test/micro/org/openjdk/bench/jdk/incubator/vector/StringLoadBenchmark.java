/*
 *  Copyright (c) 2026, Google LLC. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

package org.openjdk.bench.jdk.incubator.vector;

import static jdk.incubator.vector.VectorOperators.EQ;

import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.VectorSpecies;

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

import java.util.Random;
import java.util.concurrent.TimeUnit;

/** Compares vector and scalar loops over a String. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(
        value = 3,
        jvmArgs = {"--add-modules=jdk.incubator.vector"})
public class StringLoadBenchmark {
    static final VectorSpecies<Short> SPECIES = ShortVector.SPECIES_PREFERRED;

    static final char NEEDLE = 'a';

    @Param({"16", "64", "1024"})
    private int size;

    @Param({"latin1", "utf16"})
    private String charset;

    private String text;
    private char[] textChars;

    @Setup
    public void setup() {
        Random random = new Random(42);
        StringBuilder sb = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            sb.append((char) random.nextInt(256));
        }
        if (charset.equals("utf16")) {
            sb.setCharAt(0, '\u0100');
        }
        text = sb.toString();
        textChars = text.toCharArray();
        int expected = scalar();
        check("toCharArray", expected, toCharArray());
        check("fromCharArray", expected, fromCharArray());
        check("fromString", expected, fromString());
    }

    private static void check(String name, int expected, int actual) {
        if (actual != expected) {
            throw new AssertionError(name + ": expected " + expected + ", got " + actual);
        }
    }

    @Benchmark
    public int scalar() {
        int count = 0;
        for (int i = 0; i < size; i++) {
            if (text.charAt(i) == NEEDLE) {
                count++;
            }
        }
        return count;
    }

    @Benchmark
    public int toCharArray() {
        return countChars(text.toCharArray(), size);
    }

    @Benchmark
    public int fromCharArray() {
        return countChars(textChars, size);
    }

    @Benchmark
    public int fromString() {
        int count = 0;
        int i = 0;
        for (; i < SPECIES.loopBound(size); i += SPECIES.length()) {
            count +=
                    ShortVector.fromString(SPECIES, text, i)
                            .compare(EQ, (short) NEEDLE)
                            .trueCount();
        }
        for (; i < size; i++) {
            if (text.charAt(i) == NEEDLE) {
                count++;
            }
        }
        return count;
    }

    private static int countChars(char[] chars, int size) {
        int count = 0;
        int i = 0;
        for (; i < SPECIES.loopBound(size); i += SPECIES.length()) {
            count += ShortVector.fromCharArray(SPECIES, chars, i).compare(EQ, (short) NEEDLE).trueCount();
        }
        for (; i < size; i++) {
            if (chars[i] == NEEDLE) {
                count++;
            }
        }
        return count;
    }
}
