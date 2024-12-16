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

import jdk.internal.net.http.qpack.DecodingCallback;
import jdk.internal.net.http.qpack.FieldSectionPrefix;
import jdk.internal.net.http.qpack.QPackException;

import java.net.ProtocolException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

public sealed abstract class FieldLineReader permits FieldLineIndexedPostBaseReader,
        FieldLineIndexedReader, FieldLineLiteralsReader, FieldLineNameRefPostBaseReader,
        FieldLineNameReferenceReader {

    private final long maxSectionSize;
    private final AtomicLong sectionSizeTracker;
    FieldLineReader(long maxSectionSize, AtomicLong sectionSizeTracker) {
        this.maxSectionSize = maxSectionSize;
        this.sectionSizeTracker = sectionSizeTracker;
    }

    abstract void reset();
    abstract void configure(int b);
    abstract boolean read(ByteBuffer input, FieldSectionPrefix prefix,
                          DecodingCallback action);

    final void checkSectionSize(long fieldSize) {
        long sectionSize = sectionSizeTracker.addAndGet(fieldSize);
        if (maxSectionSize > 0 &&  sectionSize > maxSectionSize) {
            throw QPackException.decompressionFailed(
                    new ProtocolException("Size exceeds MAX_FIELD_SECTION_SIZE: %s > %s"
                            .formatted(sectionSize, maxSectionSize)), false);
        }
    }
}
