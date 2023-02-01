/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.nio.file.Files;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Fork(2)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Measurement(iterations = 5, time = 2)

public class RandomAccessFileBenchmark {

    @Param({"1", "5"})
    private int kiloBytes;
    private int size;
    private File file;

    private RandomAccessFile raf;

    private short[] shorts;
    private int[] ints;
    private long[] longs;

    @Setup(Level.Iteration)
    public void beforeRun() throws IOException {
        Random rnd = ThreadLocalRandom.current();
        size = kiloBytes << 10;
        var bytes = new byte[size];
        rnd.nextBytes(bytes);
        file = File.createTempFile(getClass().getName(), ".txt");
        Files.write(file.toPath(), bytes);
        shorts = new short[size];
        for (int i = 0; i < size / Short.BYTES; i++) {
            shorts[i] = (short) rnd.nextInt();
        }
        ints = rnd.ints(size / Integer.BYTES).toArray();
        longs = rnd.longs(size / Long.BYTES).toArray();
        raf = new RandomAccessFile(file, "rw");
        // Make it more likely file content is directly available
        for (int i = 0; i < size / Integer.BYTES; i++) {
            raf.readInt();
        }
    }

    @TearDown(Level.Iteration)
    public void afterRun() throws IOException {
        raf.close();
        file.delete();
    }

    @Benchmark
    public void readShort(Blackhole bh) throws IOException {
        raf.seek(0);
        for (int i = 0; i < size / Short.BYTES; i++) {
            bh.consume(raf.readShort());
        }
    }

    @Benchmark
    public void readInt(Blackhole bh) throws IOException {
        raf.seek(0);
        for (int i = 0; i < size / Integer.BYTES; i++) {
            bh.consume(raf.readInt());
        }
    }

    @Benchmark
    public void readLong(Blackhole bh) throws IOException {
        raf.seek(0);
        for (int i = 0; i < size / Long.BYTES; i++) {
            bh.consume(raf.readLong());
        }
    }

    @Benchmark
    public void writeShort() throws IOException {
        raf.seek(0);
        for (int i = 0; i < size / Short.BYTES; i++) {
            raf.writeShort(shorts[i]);
        }
    }

    @Benchmark
    public void writeInt() throws IOException {
        raf.seek(0);
        for (int i = 0; i < size / Integer.BYTES; i++) {
            raf.writeInt(ints[i]);
        }
    }

    @Benchmark
    public void writeLong() throws IOException {
        raf.seek(0);
        for (int i = 0; i < size / Long.BYTES; i++) {
            raf.writeLong(longs[i]);
        }
    }

}
