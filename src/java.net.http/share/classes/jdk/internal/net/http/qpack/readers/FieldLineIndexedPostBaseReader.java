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

import jdk.internal.net.http.qpack.DecodingCallback;
import jdk.internal.net.http.qpack.DynamicTable;
import jdk.internal.net.http.qpack.FieldSectionPrefix;
import jdk.internal.net.http.qpack.HeaderField;
import jdk.internal.net.http.qpack.QPACK;
import jdk.internal.net.http.qpack.QPackException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.String.format;
import static jdk.internal.net.http.http3.Http3Error.QPACK_DECOMPRESSION_FAILED;
import static jdk.internal.net.http.qpack.QPACK.Logger.Level.NORMAL;

public final class FieldLineIndexedPostBaseReader extends FieldLineReader {
    private final DynamicTable dynamicTable;
    private final IntegerReader integerReader;
    private final QPACK.Logger logger;

    public FieldLineIndexedPostBaseReader(DynamicTable dynamicTable, long maxSectionSize,
                                          AtomicLong sectionSizeTracker, QPACK.Logger logger) {
        super(maxSectionSize, sectionSizeTracker);
        this.dynamicTable = dynamicTable;
        this.integerReader = new IntegerReader(
                new ReaderError(QPACK_DECOMPRESSION_FAILED, false));
        this.logger = logger;
    }

    public void configure(int b) {
        integerReader.configure(4);
    }

    //    0   1   2   3   4   5   6   7
    //  +---+---+---+---+---+---+---+---+
    //  | 0 | 0 | 0 | 1 |  Index (4+)   |
    //  +---+---+---+---+---------------+
    //
    public boolean read(ByteBuffer input, FieldSectionPrefix prefix,
                        DecodingCallback action) {
        if (!integerReader.read(input)) {
            return false;
        }
        long relativeIndex = integerReader.get();
        long absoluteIndex = prefix.base() + relativeIndex;
        if (logger.isLoggable(NORMAL)) {
            logger.log(NORMAL, () -> format("Post-Base Indexed Field Line: base=%s index=%s[%s]",
                    prefix.base(), relativeIndex, absoluteIndex));
        }
        // If the decoder encounters a reference in a field line representation to a dynamic table entry
        // that has already been evicted or that has an absolute index greater than or equal to the declared
        // Required Insert Count (Section 4.5.1), it MUST treat this as a connection error of type
        // QPACK_DECOMPRESSION_FAILED.
        if (absoluteIndex >= prefix.requiredInsertCount()) {
            throw QPackException.decompressionFailed(
                    new IOException("header index is greater than RIC"), true);
        }
        HeaderField f = getHeaderFieldAt(absoluteIndex);
        checkSectionSize(DynamicTable.headerSize(f));
        action.onIndexed(absoluteIndex, f.name(), f.value());
        reset();
        return true;
    }

    private HeaderField getHeaderFieldAt(long index) {
        HeaderField f;
        try {
            f = dynamicTable.get(index);
        } catch (IndexOutOfBoundsException | IllegalArgumentException | IllegalStateException e) {
            throw QPackException.decompressionFailed(
                    new IOException("header not known", e), true);
        }
        return f;
    }
    public void reset() {
        integerReader.reset();
    }
}
