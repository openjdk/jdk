/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4619075
 * @summary Verify gathering and scattering of Channels.newChannel creations
 * @library ..
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @run testng/othervm/timeout=900 -Xmx8G GatherAndScatter
 * @key randomness
 */
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import jdk.test.lib.RandomFactory;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class GatherAndScatter {

    private static final Random RAND = RandomFactory.getRandom();

    // Number of different cases per test
    private static final int NUM_CASES = 50;

    // Number of buffers per case
    private static final int MAX_BUFFERS = 20;

    // List of paths to files populated by the gathering test
    // and read by the scattering test
    private List<Path> paths = new ArrayList(NUM_CASES);

    //
    // Creates a temporary file which will be deleted on exit
    //
    private static File createTempFile() throws IOException {
        Path path = Files.createTempFile(Path.of("."), "foo", ".bar");
        File file = path.toFile();
        file.deleteOnExit();
        return file;
    }

    //
    // Interface denoting a lamda with signature (ByteBuffer[],int,int)
    //
    @FunctionalInterface
    private interface GatheringOrScatteringByteChannel {
        long writeOrRead(ByteBuffer[] bufs, int offset, int length)
            throws IOException;
    }

    //
    // Tests for NPEs and preconditions on the offset and length
    //
    private static void testParamExceptions(GatheringOrScatteringByteChannel ch)
        throws IOException {
        // null buffer array
        assertThrows(NullPointerException.class,
            () -> {ch.writeOrRead(null, 0, 1);});

        // array containing one null buffer
        int numBufs = 1 + RAND.nextInt(32);
        ByteBuffer[] bufs = new ByteBuffer[numBufs];
        byte[] b = new byte[0];
        int nullIndex = RAND.nextInt(numBufs);
        for (int i = 0; i < numBufs; i++)
            if (i != nullIndex)
                bufs[i] = ByteBuffer.wrap(b);
        assertThrows(NullPointerException.class,
           () -> {ch.writeOrRead(bufs, 0, numBufs);});

        // preconditions on the offset and length parameters
        assertThrows(IndexOutOfBoundsException.class,
            () -> {ch.writeOrRead(bufs, -1, bufs.length);});
        assertThrows(IndexOutOfBoundsException.class,
            () -> {ch.writeOrRead(bufs, 1, bufs.length);});
        assertThrows(IndexOutOfBoundsException.class,
            () -> {ch.writeOrRead(bufs, 0, bufs.length + 1);});
        assertThrows(IndexOutOfBoundsException.class,
            () -> {ch.writeOrRead(bufs, bufs.length/2, bufs.length/2 + 2);});
    }

    //
    // Test handling of basic exceptions when gathering
    //
    @Test(priority = 0)
    public void gatheringExceptions() throws IOException {
        File file = createTempFile();
        try (FileOutputStream fos = new FileOutputStream(file);
            FilterOutputStream filfos = new FilterOutputStream(fos)) {
            // Create a Channels$GatheringByteChannelImpl
            try (GatheringByteChannel gbc = Channels.newGatheringChannel(filfos)) {
                // test parameters
                testParamExceptions((a, b, c) -> gbc.write(a, b, c));

                // channel is closed
                gbc.close();
                assertThrows(ClosedChannelException.class,
                    () -> {gbc.write(null, 0, 1);});
            }
        }
    }

    //
    // Test handling of basic exceptions when scattering
    //
    @Test(priority = 0)
    public void scatteringExceptions() throws IOException {
        File file = createTempFile();
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.setLength(1024);
        }
        try (FileInputStream fis = new FileInputStream(file);
            BufferedInputStream filfis = new BufferedInputStream(fis)) {
            // Create a Channels$ScatteringByteChannelImpl
            try (ScatteringByteChannel sbc = Channels.newScatteringChannel(filfis)) {
                // test parameters
                testParamExceptions((a, b, c) -> sbc.read(a, b, c));

                // one of buffers is read-only
                int numSrcs = 1 + RAND.nextInt(32);
                ByteBuffer[] srcs = new ByteBuffer[numSrcs];
                byte[] b = new byte[1];
                int readOnlyIndex = RAND.nextInt(numSrcs);
                for (int i = 0; i < numSrcs; i++) {
                    srcs[i] = ByteBuffer.wrap(b);
                    if (i == readOnlyIndex)
                        srcs[i] = srcs[i].asReadOnlyBuffer();
                }
                assertThrows(IllegalArgumentException.class,
                   () -> {sbc.read(srcs, 0, numSrcs);});

                // channel is closed
                sbc.close();
                assertThrows(ClosedChannelException.class,
                    () -> {sbc.read(null, 0, 1);});
            }
        }
    }

    //
    // Returns an array of {{Path, int}, ...} where the Path represents an
    // empty file and the int its future length.
    //
    @DataProvider
    public Object[][] gatheringProvider() throws IOException {
        Object[][] result = new Object[NUM_CASES][];

        Path dir = Files.createTempDirectory(Path.of("."), "gatherScatter");
        dir.toFile().deleteOnExit();

        for (int i = 0; i < NUM_CASES; i++) {
            Path file = Files.createTempFile(dir, "foo", ".bar");
            file.toFile().deleteOnExit();
            paths.add(file);
            int length = 1 + RAND.nextInt(1_000_000);
            result[i] = new Object[] {file, length};
        }

        return result;
    }

    //
    // Tests gathering buffers into a channel
    //
    @Test(dataProvider = "gatheringProvider", priority = 1)
    public void gather(Path f, int len) throws IOException {
        // Create a list of buffers filled with random bytes
        // whose total length is len
        int total = 0;
        List<ByteBuffer> buffers = new ArrayList();
        while (total < len) {
            int count = buffers.size() == MAX_BUFFERS || len - total < 64 ?
                len - total : RAND.nextInt(len - total);
            byte[] b = new byte[count];
            RAND.nextBytes(b);
            buffers.add(ByteBuffer.wrap(b));
            total += count;
        }

        // Derive an array of buffers from the list
        ByteBuffer[] bufs = buffers.toArray(new ByteBuffer[buffers.size()]);

        // Verify that the total number of remaining bytes is as expected.
        int sum = 0;
        for (ByteBuffer buf : bufs)
            sum += buf.remaining();
        assertEquals(len, sum);

        // Determine the range of buffers to use
        int offset = RAND.nextInt(bufs.length);
        int length = 1 + RAND.nextInt(bufs.length - offset);

        // Write the contents of the range of buffers to the file
        try (FileOutputStream fos = new FileOutputStream(f.toFile());
             FilterOutputStream filfos = new FilterOutputStream(fos)) {
            // Count the number of bytes which should be written
            sum = 0;
            for (int i = offset; i < offset + length; i++)
                sum += bufs[i].remaining();

            try (GatheringByteChannel gbc = Channels.newGatheringChannel(filfos)) {
                // Gather the range of buffers into the file
                long written = gbc.write(bufs, offset, length);

                // Verify that the number of bytes written is as expected
                assertEquals(written, sum, "written != sum");
            }
        }

        // For each buffer in range, read the same number of bytes as it
        // contains and compare the two results
        try (FileChannel fc = FileChannel.open(f, READ)) {
            for (int i = offset; i < offset + length; i++) {
                ByteBuffer buf = bufs[i];
                buf.rewind();
                ByteBuffer dst = ByteBuffer.wrap(new byte[buf.remaining()]);
                fc.read(dst);
                dst.rewind();
                int mismatch = dst.mismatch(buf);
                assertEquals(mismatch, -1);
            }
        }
    }

    //
    // Returns an array of {{Path}, ...} where the Path represents a file
    // populated by data during the gathering test
    //
    @DataProvider
    public Object[][] scatteringProvider() throws IOException {
        int numPaths = paths.size();
        Object[][] result = new Object[numPaths][];

        for (int i = 0; i < numPaths; i++) {
            result[i] = new Object[] {paths.get(i)};
        }

        return result;
    }

    //
    // Tests scattering buffers from a channel
    //
    @Test(dataProvider = "scatteringProvider", priority = 2)
    public void scatter(Path f) throws IOException {
        int len = Math.toIntExact(Files.size(f));

        // Create a list of buffers whose total length is len
        int total = 0;
        List<ByteBuffer> buffers = new ArrayList();
        while (total < len) {
            int count = buffers.size() == MAX_BUFFERS || len - total < 64 ?
                len - total : RAND.nextInt(len - total);
            buffers.add(ByteBuffer.allocate(count));
            total += count;
        }

        // Derive an array of buffers from the list
        ByteBuffer[] bufs = buffers.toArray(new ByteBuffer[buffers.size()]);

        // Verify that the total number of remaining bytes is as expected.
        int sum = 0;
        for (ByteBuffer buf : bufs)
            sum += buf.remaining();
        assertEquals(len, sum);

        // Determine the range of buffers to use
        int offset = RAND.nextInt(bufs.length);
        int length = RAND.nextInt(bufs.length - offset);

        // Read the contents of the file into the range of buffers
        try (FileInputStream fis = new FileInputStream(f.toFile());
             FilterInputStream filfis = new BufferedInputStream(fis)) {
            // Count the number of bytes which should be read
            sum = 0;
            for (int i = offset; i < offset + length; i++)
                sum += bufs[i].remaining();

            try (ScatteringByteChannel sbc = Channels.newScatteringChannel(filfis)) {
                // Gather the range of buffers into the file
                long read = sbc.read(bufs, offset, length);

                // Verify that the number of bytes read is as expected
                assertEquals(read, sum, "read != sum");
            }
        }

        // For each buffer in range, read the same number of bytes as it
        // contains and compare the two results
        try (FileChannel fc = FileChannel.open(f, READ)) {
            for (int i = offset; i < offset + length; i++) {
                ByteBuffer buf = bufs[i];
                buf.rewind();
                ByteBuffer dst = ByteBuffer.wrap(new byte[buf.remaining()]);
                fc.read(dst);
                dst.rewind();
                int mismatch = dst.mismatch(buf);
                assertEquals(mismatch, -1);
            }
        }
    }
}
