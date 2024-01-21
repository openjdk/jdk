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

import org.openjdk.jmh.annotations.*;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.SyncFailedException;
import java.util.concurrent.TimeUnit;

/**
 * Tests the cost of FileDescriptor.sync
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(3)
public class FileDescriptorSync {

    private FileOutputStream fos;
    private FileDescriptor fd;

    @Setup
    public void setup() throws IOException {
        File tmp = File.createTempFile("FileDescriptorSync", "bin");
        fos = new FileOutputStream(tmp);
        fd = fos.getFD();
    }

    @TearDown
    public void tearDown() throws IOException {
        fos.close();
    }

    @Benchmark
    public void sync() {
        try {
            fd.sync();
        } catch (SyncFailedException e) {
            // The test assumes the temp filesystem accepts syncs.
            // Avoid failing if it does not, measure the exceptional path then.
        }
    }

}
