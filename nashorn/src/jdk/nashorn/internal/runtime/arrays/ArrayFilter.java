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

import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.Undefined;
import jdk.nashorn.internal.runtime.linker.Bootstrap;

/**
 * Base class for array filters. Implements all core routines so that the
 * filter only has to implement those needed.
 */
abstract class ArrayFilter extends ArrayData {
    /** Underlying array. */
    protected ArrayData underlying;

    ArrayFilter(final ArrayData underlying) {
        super(underlying.length());
        this.underlying = underlying;
    }

    /**
     * Get the underlying {@link ArrayData} that this filter wraps
     * @return array data
     */
    protected ArrayData getUnderlying() {
        return underlying;
    }

    @Override
    public void setLength(final long length) {
        super.setLength(length);
        underlying.setLength(length);
    }

    @Override
    public Object[] asObjectArray() {
        return underlying.asObjectArray();
    }

    @Override
    public Object asArrayOfType(final Class<?> componentType) {
        return underlying.asArrayOfType(componentType);
    }

    @Override
    public void shiftLeft(final int by) {
        underlying.shiftLeft(by);
        setLength(underlying.length());
    }

    @Override
    public ArrayData shiftRight(final int by) {
        underlying = underlying.shiftRight(by);
        setLength(underlying.length());

        return this;
    }

    @Override
    public ArrayData ensure(final long safeIndex) {
        underlying = underlying.ensure(safeIndex);
        setLength(underlying.length());

        return this;
    }

    @Override
    public ArrayData shrink(final long newLength) {
        underlying = underlying.shrink(newLength);
        setLength(underlying.length());

        return this;
    }

    @Override
    public ArrayData set(final int index, final Object value, final boolean strict) {
        underlying = underlying.set(index, value, strict);
        setLength(underlying.length());

        return this;
    }

    @Override
    public ArrayData set(final int index, final int value, final boolean strict) {
        underlying = underlying.set(index, value, strict);
        setLength(underlying.length());

        return this;
    }

    @Override
    public ArrayData set(final int index, final long value, final boolean strict) {
        underlying = underlying.set(index, value, strict);
        setLength(underlying.length());

        return this;
    }

    @Override
    public ArrayData set(final int index, final double value, final boolean strict) {
        underlying = underlying.set(index, value, strict);
        setLength(underlying.length());

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
        return underlying.getInt(index);
    }

    @Override
    public long getLong(final int index) {
        return underlying.getLong(index);
    }

    @Override
    public double getDouble(final int index) {
        return underlying.getDouble(index);
    }

    @Override
    public Object getObject(final int index) {
        return underlying.getObject(index);
    }

    @Override
    public boolean has(final int index) {
        return underlying.has(index);
    }

    @Override
    public ArrayData delete(final int index) {
        underlying = underlying.delete(index);
        setLength(underlying.length());
        return this;
    }

    @Override
    public ArrayData delete(final long from, final long to) {
        underlying = underlying.delete(from, to);
        setLength(underlying.length());
        return this;
    }

    @Override
    protected ArrayData convert(final Class<?> type) {
        underlying = underlying.convert(type);
        setLength(underlying.length());
        return this;
    }

    @Override
    public Object pop() {
        final Object value = underlying.pop();
        setLength(underlying.length());

        return value;
    }

    @Override
    public long nextIndex(final long index) {
        return underlying.nextIndex(index);
    }

    static Object convertUndefinedValue(final Class<?> targetType) {
        return invoke(Bootstrap.getLinkerServices().getTypeConverter(Undefined.class, targetType),
                ScriptRuntime.UNDEFINED);
    }
}
