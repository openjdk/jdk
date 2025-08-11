/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.*;

import java.util.concurrent.TimeUnit;
import java.util.Random;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
public abstract class VectorStoreToLoadForwarding {
    @Param({"10000"})
    public int SIZE;

    @Param({  "0",   "1",   "2",   "3",   "4",   "5",   "6",   "7",   "8",   "9",
             "10",  "11",  "12",  "13",  "14",  "15",  "16",  "17",  "18",  "19",
             "20",  "21",  "22",  "23",  "24",  "25",  "26",  "27",  "28",  "29",
             "30",  "31",  "32",  "33",  "34",  "35",  "36",  "37",  "38",  "39",
             "40",  "41",  "42",  "43",  "44",  "45",  "46",  "47",  "48",  "49",
             "50",  "51",  "52",  "53",  "54",  "55",  "56",  "57",  "58",  "59",
             "60",  "61",  "62",  "63",  "64",  "65",  "66",  "67",  "68",  "69",
             "70",  "71",  "72",  "73",  "74",  "75",  "76",  "77",  "78",  "79",
             "80",  "81",  "82",  "83",  "84",  "85",  "86",  "87",  "88",  "89",
             "90",  "91",  "92",  "93",  "94",  "95",  "96",  "97",  "98",  "99",
            "100", "101", "102", "103", "104", "105", "106", "107", "108", "109",
            "110", "111", "112", "113", "114", "115", "116", "117", "118", "119",
            "120", "121", "122", "123", "124", "125", "126", "127", "128", "129"})
    public int OFFSET;

    // To get compile-time constants for OFFSET
    static final MutableCallSite MUTABLE_CONSTANT = new MutableCallSite(MethodType.methodType(int.class));
    static final MethodHandle MUTABLE_CONSTANT_HANDLE = MUTABLE_CONSTANT.dynamicInvoker();

    public int START = 1000;

    private byte[] aB;
    private short[] aS;
    private int[] aI;
    private long[] aL;

    @Param("0")
    private int seed;
    private Random r = new Random(seed);

    @Setup
    public void init() throws Throwable {
        aB = new byte[SIZE];
        aS = new short[SIZE];
        aI = new int[SIZE];
        aL = new long[SIZE];

        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)r.nextInt();
            aS[i] = (short)r.nextInt();
            aI[i] = r.nextInt();
            aL[i] = r.nextLong();
        }

        MethodHandle constant = MethodHandles.constant(int.class, OFFSET);
        MUTABLE_CONSTANT.setTarget(constant);
    }

    @CompilerControl(CompilerControl.Mode.INLINE)
    private int offset_con() throws Throwable {
        return (int) MUTABLE_CONSTANT_HANDLE.invokeExact();
    }

    @Benchmark
    public void bytes() throws Throwable {
        int offset = offset_con();
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - offset] + 1);
        }
    }

    @Benchmark
    public void shorts() throws Throwable {
        int offset = offset_con();
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - offset] + 1);
        }
    }

    @Benchmark
    public void ints() throws Throwable {
        int offset = offset_con();
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - offset] + 1;
        }
    }

    @Benchmark
    public void longs() throws Throwable {
        int offset = offset_con();
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - offset] + 1);
        }
    }

    @Fork(value = 1, jvmArgs = {
        "-XX:+UseSuperWord"
    })
    public static class Default extends VectorStoreToLoadForwarding {}

    @Fork(value = 1, jvmArgs = {
        "-XX:-UseSuperWord"
    })
    public static class NoVectorization extends VectorStoreToLoadForwarding {}

    @Fork(value = 1, jvmArgs = {
        "-XX:+UseSuperWord", "-XX:+UnlockDiagnosticVMOptions", "-XX:SuperWordStoreToLoadForwardingFailureDetection=0"
    })
    public static class NoStoreToLoadForwardFailureDetection extends VectorStoreToLoadForwarding {}
}
