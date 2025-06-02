/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.charset.StandardCharsets;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import org.junit.jupiter.api.Test;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * @test
 * @bug 8225763
 * @summary Test that the close() and end() methods on java.util.zip.Inflater
 * @run junit InflaterClose
 */
public class InflaterClose {

    private static final String originalStr = "foobarhelloworld!!!!";
    private static final byte[] originalBytes = originalStr.getBytes(US_ASCII);
    private static final byte[] compressedData = compress();

    /**
     * Closes the Inflater multiple times and then expects close() and end() to be called that
     * many times.
     */
    @Test
    public void testCloseMultipleTimes() throws Exception {
        final int numTimes = 3;
        final Inflater simpleInflater = new Inflater();
        final String inflatedData = closeMultipleTimesAfterInflating(numTimes, simpleInflater);
        assertValidInflatedData(inflatedData, simpleInflater.getClass());

        final OverrideClose overriddenClose = new OverrideClose();
        final String ocInflatedData = closeMultipleTimesAfterInflating(numTimes, overriddenClose);
        assertValidInflatedData(ocInflatedData, overriddenClose.getClass());
        // make sure close was called numTimes
        assertEquals(numTimes, overriddenClose.numTimesCloseCalled, "close() was expected to be" +
                " called " + numTimes + ", but was called " + overriddenClose.numTimesCloseCalled +
                " time(s) on " + overriddenClose.getClass().getName());

        final OverrideEnd overriddenEnd = new OverrideEnd();
        final String oeInflatedData = closeMultipleTimesAfterInflating(numTimes, overriddenEnd);
        assertValidInflatedData(oeInflatedData, overriddenEnd.getClass());
        // make sure end was called called numTimes
        assertEquals(numTimes, overriddenEnd.numTimesEndCalled, "end() was expected to be called " +
                numTimes + ", but was called " + overriddenEnd.numTimesEndCalled + " time(s) on " +
                overriddenEnd.getClass().getName());

        final OverrideCloseAndEnd overriddenCloseAndEnd = new OverrideCloseAndEnd();
        final String oceInflatedData = closeMultipleTimesAfterInflating(numTimes,
                overriddenCloseAndEnd);
        assertValidInflatedData(oceInflatedData, overriddenCloseAndEnd.getClass());
        // make sure end was called called numTimes
        assertEquals(numTimes, overriddenCloseAndEnd.numTimesEndCalled, "end() was expected" +
                " to be called " + numTimes + ", but was called " +
                overriddenCloseAndEnd.numTimesEndCalled +
                " time(s) on " + overriddenCloseAndEnd.getClass().getName());
        assertEquals(numTimes, overriddenCloseAndEnd.numTimesCloseCalled, "close() was expected" +
                " to be called " + numTimes + ", but was called " +
                overriddenCloseAndEnd.numTimesCloseCalled + " time(s) on " +
                overriddenCloseAndEnd.getClass().getName());
    }

    /**
     * Closes the Inflater first and then calls end(). Verifies that close() was called
     * just once but end() was called twice (once internally through close() and once
     * explicitly)
     */
    @Test
    public void testCloseThenEnd() throws Exception {
        final Inflater simpleInflater = new Inflater();
        final String inflatedData = inflateCloseThenEnd(simpleInflater);
        assertValidInflatedData(inflatedData, simpleInflater.getClass());

        final OverrideClose overriddenClose = new OverrideClose();
        final String ocInflatedData = inflateCloseThenEnd(overriddenClose);
        assertValidInflatedData(ocInflatedData, overriddenClose.getClass());
        // make sure close was called once
        assertEquals(1, overriddenClose.numTimesCloseCalled, "close() was expected to be called" +
                " once, but was called " + overriddenClose.numTimesCloseCalled + " time(s) on "
                + overriddenClose.getClass().getName());

        final OverrideEnd overriddenEnd = new OverrideEnd();
        final String oeInflatedData = inflateCloseThenEnd(overriddenEnd);
        assertValidInflatedData(oeInflatedData, overriddenEnd.getClass());
        // make sure end was called twice (once through close() and then explicitly)
        assertEquals(2, overriddenEnd.numTimesEndCalled, "end() was expected to be called twice," +
                " but was called " + overriddenEnd.numTimesEndCalled + " time(s) on "
                + overriddenEnd.getClass().getName());

        final OverrideCloseAndEnd overriddenCloseAndEnd = new OverrideCloseAndEnd();
        final String oceInflatedData = inflateCloseThenEnd(overriddenCloseAndEnd);
        assertValidInflatedData(oceInflatedData, overriddenCloseAndEnd.getClass());
        // make sure end was called twice (once through close and once explicitly)
        // and close was called once
        assertEquals(2, overriddenCloseAndEnd.numTimesEndCalled, "end() was expected to be called" +
                " twice, but was called " + overriddenCloseAndEnd.numTimesEndCalled
                + " time(s) on " + overriddenCloseAndEnd.getClass().getName());
        assertEquals(1, overriddenCloseAndEnd.numTimesCloseCalled, "close() was expected to be" +
                " called once, but was called " + overriddenClose.numTimesCloseCalled
                + " time(s) on " + overriddenCloseAndEnd.getClass().getName());
    }

