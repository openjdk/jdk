/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8274548
 * @summary Test gathering write of more than INT_MAX bytes
 * @requires vm.bits == 64
 * @library ..
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @run main/othervm/timeout=480 -Xmx4G LargeGatheringWrite
 * @key randomness
 */
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;

import jdk.test.lib.RandomFactory;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

public class LargeGatheringWrite {
    private static final int GB = 1024*1024*1024;

    private static final Random RND = RandomFactory.getRandom();

    private static long t0;

    private static void printTime(String msg) {
        System.out.printf("TIMESTAMP: %-16s: %f seconds%n", msg,
            (System.nanoTime() - t0)/1000000000.0);
    }

    public static void main(String[] args) throws IOException {
        t0 = System.nanoTime();
        printTime("start");

        // Create direct and heap buffers
        ByteBuffer direct = ByteBuffer.allocateDirect(GB);
        ByteBuffer heap   = ByteBuffer.allocate(GB);

        // Load buffers with random values
        assert heap.hasArray();
        RND.nextBytes(heap.array());
        direct.put(0, heap, 0, heap.capacity());

        // Create an array of buffers derived from direct and heap
        ByteBuffer[] bigBuffers = new ByteBuffer[] {
            direct,
            heap,
            direct.slice(0, GB/2),
            heap.slice(0, GB/2),
            direct.slice(GB/2, GB/2),
            heap.slice(GB/2, GB/2),
            direct.slice(GB/4, GB/2),
            heap.slice(GB/4, GB/2),
            direct.slice(0, 1),
            heap.slice(GB - 2, 1)
        };

        // Calculate the sum of all buffer capacities
        long totalLength = 0L;
        for(ByteBuffer buf : bigBuffers)
            totalLength += buf.capacity();

        // Write the data to a temporary file
        Path tempFile = Files.createTempFile("LargeGatheringWrite", ".dat");
        try {
            printTime("before writing");
            System.out.printf("Writing %d bytes of data...%n", totalLength);
            try (FileChannel fcw = FileChannel.open(tempFile, CREATE, WRITE);) {
                // Print size of individual writes and total number written
                long bytesWritten = 0;
                long n;
                while ((n = fcw.write(bigBuffers)) > 0) {
                    System.out.printf("Wrote %d bytes\n", n);
                    bytesWritten += n;
                }
                System.out.printf("Total of %d bytes written\n", bytesWritten);
            }
            printTime("after writing");

            // Verify the content written
            try (FileChannel fcr = FileChannel.open(tempFile, READ);) {
                byte[] bytes = null;
                for (ByteBuffer buf : bigBuffers) {
                    printTime("before verifying");

                    // For each buffer read the corresponding number of bytes
                    buf.rewind();
                    int length = buf.remaining();
                    System.out.printf("Checking length %d%n", length);
                    if (bytes == null || bytes.length < length)
                        bytes = new byte[length];
                    ByteBuffer dst = ByteBuffer.wrap(bytes).slice(0, length);
                    if (dst.remaining() != length)
                        throw new RuntimeException("remaining");
                    long totalRead = 0;
                    while (totalRead < length) {
                        int bytesRead = fcr.read(dst);
                        if (bytesRead < 0)
                            throw new RuntimeException("premature EOF");
                        totalRead += bytesRead;
                    }
                    dst.rewind();

                    // Verify that the bytes read from the file match the buffer
                    int mismatch;
                    if ((mismatch = dst.mismatch(buf)) != -1) {
                        String msg = String.format("mismatch: %d%n", mismatch);
                        throw new RuntimeException(msg);
                    }

                    printTime("after verifying");
                }
            }
        } finally {
            Files.delete(tempFile);
        }
        printTime("end");
    }
}
