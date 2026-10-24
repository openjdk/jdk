/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;

/**
 * Tests the overheads of I/O API.
 * This test is known to depend heavily on disk subsystem performance.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(2)
@Warmup(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@State(Scope.Thread)
public class FileWrite {

    @Param("1000000")
    private int fileSize;

    private File f;
    private FileOutputStream fos;
    private long count;

    @Setup(Level.Trial)
    public void beforeRun() throws IOException {
        f = File.createTempFile("FileWriteBench", ".bin");
    }

    @TearDown(Level.Trial)
    public void afterRun() throws IOException {
        f.delete();
    }

    @Setup(Level.Iteration)
    public void beforeIteration() throws FileNotFoundException {
        fos = new FileOutputStream(f);
    }

    @TearDown(Level.Iteration)
    public void afterIteration() throws IOException {
        fos.close();
    }

    @Benchmark
    public void test() throws IOException {
        fos.write((byte) count);
        count++;
        if (count >= fileSize) {
            // restart
            fos.close();
            fos = new FileOutputStream(f);
            count = 0;
        }
    }

    @State(Scope.Benchmark)
    @Warmup(iterations = 3, time = 2)
    @Measurement(iterations = 5, time = 5)
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @Threads(1)
    @Fork(value = 10)
    public static class OpenFileForWritingBench {
        final byte[] payload = "something".getBytes();
        final String path = System.getProperty("os.name", "unknown").toLowerCase().contains("win") ? "NUL" : "/dev/null";

        @Benchmark
        public void testFileOutputStream() throws IOException {
            try (FileOutputStream f = new FileOutputStream(path)) {
                f.write(payload);
            }
        }

        @Benchmark
        public void testRandomAccessFile() throws IOException {
            try (RandomAccessFile f = new RandomAccessFile(path, "rw")) {
                f.write(payload);
            }
        }
    }
}
