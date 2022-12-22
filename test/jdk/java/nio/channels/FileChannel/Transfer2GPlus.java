/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8271308
 * @summary Verify that transferTo() copies more than Integer.MAX_VALUE bytes
 * @library .. /test/lib
 * @build jdk.test.lib.Platform jdk.test.lib.RandomFactory FileChannelUtils
 * @run main/othervm/timeout=300 Transfer2GPlus
 * @key randomness
 */

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import jdk.test.lib.Platform;
import jdk.test.lib.RandomFactory;

import static java.nio.file.StandardOpenOption.*;

public class Transfer2GPlus {
    private static final long BASE   = (long)Integer.MAX_VALUE;
    private static final int  EXTRA  = 1024;
    private static final long LENGTH = BASE + EXTRA;
    private static final Random GEN  = RandomFactory.getRandom();

    public static void main(String[] args) throws IOException {
        Path src = FileChannelUtils.createSparseTempFile("src", ".dat");
        src.toFile().deleteOnExit();
        long t0 = System.nanoTime();
        byte[] b = createSrcFile(src);
        long t1 = System.nanoTime();
        System.out.printf("  Wrote large file in %d ns (%d ms) %n",
                t1 - t0, TimeUnit.NANOSECONDS.toMillis(t1 - t0));
        t0 = t1;
        testToFileChannel(src, b);
        t1 = System.nanoTime();
        System.out.printf("  Copied to file channel in %d ns (%d ms) %n",
                t1 - t0, TimeUnit.NANOSECONDS.toMillis(t1 - t0));
        t0 = t1;
        testToWritableByteChannel(src, b);
        t1 = System.nanoTime();
        System.out.printf("  Copied to byte channel in %d ns (%d ms) %n",
                t1 - t0, TimeUnit.NANOSECONDS.toMillis(t1 - t0));
    }

    // Create a file of size LENGTH with EXTRA random bytes at offset BASE.
    private static byte[] createSrcFile(Path src)
        throws IOException {
        try (FileChannel fc = FileChannel.open(src, WRITE)) {
            fc.position(BASE);
            byte[] b = new byte[EXTRA];
            GEN.nextBytes(b);
            fc.write(ByteBuffer.wrap(b));
            return b;
        }
    }

    // Exercises transferToDirectly() on Linux and transferToTrustedChannel()
    // on macOS and Windows.
    private static void testToFileChannel(Path src, byte[] expected)
        throws IOException {
        Path dst = Files.createTempFile("dst", ".dat");
        dst.toFile().deleteOnExit();
        try (FileChannel srcCh = FileChannel.open(src)) {
            try (FileChannel dstCh = FileChannel.open(dst, READ, WRITE)) {
                long total = 0L;
                if ((total = srcCh.transferTo(0, LENGTH, dstCh)) < LENGTH) {
                    if (!Platform.isLinux())
                        throw new RuntimeException("Transfer too small: " + total);

                    // If this point is reached we're on Linux which cannot
                    // transfer all LENGTH bytes in one call to sendfile(2),
                    // so loop to get the rest.
                    do {
                        long n = srcCh.transferTo(total, LENGTH, dstCh);
                        if (n == 0)
                            break;
                        total += n;
                    } while (total < LENGTH);
                }

                if (dstCh.size() < LENGTH)
                    throw new RuntimeException("Target file too small: " +
                        dstCh.size() + " < " + LENGTH);

                System.out.println("Transferred " + total + " bytes");

                dstCh.position(BASE);
                ByteBuffer bb = ByteBuffer.allocate(EXTRA);
                dstCh.read(bb);
                if (!Arrays.equals(bb.array(), expected))
                    throw new RuntimeException("Unexpected values");
            }
        }
    }

    // Exercises transferToArbitraryChannel() on all platforms.
    private static void testToWritableByteChannel(Path src, byte[] expected)
        throws IOException {
        // transfer src to channel that is not FileChannelImpl
        try (FileChannel srcCh = FileChannel.open(src);
             ByteArrayOutputStream baos = new ByteArrayOutputStream(EXTRA);
             OutputStream os = new SkipBytesStream(baos, BASE);
             WritableByteChannel wbc = Channels.newChannel(os)){

            long n;
            if ((n = srcCh.transferTo(0, LENGTH, wbc)) < LENGTH)
                throw new RuntimeException("Too few bytes transferred: " +
                        n + " < " + LENGTH);

            System.out.println("Transferred " + n + " bytes");

            byte[] b = baos.toByteArray();
            if (!Arrays.equals(b, expected))
                throw new RuntimeException("Unexpected values");
        }
    }

    /**
     * Stream that discards the first bytesToSkip bytes, then passes through
     */
    static class SkipBytesStream extends FilterOutputStream {

        private long bytesToSkip;

        public SkipBytesStream(OutputStream out, long bytesToSkip) {
            super(out);
            this.bytesToSkip = bytesToSkip;
        }

        @Override
        public void write(int b) throws IOException {
            if (bytesToSkip > 0) {
                bytesToSkip--;
            } else {
                super.write(b);
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            // check copied from FilterOutputStream
            if ((off | len | (b.length - (len + off)) | (off + len)) < 0)
                throw new IndexOutOfBoundsException();

            if (bytesToSkip >= len) {
                bytesToSkip -= len;
            } else {
                int skip = (int)bytesToSkip;
                bytesToSkip = 0;
                super.write(b, off + skip, len - skip);
            }
        }
    }
}
