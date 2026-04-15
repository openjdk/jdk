/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.util.zip.InflaterOutputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/*
 * @test
 * @bug 8369181
 * @summary Verify the behaviour of basic operations on InflaterOutputStream
 * @run junit ${test.main.class}
 */
class InflaterOutputStreamTest {

    private static final byte[] INPUT_CONTENT = "hello world".getBytes(US_ASCII);
    private static byte[] COMPRESSED_CONTENT;

    @BeforeAll
    static void beforeAll() throws Exception {
        // created the compressed content
        final ByteArrayOutputStream compressedBaos = new ByteArrayOutputStream();
        try (Deflater compressor = new Deflater()) {
            compressor.setInput(INPUT_CONTENT);
            compressor.finish();
            while (!compressor.finished()) {
                final byte[] tmpBuffer = new byte[100];
                final int numCompressed = compressor.deflate(tmpBuffer);
                compressedBaos.write(tmpBuffer, 0, numCompressed);
            }
        }
        COMPRESSED_CONTENT = compressedBaos.toByteArray();
    }

    static List<Arguments> inflaterOutputStreams() {
        final List<Arguments> args = new ArrayList<>();
        args.add(Arguments.of(new InflaterOutputStream(new ByteArrayOutputStream())));
        args.add(Arguments.of(new InflaterOutputStream(new ByteArrayOutputStream(),
                new Inflater())));
        args.add(Arguments.of(new InflaterOutputStream(new ByteArrayOutputStream(),
                new Inflater(), 1024)));
        return args;
    }

    /*
     * Verify that write() methods throw an IOException when the InflaterOutputStream
     * is already closed.
     */
    @ParameterizedTest
    @MethodSource("inflaterOutputStreams")
    void testWriteAfterClose(final InflaterOutputStream ios) throws Exception {
        ios.close();
        IOException ioe = assertThrows(IOException.class, () -> ios.write(COMPRESSED_CONTENT));
        // verify it failed for right reason
        assertStreamClosedIOE(ioe);
    }

    /*
     * Verify that flush() throws an IOException when the InflaterOutputStream
     * is already closed
     */
    @ParameterizedTest
    @MethodSource("inflaterOutputStreams")
    void testFlushAfterClose(final InflaterOutputStream ios) throws Exception {
        ios.close();
        final IOException ioe = assertThrows(IOException.class, ios::flush);
        // verify it failed for right reason
        assertStreamClosedIOE(ioe);
    }

    /*
     * Verify that finish() throws an IOException when the InflaterOutputStream
     * is already closed.
     */
    @ParameterizedTest
    @MethodSource("inflaterOutputStreams")
    void testFinishAfterClose(final InflaterOutputStream ios) throws Exception {
        ios.close();
        final IOException ioe = assertThrows(IOException.class, ios::finish);
        // verify it failed for right reason
        assertStreamClosedIOE(ioe);
    }

    /*
     * Verify that after finish() is called on an InflaterOutputStream that was constructed
     * without specifying a Inflater, any subsequent writes will throw an IOException.
     */
    @Test
    void testWriteAfterFinish() throws IOException {
        // InflaterOutputStream that constructs an internal default Inflater
        final InflaterOutputStream ios = new InflaterOutputStream(new ByteArrayOutputStream());
        ios.finish();
        final IOException ioe = assertThrows(IOException.class, () -> ios.write(COMPRESSED_CONTENT));
        final String msg = ioe.getMessage();
        // verify that it's the right IOException
        if (msg == null || !msg.contains("Inflater closed")) {
            // propagate the original exception
            fail("unexpected exception message in IOException", ioe);
        }

        // repeat with a InflaterOutputStream that is passed a Inflater
        try (Inflater intf = new Inflater();
             ByteArrayOutputStream baos = new ByteArrayOutputStream();
             InflaterOutputStream stream = new InflaterOutputStream(baos, intf)) {

            stream.finish();
            // not expected to throw any exception
            stream.write(COMPRESSED_CONTENT);
            stream.flush();
            // verify the decompressed content is as expected
            final byte[] decompressed = baos.toByteArray();
            assertArrayEquals(INPUT_CONTENT, decompressed, "unexpected decompressed content");
        }
    }

    private void assertStreamClosedIOE(final IOException ioe) {
        final String msg = ioe.getMessage();
        if (msg == null || !msg.contains("Stream closed")) {
            // propagate the original exception
            fail("unexpected exception message in IOException", ioe);
        }
    }
}
