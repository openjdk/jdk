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
package jdk.internal.net.http.qpack.readers;

import jdk.internal.net.http.qpack.DecodingCallback;
import jdk.internal.net.http.qpack.DynamicTable;
import jdk.internal.net.http.qpack.FieldSectionPrefix;
import jdk.internal.net.http.qpack.HeaderField;
import jdk.internal.net.http.qpack.QPACK;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.String.format;
import static jdk.internal.net.http.http3.Http3Error.QPACK_DECOMPRESSION_FAILED;
import static jdk.internal.net.http.qpack.QPACK.Logger.Level.NORMAL;

final class FieldLineNameReferenceReader extends FieldLineReader {
    private long intValue;
    private boolean hideIntermediary;
    private boolean huffmanValue;
    private final StringBuilder value;
    private final IntegerReader integerReader;
    private final StringReader stringReader;
    private final QPACK.Logger logger;

    private boolean firstValueRead = false;

    FieldLineNameReferenceReader(DynamicTable dynamicTable, long maxSectionSize,
                                 AtomicLong sectionSizeTracker, QPACK.Logger logger) {
        super(dynamicTable, maxSectionSize, sectionSizeTracker);
        this.logger = logger;
        var errorToReport = new ReaderError(QPACK_DECOMPRESSION_FAILED, false);
        integerReader = new IntegerReader(errorToReport);
        stringReader = new StringReader(errorToReport);
        value = new StringBuilder(1024);
    }

    public void configure(int b) {
        fromStaticTable = (b & 0b0001_0000) != 0;
        hideIntermediary = (b & 0b0010_0000) != 0;
        integerReader.configure(4);
    }

    //              0   1   2   3   4   5   6   7
    //            +---+---+---+---+---+---+---+---+
    //            | 0 | 1 | N | T | NameIndex (4+)|
    //            +---+---+-----------------------+
    //            | H |     Value Length (7+)     |
    //            +---+---------------------------+
    //            | Value String (Length octets)  |
    //            +-------------------------------+
    //
    public boolean read(ByteBuffer input, FieldSectionPrefix prefix,
                        DecodingCallback action) {
        if (!completeReading(input)) {
            if (firstValueRead) {
                long readPart = DynamicTable.ENTRY_SIZE + value.length();
                checkPartialSize(readPart);
            }
            return false;
        }
        if (logger.isLoggable(NORMAL)) {
            logger.log(NORMAL, () -> format(
                    "literal with name reference (%s, %s, '%s', huffman=%b)",
                    fromStaticTable ? "static" : "dynamic", intValue, value, huffmanValue));
        }
        long absoluteIndex = fromStaticTable ? intValue : prefix.base() - 1 - intValue;
        checkEntryIndex(absoluteIndex, prefix);
        HeaderField f = entryAtIndex(absoluteIndex);
        String valueStr = value.toString();
        checkSectionSize(DynamicTable.headerSize(f.name(), valueStr));
        action.onLiteralWithNameReference(absoluteIndex, f.name(), valueStr,
                                          huffmanValue, hideIntermediary);
        reset();
        return true;
    }

    private boolean completeReading(ByteBuffer input) {
        if (!firstValueRead) {
            if (!integerReader.read(input)) {
                return false;
            }
            intValue = integerReader.get();
            integerReader.reset();

            firstValueRead = true;
            return false;
        } else {
            if (!stringReader.read(input, value, getMaxFieldLineLimit())) {
                return false;
            }
        }
        huffmanValue = stringReader.isHuffmanEncoded();
        stringReader.reset();

        return true;
    }

    public void reset() {
        value.setLength(0);
        firstValueRead = false;
    }
}
