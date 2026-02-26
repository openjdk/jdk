/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.net.http.http3.ConnectionSettings;
import jdk.internal.net.http.http3.Http3Error;
import jdk.internal.net.http.http3.frames.SettingsFrame;
import jdk.internal.net.http.qpack.TableEntry;
import jdk.internal.net.http.qpack.writers.EncoderInstructionsWriter;
import jdk.internal.net.http.qpack.writers.HeaderFrameWriter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/*
 * @test
 * @modules java.base/jdk.internal.net.quic
 *          java.net.http/jdk.internal.net.http.hpack
 *          java.net.http/jdk.internal.net.http.qpack:+open
 *          java.net.http/jdk.internal.net.http.qpack.readers
 *          java.net.http/jdk.internal.net.http.qpack.writers
 *          java.net.http/jdk.internal.net.http.common
 *          java.net.http/jdk.internal.net.http.quic
 *          java.net.http/jdk.internal.net.http.quic.streams
 *          java.net.http/jdk.internal.net.http.http3.streams
 *          java.net.http/jdk.internal.net.http.http3.frames
 *          java.net.http/jdk.internal.net.http.http3
 * @build EncoderDecoderConnector
 * @run junit/othervm -Djdk.internal.httpclient.qpack.log.level=EXTRA
 *                     EncoderDecoderConnectionTest
 */
public class EncoderDecoderConnectionTest {

    @Test
    public void capacityUpdateTest() {
        AtomicReference<Throwable> error = new AtomicReference<>();
        EncoderDecoderConnector encoderDecoderConnector = new EncoderDecoderConnector();
        TestErrorHandler encoderErrorHandler = new TestErrorHandler();
        TestErrorHandler decoderErrorHandler = new TestErrorHandler();
        var conn = encoderDecoderConnector.newEncoderDecoderPair(entry -> true,
                encoderErrorHandler::qpackErrorHandler, decoderErrorHandler::qpackErrorHandler, error::set);

        var encoder = conn.encoder();
        var decoder = conn.decoder();

        // Set encoder and decoder maximum dynamic table capacity
        SettingsFrame settingsFrame = SettingsFrame.defaultRFCSettings();
        settingsFrame.setParameter(SettingsFrame.SETTINGS_QPACK_MAX_TABLE_CAPACITY, 2048L);
        ConnectionSettings settings = ConnectionSettings.createFrom(settingsFrame);
        decoder.configure(settings);
        encoder.configure(settings);

        // Encoder - update DT capacity
        final long capacityToSet = 1024L;
        encoder.setTableCapacity(capacityToSet);

        // Check that no errors observed
        Assertions.assertNull(encoderErrorHandler.error.get());
        Assertions.assertNull(encoderErrorHandler.http3Error.get());
        Assertions.assertNull(decoderErrorHandler.error.get());
        Assertions.assertNull(decoderErrorHandler.http3Error.get());

        // Check that encoder's table capacity is updated
        Assertions.assertEquals(capacityToSet, conn.encoderTable().capacity());
        // Since encoder/decoder streams are cross-wired we expect see dynamic
        // table capacity updated for the decoder too
        Assertions.assertEquals(conn.encoderTable().capacity(),
                                conn.decoderTable().capacity());
    }

    @Test
    public void entryInsertionTest() {
        AtomicReference<Throwable> error = new AtomicReference<>();
        EncoderDecoderConnector encoderDecoderConnector = new EncoderDecoderConnector();
        TestErrorHandler encoderErrorHandler = new TestErrorHandler();
        TestErrorHandler decoderErrorHandler = new TestErrorHandler();

        var conn = encoderDecoderConnector.newEncoderDecoderPair(entry -> true,
                encoderErrorHandler::qpackErrorHandler, decoderErrorHandler::qpackErrorHandler,
                error::set);
        var encoder = conn.encoder();
        var decoder = conn.decoder();

        // Set encoder and decoder maximum dynamic table capacity
        SettingsFrame settingsFrame = SettingsFrame.defaultRFCSettings();
        settingsFrame.setParameter(SettingsFrame.SETTINGS_QPACK_MAX_TABLE_CAPACITY, 2048L);
        ConnectionSettings settings = ConnectionSettings.createFrom(settingsFrame);
        decoder.configure(settings);
        encoder.configure(settings);

        // Update encoder and decoder DTs capacity - note that "Set Dynamic Table Capacity"
        // is issued by the encoder that updates capacity on the decoder side
        encoder.setTableCapacity(1024L);

        // Create table entry for insertion to the dynamic table
        var entryToInsert = new TableEntry("test", "testValue");

        // Create encoder instruction writer for generating "Insert with Literal Name"
        // encoder instruction
        var encoderInstructionWriter = new EncoderInstructionsWriter();

        // Check that no errors observed
        Assertions.assertNull(encoderErrorHandler.error.get());
        Assertions.assertNull(encoderErrorHandler.http3Error.get());
        Assertions.assertNull(decoderErrorHandler.error.get());
        Assertions.assertNull(decoderErrorHandler.http3Error.get());

        // Issue the insert instruction on encoder stream
        conn.encoderTable().insertWithEncoderStreamUpdate(entryToInsert,
                encoderInstructionWriter, conn.encoderStreams(),
                encoder.newEncodingContext(0, 0, new HeaderFrameWriter()));
        var encoderHeader = conn.encoderTable().get(0);
        var decoderHeader = conn.decoderTable().get(0);
        Assertions.assertEquals(decoderHeader.name(), encoderHeader.name());
        Assertions.assertEquals(decoderHeader.value(), encoderHeader.value());
    }

