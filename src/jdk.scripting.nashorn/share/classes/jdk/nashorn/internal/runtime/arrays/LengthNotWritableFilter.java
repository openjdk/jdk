/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.runtime.arrays;

import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.ScriptRuntime;

/**
 * Filter to use for ArrayData where the length is not writable.
 * The default behavior is just to ignore {@link ArrayData#setLength}
 */
final class LengthNotWritableFilter extends ArrayFilter {
    private final SortedMap<Long, Object> extraElements; //elements with index >= length

    /**
     * Constructor
     * @param underlying array
     */
    LengthNotWritableFilter(final ArrayData underlying) {
        this(underlying, new TreeMap<Long, Object>());
    }

    private LengthNotWritableFilter(final ArrayData underlying, final SortedMap<Long, Object> extraElements) {
        super(underlying);
        this.extraElements = extraElements;
    }

    @Override
    public ArrayData copy() {
        return new LengthNotWritableFilter(underlying.copy(), new TreeMap<>(extraElements));
    }

    @Override
    public boolean has(final int index) {
        return super.has(index) || extraElements.containsKey((long)index);
    }

    /**
     * Set the length of the data array
     *
     * @param length the new length for the data array
     */
    @Override
    public void setLength(final long length) {
        //empty - setting length for a LengthNotWritableFilter is always a nop
    }

    @Override
    public ArrayData ensure(final long index) {
        return this;
    }

    @Override
    public ArrayData slice(final long from, final long to) {
        //return array[from...to), or array[from...length] if undefined, in this case not as we are an ArrayData
        return new LengthNotWritableFilter(underlying.slice(from, to), extraElements.subMap(from, to));
    }

    private boolean checkAdd(final long index, final Object value) {
        if (index >= length()) {
            extraElements.put(index, value);
            return true;
        }
        return false;
    }

    private Object get(final long index) {
        final Object obj = extraElements.get(index);
        if (obj == null) {
            return ScriptRuntime.UNDEFINED;
        }
        return obj;
    }

    @Override
    public int getInt(final int index) {
        if (index >= length()) {
            return JSType.toInt32(get(index));
        }
        return underlying.getInt(index);
    }

    @Override
    public int getIntOptimistic(final int index, final int programPoint) {
        if (index >= length()) {
            return JSType.toInt32Optimistic(get(index), programPoint);
        }
        return underlying.getIntOptimistic(index, programPoint);
    }

    @Override
    public double getDouble(final int index) {
        if (index >= length()) {
            return JSType.toNumber(get(index));
        }
        return underlying.getDouble(index);
    }

    @Override
    public double getDoubleOptimistic(final int index, final int programPoint) {
        if (index >= length()) {
            return JSType.toNumberOptimistic(get(index), programPoint);
        }
        return underlying.getDoubleOptimistic(index, programPoint);
    }

    @Override
    public Object getObject(final int index) {
        if (index >= length()) {
            return get(index);
        }
        return underlying.getObject(index);
    }

    @Override
    public ArrayData set(final int index, final Object value, final boolean strict) {
        if (checkAdd(index, value)) {
            return this;
        }
        underlying = underlying.set(index, value, strict);
        return this;
    }

    @Override
    public ArrayData set(final int index, final int value, final boolean strict) {
        if (checkAdd(index, value)) {
            return this;
        }
        underlying = underlying.set(index, value, strict);
        return this;
    }

    @Override
    public ArrayData set(final int index, final double value, final boolean strict) {
        if (checkAdd(index, value)) {
            return this;
        }
        underlying = underlying.set(index, value, strict);
        return this;
    }

    @Override
    public ArrayData delete(final int index) {
        extraElements.remove(ArrayIndex.toLongIndex(index));
        underlying = underlying.delete(index);
        return this;
    }

    @Override
    public ArrayData delete(final long fromIndex, final long toIndex) {
        for (final Iterator<Long> iter = extraElements.keySet().iterator(); iter.hasNext();) {
            final long next = iter.next();
            if (next >= fromIndex && next <= toIndex) {
                iter.remove();
            }
            if (next > toIndex) { //ordering guaranteed because TreeSet
                break;
            }
        }
        underlying = underlying.delete(fromIndex, toIndex);
        return this;
    }

    @Override
    public Iterator<Long> indexIterator() {
        final List<Long> keys = computeIteratorKeys();
        keys.addAll(extraElements.keySet()); //even if they are outside length this is fine
        return keys.iterator();
    }

}
