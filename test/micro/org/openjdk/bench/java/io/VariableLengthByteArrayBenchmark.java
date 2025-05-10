/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.VariableLengthByteArray;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;


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

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
public class VariableLengthByteArrayBenchmark {
    public static class NoOpOutputStream extends OutputStream {
        public byte[] data;

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            data = b;
        }

        @Override
        public void write(int b) throws IOException {
        }

    }

    public byte currentByte;

    @Param(value = { "32", "8192" })
    public int initialSize;

    @Param(value = { "512" })
    public int inputArraySize;

    public byte[] inputBytes;

    public OutputStream noopOut;

    public ByteArrayOutputStream out;

    @Param(value = { "BOAS", "VLBA" })
    public String outputStreamType;

    public ByteArrayOutputStream populatedOutputStream;

    @Param(value = { "4", "" + (16 * 1024), "" + (16 * 1024 * 1024) })
    public int responseSize;

    public Serializable serializableTarget;

    @Benchmark
    public Object toByteArray() throws IOException {
        return populatedOutputStream.toByteArray();
    }

    private ByteArrayOutputStream getNewOutputStream() {
        switch (outputStreamType) {
        case "BOAS":
            return new ByteArrayOutputStream();
        case "VLBA":
            return VariableLengthByteArray.createByteArrayOutputStream();
        default:
            throw new RuntimeException("Unrecognized type parameter: " + outputStreamType);
        }

    }

    @Setup
    public void setup() throws IOException {
        byte[] bytes = new byte[inputArraySize];
        for (int i = 0; i < inputArraySize; i++) {
            bytes[i] = (byte) i;
        }
        inputBytes = bytes;
        noopOut = new NoOpOutputStream();

        HashMap<String, Serializable> map = new HashMap<>();
        for (int i = 0; i < 150; i++) {
            map.put("" + i, "" + i);
            map.put("Integer_" + i, i);
            map.put("BigInteger_" + i, BigInteger.valueOf(i));
            map.put("hashSet_" + i, new HashSet<>());
        }

        populatedOutputStream = getNewOutputStream();
        int size = 0;
        while (size < responseSize) {
            populatedOutputStream.write(inputBytes);
            size += inputBytes.length;
        }

    }

    @Benchmark
    public void writeArrays() throws IOException {
        out = getNewOutputStream();
        int numWrites = responseSize / inputBytes.length + 1;
        for (int i = 0; i < numWrites; i++) {
            out.write(inputBytes);
        }
    }

    /**
     * This test is prone to the loop being optimized; calculating each byte avoids
     * that.
     *
     * @throws IOException
     */
    @Measurement(iterations = 4, time = 1, timeUnit = TimeUnit.SECONDS)
    @Benchmark
    public void writePrimitives() throws IOException {
        out = getNewOutputStream();
        for (int i = 0; i < responseSize; i++) {
            // logic below provides unpredictable data to avoid low-level shortcuts
            out.write(inputBytes[Math.abs(i + i * 17) % inputBytes.length]);
        }
    }

    @Benchmark
    public void writeTo() throws IOException {
        populatedOutputStream.writeTo(noopOut);
    }
}
