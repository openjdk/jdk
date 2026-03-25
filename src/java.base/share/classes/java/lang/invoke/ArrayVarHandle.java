/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.invoke;

import java.util.Optional;

import jdk.internal.misc.Unsafe;
import jdk.internal.util.Preconditions;
import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.ForceInline;

import static java.lang.invoke.MethodHandleStatics.UNSAFE;

/// The var handle for polymorphic arrays.
final class ArrayVarHandle extends VarHandle {
    static final int REFERENCE_BASE = Math.toIntExact(Unsafe.ARRAY_OBJECT_BASE_OFFSET);
    static final int REFERENCE_SHIFT = Integer.numberOfTrailingZeros(Unsafe.ARRAY_OBJECT_INDEX_SCALE);
    final Class<?> arrayType;
    final Class<?> componentType;

    ArrayVarHandle(Class<?> arrayType) {
        this(arrayType, false);
    }

    private ArrayVarHandle(Class<?> arrayType, boolean exact) {
        super(ArrayVarHandle.FORM, exact);
        this.arrayType = arrayType;
        this.componentType = arrayType.getComponentType();
    }

    @Override
    public ArrayVarHandle withInvokeExactBehavior() {
        return hasInvokeExactBehavior()
                ? this
                : new ArrayVarHandle(arrayType, true);
    }

    @Override
    public ArrayVarHandle withInvokeBehavior() {
        return !hasInvokeExactBehavior()
                ? this
                : new ArrayVarHandle(arrayType, false);
    }

    @Override
    public Optional<VarHandleDesc> describeConstable() {
        var arrayTypeRef = arrayType.describeConstable();
        if (arrayTypeRef.isEmpty())
            return Optional.empty();

        return Optional.of(VarHandleDesc.ofArray(arrayTypeRef.get()));
    }

    @Override
    final MethodType accessModeTypeUncached(AccessType at) {
        return at.accessModeType(arrayType, componentType, int.class);
    }

    @ForceInline
    static Object storeCheck(ArrayVarHandle handle, Object[] oarray, Object value) {
        if (value == null && ValueClass.isNullRestrictedArray(oarray)) {
            throw new NullPointerException("null not allowed for null-restricted array " + oarray.getClass().toGenericString());
        }
        if (handle.arrayType == oarray.getClass()) {
            // Fast path: static array type same as argument array type
            return handle.componentType.cast(value);
        } else {
            // Slow path: check value against argument array component type
            return reflectiveTypeCheck(oarray, value);
        }
    }

    @ForceInline
    static Object reflectiveTypeCheck(Object[] oarray, Object value) {
        try {
            return oarray.getClass().getComponentType().cast(value);
        } catch (ClassCastException e) {
            throw new ArrayStoreException();
        }
    }

    @ForceInline
    static Object get(VarHandle ob, Object oarray, int index) {
        ArrayVarHandle handle = (ArrayVarHandle) ob;
        Object[] array = (Object[]) handle.arrayType.cast(oarray);
        return array[index];
    }

    @ForceInline
    static void set(VarHandle ob, Object oarray, int index, Object value) {
        ArrayVarHandle handle = (ArrayVarHandle) ob;
        Object[] array = (Object[]) handle.arrayType.cast(oarray);
        array[index] = storeCheck(handle, array, value);
    }

    @ForceInline
    static Object getVolatile(VarHandle ob, Object oarray, int index) {
        ArrayVarHandle handle = (ArrayVarHandle) ob;
        Object[] array = (Object[]) handle.arrayType.cast(oarray);
        Class<?> arrayType = oarray.getClass();
        if (ValueClass.isFlatArray(oarray)) {
            // delegate to flat access primitives
            VarHandles.checkAtomicFlatArray(array);
            int aoffset = (int) UNSAFE.arrayInstanceBaseOffset(array);
            int ascale = UNSAFE.arrayInstanceIndexScale(array);
            int ashift = 31 - Integer.numberOfLeadingZeros(ascale);
            int layout = UNSAFE.arrayLayout(array);
            return UNSAFE.getFlatValueVolatile(array,
                    (((long) Preconditions.checkIndex(index, array.length, Preconditions.AIOOBE_FORMATTER)) << ashift) + aoffset, layout, arrayType.componentType());
        }
        return UNSAFE.getReferenceVolatile(array,
                (((long) Preconditions.checkIndex(index, array.length, Preconditions.AIOOBE_FORMATTER)) << REFERENCE_SHIFT) + REFERENCE_BASE);
    }

