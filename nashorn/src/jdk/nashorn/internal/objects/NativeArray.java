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

import static jdk.nashorn.internal.runtime.ECMAErrors.rangeError;
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.PropertyDescriptor.VALUE;
import static jdk.nashorn.internal.runtime.PropertyDescriptor.WRITABLE;
import static jdk.nashorn.internal.runtime.arrays.ArrayLikeIterator.arrayLikeIterator;
import static jdk.nashorn.internal.runtime.arrays.ArrayLikeIterator.reverseArrayLikeIterator;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.internal.objects.annotations.Attribute;
import jdk.nashorn.internal.objects.annotations.Constructor;
import jdk.nashorn.internal.objects.annotations.Function;
import jdk.nashorn.internal.objects.annotations.Getter;
import jdk.nashorn.internal.objects.annotations.ScriptClass;
import jdk.nashorn.internal.objects.annotations.Setter;
import jdk.nashorn.internal.objects.annotations.SpecializedConstructor;
import jdk.nashorn.internal.objects.annotations.Where;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.PropertyDescriptor;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.Undefined;
import jdk.nashorn.internal.runtime.arrays.ArrayData;
import jdk.nashorn.internal.runtime.arrays.ArrayIndex;
import jdk.nashorn.internal.runtime.arrays.ArrayLikeIterator;
import jdk.nashorn.internal.runtime.arrays.IteratorAction;
import jdk.nashorn.internal.runtime.linker.Bootstrap;
import jdk.nashorn.internal.runtime.linker.InvokeByName;

/**
 * Runtime representation of a JavaScript array. NativeArray only holds numeric
 * keyed values. All other values are stored in spill.
 */
@ScriptClass("Array")
public final class NativeArray extends ScriptObject {
    private static final InvokeByName JOIN = new InvokeByName("join", ScriptObject.class);

    private static final MethodHandle EVERY_CALLBACK_INVOKER   = createIteratorCallbackInvoker(boolean.class);
    private static final MethodHandle SOME_CALLBACK_INVOKER    = createIteratorCallbackInvoker(boolean.class);
    private static final MethodHandle FOREACH_CALLBACK_INVOKER = createIteratorCallbackInvoker(void.class);
    private static final MethodHandle MAP_CALLBACK_INVOKER     = createIteratorCallbackInvoker(Object.class);
    private static final MethodHandle FILTER_CALLBACK_INVOKER  = createIteratorCallbackInvoker(boolean.class);

    private static final MethodHandle REDUCE_CALLBACK_INVOKER = Bootstrap.createDynamicInvoker("dyn:call", Object.class,
            Object.class, Undefined.class, Object.class, Object.class, long.class, Object.class);
    private static final MethodHandle CALL_CMP                = Bootstrap.createDynamicInvoker("dyn:call", double.class,
            ScriptFunction.class, Object.class, Object.class, Object.class);

    private static final InvokeByName TO_LOCALE_STRING = new InvokeByName("toLocaleString", ScriptObject.class, String.class);

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    static PropertyMap getInitialMap() {
        return $nasgenmap$;
    }

    /*
     * Constructors.
     */
    NativeArray() {
        this(ArrayData.initialArray());
    }

    NativeArray(final long length) {
        // TODO assert valid index in long before casting
        this(ArrayData.allocate((int) length));
    }

    NativeArray(final int[] array) {
        this(ArrayData.allocate(array));
    }

    NativeArray(final long[] array) {
        this(ArrayData.allocate(array));
    }

    NativeArray(final double[] array) {
        this(ArrayData.allocate(array));
    }

    NativeArray(final Object[] array) {
        this(ArrayData.allocate(array.length));

        ArrayData arrayData = this.getArray();
        arrayData.ensure(array.length - 1);

        for (int index = 0; index < array.length; index++) {
            final Object value = array[index];

            if (value == ScriptRuntime.EMPTY) {
                arrayData = arrayData.delete(index);
            } else {
                arrayData = arrayData.set(index, value, false);
            }
        }

        this.setArray(arrayData);
    }

    NativeArray(final ArrayData arrayData) {
        this(arrayData, Global.instance());
    }

    NativeArray(final ArrayData arrayData, final Global global) {
        super(global.getArrayPrototype(), global.getArrayMap());
        this.setArray(arrayData);
        this.setIsArray();
    }

    @Override
    public String getClassName() {
        return "Array";
    }

    @Override
    public Object getLength() {
        return getArray().length() & JSType.MAX_UINT;
    }

