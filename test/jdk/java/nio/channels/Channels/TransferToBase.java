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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import jdk.test.lib.RandomFactory;

import static java.lang.String.format;
import static java.nio.file.StandardOpenOption.*;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

class TransferToBase {
    static final int MIN_SIZE      = 10_000;
    static final int MAX_SIZE_INCR = 100_000_000 - MIN_SIZE;

    static final int ITERATIONS = 10;

    static final int NUM_WRITES = 3*1024;
    static final int BYTES_PER_WRITE = 1024*1024;
    static final long BYTES_WRITTEN = (long) NUM_WRITES*BYTES_PER_WRITE;

    static final Random RND = RandomFactory.getRandom();

    static final Path CWD = Path.of(".");

    /*
     * Asserts that the transferred content is correct, i.e., compares the bytes
     * actually transferred to those expected. The position of the input and
     * output streams before the transfer are zero (BOF).
     */
    static void checkTransferredContents(InputStreamProvider inputStreamProvider,
            OutputStreamProvider outputStreamProvider, byte[] inBytes) throws Exception {
        checkTransferredContents(inputStreamProvider, outputStreamProvider, inBytes, 0, 0);
    }

    /*
     * Asserts that the transferred content is correct, i.e. compares the bytes
     * actually transferred to those expected. The positions of the input and
     * output streams before the transfer are provided by the caller.
     */
    static void checkTransferredContents(InputStreamProvider inputStreamProvider,
            OutputStreamProvider outputStreamProvider, byte[] inBytes, int posIn, int posOut) throws Exception {
        AtomicReference<Supplier<byte[]>> recorder = new AtomicReference<>();
        try (InputStream in = inputStreamProvider.input(inBytes);
            OutputStream out = outputStreamProvider.output(recorder::set)) {
            // skip bytes until starting position
            in.skipNBytes(posIn);
            out.write(new byte[posOut]);

            long reported = in.transferTo(out);
            int count = inBytes.length - posIn;

            assertEquals(reported, count, format("reported %d bytes but should report %d", reported, count));

            byte[] outBytes = recorder.get().get();
            assertTrue(Arrays.equals(inBytes, posIn, posIn + count, outBytes, posOut, posOut + count),
                format("inBytes.length=%d, outBytes.length=%d", count, outBytes.length));
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

    interface OutputStreamProvider {
        OutputStream output(Consumer<Supplier<byte[]>> spy) throws Exception;
    }

    /*
     * Creates a provider for an input stream which wraps a readable byte
     * channel but is not a file channel
     */
    static InputStreamProvider readableByteChannelInput() {
        return bytes -> Channels.newInputStream(Channels.newChannel(new ByteArrayInputStream(bytes)));
    }

    /*
     * Creates a provider for an output stream which wraps a file channel
     */
    static OutputStreamProvider fileChannelOutput() {
        return spy -> {
            Path path = Files.createTempFile(CWD, "fileChannelOutput", null);
            FileChannel fileChannel = FileChannel.open(path, WRITE);
            spy.accept(() -> {
                try {
                    return Files.readAllBytes(path);
                } catch (IOException e) {
                    throw new AssertionError("Failed to verify output file", e);
                }
            });
            return Channels.newOutputStream(fileChannel);
        };
    }

}