    @ForceInline
    static void setVolatile(VarHandle ob, Object oarray, int index, Object value) {
        ArrayVarHandle handle = (ArrayVarHandle) ob;
        Object[] array = (Object[]) handle.arrayType.cast(oarray);
        Class<?> arrayType = oarray.getClass();
        if (ValueClass.isFlatArray(oarray)) {
            // delegate to flat access primitives
            VarHandles.checkAtomicFlatArray(array);
            int aoffset = (int) UNSAFE.arrayInstanceBaseOffset(array);
            int ascale = UNSAFE.arrayInstanceIndexScale(array);
            int ashift = 31 - Integer.numberOfLeadingZeros(ascale);
            int layout = UNSAFE.arrayLayout(array);
            UNSAFE.putFlatValueVolatile(array,
                    (((long) Preconditions.checkIndex(index, array.length, Preconditions.AIOOBE_FORMATTER)) << ashift) + aoffset, layout, arrayType.componentType(),
                    storeCheck(handle, array, value));
            return;
        }
        UNSAFE.putReferenceVolatile(array,
                (((long) Preconditions.checkIndex(index, array.length, Preconditions.AIOOBE_FORMATTER)) << REFERENCE_SHIFT) + REFERENCE_BASE,
                storeCheck(handle, array, value));
    }

    @ForceInline
    static Object getOpaque(VarHandle ob, Object oarray, int index) {
        ArrayVarHandle handle = (ArrayVarHandle) ob;
        Object[] array = (Object[]) handle.arrayType.cast(oarray);
        Class<?> arrayType = oarray.getClass();
        if (ValueClass.isFlatArray(oarray)) {
            // delegate to flat access primitives
            VarHandles.checkAtomicFlatArray(array);
            int aoffset = (int) UNSAFE.arrayInstanceBaseOffset(array);
            int ascale = UNSAFE.arrayInstanceIndexScale(array);
            int ashift = 31 - Integer.numberOfLeadingZeros(ascale);
            int layout = UNSAFE.arrayLayout(array);
            return UNSAFE.getFlatValueOpaque(array,
                    (((long) Preconditions.checkIndex(index, array.length, Preconditions.AIOOBE_FORMATTER)) << ashift) + aoffset, layout, arrayType.componentType());
        }
        return UNSAFE.getReferenceOpaque(array,
                (((long) Preconditions.checkIndex(index, array.length, Preconditions.AIOOBE_FORMATTER)) << REFERENCE_SHIFT) + REFERENCE_BASE);
    }

    @ForceInline
    static void setOpaque(VarHandle ob, Object oarray, int index, Object value) {
        ArrayVarHandle handle = (ArrayVarHandle) ob;
        Object[] array = (Object[]) handle.arrayType.cast(oarray);
        Class<?> arrayType = oarray.getClass();
        if (ValueClass.isFlatArray(oarray)) {
            // delegate to flat access primitives
            VarHandles.checkAtomicFlatArray(array);
            int aoffset = (int) UNSAFE.arrayInstanceBaseOffset(array);
            int ascale = UNSAFE.arrayInstanceIndexScale(array);
            int ashift = 31 - Integer.numberOfLeadingZeros(ascale);
            int layout = UNSAFE.arrayLayout(array);
            UNSAFE.putFlatValueOpaque(array,
                    (((long) Preconditions.checkIndex(index, array.length, Preconditions.AIOOBE_FORMATTER)) << ashift) + aoffset, layout, arrayType.componentType(),
                    storeCheck(handle, array, value));
            return;
        }
        UNSAFE.putReferenceOpaque(array,
                (((long) Preconditions.checkIndex(index, array.length, Preconditions.AIOOBE_FORMATTER)) << REFERENCE_SHIFT) + REFERENCE_BASE,
                storeCheck(handle, array, value));
    }

