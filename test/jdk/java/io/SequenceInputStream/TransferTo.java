/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.testng.annotations.Test;

import jdk.test.lib.RandomFactory;

import static java.lang.String.format;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/*
 * @test
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @run testng/othervm/timeout=180 TransferTo
 * @bug 8297298
 * @summary Tests whether java.io.SequenceInputStream.transferTo conforms to the
 *          InputStream.transferTo specification
 * @key randomness
 */
public class TransferTo {
    private static final int MIN_SIZE      = 10_000;
    private static final int MAX_SIZE_INCR = 100_000_000 - MIN_SIZE;

    private static final int ITERATIONS = 10;

    private static final Random RND = RandomFactory.getRandom();

    /*
     * Testing API compliance: input stream must throw NullPointerException
     * when parameter "out" is null.
     */
    @Test
    public void testNullPointerException() throws Exception {
        // factory for incoming data provider
        InputStreamProvider inputStreamProvider = byteArrayInput();

        // tests empty input stream
        assertThrows(NullPointerException.class,
                () -> inputStreamProvider.input().transferTo(null));

        // tests single-byte input stream
        assertThrows(NullPointerException.class,
                () -> inputStreamProvider.input((byte) 1).transferTo(null));

        // tests dual-byte input stream
        assertThrows(NullPointerException.class,
                () -> inputStreamProvider.input((byte) 1, (byte) 2).transferTo(null));
    }

    /*
     * Testing API compliance: complete content of input stream must be
     * transferred to output stream.
     */
    @Test
    public void testStreamContents() throws Exception {
        // factory for incoming data provider
        InputStreamProvider inputStreamProvider = byteArrayInput();

        // factory for outgoing data recorder
        OutputStreamProvider outputStreamProvider = byteArrayOutput();

        // tests empty input stream
        checkTransferredContents(inputStreamProvider,
                outputStreamProvider, new byte[0]);

        // tests input stream with a length between 1k and 4k
        checkTransferredContents(inputStreamProvider,
                outputStreamProvider, createRandomBytes(1024, 4096));

        // tests input stream with several data chunks, as 16k is more than a
        // single chunk can hold
        checkTransferredContents(inputStreamProvider,
                outputStreamProvider, createRandomBytes(16384, 16384));

        // tests randomly chosen starting positions within source and
        // target stream
        for (int i = 0; i < ITERATIONS; i++) {
            byte[] inBytes = createRandomBytes(MIN_SIZE, MAX_SIZE_INCR);
            int posIn = RND.nextInt(inBytes.length);
            int posOut = RND.nextInt(MIN_SIZE);
            checkTransferredContents(inputStreamProvider,
                    outputStreamProvider, inBytes, posIn, posOut);
        }

        // tests reading beyond source EOF (must not transfer any bytes)
        checkTransferredContents(inputStreamProvider,
                outputStreamProvider, createRandomBytes(4096, 0), 4096, 0);

        // tests writing beyond target EOF (must extend output stream)
        checkTransferredContents(inputStreamProvider,
                outputStreamProvider, createRandomBytes(4096, 0), 0, 4096);
    }

    /*
     * Special case: Assert subsequent input stream is read when preceding stream already was MAX_VALUE long.
     * Note: Not testing actual content as it requires multiple GBs of memory and long time.
     */
    @Test
    public void testHugeStream() throws Exception {
        InputStream is1 = repeat(0, Long.MAX_VALUE);
        InputStream is2 = repeat(0, 1);
        assertNotEquals(is1.available(), 0);
        assertNotEquals(is2.available(), 0);
        SequenceInputStream sis = new SequenceInputStream(is1, is2);
        OutputStream nos = OutputStream.nullOutputStream();
        sis.transferTo(nos);
        assertEquals(is1.available(), 0);
        assertEquals(is2.available(), 0);
    }

    /*
     * Produces an input stream that returns b count times.
     * Builds a dysfunctional mock that solely implements
     * available() and transferTo() particually,
     * but fails with any other operation.
     */
    private static InputStream repeat(int b, long count) {
        return new InputStream() {
            private long pos;
            @Override
            public int available() throws IOException {
                return (int) Math.min(count - pos, Integer.MAX_VALUE);
            }
            @Override
            public int read() throws IOException {
                throw new UnsupportedOperationException();
            }
            @Override
            public long transferTo(OutputStream os) throws IOException {
                // skipping actual writing to os to spare time
                pos += count;
                return count;
            }
        };
    }

    /*
     * Asserts that the transferred content is correct, i.e., compares the bytes
     * actually transferred to those expected. The position of the input and
     * output streams before the transfer are zero (BOF).
     */
    private static void checkTransferredContents(InputStreamProvider inputStreamProvider,
            OutputStreamProvider outputStreamProvider, byte[] inBytes) throws Exception {
        checkTransferredContents(inputStreamProvider,
                outputStreamProvider, inBytes, 0, 0);
    }

    /*
     * Asserts that the transferred content is correct, i. e. compares the bytes
     * actually transferred to those expected. The positions of the input and
     * output streams before the transfer are provided by the caller.
     */
    private static void checkTransferredContents(InputStreamProvider inputStreamProvider,
            OutputStreamProvider outputStreamProvider, byte[] inBytes, int posIn,
            int posOut) throws Exception {
        AtomicReference<Supplier<byte[]>> recorder = new AtomicReference<>();
        try (InputStream in = inputStreamProvider.input(inBytes);
            OutputStream out = outputStreamProvider.output(recorder::set)) {
            // skip bytes until starting position
            in.skipNBytes(posIn);
            out.write(new byte[posOut]);

            long reported = in.transferTo(out);
            int count = inBytes.length - posIn;

            assertEquals(reported, count,
                    format("reported %d bytes but should report %d", reported, count));

            byte[] outBytes = recorder.get().get();
            assertTrue(Arrays.equals(inBytes, posIn, posIn + count,
                    outBytes, posOut, posOut + count),
                    format("inBytes.length=%d, outBytes.length=%d", count, outBytes.length));
        }
    }

    /*
     * Creates an array of random size (between min and min + maxRandomAdditive)
     * filled with random bytes
     */
    private static byte[] createRandomBytes(int min, int maxRandomAdditive) {
        byte[] bytes = new byte[min +
                                (maxRandomAdditive == 0 ? 0 : RND.nextInt(maxRandomAdditive))];
        RND.nextBytes(bytes);
        return bytes;
    }

    private interface InputStreamProvider {
        InputStream input(byte... bytes) throws Exception;
    }

    private interface OutputStreamProvider {
        OutputStream output(Consumer<Supplier<byte[]>> spy) throws Exception;
    }

    private static InputStreamProvider byteArrayInput() {
        return bytes -> {
            InputStream is1 = new ByteArrayInputStream(bytes, 0, bytes.length / 2);
            InputStream is2 = new ByteArrayInputStream(bytes, bytes.length / 2, bytes.length);
            return new SequenceInputStream(is1, is2);
        };
    }

    private static OutputStreamProvider byteArrayOutput() {
        return new OutputStreamProvider() {
            @Override
            public OutputStream output(Consumer<Supplier<byte[]>> spy) {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                spy.accept(outputStream::toByteArray);
                return outputStream;
            }
        };
    }

}
