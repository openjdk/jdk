/*
 * Copyright (c) 2020, 2024, Red Hat Inc. All rights reserved.
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
@Fork(value = 4, warmups = 0)
@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 2, time = 2)
@State(Scope.Thread)
public class ObjectInputStreamTest {
    private final int size = 1024;

    private ByteArrayInputStream bais;
    private ByteArrayInputStream utfDataAscii;
    private ByteArrayInputStream utfData;

    // Overhead of creating an ObjectInputStream is significant, need to increase the number of data elements
    // to balance work
    private static final int REPEATS = 20;


    @Setup(Level.Iteration)
    public void setup() throws IOException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException {
        byte[] bytes = new byte[size];
        ThreadLocalRandom.current().nextBytes(bytes);
        bais = new ByteArrayInputStream(bytes);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream dataOut = new ObjectOutputStream(baos);
        for (int i = 0; i < REPEATS; i++) {
            dataOut.writeUTF("small");
            dataOut.writeUTF("slightly longer string that is more likely to trigger use of simd intrinsics");
        }
        dataOut.flush();
        utfDataAscii = new ByteArrayInputStream(baos.toByteArray());

        baos = new ByteArrayOutputStream();
        dataOut = new ObjectOutputStream(baos);
        for (int i = 0; i < REPEATS; i++) {
            dataOut.writeUTF("sm\u0132ll");
            dataOut.writeUTF("slightly longer string th\u0131t is more likely to trigger use of simd intrinsics");
        }
        dataOut.flush();
        utfData = new ByteArrayInputStream(baos.toByteArray());
    }

    @Benchmark
    public void readUTF(Blackhole bh) throws Exception {
        utfDataAscii.reset();
        ObjectInputStream dis = new ObjectInputStream(utfDataAscii);
        for (int i = 0; i < REPEATS; i++) {
            bh.consume(dis.readUTF());
            bh.consume(dis.readUTF());
        }
    }

    @Benchmark
    public void readUTFMixed(Blackhole bh) throws Exception {
        utfData.reset();
        ObjectInputStream dis = new ObjectInputStream(utfData);
        for (int i = 0; i < REPEATS; i++) {
            bh.consume(dis.readUTF());
            bh.consume(dis.readUTF());
        }
    }
}