    @ForceInline
    static Object getAcquire(VarHandle ob, Object oarray, int index) {
        ArrayVarHandle handle = (ArrayVarHandle) ob;
        Object[] array = (Object[]) handle.arrayType.cast(oarray);
        Class<?> arrayType = oarray.getClass();
        if (ValueClass.isFlatArray(oarray)) {
            // delegate to flat access primitives
            VarHandles.checkAtomicFlatArray(array);
            int aoffset = (int) UNSAFE.arrayInstanceBaseOffset(array);
            int ascale = UNSAFE.arrayInstanceIndexScale(array);
            int ashift = 31 - Integer.numberOfLeadingZeros(ascale);
            int layout = UNSAFE.arrayLayout(array);
            return UNSAFE.getFlatValueAcquire(array,
                    (((long) Preconditions.checkIndex(index, array.length, Preconditions.AIOOBE_FORMATTER)) << ashift) + aoffset, layout, arrayType.componentType());
        }
        return UNSAFE.getReferenceAcquire(array,
                (((long) Preconditions.checkIndex(index, array.length, Preconditions.AIOOBE_FORMATTER)) << REFERENCE_SHIFT) + REFERENCE_BASE);
    }

    @ForceInline
    static void setRelease(VarHandle ob, Object oarray, int index, Object value) {
        ArrayVarHandle handle = (ArrayVarHandle) ob;
        Object[] array = (Object[]) handle.arrayType.cast(oarray);
        Class<?> arrayType = oarray.getClass();
        if (ValueClass.isFlatArray(oarray)) {
            // delegate to flat access primitives
            VarHandles.checkAtomicFlatArray(array);
            int aoffset = (int) UNSAFE.arrayInstanceBaseOffset(array);
            int ascale = UNSAFE.arrayInstanceIndexScale(array);
            int ashift = 31 - Integer.numberOfLeadingZeros(ascale);
            int layout = UNSAFE.arrayLayout(array);
            UNSAFE.putFlatValueRelease(array,
                    (((long) Preconditions.checkIndex(index, array.length, Preconditions.AIOOBE_FORMATTER)) << ashift) + aoffset, layout, arrayType.componentType(),
                    storeCheck(handle, array, value));
            return;
        }
        UNSAFE.putReferenceRelease(array,
                (((long) Preconditions.checkIndex(index, array.length, Preconditions.AIOOBE_FORMATTER)) << REFERENCE_SHIFT) + REFERENCE_BASE,
                storeCheck(handle, array, value));
    }

    @ForceInline
    static boolean compareAndSet(VarHandle ob, Object oarray, int index, Object expected, Object value) {
        ArrayVarHandle handle = (ArrayVarHandle) ob;
        Object[] array = (Object[]) handle.arrayType.cast(oarray);
        Class<?> arrayType = oarray.getClass();
        if (ValueClass.isFlatArray(oarray)) {
            // delegate to flat access primitives
            VarHandles.checkAtomicFlatArray(array);
            int aoffset = (int) UNSAFE.arrayInstanceBaseOffset(array);
            int ascale = UNSAFE.arrayInstanceIndexScale(array);
            int ashift = 31 - Integer.numberOfLeadingZeros(ascale);
            int layout = UNSAFE.arrayLayout(array);
            return UNSAFE.compareAndSetFlatValue(array,
                    (((long) Preconditions.checkIndex(index, array.length, Preconditions.AIOOBE_FORMATTER)) << ashift) + aoffset, layout, arrayType.componentType(),
                    arrayType.componentType().cast(expected),
                    storeCheck(handle, array, value));
        }
        return UNSAFE.compareAndSetReference(array,
                (((long) Preconditions.checkIndex(index, array.length, Preconditions.AIOOBE_FORMATTER)) << REFERENCE_SHIFT) + REFERENCE_BASE, handle.componentType,
                handle.componentType.cast(expected),
                storeCheck(handle, array, value));
    }

    @ForceInline
    static Object compareAndExchange(VarHandle ob, Object oarray, int index, Object expected, Object value) {
        ArrayVarHandle handle = (ArrayVarHandle) ob;
        Object[] array = (Object[]) handle.arrayType.cast(oarray);
        Class<?> arrayType = oarray.getClass();
        if (ValueClass.isFlatArray(oarray)) {
            // delegate to flat access primitives
            VarHandles.checkAtomicFlatArray(array);
            int aoffset = (int) UNSAFE.arrayInstanceBaseOffset(array);
            int ascale = UNSAFE.arrayInstanceIndexScale(array);
            int ashift = 31 - Integer.numberOfLeadingZeros(ascale);
            int layout = UNSAFE.arrayLayout(array);
            return UNSAFE.compareAndExchangeFlatValue(array,
                    (((long) Preconditions.checkIndex(index, array.length, Preconditions.AIOOBE_FORMATTER)) << ashift) + aoffset, layout, arrayType.componentType(),
                    arrayType.componentType().cast(expected),
                    storeCheck(handle, array, value));
        }
        return UNSAFE.compareAndExchangeReference(array,
                (((long) Preconditions.checkIndex(index, array.length, Preconditions.AIOOBE_FORMATTER)) << REFERENCE_SHIFT) + REFERENCE_BASE, handle.componentType,
                handle.componentType.cast(expected),
                storeCheck(handle, array, value));
    }