    @Test
    public void decoderErrorReportingTest() {
        AtomicReference<Throwable> error = new AtomicReference<>();
        TestErrorHandler encoderErrorHandler = new TestErrorHandler();
        TestErrorHandler decoderErrorHandler = new TestErrorHandler();
        EncoderDecoderConnector encoderDecoderConnector = new EncoderDecoderConnector();
        var conn = encoderDecoderConnector.newEncoderDecoderPair(e -> false,
                encoderErrorHandler::qpackErrorHandler,
                decoderErrorHandler::qpackErrorHandler,
                error::set);
        SettingsFrame settingsFrame = SettingsFrame.defaultRFCSettings();
        settingsFrame.setParameter(SettingsFrame.SETTINGS_QPACK_MAX_TABLE_CAPACITY, 2048L);
        ConnectionSettings settings = ConnectionSettings.createFrom(settingsFrame);
        conn.encoder().configure(settings);
        conn.encoderTable().setCapacity(1024L);
        conn.encoderTable().insertWithEncoderStreamUpdate(
                new TableEntry("a", "b"),
                new EncoderInstructionsWriter(),
                conn.encoderStreams(),
                conn.encoder().newEncodingContext(0, 0, new HeaderFrameWriter()));

        // QPACK_ENCODER_STREAM_ERROR is expected on the decoder side
        // since the decoder dynamic table capacity was not updated
        Assertions.assertEquals(Http3Error.QPACK_ENCODER_STREAM_ERROR,
                                decoderErrorHandler.http3Error.get());

        // It is expected that http3 error reported to
        // the decoder error handler only
        Assertions.assertNull(encoderErrorHandler.http3Error.get());
    }

    @Test
    public void overflowIntegerInInstructions() {
        AtomicReference<Throwable> error = new AtomicReference<>();
        TestErrorHandler encoderErrorHandler = new TestErrorHandler();
        TestErrorHandler decoderErrorHandler = new TestErrorHandler();
        EncoderDecoderConnector encoderDecoderConnector = new EncoderDecoderConnector();
        var conn = encoderDecoderConnector.newEncoderDecoderPair(e -> false,
                encoderErrorHandler::qpackErrorHandler,
                decoderErrorHandler::qpackErrorHandler,
                error::set);

        // Forge byte buffer with encoder instruction containing integer >
        // QPACK_MAX_INTEGER_VALUE
        //   0   1   2   3   4   5   6   7
        //  +---+---+---+---+---+---+---+---+
        //  | 0 | 0 | 1 |   Capacity (5+)   |
        //  +---+---+---+-------------------+
        var encoderInstBb = instructionWithOverflowInteger(5, 0b0010_0000);
        conn.encoderStreams().submitData(encoderInstBb);

        // Send bad decoder instruction back to encoder
        //   0   1   2   3   4   5   6   7
        //  +---+---+---+---+---+---+---+---+
        //  | 0 | 1 |     Stream ID (6+)    |
        //  +---+---+-----------------------+
        var buffer = instructionWithOverflowInteger(6, 0b0100_0000);
        conn.decoderStreams().submitData(buffer);

        // Analyze errors for expected results
        Throwable encoderError = encoderErrorHandler.error.get();
        Http3Error encoderHttp3Error = encoderErrorHandler.http3Error.get();
        Throwable decoderError = decoderErrorHandler.error.get();
        Http3Error decoderHttp3Error = decoderErrorHandler.http3Error.get();

        System.err.println("Encoder Error: " + encoderError);
        System.err.println("Encoder Http3 error: " + encoderHttp3Error);
        System.err.println("Decoder Error: " + decoderError);
        System.err.println("Decoder Http3 error: " + decoderHttp3Error);

        if (encoderError == null || !(encoderError instanceof IOException)) {
            Assertions.fail("Incorrect encoder error type", encoderError);
        }
        if (decoderError == null || !(decoderError instanceof IOException)) {
            Assertions.fail("Incorrect decoder error type", decoderError);
        }
        Assertions.assertEquals(Http3Error.QPACK_DECODER_STREAM_ERROR, encoderHttp3Error);
        Assertions.assertEquals(Http3Error.QPACK_ENCODER_STREAM_ERROR, decoderHttp3Error);
    }

    private static ByteBuffer instructionWithOverflowInteger(int N, int payload) {
        var buffer = ByteBuffer.allocate(11);
        int max = (2 << (N - 1)) - 1;
        buffer.put((byte) (payload | max));
        for (int i = 0; i < 9; i++) {
            buffer.put((byte) 128);
        }
        buffer.put((byte)10);
        buffer.flip();
        return buffer;
    }

    private static class TestErrorHandler {
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final AtomicReference<Http3Error> http3Error = new AtomicReference<>();

        public void qpackErrorHandler(Throwable error, Http3Error http3Error) {
            this.error.set(error);
            this.http3Error.set(http3Error);
        }
    }
}
