/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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


import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;

/**
 * @test
 * @bug 8225763
 * @summary Test that the close() and end() methods on java.util.zip.Deflater
 */
public class DeflaterClose {

    private static final String data = "foobarhelloworld!!!!";

    private static ByteBuffer generateDeflatedData(final Deflater deflater) {
        final byte[] deflatedData = new byte[100];
        deflater.setInput(data.getBytes(StandardCharsets.UTF_8));
        deflater.finish();
        final int numCompressed = deflater.deflate(deflatedData);
        if (numCompressed == 0) {
            throw new RuntimeException("Deflater, unexpectedly, expects more input");
        }
        return ByteBuffer.wrap(deflatedData, 0, numCompressed);
    }

    public static void main(final String[] args) throws Exception {
        final DeflaterClose self = new DeflaterClose();
        self.testCloseOnce();
        self.testCloseMultipleTimes();
        self.testCloseThenEnd();
        self.testEndThenClose();
    }

    /**
     * Closes Deflater just once and then expects that the close() was called once and so was end()
     *
     * @throws Exception
     */
    private void testCloseOnce() throws Exception {
        final Deflater simpleDeflater = new Deflater();
        testCloseOnce(simpleDeflater);

        final OverrideClose overridenClose = new OverrideClose();
        testCloseOnce(overridenClose);
        // make sure close was called once
        if (overridenClose.numTimesCloseCalled != 1) {
            throw new Exception("close() was expected to be called once, but was called "
                    + overridenClose.numTimesCloseCalled + " time(s) on " + overridenClose.getClass().getName());
        }

        final OverrideEnd overridenEnd = new OverrideEnd();
        testCloseOnce(overridenEnd);
        // make sure end was called once
        if (overridenEnd.numTimesEndCalled != 1) {
            throw new Exception("end() was expected to be called once, but was called "
                    + overridenEnd.numTimesEndCalled + " time(s) on " + overridenEnd.getClass().getName());
        }

        final OverrideCloseAndEnd overridenCloseAndEnd = new OverrideCloseAndEnd();
        testCloseOnce(overridenCloseAndEnd);
        // make sure end and close was called once
        if (overridenCloseAndEnd.numTimesEndCalled != 1) {
            throw new Exception("end() was expected to be called once, but was called "
                    + overridenCloseAndEnd.numTimesEndCalled + " time(s) on " + overridenCloseAndEnd.getClass().getName());
        }
        if (overridenCloseAndEnd.numTimesCloseCalled != 1) {
            throw new Exception("close() was expected to be called once, but was called "
                    + overridenClose.numTimesCloseCalled + " time(s) on " + overridenCloseAndEnd.getClass().getName());
        }
    }

    /**
     * Closes the Deflater more than once and then expects close() to be called that many times
     * but end() just once
     *
     * @throws Exception
     */
    private void testCloseMultipleTimes() throws Exception {
        final int numTimes = 3;
        final Deflater simpleDeflater = new Deflater();
        testCloseMultipleTimes(numTimes, simpleDeflater);

        final OverrideClose overridenClose = new OverrideClose();
        testCloseMultipleTimes(numTimes, overridenClose);
        // make sure close was called numTimes
        if (overridenClose.numTimesCloseCalled != numTimes) {
            throw new Exception("close() was expected to be called " + numTimes + ", but was called "
                    + overridenClose.numTimesCloseCalled + " time(s) on " + overridenClose.getClass().getName());
        }

        final OverrideEnd overridenEnd = new OverrideEnd();
        testCloseMultipleTimes(numTimes, overridenEnd);
        // make sure end was called *only once*
        if (overridenEnd.numTimesEndCalled != 1) {
            throw new Exception("end() was expected to be called once, but was called "
                    + overridenEnd.numTimesEndCalled + " time(s) on " + overridenEnd.getClass().getName());
        }

        final OverrideCloseAndEnd overridenCloseAndEnd = new OverrideCloseAndEnd();
        testCloseMultipleTimes(numTimes, overridenCloseAndEnd);
        // make sure end was called only once but close was called numTimes
        if (overridenCloseAndEnd.numTimesEndCalled != 1) {
            throw new Exception("end() was expected to be called once, but was called "
                    + overridenCloseAndEnd.numTimesEndCalled + " time(s) on " + overridenCloseAndEnd.getClass().getName());
        }
        if (overridenCloseAndEnd.numTimesCloseCalled != numTimes) {
            throw new Exception("close() was expected to be called " + numTimes + ", but was called "
                    + overridenClose.numTimesCloseCalled + " time(s) on " + overridenCloseAndEnd.getClass().getName());
        }
    }