    @ForceInline
    static Object compareAndExchangeAcquire(VarHandle ob, Object oarray, int index, Object expected, Object value) {
        ArrayVarHandle handle = (ArrayVarHandle) ob;
        Object[] array = (Object[]) handle.arrayType.cast(oarray);
        Class<?> arrayType = oarray.getClass();
        if (ValueClass.isFlatArray(oarray)) {
            // delegate to flat access primitives
            VarHandles.checkAtomicFlatArray(array);
            int aoffset = (int) UNSAFE.arrayInstanceBaseOffset(array);
            int ascale = UNSAFE.arrayInstanceIndexScale(array);
            int ashift = 31 - Integer.numberOfLeadingZeros(ascale);
            int layout = UNSAFE.arrayLayout(array);
            return UNSAFE.compareAndExchangeFlatValueAcquire(array,
                    (((long) Preconditions.checkIndex(index, array.length, Preconditions.AIOOBE_FORMATTER)) << ashift) + aoffset, layout, arrayType.componentType(),
                    arrayType.componentType().cast(expected),
                    storeCheck(handle, array, value));
        }
        return UNSAFE.compareAndExchangeReferenceAcquire(array,
                (((long) Preconditions.checkIndex(index, array.length, Preconditions.AIOOBE_FORMATTER)) << REFERENCE_SHIFT) + REFERENCE_BASE, handle.componentType,
                handle.componentType.cast(expected),
                storeCheck(handle, array, value));
    }

    @ForceInline
    static Object compareAndExchangeRelease(VarHandle ob, Object oarray, int index, Object expected, Object value) {
        ArrayVarHandle handle = (ArrayVarHandle) ob;
        Object[] array = (Object[]) handle.arrayType.cast(oarray);
        Class<?> arrayType = oarray.getClass();
        if (ValueClass.isFlatArray(oarray)) {
            // delegate to flat access primitives
            VarHandles.checkAtomicFlatArray(array);
            int aoffset = (int) UNSAFE.arrayInstanceBaseOffset(array);
            int ascale = UNSAFE.arrayInstanceIndexScale(array);
            int ashift = 31 - Integer.numberOfLeadingZeros(ascale);
            int layout = UNSAFE.arrayLayout(array);
            return UNSAFE.compareAndExchangeFlatValueRelease(array,
                    (((long) Preconditions.checkIndex(index, array.length, Preconditions.AIOOBE_FORMATTER)) << ashift) + aoffset, layout, arrayType.componentType(),
                    arrayType.componentType().cast(expected),
                    storeCheck(handle, array, value));
        }
        return UNSAFE.compareAndExchangeReferenceRelease(array,
                (((long) Preconditions.checkIndex(index, array.length, Preconditions.AIOOBE_FORMATTER)) << REFERENCE_SHIFT) + REFERENCE_BASE, handle.componentType,
                handle.componentType.cast(expected),
                storeCheck(handle, array, value));
    }

    @ForceInline
    static boolean weakCompareAndSetPlain(VarHandle ob, Object oarray, int index, Object expected, Object value) {
        ArrayVarHandle handle = (ArrayVarHandle) ob;
        Object[] array = (Object[]) handle.arrayType.cast(oarray);
        Class<?> arrayType = oarray.getClass();
        if (ValueClass.isFlatArray(oarray)) {
            // delegate to flat access primitives
            VarHandles.checkAtomicFlatArray(array);
            int aoffset = (int) UNSAFE.arrayInstanceBaseOffset(array);
            int ascale = UNSAFE.arrayInstanceIndexScale(array);
            int ashift = 31 - Integer.numberOfLeadingZeros(ascale);
            int layout = UNSAFE.arrayLayout(array);
            return UNSAFE.weakCompareAndSetFlatValuePlain(array,
                    (((long) Preconditions.checkIndex(index, array.length, Preconditions.AIOOBE_FORMATTER)) << ashift) + aoffset, layout, arrayType.componentType(),
                    arrayType.componentType().cast(expected),
                    storeCheck(handle, array, value));
        }
        return UNSAFE.weakCompareAndSetReferencePlain(array,
                (((long) Preconditions.checkIndex(index, array.length, Preconditions.AIOOBE_FORMATTER)) << REFERENCE_SHIFT) + REFERENCE_BASE, handle.componentType,
                handle.componentType.cast(expected),
                storeCheck(handle, array, value));
    }

