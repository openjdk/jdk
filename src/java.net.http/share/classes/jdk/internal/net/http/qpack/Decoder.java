/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.internal.net.http.qpack;

import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.http3.ConnectionSettings;
import jdk.internal.net.http.http3.streams.QueuingStreamPair;
import jdk.internal.net.http.http3.streams.UniStreamPair;
import jdk.internal.net.http.qpack.QPACK.QPACKErrorHandler;
import jdk.internal.net.http.qpack.QPACK.StreamPairSupplier;
import jdk.internal.net.http.qpack.readers.EncoderInstructionsReader;
import jdk.internal.net.http.qpack.readers.HeaderFrameReader;
import jdk.internal.net.http.qpack.writers.DecoderInstructionsWriter;
import jdk.internal.net.http.quic.streams.QuicStreamReader;

import java.io.IOException;
import java.net.ProtocolException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static jdk.internal.net.http.http3.Http3Error.H3_CLOSED_CRITICAL_STREAM;
import static jdk.internal.net.http.http3.frames.SettingsFrame.DEFAULT_SETTINGS_MAX_FIELD_SECTION_SIZE;
import static jdk.internal.net.http.qpack.DynamicTable.ENTRY_SIZE;
import static jdk.internal.net.http.qpack.QPACK.Logger.Level.EXTRA;
import static jdk.internal.net.http.qpack.QPACK.Logger.Level.NORMAL;

/**
 * Decodes headers from their binary representation.
 *
 * <p> Typical lifecycle looks like this:
 *
 * <p> {@link #Decoder(StreamPairSupplier, QPACKErrorHandler)  new Decoder}
 * ({@link #configure(ConnectionSettings)} called once from our HTTP/3 settings
 * {@link #decodeHeader(ByteBuffer, boolean, HeaderFrameReader) decodeHeader}
 *
 * <p> {@code Decoder} does not require a complete header block in a single
 * {@code ByteBuffer}. The header block can be spread across many buffers of any
 * size and decoded one-by-one the way it makes most sense for the user. This
 * way also allows not to limit the size of the header block.
 *
 * <p> Headers are delivered to the {@linkplain DecodingCallback callback} as
 * soon as they become decoded. Using the callback also gives the user freedom
 * to decide how headers are processed. The callback does not limit the number
 * of headers decoded during single decoding operation.
 */

public final class Decoder {

    private final QPACK.Logger logger;
    private final DynamicTable dynamicTable;
    private final EncoderInstructionsReader encoderInstructionsReader;
    private final QueuingStreamPair decoderStreamPair;
    private static final AtomicLong DECODERS_IDS = new AtomicLong();
    // ID of last acknowledged entry acked by Insert Count Increment
    // or section acknowledgement instruction
    private long acknowledgedInsertsCount;
    private final ReentrantLock ackInsertCountLock = new ReentrantLock();
    private final AtomicLong blockedStreamsCounter = new AtomicLong();
    private volatile long maxBlockedStreams;
    private final QPACKErrorHandler qpackErrorHandler;
    private volatile long maxFieldSectionSize = DEFAULT_SETTINGS_MAX_FIELD_SECTION_SIZE;
    private final AtomicLong concurrentDynamicTableInsertions =
            new AtomicLong();
    private static final long MAX_LITERAL_WITH_INDEXING =
            Utils.getIntegerNetProperty("jdk.httpclient.maxLiteralWithIndexing", 512);

    /**
     * Constructs a {@code Decoder} with zero initial capacity of the dynamic table.
     *
     * <p> Dynamic table capacity values has to be agreed between decoder and encoder out-of-band,
     * e.g. by a protocol that uses QPACK.
     * <p> Maximum dynamic table capacity is determined by the value of SETTINGS_QPACK_MAX_TABLE_CAPACITY
     * HTTP/3 setting sent by the decoder side (see
     * <a href="https://www.rfc-editor.org/rfc/rfc9204.html#name-maximum-dynamic-table-capac">
     * 3.2.3. Maximum Dynamic Table Capacity</a>).
     * <p> An encoder informs the decoder of a change to the dynamic table capacity using the
     * "Set Dynamic Table Capacity" instruction
     * (see <a href="https://www.rfc-editor.org/rfc/rfc9204.html#set-dynamic-capacity">
     * 4.3.1. Set Dynamic Table Capacity</a>)
     *
     * @see Decoder#configure(ConnectionSettings)
     */
    public Decoder(StreamPairSupplier streams, QPACKErrorHandler errorHandler) {
        long id = DECODERS_IDS.incrementAndGet();
        logger = QPACK.getLogger().subLogger("Decoder#" + id);
        if (logger.isLoggable(NORMAL)) {
            logger.log(NORMAL, () -> "New decoder");
        }
        dynamicTable = new DynamicTable(logger.subLogger("DynamicTable"), false);
        decoderStreamPair = streams.create(this::processEncoderInstruction);
        qpackErrorHandler = errorHandler;
        encoderInstructionsReader = new EncoderInstructionsReader(new DecoderTableCallback(), logger);
    }

