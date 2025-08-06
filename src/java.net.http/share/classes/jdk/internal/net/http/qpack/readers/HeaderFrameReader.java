/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http.qpack.readers;

import jdk.internal.net.http.common.SequentialScheduler;
import jdk.internal.net.http.qpack.DecodingCallback;
import jdk.internal.net.http.qpack.DynamicTable;
import jdk.internal.net.http.qpack.FieldSectionPrefix;
import jdk.internal.net.http.qpack.QPACK;
import jdk.internal.net.http.qpack.QPackException;
import jdk.internal.net.http.quic.streams.QuicStreamReader;

import java.io.IOException;
import java.net.ProtocolException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static jdk.internal.net.http.http3.Http3Error.H3_INTERNAL_ERROR;
import static jdk.internal.net.http.http3.Http3Error.QPACK_DECOMPRESSION_FAILED;
import static jdk.internal.net.http.qpack.QPACK.Logger.Level.EXTRA;
import static jdk.internal.net.http.qpack.QPACK.Logger.Level.NORMAL;

public class HeaderFrameReader {

    private enum State {
        // Nothing has been read so-far, "Required Insert Count" (RIC) will be read next
        INITIAL,
        // "Required Insert Count" read is done, "S" and "Delta Base" are next
        DELTA_BASE,
        // Encoded Field Section Prefix read is done, ready to start reading header fields.
        // In this state we only select a proper reader based on the field line encoding type,
        // ie the first byte is analysed to select a proper reader.
        SELECT_FIELD_READER,
        INDEXED,
        INDEX_WITH_POST_BASE,
        LITERAL_WITH_LITERAL_NAME,
        LITERAL_WITH_NAME_REF,
        LITERAL_WITH_POST_BASE,
        AWAITING_DT_INSERT_COUNT
    }

    /*
        4.5.1. Encoded Field Section Prefix
        Each encoded field section is prefixed with two integers. The Required Insert Count
         is encoded as an integer with an 8-bit prefix using the encoding described in Section 4.5.1.1.
         The Base is encoded as a Sign bit ('S') and a Delta Base value with a 7-bit prefix;
         see Section 4.5.1.2.

          0   1   2   3   4   5   6   7
        +---+---+---+---+---+---+---+---+
        |   Required Insert Count (8+)  |
        +---+---------------------------+
        | S |      Delta Base (7+)      |
        +---+---------------------------+
     */
    long requiredInsertCount;
    long deltaBase;
    int signBit;
    volatile FieldSectionPrefix fieldSectionPrefix;
    private final IntegerReader integerReader;
    private FieldLineReader reader;
    private final QPACK.Logger logger;
    private final FieldLineIndexedReader indexedReader;
    private final FieldLineIndexedPostBaseReader indexedPostBaseReader;
    private final FieldLineNameReferenceReader literalWithNameReferenceReader;
    private final FieldLineNameRefPostBaseReader literalWithNameRefPostBaseReader;

    private final FieldLineLiteralsReader literalWithLiteralNameReader;
    // Need dynamic table reference for decoding field line section prefix
    private final DynamicTable dynamicTable;
    private final DecodingCallback decodingCallback;

    private volatile State state = State.INITIAL;

    private final SequentialScheduler headersScheduler = SequentialScheduler.lockingScheduler(this::readLoop);
    private final ConcurrentLinkedQueue<ByteBuffer> headersData = new ConcurrentLinkedQueue<>();

    private final AtomicLong blockedStreamsCounter;
    private final long maxBlockedStreams;

    // A tracker of header data received by the decoder, to check that the peer encoder
    // honours the SETTINGS_MAX_FIELD_SECTION_SIZE value:
    // RFC-9114: 4.2.2. Header Size Constraints
    // "If an implementation wishes to advise its peer of this limit, it can
    // be conveyed as a number of bytes in the SETTINGS_MAX_FIELD_SECTION_SIZE parameter.
    // An implementation that has received this parameter SHOULD NOT send an HTTP message
    // header that exceeds the indicated size"
    // "A client can discard responses that it cannot process."
    //
    // Maximum allowed value is passed to FieldLineReader's implementations and not stored in
    // HeaderFrameReader instance.
    private final AtomicLong fieldSectionSizeTracker;