    /**
     * ECMA 15.4.5.1 [[DefineOwnProperty]] ( P, Desc, Throw )
     */
    @Override
    public boolean defineOwnProperty(final String key, final Object propertyDesc, final boolean reject) {
        final PropertyDescriptor desc = toPropertyDescriptor(Global.instance(), propertyDesc);

        // never be undefined as "length" is always defined and can't be deleted for arrays
        // Step 1
        final PropertyDescriptor oldLenDesc = (PropertyDescriptor) super.getOwnPropertyDescriptor("length");

        // Step 2
        // get old length and convert to long
        long oldLen = NativeArray.validLength(oldLenDesc.getValue(), true);

        // Step 3
        if ("length".equals(key)) {
            // check for length being made non-writable
            if (desc.has(WRITABLE) && !desc.isWritable()) {
                setIsLengthNotWritable();
            }

            // Step 3a
            if (!desc.has(VALUE)) {
                return super.defineOwnProperty("length", desc, reject);
            }

            // Step 3b
            final PropertyDescriptor newLenDesc = desc;

            // Step 3c and 3d - get new length and convert to long
            final long newLen = NativeArray.validLength(newLenDesc.getValue(), true);

            // Step 3e
            newLenDesc.setValue(newLen);

            // Step 3f
            // increasing array length - just need to set new length value (and attributes if any) and return
            if (newLen >= oldLen) {
                return super.defineOwnProperty("length", newLenDesc, reject);
            }

            // Step 3g
            if (!oldLenDesc.isWritable()) {
                if (reject) {
                    throw typeError("property.not.writable", "length", ScriptRuntime.safeToString(this));
                }
                return false;
            }

            // Step 3h and 3i
            final boolean newWritable = (!newLenDesc.has(WRITABLE) || newLenDesc.isWritable());
            if (!newWritable) {
                newLenDesc.setWritable(true);
            }

            // Step 3j and 3k
            final boolean succeeded = super.defineOwnProperty("length", newLenDesc, reject);
            if (!succeeded) {
                return false;
            }

            // Step 3l
            // make sure that length is set till the point we can delete the old elements
            while (newLen < oldLen) {
                oldLen--;
                final boolean deleteSucceeded = delete(oldLen, false);
                if (!deleteSucceeded) {
                    newLenDesc.setValue(oldLen + 1);
                    if (!newWritable) {
                        newLenDesc.setWritable(false);
                    }
                    super.defineOwnProperty("length", newLenDesc, false);
                    if (reject) {
                        throw typeError("property.not.writable", "length", ScriptRuntime.safeToString(this));
                    }
                    return false;
                }
            }

            // Step 3m
            if (!newWritable) {
                // make 'length' property not writable
                final ScriptObject newDesc = Global.newEmptyInstance();
                newDesc.set(WRITABLE, false, false);
                return super.defineOwnProperty("length", newDesc, false);
            }

            return true;
        }

        // Step 4a
        final int index = ArrayIndex.getArrayIndex(key);
        if (ArrayIndex.isValidArrayIndex(index)) {
            final long longIndex = ArrayIndex.toLongIndex(index);
            // Step 4b
            // setting an element beyond current length, but 'length' is not writable
            if (longIndex >= oldLen && !oldLenDesc.isWritable()) {
                if (reject) {
                    throw typeError("property.not.writable", Long.toString(longIndex), ScriptRuntime.safeToString(this));
                }
                return false;
            }

            // Step 4c
            // set the new array element
            final boolean succeeded = super.defineOwnProperty(key, desc, false);

            // Step 4d
            if (!succeeded) {
                if (reject) {
                    throw typeError("cant.redefine.property", key, ScriptRuntime.safeToString(this));
                }
                return false;
            }

            // Step 4e -- adjust new length based on new element index that is set
            if (longIndex >= oldLen) {
                oldLenDesc.setValue(longIndex + 1);
                super.defineOwnProperty("length", oldLenDesc, false);
            }

            // Step 4f
            return true;
        }

        // not an index property
        return super.defineOwnProperty(key, desc, reject);
    }

    /**
     * Return the array contents upcasted as an ObjectArray, regardless of
     * representation
     *
     * @return an object array
     */
    public Object[] asObjectArray() {
        return getArray().asObjectArray();
    }

    /**
     * ECMA 15.4.3.2 Array.isArray ( arg )
     *
     * @param self self reference
     * @param arg  argument - object to check
     * @return true if argument is an array
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object isArray(final Object self, final Object arg) {
        return isArray(arg) || (arg == Global.instance().getArrayPrototype())
                || (arg instanceof NativeRegExpExecResult)
                || (arg instanceof ScriptObjectMirror && ((ScriptObjectMirror)arg).isArray());
    }

    /**
     * Length getter
     * @param self self reference
     * @return the length of the object
     */
    @Getter(attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_CONFIGURABLE)
    public static Object length(final Object self) {
        if (isArray(self)) {
            return ((ScriptObject) self).getArray().length() & JSType.MAX_UINT;
        }

        return 0;
    }

    /**
     * Length setter
     * @param self   self reference
     * @param length new length property
     */
    @Setter(attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_CONFIGURABLE)
    public static void length(final Object self, final Object length) {
        if (isArray(self)) {
            ((ScriptObject) self).setLength(validLength(length, true));
        }
    }

