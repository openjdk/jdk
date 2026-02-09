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
import jdk.internal.net.http.qpack.DecodingCallback;
import jdk.internal.net.http.qpack.DynamicTable;
import jdk.internal.net.http.qpack.Encoder;
import jdk.test.lib.RandomFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/*
 * @test
 * @key randomness
 * @library /test/lib
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
 *                     -Djdk.http.qpack.allowBlockingEncoding=true
 *                     -Djdk.http.qpack.decoderBlockedStreams=4
 *                     DynamicTableFieldLineRepresentationTest
 */
public class DynamicTableFieldLineRepresentationTest {

    private static final Random RANDOM = RandomFactory.getRandom();
    private static final long DT_CAPACITY = 4096L;

    //4.5.2.  Indexed Field Line
    @Test
    public void indexedFieldLineOnDynamicTable() throws IOException {
        boolean sensitive = RANDOM.nextBoolean();
        List<ByteBuffer> buffers = new ArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        EncoderDecoderConnector encoderDecoderConnector = new EncoderDecoderConnector();

        TestErrorHandler encoderErrorHandler = new TestErrorHandler();
        TestErrorHandler decoderErrorHandler = new TestErrorHandler();

        var conn = encoderDecoderConnector.newEncoderDecoderPair(entry -> true,
                encoderErrorHandler::qpackErrorHandler, decoderErrorHandler::qpackErrorHandler,
                error::set);

        // Create encoder and decoder
        var encoder = conn.encoder();
        var decoder = conn.decoder();

        // Configure settingsFrames and prepopulate table
        configureConnector(conn,1);

        var name = conn.decoderTable().get(0).name();
        var value = conn.decoderTable().get(0).value();

        // Create header frame reader and writer
        var headerFrameWriter = encoder.newHeaderFrameWriter();
        var callback = new TestingDynamicCallBack( name, value);
        var headerFrameReader = decoder.newHeaderFrameReader(callback);

        // create encoding context
        Encoder.EncodingContext context = encoder.newEncodingContext(0, 1,
                headerFrameWriter);
        ByteBuffer headersBb = ByteBuffer.allocate(2048);

        // Configures encoder for writing the header name:value pair
        encoder.header(context, name, value, sensitive, -1);

        // Write the header
        headerFrameWriter.write(headersBb);
        assertNotEquals(0, headersBb.position());
        headersBb.flip();
        buffers.add(headersBb);

        // Generate field section prefix bytes
        encoder.generateFieldLineSectionPrefix(context, buffers);

        // Decode headers
        decoder.decodeHeader(buffers.get(0), false, headerFrameReader);
        decoder.decodeHeader(buffers.get(1), true, headerFrameReader);
        assertEquals(name, callback.lastIndexedName);
    }

    //4.5.3.  Indexed Field Line with Post-Base Index
    @Test
    public void indexedFieldLineOnDynamicTablePostBase() throws IOException {
        System.err.println("start indexedFieldLineOnDynamicTablePostBase");
        boolean sensitive = RANDOM.nextBoolean();

        List<ByteBuffer> buffers = new ArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        EncoderDecoderConnector encoderDecoderConnector = new EncoderDecoderConnector();

        TestErrorHandler encoderErrorHandler = new TestErrorHandler();
        TestErrorHandler decoderErrorHandler = new TestErrorHandler();

        var conn = encoderDecoderConnector.newEncoderDecoderPair(entry -> true,
                encoderErrorHandler::qpackErrorHandler, decoderErrorHandler::qpackErrorHandler,
                error::set);

        // Create encoder and decoder
        var encoder = conn.encoder();
        var decoder = conn.decoder();

        // Create settings frame with dynamic table capacity and number of blocked streams
        configureConnector(conn, 3);

        var name = conn.decoderTable().get(1).name();
        var value = conn.decoderTable().get(1).value();

        // Create header frame reader and writer
        var headerFrameWriter = encoder.newHeaderFrameWriter();
        var callback = new TestingDynamicCallBack( name, value);
        var headerFrameReader = decoder.newHeaderFrameReader(callback);

        // create encoding context
        Encoder.EncodingContext context = encoder.newEncodingContext(0, 1,
                headerFrameWriter);
        ByteBuffer headersBb = ByteBuffer.allocate(2048);

        // Configures encoder for writing the header name:value pair
        encoder.header(context, name, value, sensitive, -1);

        // Write the header
        headerFrameWriter.write(headersBb);
        assertNotEquals(0, headersBb.position());
        headersBb.flip();
        buffers.add(headersBb);

        // Generate field section prefix bytes
        encoder.generateFieldLineSectionPrefix(context, buffers);

        // Decode headers
        decoder.decodeHeader(buffers.get(0), false, headerFrameReader);
        decoder.decodeHeader(buffers.get(1), true, headerFrameReader);
        assertEquals(name, callback.lastIndexedName);
        assertEquals(value, callback.lastValue);
    }

