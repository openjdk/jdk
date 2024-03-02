/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

import jdk.test.lib.RandomFactory;

import static java.lang.String.format;
import static java.nio.file.StandardOpenOption.*;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

class SkipBase {
    static final int MIN_SIZE      = 10_000;
    static final int MAX_SIZE_INCR = 100_000_000 - MIN_SIZE;

    static final int ITERATIONS = 10;

    static final Random RND = RandomFactory.getRandom();

    static final Path CWD = Path.of(".");

    /*
     * Asserts that the skipped content is correct, i.e. compares the bytes
     * still in the input stream to those expected. The position of the input
     * stream before the skip is zero (BOF), and the number of bytes to skip is
     * "all bytes til EOF".
     */
    static void checkSkippedContents(InputStreamProvider inputStreamProvider,
            byte[] inBytes) throws Exception {
        checkSkippedContents(inputStreamProvider, inBytes, inBytes.length);
    }

    /*
     * Asserts that the skipped content is correct, i.e. compares the bytes
     * still in the input stream to those expected. The position of the input
     * stream before the skip is zero (BOF). The number of bytes to skip is
     * provided by the caller.
     */
    static void checkSkippedContents(InputStreamProvider inputStreamProvider,
            byte[] inBytes, long count) throws Exception {
        checkSkippedContents(inputStreamProvider, inBytes, 0, count, false);
    }

    /*
     * Asserts that the skipped content is correct, i.e. compares the bytes
     * still in the input stream to those expected. The position of the input
     * stream before the skip and the number of bytes to skip are provided by
     * the caller.
     */
    static void checkSkippedContents(InputStreamProvider inputStreamProvider,
            byte[] inBytes, int posIn, long count, boolean mustNotSkipAnything) throws Exception {
        try (InputStream in = inputStreamProvider.input(inBytes)) {
            // consume bytes until starting position
            in.readNBytes(posIn);
            long reported = in.skip(count);
            byte[] reminder = in.readAllBytes();

            if (mustNotSkipAnything)
                assertTrue(reported == 0, format("must not skip any bytes, but skipped %d", reported));
            assertTrue(reported >= 0 && reported <= count, format("reported %d bytes but should report between 0 and %d", reported, count));
            int expectedRemainingCount = inBytes.length - posIn - Math.toIntExact(reported);
            assertEquals(reminder.length, expectedRemainingCount,
                    format("remaining %d bytes but should remain %d", reminder.length, expectedRemainingCount));
            assertTrue(Arrays.equals(reminder, 0, reminder.length,
                    inBytes, posIn + Math.toIntExact(reported), inBytes.length),
                    "remaining bytes are dissimilar");
        }
    }

    /*
     * Creates an array of random size (between min and min + maxRandomAdditive)
     * filled with random bytes
     */
    static byte[] createRandomBytes(int min, int maxRandomAdditive) {
        byte[] bytes = new byte[min + (maxRandomAdditive == 0 ? 0 : RND.nextInt(maxRandomAdditive))];
        RND.nextBytes(bytes);
        return bytes;
    }

    interface InputStreamProvider {
        InputStream input(byte... bytes) throws Exception;
    }

    /*
     * Creates a provider for an input stream which wraps a readable byte
     * channel but is not a file channel.
     */
    static InputStreamProvider readableByteChannelInput() {
        return bytes -> Channels.newInputStream(Channels.newChannel(new ByteArrayInputStream(bytes)));
    }

    /*
     * Testing API compliance: 0...n bytes of input stream must be
     * skipped, and the remainder of bytes must not be changed.
     */
    static void assertStreamContents(InputStreamProvider inputStreamProvider) throws Exception {
        // tests empty input stream
        checkSkippedContents(inputStreamProvider, new byte[0]);

        // tests input stream with a length between 1k and 4k
        checkSkippedContents(inputStreamProvider, createRandomBytes(1024, 3072));

        // tests input stream with several data chunks, as 16k is more than a
        // single chunk can hold
        checkSkippedContents(inputStreamProvider, createRandomBytes(16384, 16384));

        // tests randomly chosen starting positions and counts
        for (int i = 0; i < ITERATIONS; i++) {
            byte[] inBytes = createRandomBytes(MIN_SIZE, MAX_SIZE_INCR);
            int posIn = RND.nextInt(inBytes.length);
            long count = RND.nextLong(MIN_SIZE + MAX_SIZE_INCR - posIn);
            checkSkippedContents(inputStreamProvider, inBytes, posIn, count, false);
        }

        // tests reading beyond source EOF (must not skip any bytes)
        checkSkippedContents(inputStreamProvider, createRandomBytes(4096, 0),
                4096, 1, true);
    }

}