    static long validLength(final Object length, final boolean reject) {
        final double doubleLength = JSType.toNumber(length);
        if (!Double.isNaN(doubleLength) && JSType.isRepresentableAsLong(doubleLength)) {
            final long len = (long) doubleLength;
            if (len >= 0 && len <= JSType.MAX_UINT) {
                return len;
            }
        }
        if (reject) {
            throw rangeError("inappropriate.array.length", ScriptRuntime.safeToString(length));
        }
        return -1;
    }

    /**
     * ECMA 15.4.4.2 Array.prototype.toString ( )
     *
     * @param self self reference
     * @return string representation of array
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object toString(final Object self) {
        final Object obj = Global.toObject(self);
        if (obj instanceof ScriptObject) {
            final ScriptObject sobj = (ScriptObject)obj;
            try {
                final Object join = JOIN.getGetter().invokeExact(sobj);
                if (Bootstrap.isCallable(join)) {
                    return JOIN.getInvoker().invokeExact(join, sobj);
                }
            } catch (final RuntimeException | Error e) {
                throw e;
            } catch (final Throwable t) {
                throw new RuntimeException(t);
            }
        }

        // FIXME: should lookup Object.prototype.toString and call that?
        return ScriptRuntime.builtinObjectToString(self);
    }

    /**
     * ECMA 15.4.4.3 Array.prototype.toLocaleString ( )
     *
     * @param self self reference
     * @return locale specific string representation for array
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object toLocaleString(final Object self) {
        final StringBuilder sb = new StringBuilder();
        final Iterator<Object> iter = arrayLikeIterator(self, true);

        while (iter.hasNext()) {
            final Object obj = iter.next();

            if (obj != null && obj != ScriptRuntime.UNDEFINED) {
                final Object val = JSType.toScriptObject(obj);

                try {
                    if (val instanceof ScriptObject) {
                        final ScriptObject sobj           = (ScriptObject)val;
                        final Object       toLocaleString = TO_LOCALE_STRING.getGetter().invokeExact(sobj);

                        if (Bootstrap.isCallable(toLocaleString)) {
                            sb.append((String)TO_LOCALE_STRING.getInvoker().invokeExact(toLocaleString, sobj));
                        } else {
                            throw typeError("not.a.function", "toLocaleString");
                        }
                    }
                } catch (final Error|RuntimeException t) {
                    throw t;
                } catch (final Throwable t) {
                    throw new RuntimeException(t);
                }
            }

            if (iter.hasNext()) {
                sb.append(",");
            }
        }

        return sb.toString();
    }

    /**
     * ECMA 15.4.2.2 new Array (len)
     *
     * @param newObj was the new operator used to instantiate this array
     * @param self   self reference
     * @param args   arguments (length)
     * @return the new NativeArray
     */
    @Constructor(arity = 1)
    public static Object construct(final boolean newObj, final Object self, final Object... args) {
        switch (args.length) {
        case 0:
            return new NativeArray(0);
        case 1:
            final Object len = args[0];
            if (len instanceof Number) {
                long length;
                if (len instanceof Integer || len instanceof Long) {
                    length = ((Number) len).longValue();
                    if (length >= 0 && length < JSType.MAX_UINT) {
                        return new NativeArray(length);
                    }
                }

                length = JSType.toUint32(len);

                /*
                 * If the argument len is a Number and ToUint32(len) is equal to
                 * len, then the length property of the newly constructed object
                 * is set to ToUint32(len). If the argument len is a Number and
                 * ToUint32(len) is not equal to len, a RangeError exception is
                 * thrown.
                 */
                final double numberLength = ((Number) len).doubleValue();
                if (length != numberLength) {
                    throw rangeError("inappropriate.array.length", JSType.toString(numberLength));
                }

                return new NativeArray(length);
            }
            /*
             * If the argument len is not a Number, then the length property of
             * the newly constructed object is set to 1 and the 0 property of
             * the newly constructed object is set to len
             */
            return new NativeArray(new Object[]{args[0]});
            //fallthru
        default:
            return new NativeArray(args);
        }
    }

    /**
     * ECMA 15.4.2.2 new Array (len)
     *
     * Specialized constructor for zero arguments - empty array
     *
     * @param newObj was the new operator used to instantiate this array
     * @param self   self reference
     * @return the new NativeArray
     */
    @SpecializedConstructor
    public static Object construct(final boolean newObj, final Object self) {
        return new NativeArray(0);
    }

    /**
     * ECMA 15.4.2.2 new Array (len)
     *
     * Specialized constructor for one integer argument (length)
     *
     * @param newObj was the new operator used to instantiate this array
     * @param self   self reference
     * @param length array length
     * @return the new NativeArray
     */
    @SpecializedConstructor
    public static Object construct(final boolean newObj, final Object self, final int length) {
        if (length >= 0) {
            return new NativeArray(length);
        }

        return construct(newObj, self, new Object[]{length});
    }

