/*
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

package micro.org.openjdk.bench.java.io;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(value = 4, warmups = 0)
@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 2, time = 2)
@State(Scope.Thread)
public class DataInputStreamTest {
    private static final int SIZE = 1024;

    private ByteArrayInputStream bais;
    private ByteArrayInputStream utfDataAsciiMixed;
    private ByteArrayInputStream utfDataMixed;

    private ByteArrayInputStream utfDataAsciiSmall;
    private ByteArrayInputStream utfDataSmall;

    private ByteArrayInputStream utfDataAsciiLarge;
    private ByteArrayInputStream utfDataLarge;

    private static final int REPEATS = 20;

    @Setup(Level.Iteration)
    public void setup() throws IOException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException {
        byte[] bytes = new byte[SIZE];
        ThreadLocalRandom.current().nextBytes(bytes);
        bais = new ByteArrayInputStream(bytes);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dataOut = new DataOutputStream(baos);
        for (int i = 0; i < REPEATS; i++) {
            dataOut.writeUTF("small");
            dataOut.writeUTF("slightly longer string that is more likely to trigger use of simd intrinsics");
        }
        dataOut.flush();
        utfDataAsciiMixed = new ByteArrayInputStream(baos.toByteArray());

        baos = new ByteArrayOutputStream();
        dataOut = new DataOutputStream(baos);
        for (int i = 0; i < REPEATS; i++) {
            dataOut.writeUTF("slightly longer string that is more likely to trigger use of simd intrinsics");
            dataOut.writeUTF("slightly longer string that is more likely to trigger use of simd intrinsics");
        }
        dataOut.flush();
        utfDataAsciiLarge = new ByteArrayInputStream(baos.toByteArray());

        baos = new ByteArrayOutputStream();
        dataOut = new DataOutputStream(baos);
        for (int i = 0; i < REPEATS; i++) {
            dataOut.writeUTF("smol");
            dataOut.writeUTF("smally");
        }
        dataOut.flush();
        utfDataAsciiSmall = new ByteArrayInputStream(baos.toByteArray());

        baos = new ByteArrayOutputStream();
        dataOut = new DataOutputStream(baos);
        for (int i = 0; i < REPEATS; i++) {
            dataOut.writeUTF("sm\u00FFll");
            dataOut.writeUTF("slightly longer string th\u01F3t is more likely to trigger use of simd intrinsics");
        }
        dataOut.flush();
        utfDataMixed = new ByteArrayInputStream(baos.toByteArray());

        baos = new ByteArrayOutputStream();
        dataOut = new DataOutputStream(baos);
        for (int i = 0; i < REPEATS; i++) {
            dataOut.writeUTF("sm\u00F3l");
            dataOut.writeUTF("small\u0132");
        }
        dataOut.flush();
        utfDataSmall = new ByteArrayInputStream(baos.toByteArray());

        baos = new ByteArrayOutputStream();
        dataOut = new DataOutputStream(baos);
        for (int i = 0; i < REPEATS; i++) {
            dataOut.writeUTF("slightly longer string that is more likely to trigg\u0131r use of simd intrinsics");
            dataOut.writeUTF("slightly longer string th\u0131t is more likely to trigger use of simd intrinsics");
        }
        dataOut.flush();
        utfDataLarge = new ByteArrayInputStream(baos.toByteArray());
    }

    @Benchmark
    public void readChar(Blackhole bh) throws Exception {
        bais.reset();
        DataInputStream dis = new DataInputStream(bais);
        for (int i = 0; i < SIZE / 2; i++) {
            bh.consume(dis.readChar());
        }
    }

    @Benchmark
    public void readInt(Blackhole bh) throws Exception {
        bais.reset();
        DataInputStream dis = new DataInputStream(bais);
        for (int i = 0; i < SIZE / 4; i++) {
            bh.consume(dis.readInt());
        }
    }

    @Benchmark
    public void readUTFAsciiMixed(Blackhole bh) throws Exception {
        utfDataAsciiMixed.reset();
        DataInputStream dis = new DataInputStream(utfDataAsciiMixed);
        for (int i = 0; i < REPEATS; i++) {
            bh.consume(dis.readUTF());
            bh.consume(dis.readUTF());
        }
    }

    @Benchmark
    public void readUTFAsciiSmall(Blackhole bh) throws Exception {
        utfDataAsciiSmall.reset();
        DataInputStream dis = new DataInputStream(utfDataAsciiSmall);
        for (int i = 0; i < REPEATS; i++) {
            bh.consume(dis.readUTF());
            bh.consume(dis.readUTF());
        }
    }

    @Benchmark
    public void readUTFAsciiLarge(Blackhole bh) throws Exception {
        utfDataAsciiLarge.reset();
        DataInputStream dis = new DataInputStream(utfDataAsciiLarge);
        for (int i = 0; i < REPEATS; i++) {
            bh.consume(dis.readUTF());
            bh.consume(dis.readUTF());
        }
    }

    @Benchmark
    public void readUTFMixed(Blackhole bh) throws Exception {
        utfDataMixed.reset();
        DataInputStream dis = new DataInputStream(utfDataMixed);
        for (int i = 0; i < REPEATS; i++) {
            bh.consume(dis.readUTF());
            bh.consume(dis.readUTF());
        }
    }

    @Benchmark
    public void readUTFSmall(Blackhole bh) throws Exception {
        utfDataSmall.reset();
        DataInputStream dis = new DataInputStream(utfDataSmall);
        for (int i = 0; i < REPEATS; i++) {
            bh.consume(dis.readUTF());
            bh.consume(dis.readUTF());
        }
    }

    @Benchmark
    public void readUTFLarge(Blackhole bh) throws Exception {
        utfDataLarge.reset();
        DataInputStream dis = new DataInputStream(utfDataLarge);
        for (int i = 0; i < REPEATS; i++) {
            bh.consume(dis.readUTF());
            bh.consume(dis.readUTF());
        }
    }
}