    private static final AtomicLong HEADER_FRAME_READER_IDS = new AtomicLong();

    private void readLoop() {
        try {
            readLoop0();
        } catch (QPackException qPackException) {
            Throwable cause = qPackException.getCause();
            if (qPackException.isConnectionError()) {
                decodingCallback.onConnectionError(cause, qPackException.http3Error());
            } else {
                decodingCallback.onStreamError(cause, qPackException.http3Error());
            }
        } catch (Throwable throwable) {
            decodingCallback.onConnectionError(throwable, H3_INTERNAL_ERROR);
        } finally {
            // Stop the scheduler, clear the reader's queue and
            // remove all insert count notification events associated
            // with current stream.
            if (decodingCallback.hasError()) {
                headersScheduler.stop();
                headersData.clear();
                dynamicTable.cleanupStreamInsertCountNotifications(decodingCallback.streamId());
            }
        }
    }

    private void readLoop0() {
        ByteBuffer headerBlock;
        OUTER:
        while (!decodingCallback.hasError() && (headerBlock = headersData.peek()) != null) {
            boolean endOfHeaderBlock = headerBlock == QuicStreamReader.EOF;
            State state = this.state;
            FieldSectionPrefix sectionPrefix = this.fieldSectionPrefix;
            while (!decodingCallback.hasError() && headerBlock.hasRemaining()) {
                if (state == State.SELECT_FIELD_READER) {
                    int b = headerBlock.get(headerBlock.position()) & 0xff; // absolute read
                    state = this.state = selectHeaderReaderState(b);
                    if (logger.isLoggable(EXTRA)) {
                        String message = format("next binary representation %s (first byte 0x%02x)", state, b);
                        logger.log(EXTRA, () -> message);
                    }
                    reader = switch (state) {
                        case INDEXED -> indexedReader;
                        case LITERAL_WITH_NAME_REF -> literalWithNameReferenceReader;
                        case LITERAL_WITH_LITERAL_NAME -> literalWithLiteralNameReader;
                        case INDEX_WITH_POST_BASE -> indexedPostBaseReader;
                        case LITERAL_WITH_POST_BASE -> literalWithNameRefPostBaseReader;
                        default -> throw QPackException.decompressionFailed(
                                new InternalError("Unexpected decoder state: " + state), false);
                    };
                    reader.configure(b);
                } else if (state == State.INITIAL) {
                    if (!integerReader.read(headerBlock)) {
                        continue;
                    }
                    // Required Insert Count was fully read
                    requiredInsertCount = integerReader.get();
                    if (logger.isLoggable(NORMAL)) {
                        logger.log(NORMAL, () -> format("Encoded Required Insert Count = %d", requiredInsertCount));
                    }
                    // Continue reading S and Delta Base values
                    state = this.state = State.DELTA_BASE;
                    // Reset integer reader
                    integerReader.reset();
                    // Prepare it for reading S and Delta Base (7+)
                    integerReader.configure(7);
                    continue;
                } else if (state == State.DELTA_BASE) {
                    if (signBit == -1) {
                        int b = headerBlock.get(headerBlock.position()) & 0xff; // absolute read
                        signBit = (b & 0b1000_0000) == 0b1000_0000 ? 1 : 0;
                        if (logger.isLoggable(NORMAL)) {
                            logger.log(NORMAL, () -> format("Base Sign = %d", signBit));
                        }
                    }
                    if (!integerReader.read(headerBlock)) {
                        continue;
                    }
                    deltaBase = integerReader.get();
                    if (logger.isLoggable(NORMAL)) {
                        logger.log(NORMAL, () -> format("Delta Base = %d", deltaBase));
                    }
                    // Construct field section prefix from the parsed fields
                    sectionPrefix = this.fieldSectionPrefix =
                            FieldSectionPrefix.decode(requiredInsertCount, deltaBase,
                                                      signBit, dynamicTable);

                    // Check if decoding of field section is blocked due to not yet received
                    // dynamic table entries
                    long insertCount = dynamicTable.insertCount();
                    if (sectionPrefix.requiredInsertCount() > insertCount) {
                        long blocked = blockedStreamsCounter.incrementAndGet();
                        if (logger.isLoggable(EXTRA)) {
                            logger.log(EXTRA,
                                    () -> "Blocked stream observed. Total blocked: " + blocked +
                                            " Max allowed: " + maxBlockedStreams);
                        }
                        // System property value is checked here instead of the HTTP3 settings because decoder uses its
                        // value to update connection settings. HTTP client's encoder implementation won't block the streams -
                        // only acknowledged entry references is used, therefore this connection setting is not consulted
                        // on encoder side.
                        if (blocked > maxBlockedStreams) {
                            var ioException = new IOException(("too many blocked streams: current=%d;  max=%d; " +
                                    "prefixCount=%d; tableCount=%d").formatted(blocked, maxBlockedStreams,
                                    sectionPrefix.requiredInsertCount(), insertCount));
                            //  If a decoder encounters more blocked streams than it promised to support,
                            //  it MUST treat this as a connection error of type QPACK_DECOMPRESSION_FAILED.
                            throw QPackException.decompressionFailed(ioException, true);
                        } else {
                            CompletableFuture<Void> future =
                                    dynamicTable.awaitFutureInsertCount(decodingCallback.streamId(),
                                            sectionPrefix.requiredInsertCount());
                            state = this.state = State.AWAITING_DT_INSERT_COUNT;
                            future.thenRun(this::onInsertCountUpdate);
                        }
                        break OUTER;
                    }
                    // The stream is unblocked - field lines can be decoded now
                    state = this.state = State.SELECT_FIELD_READER;
                    continue;
                } else if (state == State.AWAITING_DT_INSERT_COUNT) {
                    // If we're waiting for a specific dynamic table update
                    return;
                }
                if (reader.read(headerBlock, sectionPrefix, decodingCallback)) {
                    // Finished reading of one header field line
                    state = this.state = State.SELECT_FIELD_READER;
                }
            }
            if (!headerBlock.hasRemaining()) {
                var head = headersData.poll();
                assert head == headerBlock;
            }
            if (endOfHeaderBlock) {
                if (state == State.SELECT_FIELD_READER) {
                    decodingCallback.onComplete();
                } else {
                    logger.log(NORMAL, () -> "unexpected end of representation");
                    throw QPackException.decompressionFailed(
                            new ProtocolException("Unexpected end of header block"), true);
                }
            }
        }
    }