    // 4.5.4.  Literal Field Line with Name Reference
    // A literal field line with name reference representation encodes a field line
    // where the field name matches the field name of an entry in the static table
    // or the field name of an entry in the dynamic table with an absolute index
    // less than the value of the Base.
    @Test
    public void literalFieldLineNameReference() throws IOException {
        System.err.println("start literalFieldLineNameReference");
        boolean sensitive = RANDOM.nextBoolean();

        List<ByteBuffer> buffers = new ArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        EncoderDecoderConnector encoderDecoderConnector = new EncoderDecoderConnector();

        TestErrorHandler encoderErrorHandler = new TestErrorHandler();
        TestErrorHandler decoderErrorHandler = new TestErrorHandler();

        var conn = encoderDecoderConnector.newEncoderDecoderPair(entry -> false,
                encoderErrorHandler::qpackErrorHandler, decoderErrorHandler::qpackErrorHandler,
                error::set);

        // Create encoder and decoder
        var encoder = conn.encoder();
        var decoder = conn.decoder();

        // Create settings frame with dynamic table capacity and number of blocked streams
        configureConnector(conn, 3);

        var name = conn.decoderTable().get(1).name();
        var value = conn.decoderTable().get(2).value(); // don't want value to match


        // Create header frame reader and writer
        var headerFrameWriter = encoder.newHeaderFrameWriter();
        var callback = new TestingDynamicCallBack(name, value);
        var headerFrameReader = decoder.newHeaderFrameReader(callback);

        // create encoding context
        Encoder.EncodingContext context = encoder.newEncodingContext(0, 3,
                headerFrameWriter);
        ByteBuffer headersBb = ByteBuffer.allocate(2048);

        // Configures encoder for writing the header name:value pair
        encoder.header(context, name, value, sensitive, -1);

        // Write the header
        headerFrameWriter.write(headersBb);
        assertNotEquals(0, headersBb.position());
        headersBb.flip();
        buffers.add(headersBb);

        // Generate field section prefix bytes
        encoder.generateFieldLineSectionPrefix(context, buffers);

        // Decode headers
        decoder.decodeHeader(buffers.get(0), false, headerFrameReader);
        decoder.decodeHeader(buffers.get(1), true, headerFrameReader);
        assertEquals(name, callback.lastReferenceName);
    }

    //4.5.5.  Literal Field Line with Post-Base Name Reference
    @Test
    public void literalFieldLineNameReferencePostBase() throws IOException {
        System.err.println("start literalFieldLineNameReferencePostBase");
        boolean sensitive = RANDOM.nextBoolean();

        List<ByteBuffer> buffers = new ArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        EncoderDecoderConnector encoderDecoderConnector = new EncoderDecoderConnector();

        TestErrorHandler encoderErrorHandler = new TestErrorHandler();
        TestErrorHandler decoderErrorHandler = new TestErrorHandler();

        var conn = encoderDecoderConnector.newEncoderDecoderPair(entry -> false,
                encoderErrorHandler::qpackErrorHandler, decoderErrorHandler::qpackErrorHandler,
                error::set);

        // Create encoder and decoder
        var encoder = conn.encoder();
        var decoder = conn.decoder();

        // Create settings frame with dynamic table capacity and number of blocked streams
        configureConnector(conn,4);

        var name = conn.decoderTable().get(3).name();
        var value = conn.decoderTable().get(2).value(); // don't want value to match

        // Create header frame reader and writer
        var headerFrameWriter = encoder.newHeaderFrameWriter();
        var callback = new TestingDynamicCallBack(name, value);
        var headerFrameReader = decoder.newHeaderFrameReader(callback);

        // create encoding context
        Encoder.EncodingContext context = encoder.newEncodingContext(0, 2,
                headerFrameWriter);
        ByteBuffer headersBb = ByteBuffer.allocate(2048);

        // Configures encoder for writing the header name:value pair
        encoder.header(context, name, value, sensitive, -1);

        // Write the header
        headerFrameWriter.write(headersBb);
        assertNotEquals(0, headersBb.position());
        headersBb.flip();
        buffers.add(headersBb);

        // Generate field section prefix bytes
        encoder.generateFieldLineSectionPrefix(context, buffers);

        // Decode headers
        decoder.decodeHeader(buffers.get(0), false, headerFrameReader);
        decoder.decodeHeader(buffers.get(1), true, headerFrameReader);
        assertEquals(name, callback.lastReferenceName);
        assertEquals(value, callback.lastValue);
    }

