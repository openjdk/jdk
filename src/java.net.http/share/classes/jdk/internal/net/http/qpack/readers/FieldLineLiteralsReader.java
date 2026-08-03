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
import jdk.internal.net.http.qpack.QPACK;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.String.format;
import static jdk.internal.net.http.http3.Http3Error.QPACK_DECOMPRESSION_FAILED;
import static jdk.internal.net.http.qpack.DynamicTable.ENTRY_SIZE;
import static jdk.internal.net.http.qpack.QPACK.Logger.Level.NORMAL;

final class FieldLineLiteralsReader extends FieldLineReader {
    private boolean hideIntermediary;
    private boolean huffmanName, huffmanValue;
    private final StringBuilder name, value;
    private final StringReader stringReader;
    private final QPACK.Logger logger;
    private boolean firstValueRead = false;

    public FieldLineLiteralsReader(long maxSectionSize, AtomicLong sectionSizeTracker,
                                   QPACK.Logger logger) {
        // Dynamic table is not needed for literals reader
        super(null, maxSectionSize, sectionSizeTracker);
        this.logger = logger;
        stringReader = new StringReader(new ReaderError(QPACK_DECOMPRESSION_FAILED, false));
        name = new StringBuilder(512);
        value = new StringBuilder(1024);
    }

    public void configure(int b) {
        hideIntermediary = (b & 0b0001_0000) != 0;
    }

    //
    //              0   1   2   3   4   5   6   7
    //            +---+---+---+---+---+---+---+---+
    //            | 0 | 0 | 1 | N | H |NameLen(3+)|
    //            +---+---+-----------------------+
    //            |  Name String (Length bytes)   |
    //            +---+---------------------------+
    //            | H |     Value Length (7+)     |
    //            +---+---------------------------+
    //            |  Value String (Length bytes)  |
    //            +-------------------------------+
    //
    public boolean read(ByteBuffer input, FieldSectionPrefix prefix,
                        DecodingCallback action) {
        if (!completeReading(input)) {
            long readPart = ENTRY_SIZE + name.length() + value.length();
            checkPartialSize(readPart);
            return false;
        }
        String n = name.toString();
        String v = value.toString();
        if (logger.isLoggable(NORMAL)) {
            logger.log(NORMAL, () -> format(
                    "literal with literal name ('%s', huffman=%b, '%s', huffman=%b)",
                    n, huffmanName, v, huffmanValue));
        }
        checkSectionSize(DynamicTable.headerSize(n, v));
        action.onLiteralWithLiteralName(n, huffmanName, v, huffmanValue, hideIntermediary);
        reset();
        return true;
    }

    private boolean completeReading(ByteBuffer input) {
        if (!firstValueRead) {
            if (!stringReader.read(3, input, name, getMaxFieldLineLimit(name.length()))) {
                return false;
            }
            huffmanName = stringReader.isHuffmanEncoded();
            stringReader.reset();
            firstValueRead = true;
            return false;
        } else {
            int maxLength = getMaxFieldLineLimit(name.length() + value.length());
            if (!stringReader.read(input, value, maxLength)) {
                return false;
            }
        }
        huffmanValue = stringReader.isHuffmanEncoded();
        stringReader.reset();
        return true;
    }

    public void reset() {
        name.setLength(0);
        value.setLength(0);
        firstValueRead = false;
    }
}
