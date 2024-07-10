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

import jdk.internal.net.http.qpack.QPACK;
import jdk.internal.net.http.qpack.TableEntry;

import java.nio.ByteBuffer;

import static java.lang.String.format;
import static jdk.internal.net.http.qpack.QPACK.Logger.Level.EXTRA;

final class FieldLineIndexedWriter implements BinaryRepresentationWriter {
    private final QPACK.Logger logger;
    private final IntegerWriter intWriter = new IntegerWriter();

    public FieldLineIndexedWriter(QPACK.Logger logger) {
        this.logger = logger;
    }

    public BinaryRepresentationWriter configure(TableEntry e, long base) {
        return e.isStaticTable() ? configureStatic(e) : configureDynamic(e, base);
    }

    private BinaryRepresentationWriter configureStatic(TableEntry e) {
        assert e.isStaticTable();
        if (logger.isLoggable(EXTRA)) {
            logger.log(EXTRA, () -> format("Indexed Field Line Static Table reference" +
                    " (%s, '%s', '%s')", e.index(), e.name(), e.value()));
        }
        return this.staticIndex(e.index());
    }

    private BinaryRepresentationWriter configureDynamic(TableEntry e, long base) {
        assert !e.isStaticTable();
        // RFC-9204: 3.2.6. Post-Base Indexing
        // Post-Base indices are used in field line representations for entries with absolute
        //  indices greater than or equal to Base, starting at 0 for the entry with absolute index
        //  equal to Base and increasing in the same direction as the absolute index.
        boolean usePostBase = e.index() >= base;
        long index = usePostBase ? e.index() - base : base - 1 - e.index();
        if (logger.isLoggable(EXTRA)) {
            logger.log(EXTRA, () -> format("Indexed Field Line Dynamic Table reference %s  (%s[%s], '%s', '%s')",
                    usePostBase ? "with Post-Base Index" : "", index, e.index(), e.name(), e.value()));
        }
        if (usePostBase) {
            return dynamicPostBaseIndex(index);
        } else {
            return dynamicIndex(index);
        }
    }

    @Override
    public boolean write(ByteBuffer destination) {
        return intWriter.write(destination);
    }

    @Override
    public BinaryRepresentationWriter reset() {
        intWriter.reset();
        return this;
    }

    private FieldLineIndexedWriter staticIndex(long absoluteIndex) {
        int N = 6;
        intWriter.configure(absoluteIndex, N, 0b1100_0000);
        return this;
    }

    private FieldLineIndexedWriter dynamicIndex(long relativeIndex) {
        assert relativeIndex >= 0;
        int N = 6;
        intWriter.configure(relativeIndex, N, 0b1000_0000);
        return this;
    }

    private FieldLineIndexedWriter dynamicPostBaseIndex(long relativeIndex) {
        assert relativeIndex >= 0;
        int N = 4;
        intWriter.configure(relativeIndex, N, 0b0001_0000);
        return this;
    }
}
