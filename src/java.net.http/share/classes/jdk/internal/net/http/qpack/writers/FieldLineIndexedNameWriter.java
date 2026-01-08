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

final class FieldLineIndexedNameWriter implements BinaryRepresentationWriter {
    private int state = NEW;
    private final QPACK.Logger logger;
    private final IntegerWriter intWriter = new IntegerWriter();
    private final StringWriter valueWriter = new StringWriter();
    private static final int NEW               = 0;
    private static final int NAME_PART_WRITTEN = 1;
    private static final int VALUE_WRITTEN     = 2;

    FieldLineIndexedNameWriter(QPACK.Logger logger) {
        this.logger = logger;
    }

    public BinaryRepresentationWriter configure(TableEntry e, boolean hideIntermediary, long base)
            throws IndexOutOfBoundsException {
        return e.isStaticTable() ? configureStatic(e, hideIntermediary) :
                                   configureDynamic(e, hideIntermediary, base);
    }

    private BinaryRepresentationWriter configureStatic(TableEntry e, boolean hideIntermediary)
            throws IndexOutOfBoundsException {
        if (logger.isLoggable(EXTRA)) {
            logger.log(EXTRA, () -> format(
                    "Field Line With Static Table Name Reference" +
                            " (%s, '%s', huffman=%b, hideIntermediary=%b)",
                    e.index(), e.value(), e.huffmanValue(), hideIntermediary));
        }
        return this.staticIndex(e.index(), hideIntermediary).value(e);
    }

    private BinaryRepresentationWriter configureDynamic(TableEntry e, boolean hideIntermediary, long base)
            throws IndexOutOfBoundsException {
        boolean usePostBase = e.index() >= base;
        long index = usePostBase ? e.index() - base : base - 1 - e.index();
        if (logger.isLoggable(EXTRA)) {
            logger.log(EXTRA, () -> format(
                    "Field Line With %s Dynamic Table Name Reference" +
                            " (%s, '%s', huffman=%b, hideIntermediary=%b)",
                    usePostBase ? "Post-Base" : "", index, e.value(), e.huffmanValue(),
                    hideIntermediary));
        }
        if (usePostBase) {
            return this.dynamicPostBaseIndex(index, hideIntermediary).value(e);
        } else {
            return this.dynamicIndex(index, hideIntermediary).value(e);
        }
    }

    @Override
    public boolean write(ByteBuffer destination) {
        if (state < NAME_PART_WRITTEN) {
            if (!intWriter.write(destination)) {
                return false;
            }
            state = NAME_PART_WRITTEN;
        }
        if (state < VALUE_WRITTEN) {
            if (!valueWriter.write(destination)) {
                return false;
            }
            state = VALUE_WRITTEN;
        }
        return state == VALUE_WRITTEN;
    }

    @Override
    public FieldLineIndexedNameWriter reset() {
        intWriter.reset();
        valueWriter.reset();
        state = NEW;
        return this;
    }

    private FieldLineIndexedNameWriter staticIndex(long absoluteIndex, boolean hideIntermediary) {
        int payload = 0b0101_0000;
        if (hideIntermediary) {
            payload |= 0b0010_0000;
        }
        intWriter.configure(absoluteIndex, 4, payload);
        return this;
    }

    private FieldLineIndexedNameWriter dynamicIndex(long relativeIndex, boolean hideIntermediary) {
        int payload = 0b0100_0000;
        if (hideIntermediary) {
            payload |= 0b0010_0000;
        }
        intWriter.configure(relativeIndex, 4, payload);
        return this;
    }

    private FieldLineIndexedNameWriter dynamicPostBaseIndex(long relativeIndex, boolean hideIntermediary) {
        int payload = 0b0000_0000;
        if (hideIntermediary) {
            payload |= 0b0000_1000;
        }
        intWriter.configure(relativeIndex, 3, payload);
        return this;
    }

    private FieldLineIndexedNameWriter value(TableEntry e) {
        int N = 7;
        int payload = 0b0000_0000;
        if (e.huffmanValue()) {
            payload |= 0b1000_0000;
        }
        valueWriter.configure(e.value(), N, payload, e.huffmanValue());
        return this;
    }
}
