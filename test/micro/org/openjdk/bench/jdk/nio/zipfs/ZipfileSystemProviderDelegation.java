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
package org.openjdk.bench.jdk.nio.zipfs;

import  org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Simple benchmark measuring cost of Files::exist, Files::isDirectory and
 * Files::isRegularFile with ZipFileSystem.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
public class ZipfileSystemProviderDelegation {

    public static final String ZIP_FILE = "zipfsprovider-delegation-benchmark.zip";
    public static final String NON_EXISTENT_SUFFIX = "-nope";
    @Param({"256", "512"})
    private int entriesToTest;
    public String[] entries;
    private FileSystem zipfs;

    private int index = 0;

    @Setup
    public void setup() throws IOException {
        Path zip = Paths.get(ZIP_FILE);
        Files.deleteIfExists(zip);
        Random random = new Random(4711);
        entries = new String[entriesToTest];
        URI zipURI = URI.create("jar:file:"+ zip.toAbsolutePath().toString());
        Map<String, String> env = new HashMap<>();
        env.put("create", "true");
        zipfs = FileSystems.newFileSystem(zipURI, env);
            for (int i = 0; i < entriesToTest; i++) {
                Path dir = zipfs.getPath("dir-" + (random.nextInt(90000) + 10000)
                        + "-" + i);
                Files.createDirectory(dir);
                Path entry = dir.resolve("entry-" +
                        (random.nextInt(90000) + 10000)
                        + "-" + i);
                Files.write(entry, "".getBytes(StandardCharsets.UTF_8));
                entries[i] = entry.toString();
            }
    }

    @TearDown
    public void cleanup() throws IOException {
        zipfs.close();
        Files.deleteIfExists(Paths.get(ZIP_FILE));
    }

    @Benchmark
    public void existsWithEntry() {
        if (index >= entriesToTest) {
            index = 0;
        }
        Files.exists(zipfs.getPath(entries[index++]));
    }

    @Benchmark
    public void existsWithNonExistingEntry() {
        if (index >= entriesToTest) {
            index = 0;
        }
        Files.exists(zipfs.getPath(entries[index++] + NON_EXISTENT_SUFFIX));
    }

    @Benchmark
    public void isDirectoryWithEntry() {
        if (index >= entriesToTest) {
            index = 0;
        }
        Files.isDirectory(zipfs.getPath(entries[index++]));
    }

    @Benchmark
    public void isDirectoryWithNonExistingEntry() {
        if (index >= entriesToTest) {
            index = 0;
        }
        Files.isDirectory(zipfs.getPath(entries[index++] + NON_EXISTENT_SUFFIX));
    }

    @Benchmark
    public void isRegularFileWithEntry() {
        if (index >= entriesToTest) {
            index = 0;
        }
        Files.isRegularFile(zipfs.getPath(entries[index++]));
    }

    @Benchmark
    public void isRegularFileWithNonExistingEntry() {
        if (index >= entriesToTest) {
            index = 0;
        }
        Files.isRegularFile(zipfs.getPath(entries[index++] + NON_EXISTENT_SUFFIX));
    }

}
