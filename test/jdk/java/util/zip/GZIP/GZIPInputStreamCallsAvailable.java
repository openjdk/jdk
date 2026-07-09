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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Random;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import jdk.test.lib.RandomFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/*
 * @test
 * @summary Verify the behaviour of GZIPInputStream when dealing with InputStream.available()
 *          on the underlying stream and the jdk.util.gzip.tryReadAheadAfterTrailer
 *          system property being enabled/disabled
 * @key randomness
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @run junit/othervm -Djdk.util.gzip.tryReadAheadAfterTrailer=true GZIPInputStreamCallsAvailable
 * @run junit/othervm -Djdk.util.gzip.tryReadAheadAfterTrailer=false GZIPInputStreamCallsAvailable
 * @run junit GZIPInputStreamCallsAvailable
 */
class GZIPInputStreamCallsAvailable {

    private static final boolean AVAILABLE_METHOD_INVOCATION_SKIPPED =
            Boolean.getBoolean("jdk.util.gzip.tryReadAheadAfterTrailer");
    private static final Random random = RandomFactory.getRandom();

    private record TestData(byte[] uncompressed, byte[] compressed) {
    }

    static List<Integer> numGZIPMembers() {
        return List.of(1,
                33,
                random.nextInt(2, 1001) // a reasonably large number of members
        );
    }

    /*
     * Verify that GZIPInputStream reads and returns the correct decompressed data when:
     *  - the underlying InputStream.available() returns an accurate value
     *  - and when the GZIPInputStream isn't expected to call the underlying InputStream.available()
     *    method
     */
    @ParameterizedTest
    @MethodSource("numGZIPMembers")
    void testMultipleMembers(final int numMembers) throws IOException {
        final TestData testData = createGZIPStream(numMembers);
        final InputStream underlyingStream = AVAILABLE_METHOD_INVOCATION_SKIPPED
                // stream whose available() method isn't expected to be invoked
                ? new AlwaysThrowFromAvailable(new ByteArrayInputStream(testData.compressed))
                // stream whose available() will be invoked and returns an accurate value
                : new ByteArrayInputStream(testData.compressed);
        try (GZIPInputStream gzip = new GZIPInputStream(underlyingStream)) {
            final byte[] decompressed = gzip.readAllBytes();
            assertArrayEquals(testData.uncompressed, decompressed, "unexpected decompressed data");
        }
    }

    /*
     * Creates and returns bytes representing a GZIP stream consisting of the given number of
     * members.
     */
    private static TestData createGZIPStream(final int numMembers) throws IOException {
        final String content = "foo bar hello world from " + GZIPInputStreamCallsAvailable.class;
        final ByteArrayOutputStream uncompressed = new ByteArrayOutputStream();
        final ByteArrayOutputStream gzipped = new ByteArrayOutputStream();
        for (int i = 1; i <= numMembers; i++) {
            final ByteArrayOutputStream member = new ByteArrayOutputStream();
            try (final OutputStream gzip = new GZIPOutputStream(member)) {
                final byte[] memberRawBytes = ("member-" + i + " " + content).getBytes(US_ASCII);
                gzip.write(memberRawBytes);
                // keep track of the uncompressed content too so that it can be compared for
                // equality with the decompressed content
                uncompressed.write(memberRawBytes);
            }
            // write out the GZIP member to the stream which accumulates all the members
            gzipped.write(member.toByteArray());
        }
        return new TestData(uncompressed.toByteArray(), gzipped.toByteArray());
    }

    private static class AlwaysThrowFromAvailable extends FilterInputStream {
        public AlwaysThrowFromAvailable(InputStream in) {
            super(in);
        }

        @Override
        public int available() {
            throw new AssertionError(this.getClass().getName()
                    + ".available() wasn't expected to be invoked");
        }
    }
}
