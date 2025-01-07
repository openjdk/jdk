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
import java.util.zip.Deflater;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * @test
 * @bug 8225763
 * @summary Test that the close() and end() methods on java.util.zip.Deflater
 * @run junit DeflaterClose
 */
public class DeflaterClose {

    private static final String data = "foobarhelloworld!!!!";

    /**
     * Closes the Deflater multiple times and then expects close() and end() each
     * to be called that many times.
     */
    @Test
    public void testCloseMultipleTimes() throws Exception {
        final int numTimes = 3;
        final Deflater simpleDeflater = new Deflater();
        closeMultipleTimesAfterCompressing(numTimes, simpleDeflater);

        final OverrideClose overriddenClose = new OverrideClose();
        closeMultipleTimesAfterCompressing(numTimes, overriddenClose);
        // make sure close was called numTimes
        assertEquals(numTimes, overriddenClose.numTimesCloseCalled, "close() was expected to be" +
                " called " + numTimes + ", but was called " + overriddenClose.numTimesCloseCalled +
                " time(s) on " + overriddenClose.getClass().getName());

        final OverrideEnd overriddenEnd = new OverrideEnd();
        closeMultipleTimesAfterCompressing(numTimes, overriddenEnd);
        // make sure end was called called numTimes
        assertEquals(numTimes, overriddenEnd.numTimesEndCalled, "end() was expected to be called " +
                numTimes + ", but was called " + overriddenEnd.numTimesEndCalled + " time(s) on " +
                overriddenEnd.getClass().getName());

        final OverrideCloseAndEnd overriddenCloseAndEnd = new OverrideCloseAndEnd();
        closeMultipleTimesAfterCompressing(numTimes, overriddenCloseAndEnd);
        // make sure end was called called numTimes
        assertEquals(numTimes, overriddenCloseAndEnd.numTimesEndCalled, "end() was expected to be called " +
                numTimes + ", but was called" + overriddenCloseAndEnd.numTimesEndCalled +
                " time(s) on " + overriddenCloseAndEnd.getClass().getName());
        assertEquals(numTimes, overriddenCloseAndEnd.numTimesCloseCalled, "close() was expected" +
                " to be called " + numTimes + ", but was called " +
                overriddenClose.numTimesCloseCalled + " time(s) on " +
                overriddenCloseAndEnd.getClass().getName());
    }

    /**
     * Closes the Deflater first and then calls end(). Verifies that close() was called
     * just once but end() was called twice (once internally through close() and once
     * explicitly)
     */
    @Test
    public void testCloseThenEnd() throws Exception {
        final Deflater simpleDeflater = new Deflater();
        compressCloseThenEnd(simpleDeflater);

        final OverrideClose overriddenClose = new OverrideClose();
        compressCloseThenEnd(overriddenClose);
        // make sure close was called once
        assertEquals(1, overriddenClose.numTimesCloseCalled, "close() was expected to be called" +
                " once, but was called " + overriddenClose.numTimesCloseCalled +
                " time(s) on " + overriddenClose.getClass().getName());

        final OverrideEnd overriddenEnd = new OverrideEnd();
        compressCloseThenEnd(overriddenEnd);
        // make sure end was called twice (once through close() and then explicitly)
        assertEquals(2, overriddenEnd.numTimesEndCalled, "end() was expected to be called" +
                " twice, but was called " + overriddenEnd.numTimesEndCalled +
                " time(s) on " + overriddenEnd.getClass().getName());

        final OverrideCloseAndEnd overriddenCloseAndEnd = new OverrideCloseAndEnd();
        compressCloseThenEnd(overriddenCloseAndEnd);
        // make sure end was called twice (once through close and once explicitly)
        // and close was called once
        assertEquals(2, overriddenCloseAndEnd.numTimesEndCalled, "end() was expected to" +
                " be called twice, but was called " + overriddenCloseAndEnd.numTimesEndCalled +
                " time(s) on " + overriddenCloseAndEnd.getClass().getName());
        assertEquals(1, overriddenCloseAndEnd.numTimesCloseCalled, "close() was expected to be" +
                " called once, but was called " + overriddenClose.numTimesCloseCalled +
                " time(s) on " + overriddenCloseAndEnd.getClass().getName());
    }

    /**
     * Calls end() on the Deflater first and then calls close(). Verifies that close() was called
     * just once and end() twice.
     */
    @Test
    public void testEndThenClose() throws Exception {
        final Deflater simpleDeflater = new Deflater();
        compressEndThenClose(simpleDeflater);

        final OverrideClose overriddenClose = new OverrideClose();
        compressEndThenClose(overriddenClose);
        // make sure close was called once
        assertEquals(1, overriddenClose.numTimesCloseCalled, "close() was expected to be called" +
                " once, but was called " + overriddenClose.numTimesCloseCalled +
                " time(s) on " + overriddenClose.getClass().getName());

        final OverrideEnd overriddenEnd = new OverrideEnd();
        compressEndThenClose(overriddenEnd);
        // make sure end was called twice (once through the explicit end call and
        // once through close())
        assertEquals(2, overriddenEnd.numTimesEndCalled, "end() was expected to be called twice," +
                " but was called " + overriddenEnd.numTimesEndCalled +
                " time(s) on " + overriddenEnd.getClass().getName());

        final OverrideCloseAndEnd overriddenCloseAndEnd = new OverrideCloseAndEnd();
        compressEndThenClose(overriddenCloseAndEnd);
        // make sure end was called twice (once through the explicit end call and
        // once through close())
        assertEquals(2, overriddenCloseAndEnd.numTimesEndCalled, "end() was expected to be called" +
                " twice, but was called " + overriddenCloseAndEnd.numTimesEndCalled +
                " time(s) on " + overriddenCloseAndEnd.getClass().getName());
        assertEquals(1, overriddenCloseAndEnd.numTimesCloseCalled, "close() was expected to be " +
                "called once, but was called " + overriddenClose.numTimesCloseCalled +
                " time(s) on " + overriddenCloseAndEnd.getClass().getName());
    }

    private void closeMultipleTimesAfterCompressing(final int numTimes, final Deflater deflater) {
        compress(deflater);
        // call close() multiple times
        for (int i = 0; i < numTimes; i++) {
            deflater.close();
        }
    }

    private void compressCloseThenEnd(final Deflater deflater) {
        // compress the data then close() and then end()
        try (final Deflater compressor = deflater) {
            compress(compressor);
        }
        deflater.end();
    }

    private void compressEndThenClose(final Deflater deflater) {
        // compress the data then end() and then close()
        try (final Deflater compressor = deflater) {
            compress(compressor);
            // end() it first before it's (auto)closed by the try-with-resources
            compressor.end();
        }
    }

    private static byte[] compress(final Deflater deflater) {
        deflater.setInput(data.getBytes(StandardCharsets.UTF_8));
        deflater.finish();
        final ByteArrayOutputStream compressedBaos = new ByteArrayOutputStream();
        while (!deflater.finished()) {
            final byte[] tmpBuffer = new byte[100];
            final int numCompressed = deflater.deflate(tmpBuffer);
            compressedBaos.write(tmpBuffer, 0, numCompressed);
        }
        return compressedBaos.toByteArray();
    }

    private static final class OverrideEnd extends Deflater {
        private int numTimesEndCalled = 0;

        @Override
        public void end() {
            this.numTimesEndCalled++;
            super.end();
        }
    }

    private static final class OverrideClose extends Deflater {
        private int numTimesCloseCalled = 0;

        @Override
        public void close() {
            this.numTimesCloseCalled++;
            super.close();
        }
    }

    private static final class OverrideCloseAndEnd extends Deflater {
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