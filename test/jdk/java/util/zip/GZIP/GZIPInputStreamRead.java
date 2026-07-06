/*
 * Copyright (c) 2010, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import jdk.test.lib.RandomFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 4691425
 * @summary Test the read and write of GZIPInput/OutputStream, including
 *          concatenated .gz inputstream
 * @key randomness
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @run junit ${test.main.class}
 */
class GZIPInputStreamRead {

    private static final Random random = RandomFactory.getRandom();

    /*
     * Generates GZIP content containing multiple members and then verifies
     * that using GZIPInputStream to decompress that content generates the correct
     * expected decompressed data.
     */
    @Test
    void testMultipleMembers() throws Exception {
        final int numMembers = random.nextInt(10) + 1;
        final ByteArrayOutputStream rawUncompressedBaos = new ByteArrayOutputStream();
        final ByteArrayOutputStream gzipCompressedBaos = new ByteArrayOutputStream();
        // generate GZIP content with multiple members
        for (int j = 0; j < numMembers; j++) {
            byte[] src = new byte[random.nextInt(8192) + 1];
            random.nextBytes(src);
            rawUncompressedBaos.write(src);

            try (GZIPOutputStream gzos = new GZIPOutputStream(gzipCompressedBaos)) {
                gzos.write(src);
            }
        }
        final byte[] uncompressedRawBytes = rawUncompressedBaos.toByteArray();
        final byte[] gzipCompressedBytes = gzipCompressedBaos.toByteArray();
        // decompress using GZIPInputStream and verify the decompressed output.
        // use different input buffer size for GZIPInputStream when running the verification.
        for (int j = 0; j < 10; j++) {
            final int readBufSZ = random.nextInt(2048) + 1;
            verifyDecompressed(uncompressedRawBytes,
                    gzipCompressedBytes,
                    readBufSZ,
                    512);    // the default input buffer size
            verifyDecompressed(uncompressedRawBytes,
                    gzipCompressedBytes,
                    readBufSZ,
                    random.nextInt(4096) + 1);
        }
    }

