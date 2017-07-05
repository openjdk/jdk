/*
 * Copyright (c) 2004, 2011, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.xml.internal.fastinfoset.QualifiedName;
import com.sun.xml.internal.fastinfoset.CommonResourceBundle;

public class QualifiedNameArray extends ValueArray {

    public QualifiedName[] _array;

    private QualifiedNameArray _readOnlyArray;

    public QualifiedNameArray(int initialCapacity, int maximumCapacity) {
        _array = new QualifiedName[initialCapacity];
        _maximumCapacity = maximumCapacity;
    }

    public QualifiedNameArray() {
        this(DEFAULT_CAPACITY, MAXIMUM_CAPACITY);
    }

    public final void clear() {
        _size = _readOnlyArraySize;
    }

    /**
     * Returns cloned version of internal QualifiedName[].
     * @return cloned version of internal QualifiedName[].
     */
    public final QualifiedName[] getArray() {
        if (_array == null) return null;

        final QualifiedName[] clonedArray = new QualifiedName[_array.length];
        System.arraycopy(_array, 0, clonedArray, 0, _array.length);
        return clonedArray;
    }

    public final void setReadOnlyArray(ValueArray readOnlyArray, boolean clear) {
        if (!(readOnlyArray instanceof QualifiedNameArray)) {
            throw new IllegalArgumentException(CommonResourceBundle.getInstance().
                    getString("message.illegalClass", new Object[]{readOnlyArray}));
        }

        setReadOnlyArray((QualifiedNameArray)readOnlyArray, clear);
    }

    public final void setReadOnlyArray(QualifiedNameArray readOnlyArray, boolean clear) {
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

    public final QualifiedName[] getCompleteArray() {
        if (_readOnlyArray == null) {
            // Return cloned version of internal _array
            return getArray();
//            return _array;
        } else {
            final QualifiedName[] ra = _readOnlyArray.getCompleteArray();
            final QualifiedName[] a = new QualifiedName[_readOnlyArraySize + _array.length];
            System.arraycopy(ra, 0, a, 0, _readOnlyArraySize);
            return a;
        }
    }

    public final QualifiedName getNext() {
        return (_size == _array.length) ? null : _array[_size];
    }

    public final void add(QualifiedName s) {
        if (_size == _array.length) {
            resize();
        }

       _array[_size++] = s;
    }

    protected final void resize() {
        if (_size == _maximumCapacity) {
            throw new ValueArrayResourceException(CommonResourceBundle.getInstance().getString("message.arrayMaxCapacity"));
        }

        int newSize = _size * 3 / 2 + 1;
        if (newSize > _maximumCapacity) {
            newSize = _maximumCapacity;
        }

        final QualifiedName[] newArray = new QualifiedName[newSize];
        System.arraycopy(_array, 0, newArray, 0, _size);
        _array = newArray;
    }

}
