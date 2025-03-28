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
package java.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
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

/**
 * Microbenchmark for {@code MemoryOutputStream}. Primarily built to compare
 * performance against {@code ByteArrayOutputStream}. Considers primitive
 * writes, array-based writes, and both output modes ({@code writeTo} and
 * {@code getBytes()}.
 *
 * @since 29
 * @author John Engebretson
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(2)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
public class MemoryOutputStreamBenchmark {
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

    @Param(value = { "4096", "" + (64 * 1024), "" + (1024 * 1024), "" + (16 * 1024 * 1024), "" + (256 * 1024 * 1024) })
    public long responseSize;

    @Param(value = { "512", "8192" })
    public int inputArraySize;

    @Param(value = { "16", "512", "8192" })
    public int initialSize;

    public NoOpOutputStream noopOut;

    public byte[] inputBytes;

    @Param(value = { "true", "false" })
    public boolean useMemoryOutputStream;

    public Serializable serializableTarget;

    public OutputStream populatedOutputStream;

    // @Benchmark
    public Object getByteArray() throws IOException {
        if (useMemoryOutputStream) {
            return ((MemoryOutputStream) populatedOutputStream).toByteArray();
        } else {
            return ((ByteArrayOutputStream) populatedOutputStream).toByteArray();
        }
    }

    private OutputStream getNewOutputStream() {
        return (useMemoryOutputStream ? new MemoryOutputStream(initialSize) : new ByteArrayOutputStream(initialSize));
    }

    // @Benchmark
    public void serializeObjectToMemory() throws IOException {
        OutputStream out = getNewOutputStream();
        ObjectOutputStream objectOut = new ObjectOutputStream(out);
        objectOut.writeObject(serializableTarget);
        objectOut.close();
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

//        populatedOutputStream = getNewOutputStream();
//        for (int i = 0; i < responseSize; i++) {
//            populatedOutputStream.write(i);
//        }

    }

    public byte currentByte;

    @Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
    @Benchmark
    public void writeArrays() throws IOException {
        out = getNewOutputStream();
        long numWrites = responseSize / inputBytes.length + 1;
        for (long i = 0; i < numWrites; i++) {
            out.write(inputBytes);
        }
    }

    public OutputStream out;

    /**
     * This test is prone to the loop being inlined; using the weird formula avoids
     * that.
     * 
     * @throws IOException
     */
    @Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
    @Benchmark
    public void writePrimitives() throws IOException {
        out = getNewOutputStream();
        for (int i = 0; i < responseSize; i++) {
            out.write(inputBytes[Math.abs(i + i * 17) % inputBytes.length]);
        }
    }

    @Benchmark
    public void writeTo() throws IOException {
        if (useMemoryOutputStream) {
            ((MemoryOutputStream) populatedOutputStream).writeTo(noopOut);
        } else {
            ((ByteArrayOutputStream) populatedOutputStream).writeTo(noopOut);
        }
    }
}