    /**
     * ECMA 15.4.2.2 new Array (len)
     *
     * Specialized constructor for one long argument (length)
     *
     * @param newObj was the new operator used to instantiate this array
     * @param self   self reference
     * @param length array length
     * @return the new NativeArray
     */
    @SpecializedConstructor
    public static Object construct(final boolean newObj, final Object self, final long length) {
        if (length >= 0L && length <= JSType.MAX_UINT) {
            return new NativeArray(length);
        }

        return construct(newObj, self, new Object[]{length});
    }

    /**
     * ECMA 15.4.2.2 new Array (len)
     *
     * Specialized constructor for one double argument (length)
     *
     * @param newObj was the new operator used to instantiate this array
     * @param self   self reference
     * @param length array length
     * @return the new NativeArray
     */
    @SpecializedConstructor
    public static Object construct(final boolean newObj, final Object self, final double length) {
        final long uint32length = JSType.toUint32(length);

        if (uint32length == length) {
            return new NativeArray(uint32length);
        }

        return construct(newObj, self, new Object[]{length});
    }

    /**
     * ECMA 15.4.4.4 Array.prototype.concat ( [ item1 [ , item2 [ , ... ] ] ] )
     *
     * @param self self reference
     * @param args arguments to concat
     * @return resulting NativeArray
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object concat(final Object self, final Object... args) {
        final ArrayList<Object> list = new ArrayList<>();
        concatToList(list, Global.toObject(self));

        for (final Object obj : args) {
            concatToList(list, obj);
        }

        return new NativeArray(list.toArray());
    }

    private static void concatToList(final ArrayList<Object> list, final Object obj) {
        final boolean isScriptArray = isArray(obj);
        final boolean isScriptObject = isScriptArray || obj instanceof ScriptObject;
        if (isScriptArray || obj instanceof Iterable || (obj != null && obj.getClass().isArray())) {
            final Iterator<Object> iter = arrayLikeIterator(obj, true);
            if (iter.hasNext()) {
                for(int i = 0; iter.hasNext(); ++i) {
                    final Object value = iter.next();
                    if(value == ScriptRuntime.UNDEFINED && isScriptObject && !((ScriptObject)obj).has(i)) {
                        // TODO: eventually rewrite arrayLikeIterator to use a three-state enum for handling
                        // UNDEFINED instead of an "includeUndefined" boolean with states SKIP, INCLUDE,
                        // RETURN_EMPTY. Until then, this is how we'll make sure that empty elements don't make it
                        // into the concatenated array.
                        list.add(ScriptRuntime.EMPTY);
                    } else {
                        list.add(value);
                    }
                }
            } else if (!isScriptArray) {
                list.add(obj); // add empty object, but not an empty array
            }
        } else {
            // single element, add it
            list.add(obj);
        }
    }

    /**
     * ECMA 15.4.4.5 Array.prototype.join (separator)
     *
     * @param self      self reference
     * @param separator element separator
     * @return string representation after join
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object join(final Object self, final Object separator) {
        final StringBuilder    sb   = new StringBuilder();
        final Iterator<Object> iter = arrayLikeIterator(self, true);
        final String           sep  = separator == ScriptRuntime.UNDEFINED ? "," : JSType.toString(separator);

        while (iter.hasNext()) {
            final Object obj = iter.next();

            if (obj != null && obj != ScriptRuntime.UNDEFINED) {
                sb.append(JSType.toString(obj));
            }

            if (iter.hasNext()) {
                sb.append(sep);
            }
        }

        return sb.toString();
    }

    /**
     * ECMA 15.4.4.6 Array.prototype.pop ()
     *
     * @param self self reference
     * @return array after pop
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object pop(final Object self) {
        try {
            final ScriptObject sobj = (ScriptObject)self;

            if (bulkable(sobj)) {
                return sobj.getArray().pop();
            }

            final long len = JSType.toUint32(sobj.getLength());

            if (len == 0) {
                sobj.set("length", 0, true);
                return ScriptRuntime.UNDEFINED;
            }

            final long   index   = len - 1;
            final Object element = sobj.get(index);

            sobj.delete(index, true);
            sobj.set("length", index, true);

            return element;
        } catch (final ClassCastException | NullPointerException e) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(self));
        }
    }

    /**
     * ECMA 15.4.4.7 Array.prototype.push (args...)
     *
     * @param self self reference
     * @param args arguments to push
     * @return array after pushes
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object push(final Object self, final Object... args) {
        try {
            final ScriptObject sobj   = (ScriptObject)self;

            if (bulkable(sobj)) {
                if (sobj.getArray().length() + args.length <= JSType.MAX_UINT) {
                    final ArrayData newData = sobj.getArray().push(true, args);
                    sobj.setArray(newData);
                    return newData.length();
                }
                //fallthru
            }

            long len = JSType.toUint32(sobj.getLength());
            for (final Object element : args) {
                sobj.set(len++, element, true);
            }
            sobj.set("length", len, true);

            return len;
        } catch (final ClassCastException | NullPointerException e) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(self));
        }
    }

    /**
     * ECMA 15.4.4.8 Array.prototype.reverse ()
     *
     * @param self self reference
     * @return reversed array
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object reverse(final Object self) {
        try {
            final ScriptObject sobj   = (ScriptObject)self;
            final long         len    = JSType.toUint32(sobj.getLength());
            final long         middle = len / 2;

            for (long lower = 0; lower != middle; lower++) {
                final long    upper       = len - lower - 1;
                final Object  lowerValue  = sobj.get(lower);
                final Object  upperValue  = sobj.get(upper);
                final boolean lowerExists = sobj.has(lower);
                final boolean upperExists = sobj.has(upper);

                if (lowerExists && upperExists) {
                    sobj.set(lower, upperValue, true);
                    sobj.set(upper, lowerValue, true);
                } else if (!lowerExists && upperExists) {
                    sobj.set(lower, upperValue, true);
                    sobj.delete(upper, true);
                } else if (lowerExists && !upperExists) {
                    sobj.delete(lower, true);
                    sobj.set(upper, lowerValue, true);
                }
            }
            return sobj;
        } catch (final ClassCastException | NullPointerException e) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(self));
        }
    }

    /**
     * ECMA 15.4.4.9 Array.prototype.shift ()
     *
     * @param self self reference
     * @return shifted array
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object shift(final Object self) {
        final Object obj = Global.toObject(self);

        Object first = ScriptRuntime.UNDEFINED;

        if (!(obj instanceof ScriptObject)) {
            return first;
        }

        final ScriptObject sobj   = (ScriptObject) obj;

        long len = JSType.toUint32(sobj.getLength());

        if (len > 0) {
            first = sobj.get(0);

            if (bulkable(sobj)) {
                sobj.getArray().shiftLeft(1);
            } else {
                for (long k = 1; k < len; k++) {
                    sobj.set(k - 1, sobj.get(k), true);
                }
            }
            sobj.delete(--len, true);
        } else {
            len = 0;
        }

        sobj.set("length", len, true);

        return first;
    }

    /**
     * ECMA 15.4.4.10 Array.prototype.slice ( start [ , end ] )
     *
     * @param self  self reference
     * @param start start of slice (inclusive)
     * @param end   end of slice (optional, exclusive)
     * @return sliced array
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object slice(final Object self, final Object start, final Object end) {
        final Object       obj                 = Global.toObject(self);
        final ScriptObject sobj                = (ScriptObject)obj;
        final long         len                 = JSType.toUint32(sobj.getLength());
        final long         relativeStart       = JSType.toLong(start);
        final long         relativeEnd         = (end == ScriptRuntime.UNDEFINED) ? len : JSType.toLong(end);

        long k = relativeStart < 0 ? Math.max(len + relativeStart, 0) : Math.min(relativeStart, len);
        final long finale = relativeEnd < 0 ? Math.max(len + relativeEnd, 0) : Math.min(relativeEnd, len);

        if (k >= finale) {
            return new NativeArray(0);
        }

        if (bulkable(sobj)) {
            return new NativeArray(sobj.getArray().slice(k, finale));
        }

        final NativeArray copy = new NativeArray(0);
        for (long n = 0; k < finale; n++, k++) {
            copy.defineOwnProperty(ArrayIndex.getArrayIndex(n), sobj.get(k));
        }

        return copy;
    }

    private static ScriptFunction compareFunction(final Object comparefn) {
        if (comparefn == ScriptRuntime.UNDEFINED) {
            return null;
        }

        if (! (comparefn instanceof ScriptFunction)) {
            throw typeError("not.a.function", ScriptRuntime.safeToString(comparefn));
        }

        return (ScriptFunction)comparefn;
    }

    private static Object[] sort(final Object[] array, final Object comparefn) {
        final ScriptFunction cmp = compareFunction(comparefn);

        final List<Object> list = Arrays.asList(array);
        final Object cmpThis = cmp == null || cmp.isStrict() ? ScriptRuntime.UNDEFINED : Global.instance();

        Collections.sort(list, new Comparator<Object>() {
            @Override
            public int compare(final Object x, final Object y) {
                if (x == ScriptRuntime.UNDEFINED && y == ScriptRuntime.UNDEFINED) {
                    return 0;
                } else if (x == ScriptRuntime.UNDEFINED) {
                    return 1;
                } else if (y == ScriptRuntime.UNDEFINED) {
                    return -1;
                }

                if (cmp != null) {
                    try {
                        return (int)Math.signum((double)CALL_CMP.invokeExact(cmp, cmpThis, x, y));
                    } catch (final RuntimeException | Error e) {
                        throw e;
                    } catch (final Throwable t) {
                        throw new RuntimeException(t);
                    }
                }

                return JSType.toString(x).compareTo(JSType.toString(y));
            }
        });

        return list.toArray(new Object[array.length]);
    }

    /**
     * ECMA 15.4.4.11 Array.prototype.sort ( comparefn )
     *
     * @param self       self reference
     * @param comparefn  element comparison function
     * @return sorted array
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object sort(final Object self, final Object comparefn) {
        try {
            final ScriptObject sobj    = (ScriptObject) self;
            final long         len     = JSType.toUint32(sobj.getLength());
            ArrayData          array   = sobj.getArray();

            if (len > 1) {
                // Get only non-missing elements. Missing elements go at the end
                // of the sorted array. So, just don't copy these to sort input.
                final ArrayList<Object> src = new ArrayList<>();
                for (long i = 0; i < len; i = array.nextIndex(i)) {
                    if (array.has((int) i)) {
                        src.add(array.getObject((int) i));
                    }
                }

                final Object[] sorted = sort(src.toArray(), comparefn);

                for (int i = 0; i < sorted.length; i++) {
                    array = array.set(i, sorted[i], true);
                }

                // delete missing elements - which are at the end of sorted array
                if (sorted.length != len) {
                    array = array.delete(sorted.length, len - 1);
                }

                sobj.setArray(array);
           }

            return sobj;
        } catch (final ClassCastException | NullPointerException e) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(self));
        }
    }

    /**
     * ECMA 15.4.4.12 Array.prototype.splice ( start, deleteCount [ item1 [ , item2 [ , ... ] ] ] )
     *
     * @param self self reference
     * @param args arguments
     * @return result of splice
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 2)
    public static Object splice(final Object self, final Object... args) {
        final Object obj = Global.toObject(self);

        if (!(obj instanceof ScriptObject)) {
            return ScriptRuntime.UNDEFINED;
        }

        final Object start = (args.length > 0) ? args[0] : ScriptRuntime.UNDEFINED;
        final Object deleteCount = (args.length > 1) ? args[1] : ScriptRuntime.UNDEFINED;

        Object[] items;

        if (args.length > 2) {
            items = new Object[args.length - 2];
            System.arraycopy(args, 2, items, 0, items.length);
        } else {
            items = ScriptRuntime.EMPTY_ARRAY;
        }

        final ScriptObject sobj                = (ScriptObject)obj;
        final long         len                 = JSType.toUint32(sobj.getLength());
        final long         relativeStart       = JSType.toLong(start);

        final long actualStart = relativeStart < 0 ? Math.max(len + relativeStart, 0) : Math.min(relativeStart, len);
        final long actualDeleteCount = Math.min(Math.max(JSType.toLong(deleteCount), 0), len - actualStart);

        final NativeArray array = new NativeArray(actualDeleteCount);

        for (long k = 0; k < actualDeleteCount; k++) {
            final long from = actualStart + k;

            if (sobj.has(from)) {
                array.defineOwnProperty(ArrayIndex.getArrayIndex(k), sobj.get(from));
            }
        }

        if (items.length < actualDeleteCount) {
            for (long k = actualStart; k < (len - actualDeleteCount); k++) {
                final long from = k + actualDeleteCount;
                final long to   = k + items.length;

                if (sobj.has(from)) {
                    sobj.set(to, sobj.get(from), true);
                } else {
                    sobj.delete(to, true);
                }
            }

            for (long k = len; k > (len - actualDeleteCount + items.length); k--) {
                sobj.delete(k - 1, true);
            }
        } else if (items.length > actualDeleteCount) {
            for (long k = len - actualDeleteCount; k > actualStart; k--) {
                final long from = k + actualDeleteCount - 1;
                final long to   = k + items.length - 1;

                if (sobj.has(from)) {
                    final Object fromValue = sobj.get(from);
                    sobj.set(to, fromValue, true);
                } else {
                    sobj.delete(to, true);
                }
            }
        }

        long k = actualStart;
        for (int i = 0; i < items.length; i++, k++) {
            sobj.set(k, items[i], true);
        }

        final long newLength = len - actualDeleteCount + items.length;
        sobj.set("length", newLength, true);

        return array;
    }

    /**
     * ECMA 15.4.4.13 Array.prototype.unshift ( [ item1 [ , item2 [ , ... ] ] ] )
     *
     * @param self  self reference
     * @param items items for unshift
     * @return unshifted array
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object unshift(final Object self, final Object... items) {
        final Object obj = Global.toObject(self);

        if (!(obj instanceof ScriptObject)) {
            return ScriptRuntime.UNDEFINED;
        }

        final ScriptObject sobj   = (ScriptObject)obj;
        final long         len    = JSType.toUint32(sobj.getLength());

        if (items == null) {
            return ScriptRuntime.UNDEFINED;
        }

        if (bulkable(sobj)) {
            sobj.getArray().shiftRight(items.length);

            for (int j = 0; j < items.length; j++) {
                sobj.setArray(sobj.getArray().set(j, items[j], true));
            }
        } else {
            for (long k = len; k > 0; k--) {
                final long from = k - 1;
                final long to = k + items.length - 1;

                if (sobj.has(from)) {
                    final Object fromValue = sobj.get(from);
                    sobj.set(to, fromValue, true);
                } else {
                    sobj.delete(to, true);
                }
            }

            for (int j = 0; j < items.length; j++) {
                 sobj.set(j, items[j], true);
            }
        }

        final long newLength = len + items.length;
        sobj.set("length", newLength, true);

        return newLength;
    }

    /**
     * ECMA 15.4.4.14 Array.prototype.indexOf ( searchElement [ , fromIndex ] )
     *
     * @param self           self reference
     * @param searchElement  element to search for
     * @param fromIndex      start index of search
     * @return index of element, or -1 if not found
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object indexOf(final Object self, final Object searchElement, final Object fromIndex) {
        try {
            final ScriptObject sobj = (ScriptObject)Global.toObject(self);
            final long         len  = JSType.toUint32(sobj.getLength());
            final long         n    = JSType.toLong(fromIndex);

            if (len == 0 || n >= len) {
                return -1;
            }

            for (long k = Math.max(0, (n < 0) ? (len - Math.abs(n)) : n); k < len; k++) {
                if (sobj.has(k)) {
                    if (ScriptRuntime.EQ_STRICT(sobj.get(k), searchElement)) {
                        return k;
                    }
                }
            }
        } catch (final ClassCastException | NullPointerException e) {
            //fallthru
        }

        return -1;
    }

    /**
     * ECMA 15.4.4.15 Array.prototype.lastIndexOf ( searchElement [ , fromIndex ] )
     *
     * @param self self reference
     * @param args arguments: element to search for and optional from index
     * @return index of element, or -1 if not found
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object lastIndexOf(final Object self, final Object... args) {
        try {
            final ScriptObject sobj = (ScriptObject)Global.toObject(self);
            final long         len  = JSType.toUint32(sobj.getLength());

            if (len == 0) {
                return -1;
            }

            final Object searchElement = (args.length > 0) ? args[0] : ScriptRuntime.UNDEFINED;
            final long   n             = (args.length > 1) ? JSType.toLong(args[1]) : (len - 1);

            for (long k = (n < 0) ? (len - Math.abs(n)) : Math.min(n, len - 1); k >= 0; k--) {
                if (sobj.has(k)) {
                    if (ScriptRuntime.EQ_STRICT(sobj.get(k), searchElement)) {
                        return k;
                    }
                }
            }
        } catch (final ClassCastException | NullPointerException e) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(self));
        }

        return -1;
    }

    /**
     * ECMA 15.4.4.16 Array.prototype.every ( callbackfn [ , thisArg ] )
     *
     * @param self        self reference
     * @param callbackfn  callback function per element
     * @param thisArg     this argument
     * @return true if callback function return true for every element in the array, false otherwise
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object every(final Object self, final Object callbackfn, final Object thisArg) {
        return applyEvery(Global.toObject(self), callbackfn, thisArg);
    }

    private static boolean applyEvery(final Object self, final Object callbackfn, final Object thisArg) {
        return new IteratorAction<Boolean>(Global.toObject(self), callbackfn, thisArg, true) {
            @Override
            protected boolean forEach(final Object val, final long i) throws Throwable {
                return (result = (boolean)EVERY_CALLBACK_INVOKER.invokeExact(callbackfn, thisArg, val, i, self));
            }
        }.apply();
    }

    /**
     * ECMA 15.4.4.17 Array.prototype.some ( callbackfn [ , thisArg ] )
     *
     * @param self        self reference
     * @param callbackfn  callback function per element
     * @param thisArg     this argument
     * @return true if callback function returned true for any element in the array, false otherwise
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object some(final Object self, final Object callbackfn, final Object thisArg) {
        return new IteratorAction<Boolean>(Global.toObject(self), callbackfn, thisArg, false) {
            @Override
            protected boolean forEach(final Object val, final long i) throws Throwable {
                return !(result = (boolean)SOME_CALLBACK_INVOKER.invokeExact(callbackfn, thisArg, val, i, self));
            }
        }.apply();
    }

    /**
     * ECMA 15.4.4.18 Array.prototype.forEach ( callbackfn [ , thisArg ] )
     *
     * @param self        self reference
     * @param callbackfn  callback function per element
     * @param thisArg     this argument
     * @return undefined
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object forEach(final Object self, final Object callbackfn, final Object thisArg) {
        return new IteratorAction<Object>(Global.toObject(self), callbackfn, thisArg, ScriptRuntime.UNDEFINED) {
            @Override
            protected boolean forEach(final Object val, final long i) throws Throwable {
                FOREACH_CALLBACK_INVOKER.invokeExact(callbackfn, thisArg, val, i, self);
                return true;
            }
        }.apply();
    }

    /**
     * ECMA 15.4.4.19 Array.prototype.map ( callbackfn [ , thisArg ] )
     *
     * @param self        self reference
     * @param callbackfn  callback function per element
     * @param thisArg     this argument
     * @return array with elements transformed by map function
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object map(final Object self, final Object callbackfn, final Object thisArg) {
        return new IteratorAction<NativeArray>(Global.toObject(self), callbackfn, thisArg, null) {
            @Override
            protected boolean forEach(final Object val, final long i) throws Throwable {
                final Object r = MAP_CALLBACK_INVOKER.invokeExact(callbackfn, thisArg, val, i, self);
                result.defineOwnProperty(ArrayIndex.getArrayIndex(index), r);
                return true;
            }

            @Override
            public void applyLoopBegin(final ArrayLikeIterator<Object> iter0) {
                // map return array should be of same length as source array
                // even if callback reduces source array length
                result = new NativeArray(iter0.getLength());
            }
        }.apply();
    }

    /**
     * ECMA 15.4.4.20 Array.prototype.filter ( callbackfn [ , thisArg ] )
     *
     * @param self        self reference
     * @param callbackfn  callback function per element
     * @param thisArg     this argument
     * @return filtered array
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object filter(final Object self, final Object callbackfn, final Object thisArg) {
        return new IteratorAction<NativeArray>(Global.toObject(self), callbackfn, thisArg, new NativeArray()) {
            private long to = 0;

            @Override
            protected boolean forEach(final Object val, final long i) throws Throwable {
                if ((boolean)FILTER_CALLBACK_INVOKER.invokeExact(callbackfn, thisArg, val, i, self)) {
                    result.defineOwnProperty(ArrayIndex.getArrayIndex(to++), val);
                }
                return true;
            }
        }.apply();
    }

    private static Object reduceInner(final ArrayLikeIterator<Object> iter, final Object self, final Object... args) {
        final Object  callbackfn          = args.length > 0 ? args[0] : ScriptRuntime.UNDEFINED;
        final boolean initialValuePresent = args.length > 1;

        Object initialValue = initialValuePresent ? args[1] : ScriptRuntime.UNDEFINED;

        if (callbackfn == ScriptRuntime.UNDEFINED) {
            throw typeError("not.a.function", "undefined");
        }

        if (!initialValuePresent) {
            if (iter.hasNext()) {
                initialValue = iter.next();
            } else {
                throw typeError("array.reduce.invalid.init");
            }
        }

        //if initial value is ScriptRuntime.UNDEFINED - step forward once.
        return new IteratorAction<Object>(Global.toObject(self), callbackfn, ScriptRuntime.UNDEFINED, initialValue, iter) {
            @Override
            protected boolean forEach(final Object val, final long i) throws Throwable {
                // TODO: why can't I declare the second arg as Undefined.class?
                result = REDUCE_CALLBACK_INVOKER.invokeExact(callbackfn, ScriptRuntime.UNDEFINED, result, val, i, self);
                return true;
            }
        }.apply();
    }

    /**
     * ECMA 15.4.4.21 Array.prototype.reduce ( callbackfn [ , initialValue ] )
     *
     * @param self self reference
     * @param args arguments to reduce
     * @return accumulated result
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object reduce(final Object self, final Object... args) {
        return reduceInner(arrayLikeIterator(self), self, args);
    }

    /**
     * ECMA 15.4.4.22 Array.prototype.reduceRight ( callbackfn [ , initialValue ] )
     *
     * @param self        self reference
     * @param args arguments to reduce
     * @return accumulated result
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object reduceRight(final Object self, final Object... args) {
        return reduceInner(reverseArrayLikeIterator(self), self, args);
    }

    /**
     * Determine if Java bulk array operations may be used on the underlying
     * storage. This is possible only if the object's prototype chain is empty
     * or each of the prototypes in the chain is empty.
     *
     * @param self the object to examine
     * @return true if optimizable
     */
    private static boolean bulkable(final ScriptObject self) {
        return self.isArray() && !hasInheritedArrayEntries(self) && !self.isLengthNotWritable();
    }

    private static boolean hasInheritedArrayEntries(final ScriptObject self) {
        ScriptObject proto = self.getProto();
        while (proto != null) {
            if (proto.hasArrayEntries()) {
                return true;
            }
            proto = proto.getProto();
        }

        return false;
    }

    private static MethodHandle createIteratorCallbackInvoker(final Class<?> rtype) {
        return Bootstrap.createDynamicInvoker("dyn:call", rtype, Object.class, Object.class, Object.class,
                long.class, Object.class);

    }
}
