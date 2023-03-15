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
 * @run junit TransferFromExtend
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static java.nio.file.StandardOpenOption.*;

import jdk.test.lib.RandomFactory;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class TransferFromExtend {
    private static final Random RND = RandomFactory.getRandom();

    private static final Path DIR = Path.of(System.getProperty("test.dir", "."));

    private static Stream<Arguments> paramProvider(int transferSizeMin,
                                                   int transferSizeMax) {
        List<Arguments> list = new ArrayList<Arguments>();
        int sizeDelta = transferSizeMax - transferSizeMin;
        for (int i = 0; i < 10; i++) {
            Arguments args =
                Arguments.of(RND.nextInt(1024),
                             transferSizeMin + RND.nextInt(sizeDelta),
                             1 + RND.nextInt(2047));
            list.add(args);
        }
        return list.stream();
    }

    //
    // transfer size must be greater than the threshold
    // sun.nio.ch.FileChannelImpl::MAPPED_TRANSFER_THRESHOLD (16K)
    // for a mapped transfer to be used when direct is unavailable
    //
    private static Stream<Arguments> fastParamProvider() {
        return paramProvider(16*1024 + 1, 500*1024);
    }

    private static Stream<Arguments> slowParamProvider() {
        return paramProvider(1, 64*1024);
    }

    //
    // Test the direct and memory-mapped paths. At present the direct path
    // is implemented only on Linux. The mapped path will be taken only if
    // there is no direct path and the size of the transfer is more than
    // sun.nio.ch.FileChannelImpl::MAPPED_TRANSFER_THRESHOLD (16K).
    // This method therefore tests the direct path on Linux and the mapped
    // path on other platforms.
    //
    @ParameterizedTest
    @MethodSource("fastParamProvider")
    void fromFast(long initialPosition, int bufSize, long offset)
        throws IOException {
        Path file = Files.createTempFile(DIR, "foo", "bar");
        try (FileChannel src = FileChannel.open(file, DELETE_ON_CLOSE,
                                                READ, WRITE)) {
            byte[] bytes = new byte[bufSize];
            RND.nextBytes(bytes);
            src.write(ByteBuffer.wrap(bytes), 0);
            transferFrom(src, src.size(), initialPosition, offset, bytes);
        }
    }

    //
    // Test the arbitrary source path. This method tests the
    // generic path on all platforms.
    //
    @ParameterizedTest
    @MethodSource("slowParamProvider")
    void fromSlow(long initialPosition, int bufSize, long offset)
        throws IOException {
        byte[] bytes = new byte[bufSize];
        RND.nextBytes(bytes);
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        try (ReadableByteChannel src = Channels.newChannel(bais)) {
            transferFrom(src, bufSize, initialPosition, offset, bytes);
        }
    }

    /**
     * Tests transferring bytes to a FileChannel from a ReadableByteChannel.
     *
     * @param src        the source of the bytes to transfer
     * @param count the  number of bytes to transfer
     * @param initialPos the position of the target channel before the transfer
     * @param offset     the offset beyong the end of the target channel
     * @param bytes      the bytes expected to be transferred
     *
     * @throws RuntimeException if an unexpected number of bytes is transferred
     *                          or the transferred values are not as expected
     */
    private static void transferFrom(ReadableByteChannel src, long count,
                                     long initialPos, long offset, byte[] bytes)
        throws IOException {
        Path file = Files.createTempFile(DIR, "foo", "bar");
        try (FileChannel target = FileChannel.open(file, DELETE_ON_CLOSE,
                                                   READ, WRITE)) {
            target.position(initialPos);
            target.write(ByteBuffer.wrap(new byte[] {(byte)42}));

            long position = target.size() + offset;
            long transferred = target.transferFrom(src, position, count);
            if (transferred != count)
                throw new RuntimeException(transferred + " != " + count);
            ByteBuffer buf = ByteBuffer.allocate((int)count);
            target.read(buf, position);
            if (!Arrays.equals(buf.array(), bytes))
                throw new RuntimeException("arrays unequal");
        }
    }
}