    private void configureConnector(EncoderDecoderConnector.EncoderDecoderPair connector, int numberOfEntries){
        // Create settings frame with dynamic table capacity and number of blocked streams
        SettingsFrame settingsFrame = SettingsFrame.defaultRFCSettings();
        // 4k should be enough for storing dynamic table entries added by 'prepopulateDynamicTable'
        settingsFrame.setParameter(SettingsFrame.SETTINGS_QPACK_MAX_TABLE_CAPACITY, DT_CAPACITY);
        settingsFrame.setParameter(SettingsFrame.SETTINGS_QPACK_BLOCKED_STREAMS,2);

        ConnectionSettings settings = ConnectionSettings.createFrom(settingsFrame);

        // Configure encoder and decoder with constructed ConnectionSettings
        connector.encoder().configure(settings);
        connector.decoder().configure(settings);
        connector.encoderTable().setCapacity(DT_CAPACITY);
        connector.decoderTable().setCapacity(DT_CAPACITY);

        // add basic matching entries to both
        prepopulateDynamicTable(connector.encoderTable(), numberOfEntries);
        prepopulateDynamicTable(connector.decoderTable(), numberOfEntries);
    }

    private static void prepopulateDynamicTable(DynamicTable dynamicTable, int numEntries) {
        for (int count = 0; count < numEntries; count++) {
            var header = TestHeader.withId(count);
            dynamicTable.insert(header.name(), header.value());
        }
    }

    private static class TestErrorHandler {
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final AtomicReference<Http3Error> http3Error = new AtomicReference<>();

        public void qpackErrorHandler(Throwable error, Http3Error http3Error) {
            this.error.set(error);
            this.http3Error.set(http3Error);
        }
    }

    private static class TestingDynamicCallBack implements DecodingCallback {
        final String name, value;
        String lastLiteralName = null;
        String lastReferenceName = null;
        String lastValue = null;
        String lastIndexedName = null;

        TestingDynamicCallBack(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public void onDecoded(CharSequence actualName, CharSequence value) {
            fail("onDecoded should not be called");
        }

        @Override
        public void onComplete() {
            System.out.println("completed it");
        }

        @Override
        public void onConnectionError(Throwable throwable, Http3Error http3Error) {
            fail("Decoding error: " + http3Error, throwable);
        }

        @Override
        public long streamId() {
            return 0;
        }

        @Override
        public void onIndexed(long actualIndex, CharSequence actualName, CharSequence actualValue) {
            System.out.println("Indexed called");
            assertEquals(name, actualName);
            assertEquals(value, actualValue);
            lastValue = value;
            lastIndexedName = name;
        }

        @Override
        public void onLiteralWithNameReference(long index,
                                               CharSequence actualName,
                                               CharSequence actualValue,
                                               boolean valueHuffman,
                                               boolean hideIntermediary) {
            System.out.println("Literal with name reference called");
            assertEquals(name, actualName.toString());
            assertEquals(value, actualValue.toString());
            lastReferenceName = name;
            lastValue = value;
        }

        @Override
        public void onLiteralWithLiteralName(CharSequence actualName, boolean nameHuffman,
                                             CharSequence actualValue, boolean valueHuffman,
                                             boolean hideIntermediary) {
            System.out.println("Literal with literal name called");
            assertEquals(name, actualName.toString());
            assertEquals(value, actualValue.toString());
            lastLiteralName = name;
            lastValue = value;
        }
    }

    record TestHeader(String name, String value) {
        public static BlockingDecodingTest.TestHeader withId(int id) {
            return new BlockingDecodingTest.TestHeader(NAME + id, VALUE + id);
        }
    }

    private static final String NAME = "test";
    private static final String VALUE = "valueTest";
}