    @ForceInline
    static boolean weakCompareAndSet(VarHandle ob, Object oarray, int index, Object expected, Object value) {
        ArrayVarHandle handle = (ArrayVarHandle) ob;
        Object[] array = (Object[]) handle.arrayType.cast(oarray);
        Class<?> arrayType = oarray.getClass();
        if (ValueClass.isFlatArray(oarray)) {
            // delegate to flat access primitives
            VarHandles.checkAtomicFlatArray(array);
            int aoffset = (int) UNSAFE.arrayInstanceBaseOffset(array);
            int ascale = UNSAFE.arrayInstanceIndexScale(array);
            int ashift = 31 - Integer.numberOfLeadingZeros(ascale);
            int layout = UNSAFE.arrayLayout(array);
            return UNSAFE.weakCompareAndSetFlatValue(array,
                    (((long) Preconditions.checkIndex(index, array.length, Preconditions.AIOOBE_FORMATTER)) << ashift) + aoffset, layout, arrayType.componentType(),
                    arrayType.componentType().cast(expected),
                    storeCheck(handle, array, value));
        }
        return UNSAFE.weakCompareAndSetReference(array,
                (((long) Preconditions.checkIndex(index, array.length, Preconditions.AIOOBE_FORMATTER)) << REFERENCE_SHIFT) + REFERENCE_BASE, handle.componentType,
                handle.componentType.cast(expected),
                storeCheck(handle, array, value));
    }

    @ForceInline
    static boolean weakCompareAndSetAcquire(VarHandle ob, Object oarray, int index, Object expected, Object value) {
        ArrayVarHandle handle = (ArrayVarHandle) ob;
        Object[] array = (Object[]) handle.arrayType.cast(oarray);
        Class<?> arrayType = oarray.getClass();
        if (ValueClass.isFlatArray(oarray)) {
            // delegate to flat access primitives
            VarHandles.checkAtomicFlatArray(array);
            int aoffset = (int) UNSAFE.arrayInstanceBaseOffset(array);
            int ascale = UNSAFE.arrayInstanceIndexScale(array);
            int ashift = 31 - Integer.numberOfLeadingZeros(ascale);
            int layout = UNSAFE.arrayLayout(array);
            return UNSAFE.weakCompareAndSetFlatValueAcquire(array,
                    (((long) Preconditions.checkIndex(index, array.length, Preconditions.AIOOBE_FORMATTER)) << ashift) + aoffset, layout, arrayType.componentType(),
                    arrayType.componentType().cast(expected),
                    storeCheck(handle, array, value));
        }
        return UNSAFE.weakCompareAndSetReferenceAcquire(array,
                (((long) Preconditions.checkIndex(index, array.length, Preconditions.AIOOBE_FORMATTER)) << REFERENCE_SHIFT) + REFERENCE_BASE, handle.componentType,
                handle.componentType.cast(expected),
                storeCheck(handle, array, value));
    }

    @ForceInline
    static boolean weakCompareAndSetRelease(VarHandle ob, Object oarray, int index, Object expected, Object value) {
        ArrayVarHandle handle = (ArrayVarHandle) ob;
        Object[] array = (Object[]) handle.arrayType.cast(oarray);
        Class<?> arrayType = oarray.getClass();
        if (ValueClass.isFlatArray(oarray)) {
            // delegate to flat access primitives
            VarHandles.checkAtomicFlatArray(array);
            int aoffset = (int) UNSAFE.arrayInstanceBaseOffset(array);
            int ascale = UNSAFE.arrayInstanceIndexScale(array);
            int ashift = 31 - Integer.numberOfLeadingZeros(ascale);
            int layout = UNSAFE.arrayLayout(array);
            return UNSAFE.weakCompareAndSetFlatValueRelease(array,
                    (((long) Preconditions.checkIndex(index, array.length, Preconditions.AIOOBE_FORMATTER)) << ashift) + aoffset, layout, arrayType.componentType(),
                    arrayType.componentType().cast(expected),
                    storeCheck(handle, array, value));
        }
        return UNSAFE.weakCompareAndSetReferenceRelease(array,
                (((long) Preconditions.checkIndex(index, array.length, Preconditions.AIOOBE_FORMATTER)) << REFERENCE_SHIFT) + REFERENCE_BASE, handle.componentType,
                handle.componentType.cast(expected),
                storeCheck(handle, array, value));
    }

