/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.ScriptRuntime;

/**
 * Handle arrays where the index is very large.
 */
class SparseArrayData extends ArrayData {
    static final long MAX_DENSE_LENGTH = 512 * 1024;

    /** Underlying array. */
    private ArrayData underlying;

    /** Maximum length to be stored in the array. */
    private final long maxDenseLength;

    /** Sparse elements. */
    private TreeMap<Long, Object> sparseMap;

    SparseArrayData(final ArrayData underlying, final long length) {
        this(underlying, length, new TreeMap<Long, Object>());
    }

    SparseArrayData(final ArrayData underlying, final long length, final TreeMap<Long, Object> sparseMap) {
        super(length);
        assert underlying.length() <= length;
        this.underlying = underlying;
        this.maxDenseLength = Math.max(MAX_DENSE_LENGTH, underlying.length());
        this.sparseMap = sparseMap;
    }

    @Override
    public ArrayData copy() {
        return new SparseArrayData(underlying.copy(), length(), new TreeMap<>(sparseMap));
    }

    @Override
    public Object[] asObjectArray() {
        final int length = (int) Math.min(length(), Integer.MAX_VALUE);
        final int underlyingLength = (int) Math.min(length, underlying.length());
        final Object[] objArray = new Object[length];

        for (int i = 0; i < underlyingLength; i++) {
            objArray[i] = underlying.getObject(i);
        }

        Arrays.fill(objArray, underlyingLength, length, ScriptRuntime.UNDEFINED);

        for (final Map.Entry<Long, Object> entry : sparseMap.entrySet()) {
            final long key = entry.getKey();
            if (key <= Integer.MAX_VALUE) {
                objArray[(int)key] = entry.getValue();
            } else {
                break; // ascending key order
            }
        }

        return objArray;
    }

    @Override
    public void shiftLeft(final int by) {
        underlying.shiftLeft(by);

        final TreeMap<Long, Object> newSparseMap = new TreeMap<>();

        for (final Map.Entry<Long, Object> entry : sparseMap.entrySet()) {
            final long newIndex = entry.getKey().longValue() - by;
            if (newIndex < maxDenseLength) {
                underlying = underlying.set((int) newIndex, entry.getValue(), false);
            } else if (newIndex >= 0) {
                newSparseMap.put(Long.valueOf(newIndex), entry.getValue());
            }
        }

        sparseMap = newSparseMap;
        setLength(Math.max(length() - by, 0));
    }

    @Override
    public ArrayData shiftRight(final int by) {
        final TreeMap<Long, Object> newSparseMap = new TreeMap<>();
        if (underlying.length() + by > maxDenseLength) {
            for (long i = maxDenseLength - by; i < underlying.length(); i++) {
                if (underlying.has((int) i)) {
                    newSparseMap.put(Long.valueOf(i + by), underlying.getObject((int) i));
                }
            }
            underlying = underlying.shrink((int) (maxDenseLength - by));
        }

        underlying.shiftRight(by);

        for (final Map.Entry<Long, Object> entry : sparseMap.entrySet()) {
            final long newIndex = entry.getKey().longValue() + by;
            newSparseMap.put(Long.valueOf(newIndex), entry.getValue());
        }

        sparseMap = newSparseMap;
        setLength(length() + by);

        return this;
    }

    @Override
    public ArrayData ensure(final long safeIndex) {
        if (safeIndex < maxDenseLength && underlying.length() <= safeIndex) {
            underlying = underlying.ensure(safeIndex);
        }
        setLength(Math.max(safeIndex + 1, length()));
        return this;
    }

    @Override
    public ArrayData shrink(final long newLength) {
        if (newLength < underlying.length()) {
            underlying = underlying.shrink(newLength);
            underlying.setLength(newLength);
            sparseMap.clear();
            setLength(newLength);
        }

        sparseMap.subMap(Long.valueOf(newLength), Long.MAX_VALUE).clear();
        setLength(newLength);
        return this;
    }

    @Override
    public ArrayData set(final int index, final Object value, final boolean strict) {
        if (index >= 0 && index < maxDenseLength) {
            ensure(index);
            underlying = underlying.set(index, value, strict);
            setLength(Math.max(underlying.length(), length()));
        } else {
            sparseMap.put(indexToKey(index), value);
            setLength(Math.max(index + 1, length()));
        }

        return this;
    }

    @Override
    public ArrayData set(final int index, final int value, final boolean strict) {
        if (index >= 0 && index < maxDenseLength) {
            ensure(index);
            underlying = underlying.set(index, value, strict);
            setLength(Math.max(underlying.length(), length()));
        } else {
            sparseMap.put(indexToKey(index), value);
            setLength(Math.max(index + 1, length()));
        }
        return this;
    }

