/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 * THIS FILE WAS MODIFIED BY SUN MICROSYSTEMS, INC.
 */



package com.sun.xml.internal.fastinfoset.util;

import com.sun.xml.internal.fastinfoset.CommonResourceBundle;

public class FixedEntryStringIntMap extends StringIntMap {

    private Entry _fixedEntry;

    public FixedEntryStringIntMap(String fixedEntry, int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);

        // Add the fixed entry
        final int hash = hashHash(fixedEntry.hashCode());
        final int tableIndex = indexFor(hash, _table.length);
        _table[tableIndex] = _fixedEntry = new Entry(fixedEntry, hash, _index++, null);
        if (_size++ >= _threshold) {
            resize(2 * _table.length);
        }
    }

    public FixedEntryStringIntMap(String fixedEntry, int initialCapacity) {
        this(fixedEntry, initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    public FixedEntryStringIntMap(String fixedEntry) {
        this(fixedEntry, DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
    }

    public final void clear() {
        for (int i = 0; i < _table.length; i++) {
            _table[i] = null;
        }
        _lastEntry = NULL_ENTRY;

        if (_fixedEntry != null) {
            final int tableIndex = indexFor(_fixedEntry._hash, _table.length);
            _table[tableIndex] = _fixedEntry;
            _fixedEntry._next = null;
            _size = 1;
            _index = _readOnlyMapSize + 1;
        } else {
            _size = 0;
            _index = _readOnlyMapSize;
        }
    }

    public final void setReadOnlyMap(KeyIntMap readOnlyMap, boolean clear) {
        if (!(readOnlyMap instanceof FixedEntryStringIntMap)) {
            throw new IllegalArgumentException(CommonResourceBundle.getInstance().
                    getString("message.illegalClass", new Object[]{readOnlyMap}));
        }

        setReadOnlyMap((FixedEntryStringIntMap)readOnlyMap, clear);
    }

    public final void setReadOnlyMap(FixedEntryStringIntMap readOnlyMap, boolean clear) {
        _readOnlyMap = readOnlyMap;
        if (_readOnlyMap != null) {
            readOnlyMap.removeFixedEntry();
            _readOnlyMapSize = readOnlyMap.size();
            _index = _readOnlyMapSize + _size;
            if (clear) {
                clear();
            }
        }  else {
            _readOnlyMapSize = 0;
        }
    }

    private final void removeFixedEntry() {
        if (_fixedEntry != null) {
            final int tableIndex = indexFor(_fixedEntry._hash, _table.length);
            final Entry firstEntry = _table[tableIndex];
            if (firstEntry == _fixedEntry) {
                _table[tableIndex] = _fixedEntry._next;
            } else {
                Entry previousEntry = firstEntry;
                while (previousEntry._next != _fixedEntry) {
                    previousEntry = previousEntry._next;
                }
                previousEntry._next = _fixedEntry._next;
            }

            _fixedEntry = null;
            _size--;
        }
    }
}
