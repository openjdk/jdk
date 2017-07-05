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

import static jdk.nashorn.internal.lookup.Lookup.MH;
import java.lang.invoke.MethodHandle;
import java.nio.Buffer;
import jdk.dynalink.CallSiteDescriptor;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.LinkRequest;
import jdk.nashorn.internal.lookup.Lookup;

/**
 * The superclass of all ArrayData used by TypedArrays
 *
 * @param <T> buffer implementation
 */
public abstract class TypedArrayData<T extends Buffer> extends ContinuousArrayData {

    /** wrapped native buffer */
    protected final T nb;

    /**
     * Constructor
     * @param nb wrapped native buffer
     * @param elementLength length in elements
     */
    protected TypedArrayData(final T nb, final int elementLength) {
        super(elementLength); //TODO is this right?
        this.nb = nb;
    }

    /**
     * Length in number of elements. Accessed from {@code ArrayBufferView}
     * @return element length
     */
    public final int getElementLength() {
        return (int)length();
    }

    /**
     * Is this an unsigned array data?
     * @return true if unsigned
     */
    public boolean isUnsigned() {
        return false;
    }

    /**
     * Is this a clamped array data?
     * @return true if clamped
     */
    public boolean isClamped() {
        return false;
    }

    @Override
    public boolean canDelete(final int index, final boolean strict) {
        return false;
    }

    @Override
    public boolean canDelete(final long longIndex, final boolean strict) {
        return false;
    }

    @Override
    public TypedArrayData<T> copy() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object[] asObjectArray() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ArrayData shiftLeft(final int by) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ArrayData shiftRight(final int by) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ArrayData ensure(final long safeIndex) {
        return this;
    }

    @Override
    public ArrayData shrink(final long newLength) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final boolean has(final int index) {
        return 0 <= index && index < length();
    }

    @Override
    public ArrayData delete(final int index) {
        return this;
    }

    @Override
    public ArrayData delete(final long fromIndex, final long toIndex) {
        return this;
    }

    @Override
    public TypedArrayData<T> convert(final Class<?> type) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object pop() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ArrayData slice(final long from, final long to) {
        throw new UnsupportedOperationException();
    }

    /**
     * Element getter method handle
     * @return getter
     */
    protected abstract MethodHandle getGetElem();

    /**
     * Element setter method handle
     * @return setter
     */
    protected abstract MethodHandle getSetElem();

    @Override
    public MethodHandle getElementGetter(final Class<?> returnType, final int programPoint) {
        final MethodHandle getter = getContinuousElementGetter(getClass(), getGetElem(), returnType, programPoint);
        if (getter != null) {
            return Lookup.filterReturnType(getter, returnType);
        }
        return getter;
    }

    @Override
    public MethodHandle getElementSetter(final Class<?> elementType) {
        return getContinuousElementSetter(getClass(), Lookup.filterArgumentType(getSetElem(), 2, elementType), elementType);
    }

    @Override
    protected MethodHandle getContinuousElementSetter(final Class<? extends ContinuousArrayData> clazz, final MethodHandle setHas, final Class<?> elementType) {
        final MethodHandle mh = Lookup.filterArgumentType(setHas, 2, elementType);
        return MH.asType(mh, mh.type().changeParameterType(0, clazz));
    }

    @Override
    public GuardedInvocation findFastGetIndexMethod(final Class<? extends ArrayData> clazz, final CallSiteDescriptor desc, final LinkRequest request) {
        final GuardedInvocation inv = super.findFastGetIndexMethod(clazz, desc, request);

        if (inv != null) {
            return inv;
        }

        return null;
    }

    @Override
    public GuardedInvocation findFastSetIndexMethod(final Class<? extends ArrayData> clazz, final CallSiteDescriptor desc, final LinkRequest request) { // array, index, value
        final GuardedInvocation inv = super.findFastSetIndexMethod(clazz, desc, request);

        if (inv != null) {
            return inv;
        }

        return null;
    }

}
