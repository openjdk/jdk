/*
 *  Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Random;
import jdk.incubator.vector.*;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Fork(jvmArgs = {"--add-modules=jdk.incubator.vector"})
public class RearrangeBytesBenchmark {
    @Param({"256", "512", "1024"})
    int size;

    int [][] shuffles;
    byte[] byteinp;
    byte[] byteres;

    static final VectorSpecies<Byte> bspecies64 = ByteVector.SPECIES_64;
    static final VectorSpecies<Byte> bspecies128 = ByteVector.SPECIES_128;
    static final VectorSpecies<Byte> bspecies256 = ByteVector.SPECIES_256;
    static final VectorSpecies<Byte> bspecies512 = ByteVector.SPECIES_512;

    static final byte[] specialvalsbyte = {0, -0, Byte.MIN_VALUE, Byte.MAX_VALUE};

    @Setup(Level.Trial)
    public void BmSetup() {
        Random r = new Random(1024);
        int [] bits = {64, 128, 256, 512};
        byteinp = new byte[size];
        byteres = new byte[size];

        for (int i = 4; i < size; i++) {
            byteinp[i] = (byte)i;
        }
        for (int i = 0; i < specialvalsbyte.length; i++) {
            byteinp[i] = specialvalsbyte[i];
        }

        shuffles = new int[4][];
        for (int i = 0; i < bits.length; i++) {
           int bytes = bits[i] >> 3;
           shuffles[i] = new int[bytes];
           for (int j = 0; j < bytes ; j++) {
              shuffles[i][j] = r.nextInt(bytes - 1);
           }
        }
    }

    @Benchmark
    public void testRearrangeBytes64() {
        VectorShuffle<Byte> shuffle = VectorShuffle.fromArray(bspecies512, shuffles[3], 0);
        for (int j = 0; j < bspecies512.loopBound(size); j += bspecies512.length()) {
            ByteVector.fromArray(bspecies512, byteinp, j)
                .rearrange(shuffle)
                .intoArray(byteres, j);
        }
    }
    @Benchmark
    public void testRearrangeBytes32() {
        VectorShuffle<Byte> shuffle = VectorShuffle.fromArray(bspecies256, shuffles[2], 0);
        for (int j = 0; j < bspecies256.loopBound(size); j += bspecies256.length()) {
            ByteVector.fromArray(bspecies256, byteinp, j)
                .rearrange(shuffle)
                .intoArray(byteres, j);
        }
    }
    @Benchmark
    public void testRearrangeBytes16() {
        VectorShuffle<Byte> shuffle = VectorShuffle.fromArray(bspecies128, shuffles[1], 0);
        for (int j = 0; j < bspecies128.loopBound(size); j += bspecies128.length()) {
            ByteVector.fromArray(bspecies128, byteinp, j)
                .rearrange(shuffle)
                .intoArray(byteres, j);
        }
    }
    @Benchmark
    public void testRearrangeBytes8() {
        VectorShuffle<Byte> shuffle = VectorShuffle.fromArray(bspecies64, shuffles[0], 0);
        for (int j = 0; j < bspecies64.loopBound(size); j += bspecies64.length()) {
            ByteVector.fromArray(bspecies64, byteinp, j)
                .rearrange(shuffle)
                .intoArray(byteres, j);
        }
    }
}
