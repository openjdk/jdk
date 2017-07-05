/*
 * Copyright (c) 2004, 2012, Oracle and/or its affiliates. All rights reserved.
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
 *
 * THIS FILE WAS MODIFIED BY SUN MICROSYSTEMS, INC.
 */

package com.sun.xml.internal.fastinfoset.util;

import com.sun.xml.internal.fastinfoset.CommonResourceBundle;

public class CharArrayIntMap extends KeyIntMap {

    private CharArrayIntMap _readOnlyMap;

    // Total character count of Map
    protected int _totalCharacterCount;

    static class Entry extends BaseEntry {
        final char[] _ch;
        final int _start;
        final int _length;
        Entry _next;

        public Entry(char[] ch, int start, int length, int hash, int value, Entry next) {
            super(hash, value);
            _ch = ch;
            _start = start;
            _length = length;
            _next = next;
        }

        public final boolean equalsCharArray(char[] ch, int start, int length) {
            if (_length == length) {
                int n = _length;
                int i = _start;
                int j = start;
                while (n-- != 0) {
                    if (_ch[i++] != ch[j++])
                        return false;
                }
                return true;
            }

            return false;
        }

    }

    private Entry[] _table;

    public CharArrayIntMap(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);

        _table = new Entry[_capacity];
    }

    public CharArrayIntMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    public CharArrayIntMap() {
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
    }

    public final void clear() {
        for (int i = 0; i < _table.length; i++) {
            _table[i] = null;
        }
        _size = 0;
        _totalCharacterCount = 0;
    }

    public final void setReadOnlyMap(KeyIntMap readOnlyMap, boolean clear) {
        if (!(readOnlyMap instanceof CharArrayIntMap)) {
            throw new IllegalArgumentException(CommonResourceBundle.getInstance().
                    getString("message.illegalClass", new Object[]{readOnlyMap}));
        }

        setReadOnlyMap((CharArrayIntMap)readOnlyMap, clear);
    }

    public final void setReadOnlyMap(CharArrayIntMap readOnlyMap, boolean clear) {
        _readOnlyMap = readOnlyMap;
        if (_readOnlyMap != null) {
            _readOnlyMapSize = _readOnlyMap.size();

            if (clear) {
                clear();
            }
        }  else {
            _readOnlyMapSize = 0;
        }
    }

    /**
     * Method returns an index of the passed character buffer in
     * <code>CharArrayIntMap</code>.
     *
     * @return index of character buffer in <code>CharArrayIntMap</code>,
     * otherwise NOT_PRESENT.
     */
    public final int get(char[] ch, int start, int length) {
        final int hash = hashHash(CharArray.hashCode(ch, start, length));
        return get(ch, start, length, hash);
    }

    /**
     * Method returns an index of the passed character buffer in
     * <code>CharArrayIntMap</code>. If character buffer is not in
     * <code>CharArrayIntMap</code> - it will be added.
     *
     * @return index of character buffer in <code>CharArrayIntMap</code>, or
     * NOT_PRESENT if character buffer was just added.
     */
    public final int obtainIndex(char[] ch, int start, int length, boolean clone) {
        final int hash = hashHash(CharArray.hashCode(ch, start, length));

        if (_readOnlyMap != null) {
            final int index = _readOnlyMap.get(ch, start, length, hash);
            if (index != -1) {
                return index;
            }
        }

        final int tableIndex = indexFor(hash, _table.length);
        for (Entry e = _table[tableIndex]; e != null; e = e._next) {
            if (e._hash == hash && e.equalsCharArray(ch, start, length)) {
                return e._value;
            }
        }

        if (clone) {
            char[] chClone = new char[length];
            System.arraycopy(ch, start, chClone, 0, length);

            ch = chClone;
            start = 0;
        }

        addEntry(ch, start, length, hash, _size + _readOnlyMapSize, tableIndex);
        return NOT_PRESENT;
    }

    public final int getTotalCharacterCount() {
        return _totalCharacterCount;
    }

    private final int get(char[] ch, int start, int length, int hash) {
        if (_readOnlyMap != null) {
            final int i = _readOnlyMap.get(ch, start, length, hash);
            if (i != -1) {
                return i;
            }
        }

        final int tableIndex = indexFor(hash, _table.length);
        for (Entry e = _table[tableIndex]; e != null; e = e._next) {
            if (e._hash == hash && e.equalsCharArray(ch, start, length)) {
                return e._value;
            }
        }

        return NOT_PRESENT;
    }

    private final void addEntry(char[] ch, int start, int length, int hash, int value, int bucketIndex) {
        Entry e = _table[bucketIndex];
        _table[bucketIndex] = new Entry(ch, start, length, hash, value, e);
        _totalCharacterCount += length;
                if (_size++ >= _threshold) {
            resize(2 * _table.length);
        }
    }

    private final void resize(int newCapacity) {
        _capacity = newCapacity;
        Entry[] oldTable = _table;
        int oldCapacity = oldTable.length;
        if (oldCapacity == MAXIMUM_CAPACITY) {
            _threshold = Integer.MAX_VALUE;
            return;
        }

        Entry[] newTable = new Entry[_capacity];
        transfer(newTable);
        _table = newTable;
        _threshold = (int)(_capacity * _loadFactor);
    }

    private final void transfer(Entry[] newTable) {
        Entry[] src = _table;
        int newCapacity = newTable.length;
        for (int j = 0; j < src.length; j++) {
            Entry e = src[j];
            if (e != null) {
                src[j] = null;
                do {
                    Entry next = e._next;
                    int i = indexFor(e._hash, newCapacity);
                    e._next = newTable[i];
                    newTable[i] = e;
                    e = next;
                } while (e != null);
            }
        }
    }
}