    /*
     * Generates GZIP content containing one member followed by some arbitrary non-member data.
     * The test then verifies that using GZIPInputStream to decompress that content generates
     * the correct expected decompressed data.
     */
    @Test
    void testNonMemberAfterTrailer() throws Exception {
        final byte[] rawUncompressed = new byte[random.nextInt(1234)];
        random.nextBytes(rawUncompressed);
        final ByteArrayOutputStream gzipCompressedPlusExtra = new ByteArrayOutputStream();
        // generate a valid GZIP member
        try (GZIPOutputStream gzos = new GZIPOutputStream(gzipCompressedPlusExtra)) {
            gzos.write(rawUncompressed); // GZIP compress
        }
        final int numCompressedBytes = gzipCompressedPlusExtra.size();
        // past the GZIP trailer, write some additional bytes that doesn't represent a GZIP member
        final byte[] notGZIPMagic = ByteBuffer.allocate(Integer.BYTES).
                putInt(GZIPInputStream.GZIP_MAGIC + 42)
                .array();
        gzipCompressedPlusExtra.write(notGZIPMagic);
        assertEquals(numCompressedBytes + notGZIPMagic.length, gzipCompressedPlusExtra.size(),
                "unexpected number of compressed + extra bytes");
        // now use GZIPInputStream to decompress the compressed plus extra bytes and verify
        // that the extra bytes don't cause unexpected decompressed output
        final ByteArrayOutputStream decompressedBaos = new ByteArrayOutputStream();
        int n = 0;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(gzipCompressedPlusExtra.toByteArray());
             GZIPInputStream gzipIn = new GZIPInputStream(bais)) {

            final byte[] tmpBuf = new byte[42];
            while ((n = gzipIn.read(tmpBuf)) != -1) {
                decompressedBaos.write(tmpBuf, 0, n);
            }
            final byte[] decompressed = decompressedBaos.toByteArray();
            // verify the decompressed content
            assertEquals(rawUncompressed.length, decompressed.length,
                    "unexpected number of decompressed bytes");
            assertArrayEquals(rawUncompressed, decompressed, "unexpected decompressed data");
            // make sure additional calls to read still return EOF
            assertEquals(-1, gzipIn.read(), "unexpected return from read(), expected EOF");
            assertEquals(-1, gzipIn.read(new byte[10]), "unexpected return from read(), expected EOF");
        }
    }

    /*
     * Verifies that the InputStream.available() method is invoked on the underlying InputStream
     * to determine presence of additional GZIP members in the stream.
     */
    @Test
    void testInputStreamAvailableCalled() throws Exception {
        final byte[] rawUncompressedMember1 = new byte[random.nextInt(111)];
        random.nextBytes(rawUncompressedMember1);
        System.err.println("GZIP member 1 has " + rawUncompressedMember1.length + " bytes");

        final byte[] rawUncompressedMember2 = new byte[random.nextInt(33)];
        random.nextBytes(rawUncompressedMember2);
        System.err.println("GZIP member 2 has " + rawUncompressedMember2.length + " bytes");

        final ByteArrayOutputStream twoMemberGzipCompressedBaos = new ByteArrayOutputStream();
        // generate GZIP format data with 2 valid GZIP members
        try (GZIPOutputStream gzos = new GZIPOutputStream(twoMemberGzipCompressedBaos)) {
            gzos.write(rawUncompressedMember1); // GZIP compress
            gzos.write(rawUncompressedMember2); // GZIP compress
        }
        final byte[] gzipCompressed = twoMemberGzipCompressedBaos.toByteArray();
        final AtomicBoolean availableInvoked = new AtomicBoolean();
        // an InputStream which tracks the calls to available()
        final ByteArrayInputStream underlying = new ByteArrayInputStream(gzipCompressed) {
            @Override
            public int available() {
                availableInvoked.set(true);
                return super.available();
            }
        };
        // now use GZIPInputStream to decompress the compressed data and expect the decompressed
        // data to be correct and also expect the InputStream.available() to have been invoked
        final ByteArrayOutputStream decompressedBaos = new ByteArrayOutputStream();
        int n = 0;
        try (GZIPInputStream gzipIn = new GZIPInputStream(underlying)) {

            final byte[] tmpBuf = new byte[1024];
            while ((n = gzipIn.read(tmpBuf)) != -1) {
                decompressedBaos.write(tmpBuf, 0, n);
            }
            assertTrue(availableInvoked.get(), "InputStream.available() wasn't invoked");
            final byte[] decompressed = decompressedBaos.toByteArray();
            // verify the decompressed content, it should represent the two GZIP members
            assertEquals(rawUncompressedMember1.length + rawUncompressedMember2.length,
                    decompressed.length, "unexpected number of decompressed bytes");

            assertArrayEquals(rawUncompressedMember1,
                    Arrays.copyOfRange(decompressed, 0, rawUncompressedMember1.length),
                    "unexpected decompressed data of first member");

            assertArrayEquals(rawUncompressedMember2,
                    Arrays.copyOfRange(decompressed, rawUncompressedMember1.length, decompressed.length),
                    "unexpected decompressed data of second member");

            // make sure additional calls to read still return EOF
            assertEquals(-1, gzipIn.read(), "unexpected return from read(), expected EOF");
            assertEquals(-1, gzipIn.read(new byte[42]), "unexpected return from read(), expected EOF");
        }
    }

    // verify that decompressing the gzipCompressed data using GZIPInputStream
    // generates the expected output
    private static void verifyDecompressed(final byte[] rawUncompressed,
                                           final byte[] gzipCompressed,
                                           final int readBufSize, final int gzisBufSize)
            throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(gzipCompressed);
             GZIPInputStream gzis = new GZIPInputStream(bais, gzisBufSize)) {

            byte[] result = new byte[rawUncompressed.length + 10];
            byte[] buf = new byte[readBufSize];
            int n = 0;
            int numDecompressed = 0;
            while ((n = gzis.read(buf, 0, buf.length)) != -1) {
                System.arraycopy(buf, 0, result, numDecompressed, n);
                numDecompressed += n;
                // no range check, if overflow, let it fail
            }
            assertEquals(rawUncompressed.length, numDecompressed,
                    "unexpected number of decompressed bytes");
            assertEquals(0, gzis.available(),
                    "unexpected additional bytes available in the GZIPInputStream");
            assertArrayEquals(rawUncompressed, Arrays.copyOf(result, numDecompressed),
                    "unexpected decompressed data");
        }
    }
}
