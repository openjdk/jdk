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

package org.openjdk.bench.java.nio.file;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import static java.nio.file.StandardOpenOption.*;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

@State(Scope.Benchmark)
public class FilesCopy {

    private static final int SIZE = Integer.MAX_VALUE;
    private static final Path FILE = Path.of("file.dat");
    private static final Path COPY = Path.of("copy.dat");

    @Setup
    public void init() throws IOException {
        Files.deleteIfExists(FILE);
        Files.deleteIfExists(COPY);
        try (FileChannel fc = FileChannel.open(FILE, CREATE_NEW, READ, WRITE)) {
            fc.position(SIZE);
            fc.write(ByteBuffer.wrap(new byte[] {(byte)27}));
        }
    }

    @TearDown
    public void cleanup() throws IOException {
        Files.deleteIfExists(FILE);
        Files.deleteIfExists(COPY);
    }

    @Benchmark
    public void copyFile() throws IOException {
        Files.copy(FILE, COPY);
        Files.delete(COPY);
    }

}