    /**
     * Closes the Deflater first and then calls end(). Verifies that close() was called
     * just once but end() was called twice (once internally through close() and once
     * explicitly)
     *
     * @throws Exception
     */
    private void testCloseThenEnd() throws Exception {
        final Deflater simpleDeflater = new Deflater();
        testCloseThenEnd(simpleDeflater);

        final OverrideClose overridenClose = new OverrideClose();
        testCloseThenEnd(overridenClose);
        // make sure close was called once
        if (overridenClose.numTimesCloseCalled != 1) {
            throw new Exception("close() was expected to be called once, but was called "
                    + overridenClose.numTimesCloseCalled + " time(s) on " + overridenClose.getClass().getName());
        }

        final OverrideEnd overridenEnd = new OverrideEnd();
        testCloseThenEnd(overridenEnd);
        // make sure end was called twice (once through close() and then explicitly)
        if (overridenEnd.numTimesEndCalled != 2) {
            throw new Exception("end() was expected to be called twice, but was called "
                    + overridenEnd.numTimesEndCalled + " time(s) on " + overridenEnd.getClass().getName());
        }

        final OverrideCloseAndEnd overridenCloseAndEnd = new OverrideCloseAndEnd();
        testCloseThenEnd(overridenCloseAndEnd);
        // make sure end was called twice (once through close and once explicitly) and close was called once
        if (overridenCloseAndEnd.numTimesEndCalled != 2) {
            throw new Exception("end() was expected to be called twice, but was called "
                    + overridenCloseAndEnd.numTimesEndCalled + " time(s) on " + overridenCloseAndEnd.getClass().getName());
        }
        if (overridenCloseAndEnd.numTimesCloseCalled != 1) {
            throw new Exception("close() was expected to be called once, but was called "
                    + overridenClose.numTimesCloseCalled + " time(s) on " + overridenCloseAndEnd.getClass().getName());
        }
    }

    /**
     * Calls end() on the Deflater first and then calls close(). Verifies that close() was called
     * just once and end() too was called just once. This check ensures that the latter call to close()
     * doesn't end up calling end() again.
     *
     * @throws Exception
     */
    private void testEndThenClose() throws Exception {
        final Deflater simpleDeflater = new Deflater();
        testEndThenClose(simpleDeflater);

        final OverrideClose overridenClose = new OverrideClose();
        testEndThenClose(overridenClose);
        // make sure close was called once
        if (overridenClose.numTimesCloseCalled != 1) {
            throw new Exception("close() was expected to be called once, but was called "
                    + overridenClose.numTimesCloseCalled + " time(s) on " + overridenClose.getClass().getName());
        }

        final OverrideEnd overridenEnd = new OverrideEnd();
        testEndThenClose(overridenEnd);
        // make sure end was called *only once* (through the explicit end call) and close() didn't call it again
        // internally
        if (overridenEnd.numTimesEndCalled != 1) {
            throw new Exception("end() was expected to be called once, but was called "
                    + overridenEnd.numTimesEndCalled + " time(s) on " + overridenEnd.getClass().getName());
        }

        final OverrideCloseAndEnd overridenCloseAndEnd = new OverrideCloseAndEnd();
        testEndThenClose(overridenCloseAndEnd);
        // make sure end was called *only once* (through the explicit end call) and close() didn't call it again
        // internally
        if (overridenCloseAndEnd.numTimesEndCalled != 1) {
            throw new Exception("end() was expected to be called once, but was called "
                    + overridenCloseAndEnd.numTimesEndCalled + " time(s) on " + overridenCloseAndEnd.getClass().getName());
        }
        if (overridenCloseAndEnd.numTimesCloseCalled != 1) {
            throw new Exception("close() was expected to be called once, but was called "
                    + overridenClose.numTimesCloseCalled + " time(s) on " + overridenCloseAndEnd.getClass().getName());
        }
    }


    private void testCloseOnce(final Deflater deflater) {
        // use the deflater to compress the data
        // and then let it close()
        try (final Deflater compressor = deflater) {
            generateDeflatedData(compressor);
        }
    }

    private void testCloseMultipleTimes(final int numTimes, final Deflater deflater) {
        generateDeflatedData(deflater);
        // call close()
        for (int i = 0; i < numTimes; i++) {
            deflater.close();
        }
    }

    private void testCloseThenEnd(final Deflater deflater) {
        // deflate the data, let it close() and then end()
        try (final Deflater compressor = deflater) {
            generateDeflatedData(compressor);
        }
        deflater.end();
    }

    private void testEndThenClose(final Deflater deflater) {
        // inflate the data, let it end() and then close()
        try (final Deflater compressor = deflater) {
            // end() it first, before it's (auto)closed by the try-with-resources
            compressor.end();
        }
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