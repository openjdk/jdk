/*
 * Copyright (c) 2001, 2022, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4638365
 * @summary Test FileChannel.transferFrom and transferTo for 4GB files
 * @build FileChannelUtils
 * @run testng/timeout=300 Transfer4GBFile
 */

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.Test;

import static java.nio.file.StandardOpenOption.*;

public class Transfer4GBFile {

    private static PrintStream err = System.err;
    private static PrintStream out = System.out;

    // Test transferTo with large file
    @Test
    public void xferTest04() throws Exception { // for bug 4638365
        Path source = FileChannelUtils.createSparseTempFile("blah", null);
        source.toFile().deleteOnExit();
        long testSize = ((long)Integer.MAX_VALUE) * 2;

        out.println("  Writing large file...");
        long t0 = System.nanoTime();
        try (FileChannel fc = FileChannel.open(source, READ, WRITE)) {
            fc.write(ByteBuffer.wrap("Use the source!".getBytes()), testSize - 40);
            long t1 = System.nanoTime();
            out.printf("  Wrote large file in %d ns (%d ms) %n",
                    t1 - t0, TimeUnit.NANOSECONDS.toMillis(t1 - t0));
        }

        Path sink = Files.createTempFile("sink", null);
        sink.toFile().deleteOnExit();

        try (FileChannel sourceChannel = FileChannel.open(source, READ);
             FileChannel sinkChannel = FileChannel.open(sink, WRITE)) {

            long bytesWritten = sourceChannel.transferTo(testSize - 40, 10,
                    sinkChannel);
            if (bytesWritten != 10) {
                throw new RuntimeException("Transfer test 4 failed " +
                        bytesWritten);
            }
        }

        Files.delete(source);
        Files.delete(sink);
    }

    // Test transferFrom with large file
    @Test
    public void xferTest05() throws Exception { // for bug 4638365
        // Create a source file & large sink file for the test
        Path source = Files.createTempFile("blech", null);
        source.toFile().deleteOnExit();
        initTestFile(source, 100);

        // Create the sink file as a sparse file if possible
        Path sink = FileChannelUtils.createSparseTempFile("sink", null);
        sink.toFile().deleteOnExit();
        long testSize = ((long)Integer.MAX_VALUE) * 2;
        try (FileChannel fc = FileChannel.open(sink, WRITE)){
            out.println("  Writing large file...");
            long t0 = System.nanoTime();
            fc.write(ByteBuffer.wrap("Use the source!".getBytes()),
                     testSize - 40);
            long t1 = System.nanoTime();
            out.printf("  Wrote large file in %d ns (%d ms) %n",
            t1 - t0, TimeUnit.NANOSECONDS.toMillis(t1 - t0));
        } catch (IOException e) {
            // Can't set up the test, abort it
            err.println("xferTest05 was aborted.");
            return;
        }

        // Get new channels for the source and sink and attempt transfer
        try (FileChannel sourceChannel = FileChannel.open(source, READ);
             FileChannel sinkChannel = FileChannel.open(sink, WRITE)) {
            long bytesWritten = sinkChannel.transferFrom(sourceChannel,
                    testSize - 40, 10);
            if (bytesWritten != 10) {
                throw new RuntimeException("Transfer test 5 failed " +
                        bytesWritten);
            }
        }

        Files.delete(source);
        Files.delete(sink);
    }

    /**
     * Creates file blah of specified size in bytes.
     */
    private static void initTestFile(Path blah, long size) throws Exception {
        try (BufferedWriter awriter = Files.newBufferedWriter(blah,
                StandardCharsets.ISO_8859_1)) {

            for (int i = 0; i < size; i++) {
                awriter.write("e");
            }
        }
    }
}
