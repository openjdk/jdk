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
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToLongBiFunction;

import jdk.test.lib.RandomFactory;

import static java.lang.String.format;
import static java.nio.file.StandardOpenOption.*;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;
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

    /*
     * Testing API compliance: input stream must throw NullPointerException
     * when parameter "out" is null.
     */
    static void assertNullPointerException(InputStreamProvider inputStreamProvider) {
        // tests empty input stream
        assertThrows(NullPointerException.class, () -> inputStreamProvider.input().transferTo(null));

        // tests single-byte input stream
        assertThrows(NullPointerException.class, () -> inputStreamProvider.input((byte) 1).transferTo(null));

        // tests dual-byte input stream
        assertThrows(NullPointerException.class, () -> inputStreamProvider.input((byte) 1, (byte) 2).transferTo(null));
    }

    /*
     * Testing API compliance: complete content of input stream must be
     * transferred to output stream.
     */
    static void assertStreamContents(InputStreamProvider inputStreamProvider,
            OutputStreamProvider outputStreamProvider) throws Exception {
        // tests empty input stream
        checkTransferredContents(inputStreamProvider, outputStreamProvider, new byte[0]);

        // tests input stream with a length between 1k and 4k
        checkTransferredContents(inputStreamProvider, outputStreamProvider, createRandomBytes(1024, 4096));

        // tests input stream with several data chunks, as 16k is more than a
        // single chunk can hold
        checkTransferredContents(inputStreamProvider, outputStreamProvider, createRandomBytes(16384, 16384));

        // tests randomly chosen starting positions within source and
        // target stream
        for (int i = 0; i < ITERATIONS; i++) {
            byte[] inBytes = createRandomBytes(MIN_SIZE, MAX_SIZE_INCR);
            int posIn = RND.nextInt(inBytes.length);
            int posOut = RND.nextInt(MIN_SIZE);
            checkTransferredContents(inputStreamProvider, outputStreamProvider, inBytes, posIn, posOut);
        }

        // tests reading beyond source EOF (must not transfer any bytes)
        checkTransferredContents(inputStreamProvider, outputStreamProvider, createRandomBytes(4096, 0), 4096, 0);

        // tests writing beyond target EOF (must extend output stream)
        checkTransferredContents(inputStreamProvider, outputStreamProvider, createRandomBytes(4096, 0), 0, 4096);
    }

    /*
     * Special test for stream-to-file / file-to-stream transfer of more than 2 GB.
     */
    static void testMoreThanTwoGB(String direction, BiFunction<Path, Path, InputStream> inputStreamProvider,
            BiFunction<Path, Path, OutputStream> outputStreamProvider,
            ToLongBiFunction<InputStream, OutputStream> transfer) throws IOException {
        // prepare two temporary files to be compared at the end of the test
        // set the source file name
        String sourceName = String.format("test3GBSource_transfer%s%s.tmp", direction,
            RND.nextInt(Integer.MAX_VALUE));
        Path sourceFile = CWD.resolve(sourceName);

        try {
            // set the target file name
            String targetName = String.format("test3GBTarget_transfer%s%s.tmp", direction,
                RND.nextInt(Integer.MAX_VALUE));
            Path targetFile = CWD.resolve(targetName);

            try {
                // calculate initial position to be just short of 2GB
                final long initPos = 2047*BYTES_PER_WRITE;

                // create the source file with a hint to be sparse
                try (FileChannel fc = FileChannel.open(sourceFile, CREATE_NEW, SPARSE, WRITE, APPEND)) {
                    // set initial position to avoid writing nearly 2GB
                    fc.position(initPos);

                    // Add random bytes to the remainder of the file
                    long pos = initPos;
                    while (pos < BYTES_WRITTEN) {
                        int len = Math.toIntExact(BYTES_WRITTEN - pos);
                        if (len > BYTES_PER_WRITE)
                            len = BYTES_PER_WRITE;
                        byte[] rndBytes = createRandomBytes(len, 0);
                        ByteBuffer src = ByteBuffer.wrap(rndBytes);
                        pos += fc.write(src);
                    }
                }

                // create the target file with a hint to be sparse
                try (FileChannel fc = FileChannel.open(targetFile, CREATE_NEW, WRITE, SPARSE)) {
                }

                // performing actual transfer, effectively by multiple invocations of
                // FileChannel.transferFrom(ReadableByteChannel) / FileChannel.transferTo(WritableByteChannel)
                try (InputStream inputStream = inputStreamProvider.apply(sourceFile, targetFile);
                     OutputStream outputStream = outputStreamProvider.apply(sourceFile, targetFile)) {
                    long count = transfer.applyAsLong(inputStream, outputStream);

                    // compare reported transferred bytes, must be 3 GB
                    // less the value of the initial position
                    assertEquals(count, BYTES_WRITTEN - initPos);
                }

                // compare content of both files, failing if different
                assertEquals(Files.mismatch(sourceFile, targetFile), -1);

            } finally {
                 Files.delete(targetFile);
            }
        } finally {
            Files.delete(sourceFile);
        }
    }

}
