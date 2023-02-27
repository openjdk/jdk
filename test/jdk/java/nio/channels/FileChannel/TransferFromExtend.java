/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8303260
 * @summary Test transferFrom to a position greater than size
 * @library .. /test/lib
 * @build jdk.test.lib.RandomFactory
 * @run main TransferFromExtend
 * @key randomness
 */

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;
import jdk.test.lib.RandomFactory;

import static java.nio.file.StandardOpenOption.*;

public class TransferFromExtend {
    private static final Random RND = RandomFactory.getRandom();

    private static final int ITERATIONS = 10;

    private static final int TARGET_INITIAL_POS_MAX    = 1024;
    private static final int FAST_TRANSFER_SIZE_MIN    = 16*1024 + 1;
    private static final int FAST_TRANSFER_SIZE_MAX    = 500*1024;
    private static final int TARGET_OFFSET_POS_MIN     = 1;
    private static final int TARGET_OFFSET_POS_MAX     = 2048;
    private static final int GENERIC_TRANSFER_SIZE_MAX = 64*1024;

    public static void main(String[] args) throws IOException {
        for (int i = 1; i <= ITERATIONS; i++) {
            System.out.printf("Iteration %d of %d%n", i, ITERATIONS);
            test();
        }
    }

    private static final void test() throws IOException {
        Path dir = Path.of(System.getProperty("test.dir", "."));
        Path file = Files.createTempFile(dir, "foo", "bar");
        try (FileChannel fc = FileChannel.open(file, DELETE_ON_CLOSE,
                                               READ, WRITE)) {
            fc.position(RND.nextInt(TARGET_INITIAL_POS_MAX));
            fc.write(ByteBuffer.wrap(new byte[] {(byte)42}));
            fromDirectlyOrMapped(dir, fc);
            fromArbitrary(fc);
        }
    }

    //
    // Test the direct and memory-mapped paths. At present the direct path
    // is implemented only on Linux. The mapped path will be taken only if
    // there is no direct path and the size of the transfer is more than 16K.
    // This method therefore tests the direct path on Linux and the mapped
    // path on other platforms.
    //
    private static void fromDirectlyOrMapped(Path dir, FileChannel target)
        throws IOException {
        Path file = Files.createTempFile(dir, "foo", "bar");
        try (FileChannel src = FileChannel.open(file, DELETE_ON_CLOSE,
                                                READ, WRITE)) {
            int bufSize = FAST_TRANSFER_SIZE_MIN +
                RND.nextInt(FAST_TRANSFER_SIZE_MAX - FAST_TRANSFER_SIZE_MIN);
            byte[] bytes = new byte[bufSize];
            RND.nextBytes(bytes);
            src.write(ByteBuffer.wrap(bytes), 0);

            final long size = target.size();
            final long position = size + TARGET_OFFSET_POS_MIN +
                RND.nextInt(TARGET_OFFSET_POS_MAX - TARGET_OFFSET_POS_MIN);
            final long count = src.size();
            final long transferred = target.transferFrom(src, position, count);
            if (transferred != count)
                throw new RuntimeException(transferred + " != " + count);
            ByteBuffer buf = ByteBuffer.allocate((int)count);
            target.read(buf, position);
            if (!Arrays.equals(buf.array(), bytes))
                throw new RuntimeException("arrays unequal");
        }
    }

    //
    // Test the arbitrary source path. This method tests the
    // generic path on all platforms.
    //
    private static void fromArbitrary(FileChannel target)
        throws IOException {
        int bufSize = 1 + RND.nextInt(GENERIC_TRANSFER_SIZE_MAX - 1);
        byte[] bytes = new byte[bufSize];
        RND.nextBytes(bytes);
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        try (ReadableByteChannel src = Channels.newChannel(bais)) {
            final long size = target.size();
            final long position = size + TARGET_OFFSET_POS_MIN +
                RND.nextInt(TARGET_OFFSET_POS_MAX - TARGET_OFFSET_POS_MIN);
            final long count = bytes.length;
            final long transferred = target.transferFrom(src, position, count);
            if (transferred != count)
                throw new RuntimeException(transferred + " != " + count);
            ByteBuffer buf = ByteBuffer.allocate((int)count);
            target.read(buf, position);
            if (!Arrays.equals(buf.array(), bytes))
                throw new RuntimeException("arrays unequal");
        }
    }
}