    private void onInsertCountUpdate() {
        long blocked = blockedStreamsCounter.decrementAndGet();
        if (logger.isLoggable(EXTRA)) {
            logger.log(EXTRA, () -> "Stream Unblocked - number of blocked streams: " + blocked);
        }
        state = State.SELECT_FIELD_READER;
        headersScheduler.runOrSchedule();
    }

    public HeaderFrameReader(DynamicTable dynamicTable, DecodingCallback callback,
                             AtomicLong blockedStreamsCounter, long maxBlockedStreams,
                             long maxFieldSectionSize, QPACK.Logger logger) {
        this.blockedStreamsCounter = blockedStreamsCounter;
        this.logger = logger.subLogger("HeaderFrameReader#" +
                HEADER_FRAME_READER_IDS.incrementAndGet());
        if (logger.isLoggable(NORMAL)) {
            logger.log(NORMAL, () -> format("New HeaderFrameReader, dynamic table capacity = %s",
                    dynamicTable.capacity()));
            /* To correlate with logging outside QPACK, knowing
               hashCode/toString is important */
            logger.log(NORMAL, () -> {
                String hashCode = Integer.toHexString(System.identityHashCode(this));
                return format("toString='%s', identityHashCode=%s", this, hashCode);
            });
        }
        this.fieldSectionSizeTracker = new AtomicLong();
        indexedReader = new FieldLineIndexedReader(dynamicTable,
                maxFieldSectionSize, fieldSectionSizeTracker,
                this.logger.subLogger("FieldLineIndexedReader"));
        indexedPostBaseReader = new FieldLineIndexedPostBaseReader(dynamicTable,
                maxFieldSectionSize, fieldSectionSizeTracker,
                this.logger.subLogger("FieldLineIndexedPostBaseReader"));
        literalWithNameReferenceReader = new FieldLineNameReferenceReader(dynamicTable,
                maxFieldSectionSize, fieldSectionSizeTracker,
                this.logger.subLogger("FieldLineNameReferenceReader"));
        literalWithNameRefPostBaseReader = new FieldLineNameRefPostBaseReader(dynamicTable,
                maxFieldSectionSize, fieldSectionSizeTracker,
                this.logger.subLogger("FieldLineNameRefPostBaseReader"));
        literalWithLiteralNameReader = new FieldLineLiteralsReader(
                maxFieldSectionSize, fieldSectionSizeTracker,
                this.logger.subLogger("FieldLineLiteralsReader"));
        integerReader = new IntegerReader(new ReaderError(QPACK_DECOMPRESSION_FAILED, false));
        resetPrefixVars();
        // Since reader is constructed in Initial state - it means that the
        // "Required Insert Count" will be read first.
        integerReader.configure(8);
        decodingCallback = callback;
        this.dynamicTable = dynamicTable;
        this.maxBlockedStreams = maxBlockedStreams;
    }

