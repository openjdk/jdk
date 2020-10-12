/*
 * Copyright (c) BELLSOFT. All rights reserved.
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
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class BitfieldInsert {

    public int intValue;

    @Benchmark
    public int bench1() {
        int b1, b2, b3, b4;
        int base = intValue;
        int a1 = base + 1;
        int a2 = a1 + 1;
        int a3 = a1 + 2;
        int a4 = a1 + 3;

        b1 = (base & 0x1) | ((a1 & 0x1) << 1) | ((a2 & 0x1) << 2) | ((a3 & 0x1) << 3) | ((a4 & 0x1) << 4);
        b2 = (base & 0x1) | ((a1 & 0x3) << 1) | ((a2 & 0x3) << 3) | ((a3 & 0x3) << 5) | ((a4 & 0x3) << 7);
        b3 = (base & 0x1) | ((a1 & 0x7) << 1) | ((a2 & 0x7) << 4) | ((a3 & 0x7) << 7) | ((a4 & 0x7) << 10);
        b4 = (base & 0x1) | ((a1 & 0xf) << 1) | ((a2 & 0xf) << 5) | ((a3 & 0xf) << 9) | ((a4 & 0xf) << 13);

        a1 = (base & 0x1) | ((b1 & 0x1) << 1) | ((b2 & 0x1) << 2) | ((b3 & 0x1) << 3) | ((b4 & 0x1) << 4);
        a2 = (base & 0x1) | ((b1 & 0x3) << 1) | ((b2 & 0x3) << 3) | ((b3 & 0x3) << 5) | ((b4 & 0x3) << 7);
        a3 = (base & 0x1) | ((b1 & 0x7) << 1) | ((b2 & 0x7) << 4) | ((b3 & 0x7) << 7) | ((b4 & 0x7) << 10);
        a4 = (base & 0x1) | ((b1 & 0xf) << 1) | ((b2 & 0xf) << 5) | ((b3 & 0xf) << 9) | ((b4 & 0xf) << 13);

        return a1 + a2 + a3 + a4;
    }

    @Benchmark
    public int bench2() {
        int a = intValue;
        int b = intValue + 1;
        int c = intValue + 2;
        int d = intValue + 3;
        int e = intValue + 4;
        int f = intValue + 5;
        int g = intValue + 6;
        int h = intValue + 7;
        int i = intValue + 8;
        int j = intValue + 9;
        int k = intValue + 10;
        int l = intValue + 11;
        int m = intValue + 12;
        int n = intValue + 13;
        int o = intValue + 14;
        int p = intValue + 15;
        int a1 = ((a & 0x1) << 0) | ((a & 0x3) << 1) | ((a & 0x7) << 3) | ((a & 0xf) << 6) | ((a & 0x1f) << 10) | ((a & 0x3f) << 15) | ((a & 0x7f) << 21);
        int b1 = ((b & 0x1) << 0) | ((b & 0x3) << 1) | ((b & 0x7) << 3) | ((b & 0xf) << 6) | ((b & 0x1f) << 10) | ((b & 0x3f) << 15) | ((b & 0x7f) << 21);
        int c1 = ((c & 0x1) << 0) | ((c & 0x3) << 1) | ((c & 0x7) << 3) | ((c & 0xf) << 6) | ((c & 0x1f) << 10) | ((c & 0x3f) << 15) | ((c & 0x7f) << 21);
        int d1 = ((d & 0x1) << 0) | ((d & 0x3) << 1) | ((d & 0x7) << 3) | ((d & 0xf) << 6) | ((d & 0x1f) << 10) | ((d & 0x3f) << 15) | ((d & 0x7f) << 21);
        int e1 = ((e & 0x1) << 0) | ((e & 0x3) << 1) | ((e & 0x7) << 3) | ((e & 0xf) << 6) | ((e & 0x1f) << 10) | ((e & 0x3f) << 15) | ((e & 0x7f) << 21);
        int f1 = ((f & 0x1) << 0) | ((f & 0x3) << 1) | ((b & 0x7) << 3) | ((f & 0xf) << 6) | ((f & 0x1f) << 10) | ((f & 0x3f) << 15) | ((f & 0x7f) << 21);
        int g1 = ((g & 0x1) << 0) | ((g & 0x3) << 1) | ((a & 0x7) << 3) | ((g & 0xf) << 6) | ((g & 0x1f) << 10) | ((g & 0x3f) << 15) | ((g & 0x7f) << 21);
        int h1 = ((h & 0x1) << 0) | ((h & 0x3) << 1) | ((h & 0x7) << 3) | ((h & 0xf) << 6) | ((h & 0x1f) << 10) | ((h & 0x3f) << 15) | ((h & 0x7f) << 21);
        int i1 = ((i & 0x1) << 0) | ((i & 0x3) << 1) | ((i & 0x7) << 3) | ((i & 0xf) << 6) | ((i & 0x1f) << 10) | ((i & 0x3f) << 15) | ((i & 0x7f) << 21);
        int j1 = ((j & 0x1) << 0) | ((j & 0x3) << 1) | ((j & 0x7) << 3) | ((j & 0xf) << 6) | ((j & 0x1f) << 10) | ((j & 0x3f) << 15) | ((j & 0x7f) << 21);
        int k1 = ((k & 0x1) << 0) | ((k & 0x3) << 1) | ((k & 0x7) << 3) | ((k & 0xf) << 6) | ((k & 0x1f) << 10) | ((k & 0x3f) << 15) | ((k & 0x7f) << 21);
        int l1 = ((l & 0x1) << 0) | ((l & 0x3) << 1) | ((l & 0x7) << 3) | ((l & 0xf) << 6) | ((l & 0x1f) << 10) | ((l & 0x3f) << 15) | ((l & 0x7f) << 21);
        int m1 = ((m & 0x1) << 0) | ((m & 0x3) << 1) | ((m & 0x7) << 3) | ((m & 0xf) << 6) | ((m & 0x1f) << 10) | ((m & 0x3f) << 15) | ((m & 0x7f) << 21);
        int n1 = ((n & 0x1) << 0) | ((n & 0x3) << 1) | ((n & 0x7) << 3) | ((n & 0xf) << 6) | ((n & 0x1f) << 10) | ((n & 0x3f) << 15) | ((n & 0x7f) << 21);
        int o1 = ((o & 0x1) << 0) | ((o & 0x3) << 1) | ((o & 0x7) << 3) | ((o & 0xf) << 6) | ((o & 0x1f) << 10) | ((o & 0x3f) << 15) | ((o & 0x7f) << 21);
        int p1 = ((p & 0x1) << 0) | ((p & 0x3) << 1) | ((p & 0x7) << 3) | ((p & 0xf) << 6) | ((p & 0x1f) << 10) | ((p & 0x3f) << 15) | ((p & 0x7f) << 21);
        return a1 + b1 + c1 + d1 + e1 + f1 + g1 + h1 + i1 + j1 + k1 + l1 + m1 + n1 + o1 + p1;
    }
}
