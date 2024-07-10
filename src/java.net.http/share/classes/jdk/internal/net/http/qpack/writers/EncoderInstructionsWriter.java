/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http.qpack.writers;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

import jdk.internal.net.http.hpack.QuickHuffman;
import jdk.internal.net.http.qpack.QPACK;
import jdk.internal.net.http.qpack.TableEntry;

import static java.lang.String.format;
import static jdk.internal.net.http.qpack.QPACK.Logger.Level.EXTRA;

public class EncoderInstructionsWriter {
    private BinaryRepresentationWriter writer;
    private final QPACK.Logger logger;
    private final EncoderInsertIndexedNameWriter insertIndexedNameWriter;
    private final EncoderInsertLiteralNameWriter insertLiteralNameWriter;
    private final EncoderDuplicateEntryWriter duplicateWriter;
    private final EncoderDynamicTableCapacityWriter capacityWriter;
    private boolean encoding;
    private static final AtomicLong ENCODERS_IDS = new AtomicLong();

    public EncoderInstructionsWriter() {
        this(QPACK.getLogger());
    }

    public EncoderInstructionsWriter(QPACK.Logger parentLogger) {
        long id = ENCODERS_IDS.incrementAndGet();
        this.logger = parentLogger.subLogger("EncoderInstructionsWriter#" + id);
        // Writer for "Insert with Name Reference" encoder instruction
        insertIndexedNameWriter = new EncoderInsertIndexedNameWriter(
                logger.subLogger("EncoderInsertIndexedNameWriter"));
        // Writer for "Insert with Literal Name" encoder instruction
        insertLiteralNameWriter = new EncoderInsertLiteralNameWriter(
                logger.subLogger("EncoderInsertLiteralNameWriter"));
        // Writer for "Set Dynamic Table Capacity" encoder instruction
        capacityWriter = new EncoderDynamicTableCapacityWriter();
        // Writer for "Duplicate" encoder instruction
        duplicateWriter = new EncoderDuplicateEntryWriter();
    }

    /*
     * Configure EncoderInstructionsWriter for encoding "Insert with Name Reference" or "Insert with Literal Name"
     *  encoder instruction. The instruction is selected based on TableEntry.type() value:
     *  "Insert with Name Reference" is selected for TableEntry.EntryType.NAME:
     *     0   1   2   3   4   5   6   7
     *    +---+---+---+---+---+---+---+---+
     *    | 1 | T |    Name Index (6+)    |
     *    +---+---+-----------------------+
     *    | H |     Value Length (7+)     |
     *    +---+---------------------------+
     *    |  Value String (Length bytes)  |
     *    +-------------------------------+
     *
     * "Insert with Literal Name" is selected for TableEntry.EntryType.NEITHER:
     *        0   1   2   3   4   5   6   7
     *    +---+---+---+---+---+---+---+---+
     *    | 0 | 1 | H | Name Length (5+)  |
     *    +---+---+---+-------------------+
     *    |  Name String (Length bytes)   |
     *    +---+---------------------------+
     *    | H |     Value Length (7+)     |
     *    +---+---------------------------+
     *    |  Value String (Length bytes)  |
     *    +-------------------------------+
     */
    public int configureForEntryInsertion(TableEntry e) {
        checkIfEncodingInProgress();
        encoding = true;
        writer = switch (e.type()) {
            case NAME -> insertIndexedNameWriter.configure(e);
            case NEITHER -> insertLiteralNameWriter.configure(e);
            default -> throw new IllegalArgumentException("Unsupported table entry insertion type: " + e.type());
        };
        return calculateEntryInsertionSize(e);
    }

    /*
     * Configure EncoderInstructionsWriter for encoding "Duplicate" encoder instruction:
     *        0   1   2   3   4   5   6   7
     *      +---+---+---+---+---+---+---+---+
     *      | 0 | 0 | 0 |    Index (5+)     |
     *      +---+---+---+-------------------+
     */
    public int configureForEntryDuplication(long entryIndexToDuplicate) {
        checkIfEncodingInProgress();
        encoding = true;
        duplicateWriter.configure(entryIndexToDuplicate);
        writer = duplicateWriter;
        if (logger.isLoggable(EXTRA)) {
            logger.log(EXTRA, () -> format("duplicate entry with id=%s", entryIndexToDuplicate));
        }
        return IntegerWriter.requiredBufferSize(5, entryIndexToDuplicate);
    }

    /*
     * Configure EncoderInstructionsWriter for encoding "Set Dynamic Table Capacity" encoder instruction:
     *        0   1   2   3   4   5   6   7
     *      +---+---+---+---+---+---+---+---+
     *      | 0 | 0 | 1 |   Capacity (5+)   |
     *      +---+---+---+-------------------+
     */
    public int configureForTableCapacityUpdate(long tableCapacity) {
        checkIfEncodingInProgress();
        encoding = true;
        capacityWriter.configure(tableCapacity);
        writer = capacityWriter;
        if (logger.isLoggable(EXTRA)) {
            logger.log(EXTRA, () -> format("set dynamic table capacity to %s", tableCapacity));
        }
        return IntegerWriter.requiredBufferSize(5, tableCapacity);
    }


    public boolean write(ByteBuffer byteBuffer) {
        if (!encoding) {
            throw new IllegalStateException("Writer hasn't been configured");
        }
        if (logger.isLoggable(EXTRA)) {
            logger.log(EXTRA, () -> format("writing to %s", byteBuffer));
        }
        boolean done = writer.write(byteBuffer);
        if (done) {
            writer.reset();
            encoding = false;
        }
        return done;
    }

    private int calculateEntryInsertionSize(TableEntry e) {
        int vlen = Math.min(QuickHuffman.lengthOf(e.value()), e.value().length());
        int integerValuesSize;
        return switch (e.type()) {
            case NAME -> {
                // Calculate how many bytes are needed to encode the index part:
                //         | 1 | T |    Name Index (6+)    |
                integerValuesSize = IntegerWriter.requiredBufferSize(6, e.index());
                // Calculate how many bytes are needed to encode the value length part:
                //       | H |     Value Length (7+)     |
                integerValuesSize += IntegerWriter.requiredBufferSize(7, vlen);
                // We also need vlen bytes for the value string content
                yield integerValuesSize + vlen;
            }
            case NEITHER -> {
                int nlen = Math.min(QuickHuffman.lengthOf(e.name()), e.name().length());
                // Calculate how many bytes are needed to encode the name length part:
                //     | 0 | 1 | H | Name Length (5+)  |
                integerValuesSize = IntegerWriter.requiredBufferSize(5, nlen);
                // Calculate how many bytes are needed to encode the value length part:
                //       | H |     Value Length (7+)     |
                integerValuesSize += IntegerWriter.requiredBufferSize(7, vlen);
                // We also need nlen + vlen bytes for the name and the value strings
                // content
                yield integerValuesSize + nlen + vlen;
            }
            default -> throw new IllegalArgumentException("Unsupported table entry type: " + e.type());
        };
    }

    private void checkIfEncodingInProgress() {
        if (encoding) {
            throw new IllegalStateException(
                    "Previous encoding operation hasn't finished yet");
        }
    }
}