    @Override
    public ArrayData set(final int index, final long value, final boolean strict) {
        if (index >= 0 && index < maxDenseLength) {
            ensure(index);
            underlying = underlying.set(index, value, strict);
            setLength(Math.max(underlying.length(), length()));
        } else {
            sparseMap.put(indexToKey(index), value);
            setLength(Math.max(index + 1, length()));
        }
        return this;
    }

    @Override
    public ArrayData set(final int index, final double value, final boolean strict) {
        if (index >= 0 && index < maxDenseLength) {
            ensure(index);
            underlying = underlying.set(index, value, strict);
            setLength(Math.max(underlying.length(), length()));
        } else {
            sparseMap.put(indexToKey(index), value);
            setLength(Math.max(index + 1, length()));
        }
        return this;
    }

    @Override
    public ArrayData setEmpty(final int index) {
        underlying.setEmpty(index);
        return this;
    }

    @Override
    public ArrayData setEmpty(final long lo, final long hi) {
        underlying.setEmpty(lo, hi);
        return this;
    }

    @Override
    public int getInt(final int index) {
        if (index >= 0 && index < maxDenseLength) {
            return underlying.getInt(index);
        }
        return JSType.toInt32(sparseMap.get(indexToKey(index)));
    }

    @Override
    public long getLong(final int index) {
        if (index >= 0 && index < maxDenseLength) {
            return underlying.getLong(index);
        }
        return JSType.toLong(sparseMap.get(indexToKey(index)));
    }

    @Override
    public double getDouble(final int index) {
        if (index >= 0 && index < maxDenseLength) {
            return underlying.getDouble(index);
        }
        return JSType.toNumber(sparseMap.get(indexToKey(index)));
    }

    @Override
    public Object getObject(final int index) {
        if (index >= 0 && index < maxDenseLength) {
            return underlying.getObject(index);
        }

        final Long key = indexToKey(index);
        if (sparseMap.containsKey(key)) {
            return sparseMap.get(key);
        }

        return ScriptRuntime.UNDEFINED;
    }

    @Override
    public boolean has(final int index) {
        if (index >= 0 && index < maxDenseLength) {
            return index < underlying.length() && underlying.has(index);
        }

        return sparseMap.containsKey(indexToKey(index));
    }

    @Override
    public ArrayData delete(final int index) {
        if (index >= 0 && index < maxDenseLength) {
            if (index < underlying.length()) {
                underlying = underlying.delete(index);
            }
        } else {
            sparseMap.remove(indexToKey(index));
        }

        return this;
    }

    @Override
    public ArrayData delete(final long fromIndex, final long toIndex) {
        if (fromIndex < maxDenseLength && fromIndex < underlying.length()) {
            underlying = underlying.delete(fromIndex, Math.min(toIndex, underlying.length() - 1));
        }
        if (toIndex >= maxDenseLength) {
            sparseMap.subMap(fromIndex, true, toIndex, true).clear();
        }
        return this;
    }

    private static Long indexToKey(final int index) {
        return Long.valueOf(index & JSType.MAX_UINT);
    }

    @Override
    protected ArrayData convert(final Class<?> type) {
        underlying = underlying.convert(type);
        return this;
    }

    @Override
    public Object pop() {
        if (length() == 0) {
            return ScriptRuntime.UNDEFINED;
        }
        if (length() == underlying.length()) {
            final Object result = underlying.pop();
            setLength(underlying.length());
            return result;
        }
        setLength(length() - 1);
        final Long key = Long.valueOf(length());
        return sparseMap.containsKey(key) ? sparseMap.remove(key) : ScriptRuntime.UNDEFINED;
    }

    @Override
    public ArrayData slice(final long from, final long to) {
        assert to <= length();
        final long start = from < 0 ? (from + length()) : from;
        final long newLength = to - start;

        if (start >= 0 && to <= maxDenseLength) {
            if (newLength <= underlying.length()) {
                return underlying.slice(from, to);
            }
            return underlying.slice(from, to).ensure(newLength - 1).delete(underlying.length(), newLength);
        }

        ArrayData sliced = EMPTY_ARRAY;
        sliced = sliced.ensure(newLength - 1);
        for (long i = start; i < to; i = nextIndex(i)) {
            if (has((int)i)) {
                sliced = sliced.set((int)(i - start), getObject((int)i), false);
            }
        }
        assert sliced.length() == newLength;
        return sliced;
    }

    @Override
    public long nextIndex(final long index) {
        if (index < underlying.length() - 1) {
            return underlying.nextIndex(index);
        }

        final Long nextKey = sparseMap.higherKey(index);
        if (nextKey != null) {
            return nextKey;
        }
        return length();
    }
}