    @ForceInline
    static Object getAndSet(VarHandle ob, Object oarray, int index, Object value) {
        ArrayVarHandle handle = (ArrayVarHandle) ob;
        Object[] array = (Object[]) handle.arrayType.cast(oarray);
        Class<?> arrayType = oarray.getClass();
        if (ValueClass.isFlatArray(oarray)) {
            // delegate to flat access primitives
            VarHandles.checkAtomicFlatArray(array);
            int aoffset = (int) UNSAFE.arrayInstanceBaseOffset(array);
            int ascale = UNSAFE.arrayInstanceIndexScale(array);
            int ashift = 31 - Integer.numberOfLeadingZeros(ascale);
            int layout = UNSAFE.arrayLayout(array);
            return UNSAFE.getAndSetFlatValue(array,
                    (((long) Preconditions.checkIndex(index, array.length, Preconditions.AIOOBE_FORMATTER)) << ashift) + aoffset, layout, arrayType.componentType(),
                    storeCheck(handle, array, value));
        }
        return UNSAFE.getAndSetReference(array,
                (((long) Preconditions.checkIndex(index, array.length, Preconditions.AIOOBE_FORMATTER)) << REFERENCE_SHIFT) + REFERENCE_BASE,
                handle.componentType, storeCheck(handle, array, value));
    }

    @ForceInline
    static Object getAndSetAcquire(VarHandle ob, Object oarray, int index, Object value) {
        ArrayVarHandle handle = (ArrayVarHandle) ob;
        Object[] array = (Object[]) handle.arrayType.cast(oarray);
        Class<?> arrayType = oarray.getClass();
        if (ValueClass.isFlatArray(oarray)) {
            // delegate to flat access primitives
            VarHandles.checkAtomicFlatArray(array);
            int aoffset = (int) UNSAFE.arrayInstanceBaseOffset(array);
            int ascale = UNSAFE.arrayInstanceIndexScale(array);
            int ashift = 31 - Integer.numberOfLeadingZeros(ascale);
            int layout = UNSAFE.arrayLayout(array);
            return UNSAFE.getAndSetFlatValueAcquire(array,
                    (((long) Preconditions.checkIndex(index, array.length, Preconditions.AIOOBE_FORMATTER)) << ashift) + aoffset, layout, arrayType.componentType(),
                    storeCheck(handle, array, value));
        }
        return UNSAFE.getAndSetReferenceAcquire(array,
                (((long) Preconditions.checkIndex(index, array.length, Preconditions.AIOOBE_FORMATTER)) << REFERENCE_SHIFT) + REFERENCE_BASE,
                handle.componentType, storeCheck(handle, array, value));
    }

    @ForceInline
    static Object getAndSetRelease(VarHandle ob, Object oarray, int index, Object value) {
        ArrayVarHandle handle = (ArrayVarHandle) ob;
        Object[] array = (Object[]) handle.arrayType.cast(oarray);
        Class<?> arrayType = oarray.getClass();
        if (ValueClass.isFlatArray(oarray)) {
            // delegate to flat access primitives
            VarHandles.checkAtomicFlatArray(array);
            int aoffset = (int) UNSAFE.arrayInstanceBaseOffset(array);
            int ascale = UNSAFE.arrayInstanceIndexScale(array);
            int ashift = 31 - Integer.numberOfLeadingZeros(ascale);
            int layout = UNSAFE.arrayLayout(array);
            return UNSAFE.getAndSetFlatValueRelease(array,
                    (((long) Preconditions.checkIndex(index, array.length, Preconditions.AIOOBE_FORMATTER)) << ashift) + aoffset, layout, arrayType.componentType(),
                    storeCheck(handle, array, value));
        }
        return UNSAFE.getAndSetReferenceRelease(array,
                (((long) Preconditions.checkIndex(index, array.length, Preconditions.AIOOBE_FORMATTER)) << REFERENCE_SHIFT) + REFERENCE_BASE,
                handle.componentType, storeCheck(handle, array, value));
    }

    static final VarForm FORM = new VarForm(ArrayVarHandle.class, Object[].class, Object.class, int.class);
}
