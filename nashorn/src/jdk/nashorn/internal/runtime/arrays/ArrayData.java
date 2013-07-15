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

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Array;
import jdk.nashorn.internal.runtime.GlobalObject;
import jdk.nashorn.internal.runtime.PropertyDescriptor;
import jdk.nashorn.internal.runtime.linker.Bootstrap;

/**
 * ArrayData - abstraction for wrapping array elements
 */
public abstract class ArrayData {

    /** Minimum chunk size for underlying arrays */
    protected static final int CHUNK_SIZE = 16;

    /** Mask for getting a chunk */
    protected static final int CHUNK_MASK = CHUNK_SIZE - 1;

    /**
     * Immutable empty array to get ScriptObjects started.
     */
    public static final ArrayData EMPTY_ARRAY = new NoTypeArrayData();

    /**
     * Length of the array data. Not necessarily length of the wrapped array.
     */
    private long length;

    /**
     * Constructor
     * @param length Virtual length of the array.
     */
    public ArrayData(final long length) {
        this.length = length;
    }

    /**
     * Factory method for unspecified array - start as int
     * @return ArrayData
     */
    public static ArrayData initialArray() {
        return new IntArrayData();
    }

    /**
     * Factory method for unspecified array with given length - start as int array data
     *
     * @param length the initial length
     * @return ArrayData
     */
    public static ArrayData allocate(final int length) {
        final ArrayData arrayData = new IntArrayData(length);
        return length == 0 ? arrayData : new DeletedRangeArrayFilter(arrayData, 0, length - 1);
    }

    /**
     * Factory method for unspecified given an array object
     *
     * @param  array the array
     * @return ArrayData wrapping this array
     */
    public static ArrayData allocate(final Object array) {
        final Class<?> clazz = array.getClass();

        if (clazz == int[].class) {
            return new IntArrayData((int[])array, ((int[])array).length);
        } else if (clazz == long[].class) {
            return new LongArrayData((long[])array, ((long[])array).length);
        } else if (clazz == double[].class) {
            return new NumberArrayData((double[])array, ((double[])array).length);
        } else {
            return new ObjectArrayData((Object[])array, ((Object[])array).length);
        }
    }

    /**
     * Allocate an ArrayData wrapping a given array
     *
     * @param array the array to use for initial elements
     * @return the ArrayData
     */
    public static ArrayData allocate(final int[] array) {
         return new IntArrayData(array, array.length);
    }

    /**
     * Allocate an ArrayData wrapping a given array
     *
     * @param array the array to use for initial elements
     * @return the ArrayData
     */
    public static ArrayData allocate(final long[] array) {
        return new LongArrayData(array, array.length);
    }

    /**
     * Allocate an ArrayData wrapping a given array
     *
     * @param array the array to use for initial elements
     * @return the ArrayData
     */
    public static ArrayData allocate(final double[] array) {
        return new NumberArrayData(array, array.length);
    }

    /**
     * Allocate an ArrayData wrapping a given array
     *
     * @param array the array to use for initial elements
     * @return the ArrayData
     */
    public static ArrayData allocate(final Object[] array) {
        return new ObjectArrayData(array, array.length);
    }

    /**
     * Apply a freeze filter to an ArrayData.
     *
     * @param underlying  the underlying ArrayData to wrap in the freeze filter
     * @return the frozen ArrayData
     */
    public static ArrayData freeze(final ArrayData underlying) {
        return new FrozenArrayFilter(underlying);
    }

    /**
     * Apply a seal filter to an ArrayData.
     *
     * @param underlying  the underlying ArrayData to wrap in the seal filter
     * @return the sealed ArrayData
     */
    public static ArrayData seal(final ArrayData underlying) {
        return new SealedArrayFilter(underlying);
    }

