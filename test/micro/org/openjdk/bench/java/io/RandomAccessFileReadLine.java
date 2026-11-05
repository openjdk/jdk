/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Reads 1,000 cached ASCII lines with JFR disabled, seeking once per batch.
 * Line lengths exclude the LF terminator. Results are reported per line.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(3)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class RandomAccessFileReadLine {
    private static final int LINES = 1_000;

    @Param({"1", "10", "100"})
    public int characters;

    private Path file;
    private RandomAccessFile raf;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        file = Files.createTempFile("raf-readline-jmh", ".txt");
        String line = "x".repeat(characters);
        Files.writeString(file, (line + "\n").repeat(LINES));
        raf = new RandomAccessFile(file.toFile(), "r");
        for (int i = 0; i < LINES; i++) {
            if (!line.equals(raf.readLine())) {
                throw new IOException("Unexpected line contents at line " + i);
            }
        }
        if (raf.readLine() != null) {
            throw new IOException("Expected EOF after " + LINES + " lines");
        }
    }

    @Benchmark
    @OperationsPerInvocation(LINES)
    public void readLine(Blackhole bh) throws IOException {
        raf.seek(0);
        for (int i = 0; i < LINES; i++) {
            bh.consume(raf.readLine());
        }
    }

    @TearDown(Level.Trial)
    public void teardown() throws IOException {
        raf.close();
        Files.delete(file);
    }
}
