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
import jdk.internal.net.http.qpack.QPackException;
import jdk.internal.net.http.qpack.StaticTable;

import java.io.IOException;
import java.net.ProtocolException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

sealed abstract class FieldLineReader permits FieldLineIndexedPostBaseReader,
        FieldLineIndexedReader, FieldLineLiteralsReader, FieldLineNameRefPostBaseReader,
        FieldLineNameReferenceReader {

    final long maxSectionSize;
    boolean fromStaticTable;
    private final AtomicLong sectionSizeTracker;
    private final DynamicTable dynamicTable;

    FieldLineReader(DynamicTable dynamicTable, long maxSectionSize, AtomicLong sectionSizeTracker) {
        this.maxSectionSize = maxSectionSize;
        this.sectionSizeTracker = sectionSizeTracker;
        this.dynamicTable = dynamicTable;
    }

    abstract void reset();
    abstract void configure(int b);
    abstract boolean read(ByteBuffer input, FieldSectionPrefix prefix,
                          DecodingCallback action);

    final void checkSectionSize(long fieldSize) {
        long sectionSize = sectionSizeTracker.addAndGet(fieldSize);
        if (maxSectionSize > 0 && sectionSize > maxSectionSize) {
            throw maxFieldSectionExceeded(sectionSize, maxSectionSize);
        }
    }

    final void checkPartialSize(long partialFieldSize) {
        long sectionSize = sectionSizeTracker.get() + partialFieldSize;
        if (maxSectionSize > 0 && sectionSize  > maxSectionSize) {
            throw maxFieldSectionExceeded(sectionSize, maxSectionSize);
        }
    }

    final int getMaxFieldLineLimit(int partiallyRead) {
        int maxLimit = -1;
        if (maxSectionSize > 0) {
            maxLimit = Math.clamp(maxSectionSize - partiallyRead - 32 -
                                  sectionSizeTracker.get(), 0, Integer.MAX_VALUE);
        }
        return maxLimit;
    }

    final int getMaxFieldLineLimit() {
        return getMaxFieldLineLimit(0);
    }

    private static QPackException maxFieldSectionExceeded(long sectionSize, long maxSize) {
        throw QPackException.decompressionFailed(
                new ProtocolException("Size exceeds MAX_FIELD_SECTION_SIZE: %s > %s"
                        .formatted(sectionSize, maxSize)), false);
    }

    /**
     * Checks if the decoder encounters a reference in a field line representation to
     * a dynamic table entry that has already been evicted or that has an absolute index
     * greater than or equal to the declared Required Insert Count (Section 4.5.1),
     * it MUST treat this as a connection error of type QPACK_DECOMPRESSION_FAILED.
     * @param absoluteIndex dynamic table absolute index
     * @param prefix field line section prefix
     */
    void checkEntryIndex(long absoluteIndex, FieldSectionPrefix prefix) {
        if (!fromStaticTable && absoluteIndex >= prefix.requiredInsertCount()) {
            throw QPackException.decompressionFailed(
                    new IOException("header index is greater than RIC"), true);
        }
    }

    /**
     * Return a header field entry for the specified entry index. The table type
     * is selected according to the {@code fromStaticTable} value.
     * @param index absolute index of the table entry.
     * @return a header field corresponding to the specified entry
     */
    final HeaderField entryAtIndex(long index) {
        HeaderField f;
        try {
            if (fromStaticTable) {
                f = StaticTable.HTTP3.get(index);
            } else {
                assert dynamicTable != null;
                f = dynamicTable.get(index);
            }
        } catch (IndexOutOfBoundsException | IllegalStateException | IllegalArgumentException e) {
            throw QPackException.decompressionFailed(
                    new IOException("header fields table index", e), true);
        }
        return f;
    }
}