    public QueuingStreamPair decoderStreams() {
        return decoderStreamPair;
    }

    /**
     * {@return a new {@link HeaderFrameReader} that will hold the decoding
     * state for a new request/response stream}
     */
    public HeaderFrameReader newHeaderFrameReader(DecodingCallback decodingCallback) {
        return new HeaderFrameReader(dynamicTable, decodingCallback,
                                     blockedStreamsCounter, maxBlockedStreams,
                                     maxFieldSectionSize, logger);
    }

    public void ackTableInsertions() {
        ackInsertCountLock.lock();
        try {
            long insertCount = dynamicTable.insertCount();
            assert acknowledgedInsertsCount <= insertCount;
            long incrementValue = insertCount - acknowledgedInsertsCount;
            if (incrementValue > 0) {
                // Write "Insert Count Increment" to the decoder stream
                var decoderInstructionsWriter = new DecoderInstructionsWriter();
                int instructionSize = decoderInstructionsWriter.configureForInsertCountInc(incrementValue);
                submitDecoderInstruction(decoderInstructionsWriter, instructionSize);
            }
            // Update lastAck value
            acknowledgedInsertsCount = insertCount;
        } finally {
            ackInsertCountLock.unlock();
        }
    }

    /**
     * Submit "Section Acknowledgment" instruction to the decoder stream.
     * A field line section needs to be acknowledged after completion of
     * section decoding.
     * @param streamId stream ID associated with the field section's
     * @param headerFrameReader header frame reader used to read
     *                          the field line section
     */
    public void ackSection(long streamId, HeaderFrameReader headerFrameReader) {

        FieldSectionPrefix prefix = headerFrameReader.decodedSectionPrefix();

        // 4.4.1. Section Acknowledgment: If an encoder receives a Section Acknowledgment instruction
        // referring to a stream on which every encoded field section with a non-zero Required Insert
        // Count has already been acknowledged, this MUST be treated as a connection error of type
        // QPACK_DECODER_STREAM_ERROR.
        long prefixInsertCount = prefix.requiredInsertCount();
        if (prefixInsertCount == 0) return;
        ackInsertCountLock.lock();
        try {
            var decoderInstructionsWriter = new DecoderInstructionsWriter();
            int instrSize = decoderInstructionsWriter.configureForSectionAck(streamId);
            submitDecoderInstruction(decoderInstructionsWriter, instrSize);
            if (prefixInsertCount > acknowledgedInsertsCount) {
                acknowledgedInsertsCount = prefixInsertCount;
            }
        } finally {
            ackInsertCountLock.unlock();
        }
    }

    public void cancelStream(long streamId) {
        var decoderInstructionsWriter = new DecoderInstructionsWriter();
        int instrSize = decoderInstructionsWriter.configureForStreamCancel(streamId);
        submitDecoderInstruction(decoderInstructionsWriter, instrSize);
        dynamicTable.cleanupStreamInsertCountNotifications(streamId);
    }

    /**
     * Configures maximum capacity of the decoder's dynamic table based on connection settings of
     * the HTTP client, also configures the number of allowed blocked streams.
     * The decoder's dynamic table capacity can only be changed via
     * {@linkplain EncoderInstructionsReader.Callback encoder instructions callback}.
     *
     * @param ourSettings connection settings
     */
    public void configure(ConnectionSettings ourSettings) {
        long maxCapacity = ourSettings.qpackMaxTableCapacity();
        dynamicTable.setMaxTableCapacity(maxCapacity);
        maxBlockedStreams = ourSettings.qpackBlockedStreams();
        long maxFieldSS = ourSettings.maxFieldSectionSize();
        if (maxFieldSS > 0) {
            maxFieldSectionSize = maxFieldSS;
        } else {
            // Unlimited field section size
            maxFieldSectionSize = -1L;
        }
    }