    private void resetPrefixVars() {
        requiredInsertCount = -1L;
        deltaBase = -1L;
        signBit = -1;
        fieldSectionPrefix = null;
        fieldSectionSizeTracker.set(0);
    }

    public FieldSectionPrefix decodedSectionPrefix() {
        if (deltaBase == -1L) {
            throw new IllegalStateException("Field Section Prefix not parsed yet");
        }
        return fieldSectionPrefix;
    }

    public void read(ByteBuffer headerBlock, boolean endOfHeaderBlock) {
        requireNonNull(headerBlock, "headerBlock");
        if (logger.isLoggable(NORMAL)) {
            logger.log(NORMAL, () -> format("reading %s, end of header block? %s",
                    headerBlock, endOfHeaderBlock));
        }
        headersData.add(headerBlock);
        if (endOfHeaderBlock) {
            headersData.add(QuicStreamReader.EOF);
        }
        headersScheduler.runOrSchedule();
    }

    private State selectHeaderReaderState(int b) {
        // First non-zero bit in lower 8 bits (see the caller)
        int pos = Integer.numberOfLeadingZeros(b) - 24;
        return switch (pos) {
            /*
               0   1   2   3   4   5   6   7
               +---+---+---+---+---+---+---+---+
               | 1 | T |      Index (6+)       |
               +---+---+-----------------------+
             */
            case 0 -> State.INDEXED;
            /*
              0   1   2   3   4   5   6   7
              +---+---+---+---+---+---+---+---+
              | 0 | 1 | N | T |Name Index (4+)|
              +---+---+---+---+---------------+
              | H |     Value Length (7+)     |
              +---+---------------------------+
              |  Value String (Length bytes)  |
              +-------------------------------+
             */
            case 1 -> State.LITERAL_WITH_NAME_REF;
            /*
                 0   1   2   3   4   5   6   7
               +---+---+---+---+---+---+---+---+
               | 0 | 0 | 1 | N | H |NameLen(3+)|
               +---+---+---+---+---+-----------+
               |  Name String (Length bytes)   |
               +---+---------------------------+
               | H |     Value Length (7+)     |
               +---+---------------------------+
               |  Value String (Length bytes)  |
               +-------------------------------+
             */
            case 2 -> State.LITERAL_WITH_LITERAL_NAME;
            /*
                  0   1   2   3   4   5   6   7
                +---+---+---+---+---+---+---+---+
                | 0 | 0 | 0 | 1 |  Index (4+)   |
                +---+---+---+---+---------------+
             */
            case 3 -> State.INDEX_WITH_POST_BASE;
            //     "Literal Field Line with Post-Base Name Reference":
            //              0   1   2   3   4   5   6   7
            //            +---+---+---+---+---+---+---+---+
            //            | 0 | 0 | 0 | 0 | N |NameIdx(3+)|
            //            +---+---+---+---+---------------+
            default -> {
                if ((b & 0xF0) == 0) {
                    yield State.LITERAL_WITH_POST_BASE;
                }
                throw QPackException.decompressionFailed(
                        new IOException("Unknown frame reader line prefix: " + b),
                        false);
            }
        };
    }

    /**
     * Reset the state of the HeaderFrameReader so that it's ready
     * to parse a new HeaderFrame.
     */
    public void reset() {
        state = State.INITIAL;
        reader = null;
        resetPrefixVars();
        integerReader.reset();
        integerReader.configure(8);
    }

}
