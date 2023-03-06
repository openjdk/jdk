/*
 * Copyright (c) 2023, Arm Limited. All rights reserved.
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

package org.openjdk.bench.vm.compiler;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.*;

import java.util.concurrent.TimeUnit;
import java.util.Random;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 3)
public class TestBcax {
    @Param({"2048"})
    private int LENGTH;

    private byte[] ba;
    private byte[] bb;
    private byte[] bc;
    private byte[] bd;

    private short[] sa;
    private short[] sb;
    private short[] sc;
    private short[] sd;

    private int[] ia;
    private int[] ib;
    private int[] ic;
    private int[] id;

    private long[] la;
    private long[] lb;
    private long[] lc;
    private long[] ld;

    @Param("0")
    private int seed;
    private Random random = new Random(seed);

    @Setup
    public void init() {
        ba = new byte[LENGTH];
        bb = new byte[LENGTH];
        bc = new byte[LENGTH];
        bd = new byte[LENGTH];

        sa = new short[LENGTH];
        sb = new short[LENGTH];
        sc = new short[LENGTH];
        sd = new short[LENGTH];

        ia = new int[LENGTH];
        ib = new int[LENGTH];
        ic = new int[LENGTH];
        id = new int[LENGTH];

        la = new long[LENGTH];
        lb = new long[LENGTH];
        lc = new long[LENGTH];
        ld = new long[LENGTH];

        for (int i = 0; i < LENGTH; i++) {
            ba[i] = (byte)random.nextInt();
            bb[i] = (byte)random.nextInt();
            bc[i] = (byte)random.nextInt();

            sa[i] = (short)random.nextInt();
            sb[i] = (short)random.nextInt();
            sc[i] = (short)random.nextInt();

            ia[i] = random.nextInt();
            ib[i] = random.nextInt();
            ic[i] = random.nextInt();

            la[i] = random.nextLong();
            lb[i] = random.nextLong();
            lc[i] = random.nextLong();
        }
    }

    @Benchmark
    public void testByte() {
        for (int i = 0; i < LENGTH; i++) {
            bd[i] = (byte)(ba[i] ^ (bb[i] & (~bc[i])));
        }
    }

    @Benchmark
    public void testShort() {
        for (int i = 0; i < LENGTH; i++) {
            sd[i] = (short)(sa[i] ^ (sb[i] & (~sc[i])));
        }
    }

    @Benchmark
    public void testInt() {
        for (int i = 0; i < LENGTH; i++) {
            id[i] = ia[i] ^ (ib[i] & (~ic[i]));
        }
    }

    @Benchmark
    public void testLong() {
        for (int i = 0; i < LENGTH; i++) {
            ld[i] = la[i] ^ (lb[i] & (~lc[i]));
        }
    }
}