    /**
     * Decodes a header block from the given buffer to the given callback.
     *
     * <p> Suppose a header block is represented by a sequence of
     * {@code ByteBuffer}s in the form of {@code Iterator<ByteBuffer>}. And the
     * consumer of decoded headers is represented by {@linkplain DecodingCallback the callback}
     * registered within the provided {@code headerFrameReader}.
     * Then to decode the header block, the following approach might be used:
     * {@snippet :
     *     HeaderFrameReader headerFrameReader =
     *          newHeaderFrameReader(decodingCallback);
     *     while (buffers.hasNext()) {
     *         ByteBuffer input = buffers.next();
     *         decoder.decodeHeader(input, !buffers.hasNext(), headerFrameReader);
     *     }
     * }
     *
     * <p> The decoder reads as much as possible of the header block from the
     * given buffer, starting at the buffer's position, and increments its
     * position to reflect the bytes read. The buffer's mark and limit will not
     * be modified.
     *
     * <p> Once the method is invoked with {@code endOfHeaderBlock == true}, the
     * current header block is deemed ended, and inconsistencies, if any, are
     * reported immediately via a callback registered within the {@code
     * headerFrameReader} instance.
     *
     * <p> Each callback method is called only after the implementation has
     * processed the corresponding bytes. If the bytes revealed a decoding
     * error it is reported via a callback registered within the {@code
     * headerFrameReader} instance.
     *
     * @apiNote The method asks for {@code endOfHeaderBlock} flag instead of
     * returning it for two reasons. The first one is that the user of the
     * decoder always knows which chunk is the last. The second one is to throw
     * the most detailed exception possible, which might be useful for
     * diagnosing issues.
     *
     * @implNote This implementation is not atomic in respect to decoding
     * errors. In other words, if the decoding operation has thrown a decoding
     * error, the decoder is no longer usable.
     *
     * @param headerBlock
     *         the chunk of the header block, may be empty
     * @param endOfHeaderBlock
     *         true if the chunk is the final (or the only one) in the sequence
     * @param headerFrameReader the stateful header frame reader
     * @throws NullPointerException
     *         if either {@code headerBlock} or {@code headerFrameReader} are null
     */
    public void decodeHeader(ByteBuffer headerBlock, boolean endOfHeaderBlock,
                             HeaderFrameReader headerFrameReader) {
        requireNonNull(headerFrameReader, "headerFrameReader");
        headerFrameReader.read(headerBlock, endOfHeaderBlock);
    }

    /**
     * This method is invoked when the {@linkplain
     * UniStreamPair#futureReceiverStreamReader() decoder's stream reader}
     * has data available for reading.
     */
    private void processEncoderInstruction(ByteBuffer buffer) {
        if (buffer == QuicStreamReader.EOF) {
            // RFC-9204, section 4.2:
            // Closure of either unidirectional stream type MUST be treated as a connection
            // error of type H3_CLOSED_CRITICAL_STREAM.
            qpackErrorHandler.closeOnError(
                    new ProtocolException("QPACK " + decoderStreamPair.remoteStreamType()
                            + " remote stream was unexpectedly closed"), H3_CLOSED_CRITICAL_STREAM);
            return;
        }
        try {
            int stringLengthLimit = Math.clamp(dynamicTable.capacity() - ENTRY_SIZE,
                    0, Integer.MAX_VALUE - (int) ENTRY_SIZE);
            encoderInstructionsReader.read(buffer, stringLengthLimit);
        } catch (QPackException qPackException) {
            qpackErrorHandler.closeOnError(qPackException.getCause(), qPackException.http3Error());
        }
    }