    /**
     * Calls end() on the Inflater first and then calls close(). Verifies that close() was called
     * just once and end() twice.
     */
    @Test
    public void testEndThenClose() throws Exception {
        final Inflater simpleInflater = new Inflater();
        final String inflatedData = inflateThenEndThenClose(simpleInflater);
        assertValidInflatedData(inflatedData, simpleInflater.getClass());

        final OverrideClose overriddenClose = new OverrideClose();
        final String ocInflatedData = inflateThenEndThenClose(overriddenClose);
        assertValidInflatedData(ocInflatedData, overriddenClose.getClass());
        // make sure close was called once
        assertEquals(1, overriddenClose.numTimesCloseCalled, "close() was expected to be" +
                " called once, but was called " + overriddenClose.numTimesCloseCalled +
                " time(s) on " + overriddenClose.getClass().getName());

        final OverrideEnd overriddenEnd = new OverrideEnd();
        final String oeInflatedData = inflateThenEndThenClose(overriddenEnd);
        assertValidInflatedData(oeInflatedData, overriddenEnd.getClass());
        // make sure end was called twice (once through the explicit end call and
        // once through close())
        assertEquals(2, overriddenEnd.numTimesEndCalled, "end() was expected to be called twice," +
                " but was called " + overriddenEnd.numTimesEndCalled + " time(s) on "
                + overriddenEnd.getClass().getName());

        final OverrideCloseAndEnd overriddenCloseAndEnd = new OverrideCloseAndEnd();
        final String oceInflatedData = inflateThenEndThenClose(overriddenCloseAndEnd);
        assertValidInflatedData(oceInflatedData, overriddenCloseAndEnd.getClass());
        // make sure end was called twice (once through the explicit end call and
        // once through close())
        assertEquals(2, overriddenCloseAndEnd.numTimesEndCalled, "end() was expected to be called" +
                " twice, but was called " + overriddenCloseAndEnd.numTimesEndCalled +
                " time(s) on " + overriddenCloseAndEnd.getClass().getName());
        assertEquals(1, overriddenCloseAndEnd.numTimesCloseCalled, "close() was expected to be" +
                " called once, but was called " + overriddenClose.numTimesCloseCalled +
                " time(s) on " + overriddenCloseAndEnd.getClass().getName());
    }


    private String closeMultipleTimesAfterInflating(final int numTimes, final Inflater inflater)
            throws DataFormatException {
        // inflate() then call close() multiple times
        final byte[] inflatedData = inflate(inflater, compressedData);
        // call close()
        for (int i = 0; i < numTimes; i++) {
            inflater.close();
        }
        return new String(inflatedData, StandardCharsets.UTF_8);
    }

    private String inflateCloseThenEnd(final Inflater inflater) throws Exception {
        final byte[] inflatedData;
        // inflate then close() and then end()
        try (final Inflater inflt = inflater) {
            inflatedData = inflate(inflt, compressedData);
        }
        // end() the already closed inflater
        inflater.end();
        return new String(inflatedData, StandardCharsets.UTF_8);
    }

    private String inflateThenEndThenClose(final Inflater inflater) throws Exception {
        final byte[] inflatedData;
        // inflate then end() and then close()
        try (final Inflater inflt = inflater) {
            inflatedData = inflate(inflt, compressedData);
            // end() it first before it's (auto)closed by the try-with-resources
            inflt.end();
        }
        return new String(inflatedData, StandardCharsets.UTF_8);
    }

    private static byte[] inflate(final Inflater inflater, final byte[] compressedData)
            throws DataFormatException {
        final ByteArrayOutputStream inflatedData = new ByteArrayOutputStream();
        inflater.setInput(compressedData);
        while (!inflater.finished()) {
            byte[] tmpBuffer = new byte[100];
            final int numDecompressed = inflater.inflate(tmpBuffer);
            inflatedData.write(tmpBuffer, 0, numDecompressed);
        }
        return inflatedData.toByteArray();
    }

    private static byte[] compress() {
        final ByteArrayOutputStream compressedBaos = new ByteArrayOutputStream();
        try (final Deflater deflater = new Deflater()) {
            deflater.setInput(originalBytes);
            deflater.finish();
            while (!deflater.finished()) {
                final byte[] tmpBuffer = new byte[100];
                final int numCompressed = deflater.deflate(tmpBuffer);
                compressedBaos.write(tmpBuffer, 0, numCompressed);
            }
        }
        return compressedBaos.toByteArray();
    }

    private static void assertValidInflatedData(final String inflatedData,
                                                final Class<?> inflaterType) {
        assertEquals(originalStr, inflatedData, "Unexpected inflated data " + inflatedData
                + " generated by " + inflaterType.getName() + ", expected " + originalStr);
    }

    private static final class OverrideEnd extends Inflater {
        private int numTimesEndCalled = 0;

        @Override
        public void end() {
            this.numTimesEndCalled++;
            super.end();
        }
    }

    private static final class OverrideClose extends Inflater {
        private int numTimesCloseCalled = 0;

        @Override
        public void close() {
            this.numTimesCloseCalled++;
            super.close();
        }
    }

    private static final class OverrideCloseAndEnd extends Inflater {
        private int numTimesEndCalled = 0;
        private int numTimesCloseCalled = 0;

        @Override
        public void end() {
            this.numTimesEndCalled++;
            super.end();
        }

        @Override
        public void close() {
            this.numTimesCloseCalled++;
            super.close();
        }
    }
}