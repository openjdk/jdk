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

package jdk.nashorn.internal.objects;

import static jdk.nashorn.internal.codegen.CompilerConstants.specialCall;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import jdk.nashorn.internal.objects.annotations.Attribute;
import jdk.nashorn.internal.objects.annotations.Constructor;
import jdk.nashorn.internal.objects.annotations.Function;
import jdk.nashorn.internal.objects.annotations.Property;
import jdk.nashorn.internal.objects.annotations.ScriptClass;
import jdk.nashorn.internal.objects.annotations.Where;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.arrays.ArrayData;
import jdk.nashorn.internal.runtime.arrays.TypedArrayData;

/**
 * Int16 array for the TypedArray extension
 */
@ScriptClass("Int16Array")
public final class NativeInt16Array extends ArrayBufferView {

    // initialized by nasgen
    @SuppressWarnings("unused")
    private static PropertyMap $nasgenmap$;

    /**
     * The size in bytes of each element in the array.
     */
    @Property(attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE | Attribute.NOT_CONFIGURABLE, where = Where.CONSTRUCTOR)
    public static final int BYTES_PER_ELEMENT = 2;

    private static final Factory FACTORY = new Factory(BYTES_PER_ELEMENT) {
        @Override
        public ArrayBufferView construct(final NativeArrayBuffer buffer, final int byteOffset, final int length) {
            return new NativeInt16Array(buffer, byteOffset, length);
        }

        @Override
        public Int16ArrayData createArrayData(final ByteBuffer nb, final int start, final int end) {
            return new Int16ArrayData(nb.asShortBuffer(), start, end);
        }

        @Override
        public String getClassName() {
            return "Int16Array";
        }
    };

    private static final class Int16ArrayData extends TypedArrayData<ShortBuffer> {

        private static final MethodHandle GET_ELEM = specialCall(MethodHandles.lookup(), Int16ArrayData.class, "getElem", int.class, int.class).methodHandle();
        private static final MethodHandle SET_ELEM = specialCall(MethodHandles.lookup(), Int16ArrayData.class, "setElem", void.class, int.class, int.class).methodHandle();

        private Int16ArrayData(final ShortBuffer nb, final int start, final int end) {
            super(((ShortBuffer)nb.position(start).limit(end)).slice(), end - start);
        }

        @Override
        protected MethodHandle getGetElem() {
            return GET_ELEM;
        }

        @Override
        protected MethodHandle getSetElem() {
            return SET_ELEM;
        }

        private int getElem(final int index) {
            try {
                return nb.get(index);
            } catch (final IndexOutOfBoundsException e) {
                throw new ClassCastException(); //force relink - this works for unoptimistic too
            }
        }

        private void setElem(final int index, final int elem) {
            try {
                nb.put(index, (short)elem);
            } catch (final IndexOutOfBoundsException e) {
                //swallow valid array indexes. it's ok.
                if (index < 0) {
                    throw new ClassCastException();
                }
            }
        }

        @Override
        public int getInt(final int index) {
            return getElem(index);
        }

        @Override
        public long getLong(final int index) {
            return getInt(index);
        }

        @Override
        public double getDouble(final int index) {
            return getInt(index);
        }

        @Override
        public Object getObject(final int index) {
            return getInt(index);
        }

        @Override
        public ArrayData set(final int index, final Object value, final boolean strict) {
            return set(index, JSType.toInt32(value), strict);
        }

        @Override
        public ArrayData set(final int index, final int value, final boolean strict) {
            setElem(index, value);
            return this;
        }

        @Override
        public ArrayData set(final int index, final long value, final boolean strict) {
            return set(index, (int)value, strict);
        }

        @Override
        public ArrayData set(final int index, final double value, final boolean strict) {
            return set(index, (int)value, strict);
        }
    }

    /**
     * Constructor
     *
     * @param newObj is this typed array instantiated with the new operator
     * @param self   self reference
     * @param args   args
     *
     * @return new typed array
     */
    @Constructor(arity = 1)
    public static NativeInt16Array constructor(final boolean newObj, final Object self, final Object... args) {
        return (NativeInt16Array)constructorImpl(newObj, args, FACTORY);
    }

    NativeInt16Array(final NativeArrayBuffer buffer, final int byteOffset, final int byteLength) {
        super(buffer, byteOffset, byteLength);
    }

    @Override
    protected Factory factory() {
        return FACTORY;
    }

    /**
     * Set values
     * @param self   self reference
     * @param array  multiple values of array's type to set
     * @param offset optional start index, interpreted  0 if undefined
     * @return undefined
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    protected static Object set(final Object self, final Object array, final Object offset) {
        return ArrayBufferView.setImpl(self, array, offset);
    }

    /**
     * Returns a new TypedArray view of the ArrayBuffer store for this TypedArray,
     * referencing the elements at begin, inclusive, up to end, exclusive. If either
     * begin or end is negative, it refers to an index from the end of the array,
     * as opposed to from the beginning.
     * <p>
     * If end is unspecified, the subarray contains all elements from begin to the end
     * of the TypedArray. The range specified by the begin and end values is clamped to
     * the valid index range for the current array. If the computed length of the new
     * TypedArray would be negative, it is clamped to zero.
     * <p>
     * The returned TypedArray will be of the same type as the array on which this
     * method is invoked.
     *
     * @param self self reference
     * @param begin begin position
     * @param end end position
     *
     * @return sub array
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    protected static NativeInt16Array subarray(final Object self, final Object begin, final Object end) {
        return (NativeInt16Array)ArrayBufferView.subarrayImpl(self, begin, end);
    }

    @Override
    protected ScriptObject getPrototype(final Global global) {
        return global.getInt16ArrayPrototype();
    }
}
