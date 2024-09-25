/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.java.util.zip;

import org.openjdk.jmh.annotations.*;

import java.io.*;
import java.nio.file.Files;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;

/**
 * Benchmark measuring cost of consuming streams from a ZipFile vs a ZipInputStream.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
public class ReadZipStreams {

    @Param({"1024"})
    private int size;

    @Param({"DEFLATED", "STORED"})
    private String method;

    @Param({"true"})
    private boolean buffered;

    public File zipFile;

    @Setup(Level.Trial)
    public void beforeRun() throws IOException {
        // Create a test Zip file with the number of entries.
        File tempFile = Files.createTempFile("zip-micro", ".zip").toFile();
        tempFile.deleteOnExit();
        byte[] content = new byte[2048];
        new Random().nextBytes(content);
        CRC32 crc = new CRC32();
        crc.update(content);
        try (FileOutputStream fos = new FileOutputStream(tempFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            for (int i = 0; i < size; i++) {
                ZipEntry entry = new ZipEntry(Integer.toString(i) +".txt");
                if ("STORED".equals(method)) {
                    entry.setMethod(ZipEntry.STORED);
                    entry.setCrc(crc.getValue());
                    entry.setSize(content.length);
                }
                zos.putNextEntry(entry);
                zos.write(content);
            }
        }
        zipFile = tempFile;
    }


    @Benchmark
    public void zipInputStream() throws Exception {
        try (var fi = new FileInputStream(zipFile);
             var wrap = buffered ? new BufferedInputStream(fi) : fi;
                var in = new ZipInputStream(wrap)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                in.transferTo(OutputStream.nullOutputStream());
            }
        }
    }

    @Benchmark
    public void zipFile() throws Exception {
        try (var zf = new ZipFile(zipFile)) {
            var entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                try (var in = zf.getInputStream(entry)) {
                    in.transferTo(OutputStream.nullOutputStream());
                }
            }
        }
    }

    @Benchmark
    public void zipFileOpenCloseStreams() throws Exception {
        try (var zf = new ZipFile(zipFile)) {
            var entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                zf.getInputStream(entry).close();
            }
        }
    }
}