    private void submitDecoderInstruction(DecoderInstructionsWriter decoderInstructionsWriter,
                                          int size) {
        if (size > decoderStreamPair.credit()) {
            qpackErrorHandler.closeOnError(
                    new IOException("QPACK not enough credit on a decoder stream " +
                            decoderStreamPair.remoteStreamType()), H3_CLOSED_CRITICAL_STREAM);
            return;
        }
        // All decoder instructions contain only one variable length integer.
        // Which could take up to 9 bytes max.
        ByteBuffer buffer = ByteBuffer.allocate(size);
        boolean done = decoderInstructionsWriter.write(buffer);
        // Assert that instruction is fully written, ie the correct
        // instruction size estimation was supplied.
        assert done;
        buffer.flip();
        decoderStreamPair.submitData(buffer);
    }

    void incrementAndCheckDynamicTableInsertsCount() {
        if (MAX_LITERAL_WITH_INDEXING > 0) {
            long concurrentNumberOfInserts = concurrentDynamicTableInsertions.incrementAndGet();
            if (concurrentNumberOfInserts > MAX_LITERAL_WITH_INDEXING) {
                String exceptionMessage = "Too many literal with indexing: %s > %s"
                        .formatted(concurrentNumberOfInserts, MAX_LITERAL_WITH_INDEXING);
                if (logger.isLoggable(EXTRA)) {
                    logger.log(EXTRA, () -> exceptionMessage);
                }
                throw QPackException.encoderStreamError(new ProtocolException(exceptionMessage));
            }
        }
    }

    public void resetInsertionsCounter() {
        if (MAX_LITERAL_WITH_INDEXING > 0) {
            concurrentDynamicTableInsertions.set(0);
        }
    }

    private class DecoderTableCallback implements EncoderInstructionsReader.Callback {

        private void ensureInstructionsAllowed() {
            // RFC9204 3.2.3. Maximum Dynamic Table Capacity:
            // "When the maximum table capacity is zero, the encoder MUST NOT
            // insert entries into the dynamic table and MUST NOT send any encoder
            // instructions on the encoder stream."
            if (dynamicTable.maxCapacity() == 0) {
                throw new IllegalStateException("Unexpected encoder instruction");
            }
        }

        @Override
        public void onCapacityUpdate(long capacity) {
            if (capacity == 0 && dynamicTable.maxCapacity() == 0) {
                return;
            }
            ensureInstructionsAllowed();
            dynamicTable.setCapacity(capacity);
        }

        @Override
        public void onInsert(String name, String value) {
            ensureInstructionsAllowed();
            incrementAndCheckDynamicTableInsertsCount();
            if (dynamicTable.insert(name, value) != DynamicTable.ENTRY_NOT_INSERTED) {
                ackTableInsertions();
            } else {
                // Not enough evictable space in dynamic table to insert entry
                throw new IllegalStateException("Not enough space in dynamic table");
            }
        }

        @Override
        public void onInsertIndexedName(boolean indexInStaticTable, long nameIndex, String valueString) {
            // RFC9204 7.4. Implementation Limits:
            // "If an implementation encounters a value larger than it is able to decode, this MUST be
            // treated as a stream error of type QPACK_DECOMPRESSION_FAILED if on a request stream or
            // a connection error of the appropriate type if on the encoder or decoder stream."
            ensureInstructionsAllowed();
            incrementAndCheckDynamicTableInsertsCount();
            if (dynamicTable.insert(nameIndex, indexInStaticTable, valueString) !=
                    DynamicTable.ENTRY_NOT_INSERTED) {
                ackTableInsertions();
            } else {
                // Not enough space in dynamic table to insert entry
                throw new IllegalStateException("Not enough space in dynamic table");
            }
        }

        @Override
        public void onDuplicate(long l) {
            // RFC9204 7.4. Implementation Limits:
            // "If an implementation encounters a value larger than it is able to decode, this
            //  MUST be treated as a stream error of type QPACK_DECOMPRESSION_FAILED"
            ensureInstructionsAllowed();
            incrementAndCheckDynamicTableInsertsCount();
            if (logger.isLoggable(NORMAL)) {
                logger.log(NORMAL,
                        () -> format("Processing duplicate instruction (%d)", l));
            }
            if (dynamicTable.duplicate(l) != DynamicTable.ENTRY_NOT_INSERTED) {
                ackTableInsertions();
            } else {
                // Not enough space in dynamic table to duplicate entry
                throw new IllegalStateException("Not enough space in dynamic table");
            }
        }
    }
}
