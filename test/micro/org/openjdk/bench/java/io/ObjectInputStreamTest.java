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

package org.openjdk.bench.java.io;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(value = 3, warmups = 0)
@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 2, time = 2)
@State(Scope.Thread)
public class ObjectInputStreamTest {
    private ByteArrayInputStream utfDataAsciiMixed;
    private ByteArrayInputStream utfDataMixed;

    private ByteArrayInputStream utfDataAsciiSmall;
    private ByteArrayInputStream utfDataSmall;

    private ByteArrayInputStream utfDataAsciiLarge;
    private ByteArrayInputStream utfDataLarge;

    // Overhead of creating an ObjectInputStream is significant, need to increase the number of data elements
    // to balance work
    private static final int REPEATS = 20;


    @Setup(Level.Iteration)
    public void setup() throws IOException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream dataOut = new ObjectOutputStream(baos);
        for (int i = 0; i < REPEATS; i++) {
            dataOut.writeUTF("small");
            dataOut.writeUTF("slightly longer string that is more likely to trigger use of simd intrinsics");
        }
        dataOut.flush();
        utfDataAsciiMixed = new ByteArrayInputStream(baos.toByteArray());

        baos = new ByteArrayOutputStream();
        dataOut = new ObjectOutputStream(baos);
        for (int i = 0; i < REPEATS; i++) {
            dataOut.writeUTF("slightly longer string that is more likely to trigger use of simd intrinsics");
            dataOut.writeUTF("slightly longer string that is more likely to trigger use of simd intrinsics");
        }
        dataOut.flush();
        utfDataAsciiLarge = new ByteArrayInputStream(baos.toByteArray());

        baos = new ByteArrayOutputStream();
        dataOut = new ObjectOutputStream(baos);
        for (int i = 0; i < REPEATS; i++) {
            dataOut.writeUTF("smol");
            dataOut.writeUTF("smally");
        }
        dataOut.flush();
        utfDataAsciiSmall = new ByteArrayInputStream(baos.toByteArray());

        baos = new ByteArrayOutputStream();
        dataOut = new ObjectOutputStream(baos);
        for (int i = 0; i < REPEATS; i++) {
            dataOut.writeUTF("sm\u00FFll");
            dataOut.writeUTF("slightly longer string th\u01F3t is more likely to trigger use of simd intrinsics");
        }
        dataOut.flush();
        utfDataMixed = new ByteArrayInputStream(baos.toByteArray());

        baos = new ByteArrayOutputStream();
        dataOut = new ObjectOutputStream(baos);
        for (int i = 0; i < REPEATS; i++) {
            dataOut.writeUTF("sm\u00F3l");
            dataOut.writeUTF("small\u0132");
        }
        dataOut.flush();
        utfDataSmall = new ByteArrayInputStream(baos.toByteArray());

        baos = new ByteArrayOutputStream();
        dataOut = new ObjectOutputStream(baos);
        for (int i = 0; i < REPEATS; i++) {
            dataOut.writeUTF("slightly longer string that is more likely to trigg\u0131r use of simd intrinsics");
            dataOut.writeUTF("slightly longer string th\u0131t is more likely to trigger use of simd intrinsics");
        }
        dataOut.flush();
        utfDataLarge = new ByteArrayInputStream(baos.toByteArray());
    }

    @Benchmark
    public void readUTFAsciiMixed(Blackhole bh) throws Exception {
        utfDataAsciiMixed.reset();
        ObjectInputStream ois = new ObjectInputStream(utfDataAsciiMixed);
        for (int i = 0; i < REPEATS; i++) {
            bh.consume(ois.readUTF());
            bh.consume(ois.readUTF());
        }
    }

    @Benchmark
    public void readUTFAsciiSmall(Blackhole bh) throws Exception {
        utfDataAsciiSmall.reset();
        ObjectInputStream ois = new ObjectInputStream(utfDataAsciiSmall);
        for (int i = 0; i < REPEATS; i++) {
            bh.consume(ois.readUTF());
            bh.consume(ois.readUTF());
        }
    }

    @Benchmark
    public void readUTFAsciiLarge(Blackhole bh) throws Exception {
        utfDataAsciiLarge.reset();
        ObjectInputStream ois = new ObjectInputStream(utfDataAsciiLarge);
        for (int i = 0; i < REPEATS; i++) {
            bh.consume(ois.readUTF());
            bh.consume(ois.readUTF());
        }
    }

    @Benchmark
    public void readUTFMixed(Blackhole bh) throws Exception {
        utfDataMixed.reset();
        ObjectInputStream ois = new ObjectInputStream(utfDataMixed);
        for (int i = 0; i < REPEATS; i++) {
            bh.consume(ois.readUTF());
            bh.consume(ois.readUTF());
        }
    }

    @Benchmark
    public void readUTFSmall(Blackhole bh) throws Exception {
        utfDataSmall.reset();
        ObjectInputStream ois = new ObjectInputStream(utfDataSmall);
        for (int i = 0; i < REPEATS; i++) {
            bh.consume(ois.readUTF());
            bh.consume(ois.readUTF());
        }
    }

    @Benchmark
    public void readUTFLarge(Blackhole bh) throws Exception {
        utfDataLarge.reset();
        ObjectInputStream ois = new ObjectInputStream(utfDataLarge);
        for (int i = 0; i < REPEATS; i++) {
            bh.consume(ois.readUTF());
            bh.consume(ois.readUTF());
        }
    }
}
