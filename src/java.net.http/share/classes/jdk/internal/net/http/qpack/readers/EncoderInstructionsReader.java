/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.net.http.qpack.QPACK;
import jdk.internal.net.http.qpack.QPackException;

import java.nio.ByteBuffer;

import static java.lang.String.format;
import static java.lang.System.Logger.Level.TRACE;
import static java.util.Objects.requireNonNull;
import static jdk.internal.net.http.http3.Http3Error.QPACK_ENCODER_STREAM_ERROR;

/*
 * Reader for encoder instructions defined in RFC9204
 *  "4.3 Encoder Instructions" section.
 * Read instruction is passed to the consumer via Callback
 * interface supplied to the EncoderInstructionsReader constructor.
 */
public class EncoderInstructionsReader {

    enum State {
        INIT,
        /*
              0   1   2   3   4   5   6   7
            +---+---+---+---+---+---+---+---+
            | 0 | 0 | 1 |   Capacity (5+)   |
            +---+---+---+-------------------+
         */
        DT_CAPACITY,
        /*
             0   1   2   3   4   5   6   7
           +---+---+---+---+---+---+---+---+
           | 1 | T |    Name Index (6+)    |
           +---+---+-----------------------+
           | H |     Value Length (7+)     |
           +---+---------------------------+
           |  Value String (Length bytes)  |
           +-------------------------------+
         */
        INSERT_NAME_REF_NAME,
        INSERT_NAME_REF_VALUE,
        /*
             0   1   2   3   4   5   6   7
           +---+---+---+---+---+---+---+---+
           | 0 | 1 | H | Name Length (5+)  |
           +---+---+---+-------------------+
           |  Name String (Length bytes)   |
           +---+---------------------------+
           | H |     Value Length (7+)     |
           +---+---------------------------+
           |  Value String (Length bytes)  |
           +-------------------------------+
         */
        INSERT_NAME_LIT_NAME,
        INSERT_NAME_LIT_VALUE,

        /*
             0   1   2   3   4   5   6   7
           +---+---+---+---+---+---+---+---+
           | 0 | 0 | 0 |    Index (5+)     |
           +---+---+---+-------------------+
         */
        DUPLICATE
    }

    private final QPACK.Logger logger;
    private final Callback updateCallback;
    private State state;
    private final IntegerReader integerReader;
    private final StringReader stringReader;
    private int bitT = -1;
    private long nameIndex = -1L;
    private boolean huffmanValue;
    private final StringBuilder valueString = new StringBuilder();

    private boolean huffmanName;
    private final StringBuilder nameString = new StringBuilder();

    public EncoderInstructionsReader(Callback dtUpdateCallback, QPACK.Logger logger) {
        this.logger = logger;
        this.updateCallback = dtUpdateCallback;
        this.state = State.INIT;
        var errorToReport = new ReaderError(QPACK_ENCODER_STREAM_ERROR, true);
        this.integerReader = new IntegerReader(errorToReport);
        this.stringReader = new StringReader(errorToReport);
    }

    public void read(ByteBuffer buffer, int maxStringLength) {
        try {
            read0(buffer, maxStringLength);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            // "Duplicate" and "Insert With Name Reference" instructions can reference
            // non-existing entries in the dynamic table.
            // Such errors are treated as encoder stream errors.
            throw QPackException.encoderStreamError(exception);
        }
    }

    private void read0(ByteBuffer buffer, int maxStringLength) {
        requireNonNull(buffer, "buffer");
        while (buffer.hasRemaining()) {
            switch (state) {
                case INIT:
                    state = identifyEncoderInstruction(buffer);
                    break;
                case DT_CAPACITY:
                    if (integerReader.read(buffer)) {
                        long capacity = integerReader.get();
                        if (logger.isLoggable(TRACE)) {
                            logger.log(TRACE, () -> format("Dynamic Table Capacity update: %d",
                                        capacity));
                        }
                        updateCallback.onCapacityUpdate(integerReader.get());
                        reset();
                    }
                    break;
                case INSERT_NAME_LIT_NAME:
                    if (stringReader.read(5, buffer, nameString, maxStringLength)) {
                        huffmanName = stringReader.isHuffmanEncoded();
                        stringReader.reset();
                        state = State.INSERT_NAME_LIT_VALUE;
                    }
                    break;
                case INSERT_NAME_LIT_VALUE:
                    int stringReaderLimit = maxStringLength > 0 ?
                            Math.max(maxStringLength - nameString.length(), 0) : -1;
                    if (stringReader.read(buffer, valueString, stringReaderLimit)) {
                        huffmanValue = stringReader.isHuffmanEncoded();
                        // Insert with literal name instruction completely parsed
                        if (logger.isLoggable(TRACE)) {
                            logger.log(TRACE, () -> format("Insert with Literal Name ('%s','%s'," +
                                                     " huffmanName='%s', huffmanValue='%s')", nameString,
                                    valueString, huffmanName, huffmanValue));
                        }
                        updateCallback.onInsert(nameString.toString(), valueString.toString());
                        reset();
                    }
                    break;
                case INSERT_NAME_REF_NAME:
                    if (integerReader.read(buffer)) {
                        nameIndex = integerReader.get();
                        state = State.INSERT_NAME_REF_VALUE;
                    }
                    break;
                case INSERT_NAME_REF_VALUE:
                    if (stringReader.read(buffer, valueString, maxStringLength)) {
                        // Insert with name reference instruction completely parsed
                        if (logger.isLoggable(TRACE)) {
                            logger.log(TRACE, () -> format("Insert With Name Reference (T=%d, nameIdx=%d," +
                                                    " value='%s', valueHuffman='%s')",
                                    bitT, nameIndex, valueString, stringReader.isHuffmanEncoded()));
                        }
                        updateCallback.onInsertIndexedName(bitT == 1, nameIndex, valueString.toString());
                        reset();
                    }
                    break;
                case DUPLICATE:
                    if (integerReader.read(buffer)) {
                        updateCallback.onDuplicate(integerReader.get());
                        reset();
                    }
                    break;
            }
        }
    }

    private State identifyEncoderInstruction(ByteBuffer buffer) {
        int b = buffer.get(buffer.position()) & 0xFF; // absolute read
        int pos = Integer.numberOfLeadingZeros(b) - 24;
        return switch (pos) {
            case 0 -> {
                // Configure integer reader to read out name index and read the T bit
                integerReader.configure(6);
                bitT = (b & 0b0100_0000) == 0 ? 0 : 1;
                yield State.INSERT_NAME_REF_NAME;
            }
            case 1 -> State.INSERT_NAME_LIT_NAME;
            case 2 -> {
                integerReader.configure(5);
                yield State.DT_CAPACITY;
            }
            default -> {
                boolean isDuplicateInstruction = (b & 0b1110_0000) == 0;
                if (isDuplicateInstruction) {
                    integerReader.configure(5);
                    yield State.DUPLICATE;
                } else {
                    throw QPackException.encoderStreamError(
                            new InternalError("Unexpected encoder instruction: " + b));
                }
            }
        };
    }

    public void reset() {
        state = State.INIT;
        bitT = -1;
        nameIndex = -1L;
        huffmanName = false;
        huffmanValue = false;
        resetBuffersAndReaders();
    }

    private void resetBuffersAndReaders() {
        integerReader.reset();
        stringReader.reset();
        nameString.setLength(0);
        valueString.setLength(0);
    }

    public interface Callback {
        void onCapacityUpdate(long capacity);

        void onInsert(String name, String value);

        void onInsertIndexedName(boolean indexInStaticTable, long nameIndex, String valueString);

        void onDuplicate(long l);
    }
}
