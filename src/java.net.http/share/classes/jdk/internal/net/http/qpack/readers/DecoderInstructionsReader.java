/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.net.http.qpack.QPACK.Logger;
import jdk.internal.net.http.qpack.QPackException;

import java.io.IOException;
import java.nio.ByteBuffer;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static jdk.internal.net.http.http3.Http3Error.QPACK_DECODER_STREAM_ERROR;
import static jdk.internal.net.http.qpack.QPACK.Logger.Level.EXTRA;

/*
 * Reader for decoder instructions described in RFC9204
 *  "4.4 Encoder Instructions" section.
 * Read instructions are passed to the consumer via the DecoderInstructionsReader.Callback
 *  instance supplied to the reader constructor.
 */
public class DecoderInstructionsReader {
    enum State {
        INIT,
        /*
          0   1   2   3   4   5   6   7
        +---+---+---+---+---+---+---+---+
        | 1 |      Stream ID (7+)       |
        +---+---------------------------+
         */
        SECTION_ACKNOWLEDGMENT,
        /*
          0   1   2   3   4   5   6   7
        +---+---+---+---+---+---+---+---+
        | 0 | 1 |     Stream ID (6+)    |
        +---+---+-----------------------+
         */
        STREAM_CANCELLATION,
        /*
          0   1   2   3   4   5   6   7
        +---+---+---+---+---+---+---+---+
        | 0 | 0 |     Increment (6+)    |
        +---+---+-----------------------+
         */
        INSERT_COUNT_INCREMENT
    }

    private State state;
    private final IntegerReader integerReader;
    private final Callback callback;
    private final Logger logger;

    public DecoderInstructionsReader(Callback callback, Logger logger) {
        this.integerReader = new IntegerReader(
                new ReaderError(QPACK_DECODER_STREAM_ERROR, true));
        this.callback = callback;
        this.state = State.INIT;
        this.logger = logger.subLogger("DecoderInstructionsReader");
    }

    public void read(ByteBuffer buffer) {
        requireNonNull(buffer, "buffer");
        while (buffer.hasRemaining()) {
            switch (state) {
                case INIT:
                    integerReader.reset();
                    state = identifyDecoderInstruction(buffer);
                    break;
                case INSERT_COUNT_INCREMENT, SECTION_ACKNOWLEDGMENT, STREAM_CANCELLATION:
                    // All decoder instructions consists of only one variable
                    // length integer field, therefore we fully read integer and
                    // then call the callback method depending on the state value
                    if (integerReader.read(buffer)) {
                        long value = integerReader.get();
                        if (logger.isLoggable(EXTRA)) {
                            logger.log(EXTRA, () -> format("Instruction: %s value: %s",
                                    state.name(), value));
                        }
                        // dispatch instruction to the consumer via the callback
                        dispatchParsedInstruction(value);
                        state = State.INIT;
                    }
                    break;
            }
        }
    }

    private State identifyDecoderInstruction(ByteBuffer buffer) {
        int b = buffer.get(buffer.position()) & 0xFF; // absolute read
        int pos = Integer.numberOfLeadingZeros(b) - 24;
        return switch (pos) {
            case 0 -> {
                integerReader.configure(7);
                yield State.SECTION_ACKNOWLEDGMENT;
            }
            case 1 -> {
                integerReader.configure(6);
                yield State.STREAM_CANCELLATION;
            }
            default -> {
                if ((b & 0b1100_0000) == 0) {
                    integerReader.configure(6);
                    yield State.INSERT_COUNT_INCREMENT;
                } else {
                    throw QPackException.decoderStreamError(
                            new IOException("Unexpected decoder instruction: " + b));
                }
            }
        };
    }

    private void dispatchParsedInstruction(long value) {
        switch (state) {
            case INSERT_COUNT_INCREMENT:
                callback.onInsertCountIncrement(value);
                break;
            case SECTION_ACKNOWLEDGMENT:
                callback.onSectionAck(value);
                break;
            case STREAM_CANCELLATION:
                callback.onStreamCancel(value);
                break;
            default:
                throw QPackException.decoderStreamError(
                        new IOException("Unknown decoder instruction"));
        }
    }

    public interface Callback {
        void onSectionAck(long streamId);

        void onStreamCancel(long streamId);

        void onInsertCountIncrement(long increment);
    }
}
