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

public class StringArray extends ValueArray {

    public String[] _array;

    private StringArray _readOnlyArray;

    private boolean _clear;

    public StringArray(int initialCapacity, int maximumCapacity, boolean clear) {
        _array = new String[initialCapacity];
        _maximumCapacity = maximumCapacity;
        _clear = clear;
    }

    public StringArray() {
        this(DEFAULT_CAPACITY, MAXIMUM_CAPACITY, false);
    }

    public final void clear() {
        if (_clear) for (int i = _readOnlyArraySize; i < _size; i++) {
            _array[i] = null;
        }
        _size = _readOnlyArraySize;
    }

    public final String[] getArray() {
        return _array;
    }

    public final void setReadOnlyArray(ValueArray readOnlyArray, boolean clear) {
        if (!(readOnlyArray instanceof StringArray)) {
            throw new IllegalArgumentException(CommonResourceBundle.getInstance().
                    getString("message.illegalClass", new Object[]{readOnlyArray}));
        }

        setReadOnlyArray((StringArray)readOnlyArray, clear);
    }

    public final void setReadOnlyArray(StringArray readOnlyArray, boolean clear) {
        if (readOnlyArray != null) {
            _readOnlyArray = readOnlyArray;
            _readOnlyArraySize = readOnlyArray.getSize();

            if (clear) {
                clear();
            }

            _array = getCompleteArray();
            _size = _readOnlyArraySize;
        }
    }

    public final String[] getCompleteArray() {
        if (_readOnlyArray == null) {
            return _array;
        } else {
            final String[] ra = _readOnlyArray.getCompleteArray();
            final String[] a = new String[_readOnlyArraySize + _array.length];
            System.arraycopy(ra, 0, a, 0, _readOnlyArraySize);
            return a;
        }
    }

    public final String get(int i) {
        return _array[i];
    }

    public final int add(String s) {
        if (_size == _array.length) {
            resize();
        }

       _array[_size++] = s;
       return _size;
    }

    protected final void resize() {
        if (_size == _maximumCapacity) {
            throw new ValueArrayResourceException(CommonResourceBundle.getInstance().getString("message.arrayMaxCapacity"));
        }

        int newSize = _size * 3 / 2 + 1;
        if (newSize > _maximumCapacity) {
            newSize = _maximumCapacity;
        }

        final String[] newArray = new String[newSize];
        System.arraycopy(_array, 0, newArray, 0, _size);
        _array = newArray;
    }
}
