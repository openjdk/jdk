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

import static java.lang.String.format;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import jdk.test.lib.RandomFactory;

/*
 * @test
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @run main TransferTo
 * @bug 8265891
 * @summary tests whether sun.nio.ChannelInputStream.transferTo conforms to the
 *          InputStream.transferTo contract defined in the javadoc
 * @key randomness
 */
public class TransferTo {
    private static final int MIN_SIZE      = 10_000;
    private static final int MAX_SIZE_INCR = 100_000_000 - MIN_SIZE;

    private static final int ITERATIONS = 10;

    private static final Random RND = RandomFactory.getRandom();

    public static void main(String[] args) throws Exception {
        test(fileChannelInput(), fileChannelOutput());
        test(readableByteChannelInput(), defaultOutput());
    }

    private static void test(InputStreamProvider inputStreamProvider, OutputStreamProvider outputStreamProvider)
            throws Exception {
        testNullPointerException(inputStreamProvider);
        testStreamContents(inputStreamProvider, outputStreamProvider);
    }

    private static void testNullPointerException(InputStreamProvider inputStreamProvider) throws Exception {
        try (InputStream in = inputStreamProvider.input()) {
            assertThrowsNPE(() -> in.transferTo(null), "out");
        }

        try (InputStream in = inputStreamProvider.input((byte) 1)) {
            assertThrowsNPE(() -> in.transferTo(null), "out");
        }

        try (InputStream in = inputStreamProvider.input((byte) 1, (byte) 2)) {
            assertThrowsNPE(() -> in.transferTo(null), "out");
        }
    }

    private static void testStreamContents(InputStreamProvider inputStreamProvider,
            OutputStreamProvider outputStreamProvider) throws Exception {
        checkTransferredContents(inputStreamProvider, outputStreamProvider, new byte[0]);
        checkTransferredContents(inputStreamProvider, outputStreamProvider, createRandomBytes(1024, 4096));

        // to span through several batches
        checkTransferredContents(inputStreamProvider, outputStreamProvider, createRandomBytes(16384, 16384));

        // randomly chosen starting positions within source and target
        for (int i = 0; i < ITERATIONS; i++) {
            byte[] inBytes = createRandomBytes(MIN_SIZE, MAX_SIZE_INCR);
            int posIn = RND.nextInt(inBytes.length);
            int posOut = RND.nextInt(MIN_SIZE);
            checkTransferredContents(inputStreamProvider, outputStreamProvider, inBytes, posIn, posOut);
        }

        // beyond source EOF
        checkTransferredContents(inputStreamProvider, outputStreamProvider, createRandomBytes(4096, 0), 4096, 0);

        // beyond target EOF
        checkTransferredContents(inputStreamProvider, outputStreamProvider, createRandomBytes(4096, 0), 0, 4096);
    }

    private static void checkTransferredContents(InputStreamProvider inputStreamProvider,
            OutputStreamProvider outputStreamProvider, byte[] inBytes) throws Exception {
        checkTransferredContents(inputStreamProvider, outputStreamProvider, inBytes, 0, 0);
    }

    private static void checkTransferredContents(InputStreamProvider inputStreamProvider,
            OutputStreamProvider outputStreamProvider, byte[] inBytes, int posIn, int posOut) throws Exception {
        AtomicReference<Supplier<byte[]>> recorder = new AtomicReference<>();
        try (InputStream in = inputStreamProvider.input(inBytes);
                OutputStream out = outputStreamProvider.output(recorder::set)) {
            // skip bytes till starting position
            in.skipNBytes(posIn);
            out.write(new byte[posOut]);

            long reported = in.transferTo(out);
            int count = inBytes.length - posIn;

            if (reported != count)
                throw new AssertionError(
                        format("reported %d bytes but should report %d", reported, count));

            byte[] outBytes = recorder.get().get();
            if (!Arrays.equals(inBytes, posIn, posIn + count, outBytes, posOut, posOut + count))
                throw new AssertionError(
                        format("inBytes.length=%d, outBytes.length=%d", count, outBytes.length));
        }
    }

    private static byte[] createRandomBytes(int min, int maxRandomAdditive) {
        byte[] bytes = new byte[min + (maxRandomAdditive == 0 ? 0 : RND.nextInt(maxRandomAdditive))];
        RND.nextBytes(bytes);
        return bytes;
    }

    private interface InputStreamProvider {
        InputStream input(byte... bytes) throws Exception;
    }

    private interface OutputStreamProvider {
        OutputStream output(Consumer<Supplier<byte[]>> spy) throws Exception;
    }

    private static OutputStreamProvider defaultOutput() {
        return new OutputStreamProvider() {
            @Override
            public OutputStream output(Consumer<Supplier<byte[]>> spy) {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                spy.accept(outputStream::toByteArray);
                return outputStream;
            }
        };
    }

    private static InputStreamProvider fileChannelInput() {
        return new InputStreamProvider() {
            @Override
            public InputStream input(byte... bytes) throws Exception {
                Path path = Files.createTempFile(null, null);
                Files.write(path, bytes);
                FileChannel fileChannel = FileChannel.open(path);
                return Channels.newInputStream(fileChannel);
            }
        };
    }

    private static InputStreamProvider readableByteChannelInput() {
        return new InputStreamProvider() {
            @Override
            public InputStream input(byte... bytes) throws Exception {
                return Channels.newInputStream(Channels.newChannel(new ByteArrayInputStream(bytes)));
            }
        };
    }

    private static OutputStreamProvider fileChannelOutput() {
        return new OutputStreamProvider() {
            public OutputStream output(Consumer<Supplier<byte[]>> spy) throws Exception {
                Path path = Files.createTempFile(null, null);
                FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.WRITE);
                spy.accept(() -> {
                    try {
                        return Files.readAllBytes(path);
                    } catch (IOException e) {
                        throw new AssertionError("Failed to verify output file", e);
                    }
                });
                return Channels.newOutputStream(fileChannel);
            }
        };
    }

    public interface Thrower {
        public void run() throws Throwable;
    }

    public static void assertThrowsNPE(Thrower thrower, String message) {
        assertThrows(thrower, NullPointerException.class, message);
    }

    public static <T extends Throwable> void assertThrows(Thrower thrower, Class<T> throwable, String message) {
        Throwable thrown;
        try {
            thrower.run();
            thrown = null;
        } catch (Throwable caught) {
            thrown = caught;
        }

        if (!throwable.isInstance(thrown)) {
            String caught = thrown == null ? "nothing" : thrown.getClass().getCanonicalName();
            throw new AssertionError(format("Expected to catch %s, but caught %s", throwable, caught), thrown);
        }

        if (thrown != null && !message.equals(thrown.getMessage())) {
            throw new AssertionError(
                    format("Expected exception message to be '%s', but it's '%s'", message, thrown.getMessage()));
        }
    }
}
