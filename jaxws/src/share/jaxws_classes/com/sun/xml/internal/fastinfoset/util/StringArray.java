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

    /**
     * Returns cloned version of internal String[].
     * @return cloned version of internal String[].
     */
    public final String[] getArray() {
        if (_array == null) return null;

        final String[] clonedArray = new String[_array.length];
        System.arraycopy(_array, 0, clonedArray, 0, _array.length);
        return clonedArray;
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
            // Return cloned version of internal _array
            return getArray();
//            return _array;
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
