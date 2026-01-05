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

package jdk.internal.net.http.qpack;

import jdk.internal.net.http.hpack.QuickHuffman;

// Record containing table information for entry
public record TableEntry(boolean isStaticTable, long index, CharSequence name, CharSequence value,
                         EntryType type, boolean huffmanName, boolean huffmanValue) {

    public TableEntry(boolean isStaticTable, long index, CharSequence name, CharSequence value, EntryType type) {
        this(isStaticTable, index, name, value, type,
                isHuffmanBetterFor(name, true, type),
                isHuffmanBetterFor(value, false, type));
    }

    public TableEntry toNewDynamicTableEntry(long index) {
        return new TableEntry(false, index, name, value, EntryType.NAME_VALUE);
    }

    public TableEntry relativizeDynamicTableEntry(long relativeIndex) {
        assert !isStaticTable;
        assert relativeIndex >= 0;
        return new TableEntry(false, relativeIndex, name, value, type);
    }

    public TableEntry(CharSequence name, CharSequence value) {
        this(false, -1L, name, value, EntryType.NEITHER,
                isHuffmanBetterFor(name, true, EntryType.NEITHER),
                isHuffmanBetterFor(value, false, EntryType.NEITHER));
    }

    public TableEntry toLiteralsEntry() {
        return new TableEntry(name, value);
    }

    /**
     * EntryType describes the type of TableEntry as either:
     * <p>
     * - NAME_VALUE:    a table entry where both name and value exist in table
     * - NAME: a table entry where only name is present in table
     * - NEITHER:  a table entry where neither name nor value have been found
     */
    public enum EntryType {NAME_VALUE, NAME, NEITHER}

    static boolean isHuffmanBetterFor(CharSequence str, boolean isName, EntryType type) {
        return switch (type) {
            case NEITHER -> QuickHuffman.isHuffmanBetterFor(str);
            case NAME_VALUE -> false;
            case NAME -> !isName && QuickHuffman.isHuffmanBetterFor(str);
        };
    }
}
