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

import jdk.internal.net.http.http3.Http3Error;
import jdk.internal.net.http.qpack.DecodingCallback;
import jdk.internal.net.http.qpack.DynamicTable;
import jdk.internal.net.http.qpack.FieldSectionPrefix;
import jdk.internal.net.http.qpack.HeaderField;
import jdk.internal.net.http.qpack.QPACK;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.String.format;
import static jdk.internal.net.http.qpack.QPACK.Logger.Level.NORMAL;

final class FieldLineIndexedReader extends FieldLineReader {
    private final IntegerReader integerReader;
    private final QPACK.Logger logger;

    public FieldLineIndexedReader(DynamicTable dynamicTable, long maxSectionSize,
                                  AtomicLong sectionSizeTracker, QPACK.Logger logger) {
        super(dynamicTable, maxSectionSize, sectionSizeTracker);
        this.logger = logger;
        integerReader = new IntegerReader(
                new ReaderError(Http3Error.QPACK_DECOMPRESSION_FAILED, false));
    }

    public void configure(int b) {
        integerReader.configure(6);
        fromStaticTable = (b & 0b0100_0000) != 0;
    }

    //              0   1   2   3   4   5   6   7
    //            +---+---+---+---+---+---+---+---+
    //            | 1 | T |     Index (6+)        |
    //            +---+---------------------------+
    //
    public boolean read(ByteBuffer input, FieldSectionPrefix prefix,
                        DecodingCallback action) {
        if (!integerReader.read(input)) {
            return false;
        }
        long intValue = integerReader.get();
        // "In a field line representation, a relative index of 0 refers to the
        //  entry with absolute index equal to Base - 1."
        long absoluteIndex = fromStaticTable ? intValue : prefix.base() - 1 - intValue;
        if (logger.isLoggable(NORMAL)) {
            logger.log(NORMAL, () -> format("%s index %s", fromStaticTable ? "Static" : "Dynamic",
                       absoluteIndex));
        }
        checkEntryIndex(absoluteIndex, prefix);
        HeaderField f = entryAtIndex(absoluteIndex);
        checkSectionSize(DynamicTable.headerSize(f));
        action.onIndexed(absoluteIndex, f.name(), f.value());
        reset();
        return true;
    }

    public void reset() {
        integerReader.reset();
    }
}
