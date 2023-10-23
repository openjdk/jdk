/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;


import jdk.internal.misc.Unsafe;
import jdk.internal.util.ByteArrayLittleEndian;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 3, time = 3)
@Fork(value = 3, jvmArgsAppend = {
        "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED",
        "--add-exports", "java.base/jdk.internal.util=ALL-UNNAMED"})
@State(Scope.Benchmark)
public class ByteArrayJMH {

    static Unsafe UNSAFE = Unsafe.getUnsafe();

    @Param("1")
    public static byte v1;

    @Param("1")
    public static int v2;

    @Param("1")
    public static short v3;

    @Param("1")
    public static byte v4;

    public static byte[] a = new byte[9];

    @Benchmark
    public void test0a() {
    }

    @Benchmark
    public byte[] test0b() {
        byte[] a = new byte[8];
        return a;
    }

    @Benchmark
    public byte[] test1a() {
        byte[] a = new byte[8];
        a[0] = v1;
        a[1] = (byte)v2;
        a[2] = (byte)(v2 >> 8 );
        a[3] = (byte)(v2 >> 16);
        a[4] = (byte)(v2 >> 24);
        a[5] = (byte)v3;
        a[6] = (byte)(v3 >> 8 );
        a[7] = v4;
        return a;
    }

    @Benchmark
    public byte[] test1b() {
        byte[] a = new byte[8];
        a[0] = v1;
        UNSAFE.putIntUnaligned(a, Unsafe.ARRAY_BYTE_BASE_OFFSET + 1, v2);
        UNSAFE.putShortUnaligned(a, Unsafe.ARRAY_BYTE_BASE_OFFSET + 5, v3);
        a[7] = v4;
        return a;
    }

    @Benchmark
    public byte[] test1c() {
        byte[] a = new byte[8];
        a[0] = v1;
        ByteArrayLittleEndian.setInt(a, 1, v2);
        ByteArrayLittleEndian.setShort(a, 5, v3);
        a[7] = v4;
        return a;
    }

    @Benchmark
    public void test2a() {
        a[0] = v1;
        a[1] = (byte)v2;
        a[2] = (byte)(v2 >> 8 );
        a[3] = (byte)(v2 >> 16);
        a[4] = (byte)(v2 >> 24);
        a[5] = (byte)v3;
        a[6] = (byte)(v3 >> 8 );
        a[7] = v4;
    }

    @Benchmark
    public void test2b() {
        a[0] = v1;
        UNSAFE.putIntUnaligned(a, Unsafe.ARRAY_BYTE_BASE_OFFSET + 1, v2);
        UNSAFE.putShortUnaligned(a, Unsafe.ARRAY_BYTE_BASE_OFFSET + 5, v3);
        a[7] = v4;
    }

    @Benchmark
    public void test2c() {
        a[0] = v1;
        ByteArrayLittleEndian.setInt(a, 1, v2);
        ByteArrayLittleEndian.setShort(a, 5, v3);
        a[7] = v4;
    }

    @Benchmark
    public void test3a() {
        a[0] = 't';
        a[1] = 'r';
        a[2] = 'u';
        a[3] = 'e';
        a[4] = 'f';
        a[5] = 'a';
        a[6] = 'l';
        a[7] = 's';
        a[8] = 'e';
    }

    @Benchmark
    public void test3b() {
        UNSAFE.putIntUnaligned(a, Unsafe.ARRAY_BYTE_BASE_OFFSET + 0, 0x65757274);
        UNSAFE.putIntUnaligned(a, Unsafe.ARRAY_BYTE_BASE_OFFSET + 4, 0x736c6166);
        a[8] = 'e';
    }

    @Benchmark
    public void test3c() {
        ByteArrayLittleEndian.setInt(a, 0, 0x65757274);
        ByteArrayLittleEndian.setInt(a, 4, 0x736c6166);
        a[8] = 'e';
    }

    @Benchmark
    public void test3d() {
        a[0] = 't';
        UNSAFE.putIntUnaligned(a, Unsafe.ARRAY_BYTE_BASE_OFFSET + 1, 0x66657572);
        UNSAFE.putIntUnaligned(a, Unsafe.ARRAY_BYTE_BASE_OFFSET + 5, 0x65736c61);
    }

    @Benchmark
    public void test3e() {
        a[0] = 't';
        ByteArrayLittleEndian.setInt(a, 1, 0x66657572);
        ByteArrayLittleEndian.setInt(a, 5, 0x65736c61);
    }

    void verify(String name, byte[] a, byte[] b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                throw new RuntimeException("Wrong result: " + name + ": a[" + i + "] = " + a[i] + " != b[" + i + "] = " + b[i]);
            }
        }
    }
}
