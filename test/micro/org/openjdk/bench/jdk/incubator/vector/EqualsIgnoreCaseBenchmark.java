/*
 *  Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorSpecies;
import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static jdk.incubator.vector.VectorOperators.*;

/**
 * Exploration of vectorized latin1 equalsIgnoreCase taking advantage of the fact
 * that ASCII and Latin1 were designed to optimize case-twiddling operations.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3, jvmArgs = {"--add-modules=jdk.incubator.vector"})
public class EqualsIgnoreCaseBenchmark {
    static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_PREFERRED;
    private byte[] a;
    private byte[] b;
    private int len;
    @Param({"16", "32", "64", "128", "1024"})
    private int size;

    @Setup
    public void setup() {
        a = ("a\u00e5".repeat(size/2) + "A").getBytes(StandardCharsets.ISO_8859_1);
        b = ("A\u00c5".repeat(size/2) + "B").getBytes(StandardCharsets.ISO_8859_1);
        len = a.length;
    }

    @Benchmark
    public boolean scalar() {
        return scalarEqualsIgnoreCase(a, b, len);
    }

    @Benchmark
    public boolean vectorized() {
        return vectorizedEqualsIgnoreCase(a, b, len);
    }

    private boolean vectorizedEqualsIgnoreCase(byte[] a, byte[] b, int len) {
        int i = 0;
        for (; i < SPECIES.loopBound(b.length); i += SPECIES.length()) {
            ByteVector va = ByteVector.fromArray(SPECIES, a, i);
            ByteVector vb = ByteVector.fromArray(SPECIES, b, i);
            VectorMask<Byte> equal = va.eq(vb);

            // If all bytes are equal, we can skip ahead early
            if (equal.allTrue()) {
                continue;
            }

            // ASCII and Latin-1 were designed to optimize case-twiddling operations
            ByteVector upperA = va.and((byte) 0xDF);

            // Determine which bytes represent ASCII or Latin-1 letters:
            VectorMask<Byte> asciiLetter = upperA.compare(GT, (byte) '@')
                    .and(upperA.compare(LT, (byte) '['));

            VectorMask<Byte> lat1Letter = upperA
                    .compare(LT, (byte) 0xDF)  // <= Thorn
                    .and(upperA.compare(GT, (byte) 0XBF)) // >= A-grave
                    .and(upperA.compare(EQ, (byte) 0xD7).not()); // Excluding multiplication

            VectorMask<Byte> letter = asciiLetter.or(lat1Letter);

            // Uppercase b
            ByteVector upperB = vb.and((byte) 0xDF);

            // va equalsIgnoreCase vb if:
            // 1: all bytes are equal, or
            // 2: all bytes are letters in the ASCII or latin1 ranges
            //    AND their uppercase is the same
            VectorMask<Byte> equalsIgnoreCase = equal
                    .or(letter.and(upperA.eq(upperB)));

            if (equalsIgnoreCase.allTrue()) {
                continue;
            } else {
                return false;
            }
        }
        // Process the tail
        while (i < len) {
            byte b1 = a[i];
            byte b2 = b[i];
            if (equalsIgnoreCase(b1, b2)) {
                i++;
                continue;
            }
            return false;
        }
        return true;
    }

    public boolean scalarEqualsIgnoreCase(byte[] a, byte[] b, int len) {
        int i = 0;
        while (i < len) {
            byte b1 = a[i];
            byte b2 = b[i];
            if (equalsIgnoreCase(b1, b2)) {
                i++;
                continue;
            }
            return false;
        }
        return true;
    }

    static boolean equalsIgnoreCase(byte b1, byte b2) {
        if (b1 == b2) {
            return true;
        }
        // ASCII and Latin-1 were designed to optimize case-twiddling operations
        int upper = b1 & 0xDF;
        if (upper < 'A') {
            return false;  // Low ASCII
        }
        return (upper <= 'Z' // In range A-Z
                || (upper >= 0xC0 && upper <= 0XDE && upper != 0xD7)) // ..or A-grave-Thorn, excl. multiplication
                && upper == (b2 & 0xDF); // b2 has same uppercase
    }
}