    /**
     * Return the length of the array data. This may differ from the actual
     * length of the array this wraps as length may be set or gotten as any
     * other JavaScript Property
     *
     * Even though a JavaScript array length may be a long, we only store
     * int parts for the optimized array access. For long lengths there
     * are special cases anyway.
     *
     * TODO: represent arrays with "long" lengths as a special ArrayData
     * that basically maps to the ScriptObject directly for better abstraction
     *
     * @return the length of the data
     */
    public final long length() {
        return length;
    }

    /**
     * Return a copy of the array data as an Object array.
     *
     * @return an Object array
     */
    public abstract Object[] asObjectArray();

    /**
     * Return a copy of the array data as an array of the specified type.
     *
     * @param componentType  the type of elements in the array
     * @return and array of the given type
     */
    public Object asArrayOfType(final Class<?> componentType) {
        final Object[] src = asObjectArray();
        final int l = src.length;
        final Object dst = Array.newInstance(componentType, l);
        final MethodHandle converter = Bootstrap.getLinkerServices().getTypeConverter(Object.class, componentType);
        try {
            for (int i = 0; i < src.length; i++) {
                Array.set(dst, i, invoke(converter, src[i]));
            }
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
        return dst;
    }

    /**
     * Set the length of the data array
     *
     * @param length the new length for the data array
     */
    public void setLength(final long length) {
        this.length = length;
    }

    /**
     * Shift the array data left
     *
     * TODO: explore start at an index and not at zero, to make these operations
     * even faster. Offset everything from the index. Costs memory but is probably
     * worth it
     *
     * @param by offset to shift
     */
    public abstract void shiftLeft(int by);

    /**
     * Shift the array right
     *
     * @param by offset to shift

     * @return New arraydata (or same)
     */
    public abstract ArrayData shiftRight(int by);

    /**
     * Ensure that the given index exists and won't fail subsequent
     *
     * @param safeIndex the index to ensure wont go out of bounds
     * @return new array data (or same)
     */
    public abstract ArrayData ensure(long safeIndex);

    /**
     * Shrink the array to a new length, may or may not retain the
     * inner array
     *
     * @param newLength new max length
     *
     * @return new array data (or same)
     */
    public abstract ArrayData shrink(long newLength);

    /**
     * Set an object value at a given index
     *
     * @param index the index
     * @param value the value
     * @param strict are we in strict mode
     * @return new array data (or same)
     */
    public abstract ArrayData set(int index, Object value, boolean strict);

    /**
     * Set an int value at a given index
     *
     * @param index the index
     * @param value the value
     * @param strict are we in strict mode
     * @return new array data (or same)
     */
    public abstract ArrayData set(int index, int value, boolean strict);

    /**
     * Set a long value at a given index
     *
     * @param index the index
     * @param value the value
     * @param strict are we in strict mode
     * @return new array data (or same)
     */
    public abstract ArrayData set(int index, long value, boolean strict);

    /**
     * Set an double value at a given index
     *
     * @param index the index
     * @param value the value
     * @param strict are we in strict mode
     * @return new array data (or same)
     */
    public abstract ArrayData set(int index, double value, boolean strict);

    /**
     * Set an empty value at a given index. Should only affect Object array.
     *
     * @param index the index
     * @return new array data (or same)
     */
    public ArrayData setEmpty(final int index) {
        // Do nothing.
        return this;
    }

    /**
     * Set an empty value for a given range. Should only affect Object array.
     *
     * @param lo range low end
     * @param hi range high end
     * @return new array data (or same)
     */
    public ArrayData setEmpty(final long lo, final long hi) {
        // Do nothing.
        return this;
    }

    /**
     * Get an int value from a given index
     *
     * @param index the index
     * @return the value
     */
    public abstract int getInt(int index);

    /**
     * Get a long value from a given index
     *
     * @param index the index
     * @return the value
     */
    public abstract long getLong(int index);

    /**
     * Get a double value from a given index
     *
     * @param index the index
     * @return the value
     */
    public abstract double getDouble(int index);

    /**
     * Get an Object value from a given index
     *
     * @param index the index
     * @return the value
     */
    public abstract Object getObject(int index);

    /**
     * Tests to see if an entry exists (avoids boxing.)
     * @param index the index
     * @return true if entry exists
     */
    public abstract boolean has(int index);

    /**
     * Returns if element at specific index can be deleted or not.
     *
     * @param index the index of the element
     * @param strict are we in strict mode
     *
     * @return true if element can be deleted
     */
    public boolean canDelete(final int index, final boolean strict) {
        return true;
    }

    /**
     * Returns if element at specific index range can be deleted or not.
     *
     * @param fromIndex  the start index
     * @param toIndex    the end index
     * @param strict     are we in strict mode
     *
     * @return true if range can be deleted
     */
    public boolean canDelete(final long fromIndex, final long toIndex, final boolean strict) {
        return true;
    }

    /**
     * Returns property descriptor for element at a given index
     *
     * @param global the global object
     * @param index  the index
     *
     * @return property descriptor for element
     */
    public PropertyDescriptor getDescriptor(final GlobalObject global, final int index) {
        return global.newDataDescriptor(getObject(index), true, true, true);
    }

    /**
     * Delete an array value at the given index, substituting
     * for an undefined
     *
     * @param index the index
     * @return new array data (or same)
     */
    public abstract ArrayData delete(int index);

    /**
     * Delete a given range from this array;
     *
     * @param fromIndex  from index (inclusive)
     * @param toIndex    to index (inclusive)
     *
     * @return new ArrayData after deletion
     */
    public abstract ArrayData delete(long fromIndex, long toIndex);

    /**
     * Convert the ArrayData to one with a different element type
     * Currently Arrays are not collapsed to narrower types, just to
     * wider ones. Attempting to narrow an array will assert
     *
     * @param type new element type
     * @return new array data
     */
    protected abstract ArrayData convert(Class<?> type);

    /**
     * Push an array of items to the end of the array
     *
     * @param strict are we in strict mode
     * @param items  the items
     * @return new array data (or same)
     */
    public ArrayData push(final boolean strict, final Object... items) {
        if (items.length == 0) {
            return this;
        }

        final Class<?>  widest  = widestType(items);

        ArrayData newData = convert(widest);
        long      pos     = newData.length();
        for (final Object item : items) {
            newData = newData.ensure(pos); //avoid sparse array
            newData.set((int)pos++, item, strict);
        }
        return newData;
    }

    /**
     * Pop an element from the end of the array
     *
     * @return the popped element
     */
    public abstract Object pop();

    /**
     * Slice out a section of the array and return that
     * subsection as a new array data: [from, to)
     *
     * @param from start index
     * @param to   end index + 1
     * @return new array data
     */
    public abstract ArrayData slice(long from, long to);

    private static Class<?> widestType(final Object... items) {
        assert items.length > 0;

        Class<?> widest = Integer.class;

        for (final Object item : items) {
            final Class<?> itemClass = item == null ? null : item.getClass();
            if (itemClass == Long.class) {
                if (widest == Integer.class) {
                    widest = Long.class;
                }
            } else if (itemClass == Double.class) {
                if (widest == Integer.class || widest == Long.class) {
                    widest = Double.class;
                }
            } else if (!(item instanceof Number)) {
                widest = Object.class;
                break;
            }
        }

        return widest;
    }

    /**
     * Exponential growth function for array size when in
     * need of resizing.
     *
     * @param size current size
     * @return next size to allocate for internal array
     */
    protected static int nextSize(final int size) {
        if (size == 0) {
            return CHUNK_SIZE;
        }

        int i = size;
        while ((i & CHUNK_MASK) != 0) {
            i++;
        }

        return i << 1;
    }

    /**
     * Return the next valid index from a given one. Subclassed for various
     * array representation
     *
     * @param index the current index
     *
     * @return the next index
     */
    public long nextIndex(final long index) {
        return index + 1;
    }

    static Object invoke(final MethodHandle mh, final Object arg) {
        try {
            return mh.invoke(arg);
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
