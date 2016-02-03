/*
 * Copyright (c) 2010, 2016, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.nashorn.internal.runtime.arrays.ArrayIndex.isValidArrayIndex;
import static jdk.nashorn.internal.runtime.arrays.ArrayLikeIterator.arrayLikeIterator;
import static jdk.nashorn.internal.runtime.arrays.ArrayLikeIterator.reverseArrayLikeIterator;
import static jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor.CALLSITE_STRICT;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import jdk.dynalink.CallSiteDescriptor;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.LinkRequest;
import jdk.nashorn.api.scripting.JSObject;
import jdk.nashorn.internal.objects.annotations.Attribute;
import jdk.nashorn.internal.objects.annotations.Constructor;
import jdk.nashorn.internal.objects.annotations.Function;
import jdk.nashorn.internal.objects.annotations.Getter;
import jdk.nashorn.internal.objects.annotations.ScriptClass;
import jdk.nashorn.internal.objects.annotations.Setter;
import jdk.nashorn.internal.objects.annotations.SpecializedFunction;
import jdk.nashorn.internal.objects.annotations.SpecializedFunction.LinkLogic;
import jdk.nashorn.internal.objects.annotations.Where;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.Debug;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.OptimisticBuiltins;
import jdk.nashorn.internal.runtime.PropertyDescriptor;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.Undefined;
import jdk.nashorn.internal.runtime.arrays.ArrayData;
import jdk.nashorn.internal.runtime.arrays.ArrayIndex;
import jdk.nashorn.internal.runtime.arrays.ArrayLikeIterator;
import jdk.nashorn.internal.runtime.arrays.ContinuousArrayData;
import jdk.nashorn.internal.runtime.arrays.IntElements;
import jdk.nashorn.internal.runtime.arrays.IteratorAction;
import jdk.nashorn.internal.runtime.arrays.NumericElements;
import jdk.nashorn.internal.runtime.linker.Bootstrap;
import jdk.nashorn.internal.runtime.linker.InvokeByName;

/**
 * Runtime representation of a JavaScript array. NativeArray only holds numeric
 * keyed values. All other values are stored in spill.
 */
@ScriptClass("Array")
public final class NativeArray extends ScriptObject implements OptimisticBuiltins {
    private static final Object JOIN                     = new Object();
    private static final Object EVERY_CALLBACK_INVOKER   = new Object();
    private static final Object SOME_CALLBACK_INVOKER    = new Object();
    private static final Object FOREACH_CALLBACK_INVOKER = new Object();
    private static final Object MAP_CALLBACK_INVOKER     = new Object();
    private static final Object FILTER_CALLBACK_INVOKER  = new Object();
    private static final Object REDUCE_CALLBACK_INVOKER  = new Object();
    private static final Object CALL_CMP                 = new Object();
    private static final Object TO_LOCALE_STRING         = new Object();

    /*
     * Constructors.
     */
    NativeArray() {
        this(ArrayData.initialArray());
    }

    NativeArray(final long length) {
        this(ArrayData.allocate(length));
    }

    NativeArray(final int[] array) {
        this(ArrayData.allocate(array));
    }

    NativeArray(final double[] array) {
        this(ArrayData.allocate(array));
    }

    NativeArray(final long[] array) {
        this(ArrayData.allocate(array.length));

        ArrayData arrayData = this.getArray();
        Class<?> widest = int.class;

        for (int index = 0; index < array.length; index++) {
            final long value = array[index];

            if (widest == int.class && JSType.isRepresentableAsInt(value)) {
                arrayData = arrayData.set(index, (int) value, false);
            } else if (widest != Object.class && JSType.isRepresentableAsDouble(value)) {
                arrayData = arrayData.set(index, (double) value, false);
                widest = double.class;
            } else {
                arrayData = arrayData.set(index, (Object) value, false);
                widest = Object.class;
            }
        }

        this.setArray(arrayData);
    }

    NativeArray(final Object[] array) {
        this(ArrayData.allocate(array.length));

        ArrayData arrayData = this.getArray();

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
        super(global.getArrayPrototype(), $nasgenmap$);
        setArray(arrayData);
        setIsArray();
    }

    @Override
    protected GuardedInvocation findGetIndexMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        final GuardedInvocation inv = getArray().findFastGetIndexMethod(getArray().getClass(), desc, request);
        if (inv != null) {
            return inv;
        }
        return super.findGetIndexMethod(desc, request);
    }

    @Override
    protected GuardedInvocation findSetIndexMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        final GuardedInvocation inv = getArray().findFastSetIndexMethod(getArray().getClass(), desc, request);
        if (inv != null) {
            return inv;
        }

        return super.findSetIndexMethod(desc, request);
    }

    private static InvokeByName getJOIN() {
        return Global.instance().getInvokeByName(JOIN,
                new Callable<InvokeByName>() {
                    @Override
                    public InvokeByName call() {
                        return new InvokeByName("join", ScriptObject.class);
                    }
                });
    }

    private static MethodHandle createIteratorCallbackInvoker(final Object key, final Class<?> rtype) {
        return Global.instance().getDynamicInvoker(key,
            new Callable<MethodHandle>() {
                @Override
                public MethodHandle call() {
                    return Bootstrap.createDynamicCallInvoker(rtype, Object.class, Object.class, Object.class,
                        double.class, Object.class);
                }
            });
    }

    private static MethodHandle getEVERY_CALLBACK_INVOKER() {
        return createIteratorCallbackInvoker(EVERY_CALLBACK_INVOKER, boolean.class);
    }

    private static MethodHandle getSOME_CALLBACK_INVOKER() {
        return createIteratorCallbackInvoker(SOME_CALLBACK_INVOKER, boolean.class);
    }

    private static MethodHandle getFOREACH_CALLBACK_INVOKER() {
        return createIteratorCallbackInvoker(FOREACH_CALLBACK_INVOKER, void.class);
    }

    private static MethodHandle getMAP_CALLBACK_INVOKER() {
        return createIteratorCallbackInvoker(MAP_CALLBACK_INVOKER, Object.class);
    }

    private static MethodHandle getFILTER_CALLBACK_INVOKER() {
        return createIteratorCallbackInvoker(FILTER_CALLBACK_INVOKER, boolean.class);
    }

    private static MethodHandle getREDUCE_CALLBACK_INVOKER() {
        return Global.instance().getDynamicInvoker(REDUCE_CALLBACK_INVOKER,
                new Callable<MethodHandle>() {
                    @Override
                    public MethodHandle call() {
                        return Bootstrap.createDynamicCallInvoker(Object.class, Object.class,
                             Undefined.class, Object.class, Object.class, double.class, Object.class);
                    }
                });
    }

    private static MethodHandle getCALL_CMP() {
        return Global.instance().getDynamicInvoker(CALL_CMP,
                new Callable<MethodHandle>() {
                    @Override
                    public MethodHandle call() {
                        return Bootstrap.createDynamicCallInvoker(double.class,
                            ScriptFunction.class, Object.class, Object.class, Object.class);
                    }
                });
    }

    private static InvokeByName getTO_LOCALE_STRING() {
        return Global.instance().getInvokeByName(TO_LOCALE_STRING,
                new Callable<InvokeByName>() {
                    @Override
                    public InvokeByName call() {
                        return new InvokeByName("toLocaleString", ScriptObject.class, String.class);
                    }
                });
    }

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    @Override
    public String getClassName() {
        return "Array";
    }

    @Override
    public Object getLength() {
        final long length = getArray().length();
        assert length >= 0L;
        if (length <= Integer.MAX_VALUE) {
            return (int)length;
        }
        return length;
    }

    private boolean defineLength(final long oldLen, final PropertyDescriptor oldLenDesc, final PropertyDescriptor desc, final boolean reject) {
        // Step 3a
        if (!desc.has(VALUE)) {
            return super.defineOwnProperty("length", desc, reject);
        }

        // Step 3b
        final PropertyDescriptor newLenDesc = desc;

        // Step 3c and 3d - get new length and convert to long
        final long newLen = NativeArray.validLength(newLenDesc.getValue());

        // Step 3e - note that we need to convert to int or double as long is not considered a JS number type anymore
        newLenDesc.setValue(JSType.toNarrowestNumber(newLen));

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
        final boolean newWritable = !newLenDesc.has(WRITABLE) || newLenDesc.isWritable();
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
        long o = oldLen;
        while (newLen < o) {
            o--;
            final boolean deleteSucceeded = delete(o, false);
            if (!deleteSucceeded) {
                newLenDesc.setValue(o + 1);
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
            newDesc.set(WRITABLE, false, 0);
            return super.defineOwnProperty("length", newDesc, false);
        }

        return true;
    }

    /**
     * ECMA 15.4.5.1 [[DefineOwnProperty]] ( P, Desc, Throw )
     */
    @Override
    public boolean defineOwnProperty(final Object key, final Object propertyDesc, final boolean reject) {
        final PropertyDescriptor desc = toPropertyDescriptor(Global.instance(), propertyDesc);

        // never be undefined as "length" is always defined and can't be deleted for arrays
        // Step 1
        final PropertyDescriptor oldLenDesc = (PropertyDescriptor) super.getOwnPropertyDescriptor("length");

        // Step 2
        // get old length and convert to long. Always a Long/Uint32 but we take the safe road.
        final long oldLen = JSType.toUint32(oldLenDesc.getValue());

        // Step 3
        if ("length".equals(key)) {
            // check for length being made non-writable
            final boolean result = defineLength(oldLen, oldLenDesc, desc, reject);
            if (desc.has(WRITABLE) && !desc.isWritable()) {
                setIsLengthNotWritable();
            }
            return result;
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
                    throw typeError("cant.redefine.property", key.toString(), ScriptRuntime.safeToString(this));
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
     * Spec. mentions use of [[DefineOwnProperty]] for indexed properties in
     * certain places (eg. Array.prototype.map, filter). We can not use ScriptObject.set
     * method in such cases. This is because set method uses inherited setters (if any)
     * from any object in proto chain such as Array.prototype, Object.prototype.
     * This method directly sets a particular element value in the current object.
     *
     * @param index key for property
     * @param value value to define
     */
    @Override
    public final void defineOwnProperty(final int index, final Object value) {
        assert isValidArrayIndex(index) : "invalid array index";
        final long longIndex = ArrayIndex.toLongIndex(index);
        if (longIndex >= getArray().length()) {
            // make array big enough to hold..
            setArray(getArray().ensure(longIndex));
        }
        setArray(getArray().set(index, value, false));
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

    @Override
    public void setIsLengthNotWritable() {
        super.setIsLengthNotWritable();
        setArray(ArrayData.setIsLengthNotWritable(getArray()));
    }

    /**
     * ECMA 15.4.3.2 Array.isArray ( arg )
     *
     * @param self self reference
     * @param arg  argument - object to check
     * @return true if argument is an array
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static boolean isArray(final Object self, final Object arg) {
        return isArray(arg) || (arg instanceof JSObject && ((JSObject)arg).isArray());
    }

    /**
     * Length getter
     * @param self self reference
     * @return the length of the object
     */
    @Getter(attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_CONFIGURABLE)
    public static Object length(final Object self) {
        if (isArray(self)) {
            final long length = ((ScriptObject) self).getArray().length();
            assert length >= 0L;
            // Cast to the narrowest supported numeric type to help optimistic type calculator
            if (length <= Integer.MAX_VALUE) {
                return (int) length;
            }
            return (double) length;
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
            ((ScriptObject)self).setLength(validLength(length));
        }
    }

    /**
     * Prototype length getter
     * @param self self reference
     * @return the length of the object
     */
    @Getter(name = "length", where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_CONFIGURABLE)
    public static Object getProtoLength(final Object self) {
        return length(self);  // Same as instance getter but we can't make nasgen use the same method for prototype
    }

    /**
     * Prototype length setter
     * @param self   self reference
     * @param length new length property
     */
    @Setter(name = "length", where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_CONFIGURABLE)
    public static void setProtoLength(final Object self, final Object length) {
        length(self, length);  // Same as instance setter but we can't make nasgen use the same method for prototype
    }

    static long validLength(final Object length) {
        // ES5 15.4.5.1, steps 3.c and 3.d require two ToNumber conversions here
        final double doubleLength = JSType.toNumber(length);
        if (doubleLength != JSType.toUint32(length)) {
            throw rangeError("inappropriate.array.length", ScriptRuntime.safeToString(length));
        }
        return (long) doubleLength;
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
            final InvokeByName joinInvoker = getJOIN();
            final ScriptObject sobj = (ScriptObject)obj;
            try {
                final Object join = joinInvoker.getGetter().invokeExact(sobj);
                if (Bootstrap.isCallable(join)) {
                    return joinInvoker.getInvoker().invokeExact(join, sobj);
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
     * Assert that an array is numeric, if not throw type error
     * @param self self array to check
     * @return true if numeric
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object assertNumeric(final Object self) {
        if(!(self instanceof NativeArray && ((NativeArray)self).getArray().getOptimisticType().isNumeric())) {
            throw typeError("not.a.numeric.array", ScriptRuntime.safeToString(self));
        }
        return Boolean.TRUE;
    }

    /**
     * ECMA 15.4.4.3 Array.prototype.toLocaleString ( )
     *
     * @param self self reference
     * @return locale specific string representation for array
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static String toLocaleString(final Object self) {
        final StringBuilder sb = new StringBuilder();
        final Iterator<Object> iter = arrayLikeIterator(self, true);

        while (iter.hasNext()) {
            final Object obj = iter.next();

            if (obj != null && obj != ScriptRuntime.UNDEFINED) {
                final Object val = JSType.toScriptObject(obj);

                try {
                    if (val instanceof ScriptObject) {
                        final InvokeByName localeInvoker = getTO_LOCALE_STRING();
                        final ScriptObject sobj           = (ScriptObject)val;
                        final Object       toLocaleString = localeInvoker.getGetter().invokeExact(sobj);

                        if (Bootstrap.isCallable(toLocaleString)) {
                            sb.append((String)localeInvoker.getInvoker().invokeExact(toLocaleString, sobj));
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
    public static NativeArray construct(final boolean newObj, final Object self, final Object... args) {
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
    @SpecializedFunction(isConstructor=true)
    public static NativeArray construct(final boolean newObj, final Object self) {
        return new NativeArray(0);
    }

    /**
     * ECMA 15.4.2.2 new Array (len)
     *
     * Specialized constructor for zero arguments - empty array
     *
     * @param newObj  was the new operator used to instantiate this array
     * @param self    self reference
     * @param element first element
     * @return the new NativeArray
     */
    @SpecializedFunction(isConstructor=true)
    public static Object construct(final boolean newObj, final Object self, final boolean element) {
        return new NativeArray(new Object[] { element });
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
    @SpecializedFunction(isConstructor=true)
    public static NativeArray construct(final boolean newObj, final Object self, final int length) {
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
    @SpecializedFunction(isConstructor=true)
    public static NativeArray construct(final boolean newObj, final Object self, final long length) {
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
    @SpecializedFunction(isConstructor=true)
    public static NativeArray construct(final boolean newObj, final Object self, final double length) {
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
     * @param arg argument
     * @return resulting NativeArray
     */
    @SpecializedFunction(linkLogic=ConcatLinkLogic.class)
    public static NativeArray concat(final Object self, final int arg) {
        final ContinuousArrayData newData = getContinuousArrayDataCCE(self, Integer.class).copy(); //get at least an integer data copy of this data
        newData.fastPush(arg); //add an integer to its end
        return new NativeArray(newData);
    }

    /**
     * ECMA 15.4.4.4 Array.prototype.concat ( [ item1 [ , item2 [ , ... ] ] ] )
     *
     * @param self self reference
     * @param arg argument
     * @return resulting NativeArray
     */
    @SpecializedFunction(linkLogic=ConcatLinkLogic.class)
    public static NativeArray concat(final Object self, final long arg) {
        final ContinuousArrayData newData = getContinuousArrayDataCCE(self, Long.class).copy(); //get at least a long array data copy of this data
        newData.fastPush(arg); //add a long at the end
        return new NativeArray(newData);
    }

    /**
     * ECMA 15.4.4.4 Array.prototype.concat ( [ item1 [ , item2 [ , ... ] ] ] )
     *
     * @param self self reference
     * @param arg argument
     * @return resulting NativeArray
     */
    @SpecializedFunction(linkLogic=ConcatLinkLogic.class)
    public static NativeArray concat(final Object self, final double arg) {
        final ContinuousArrayData newData = getContinuousArrayDataCCE(self, Double.class).copy(); //get at least a number array data copy of this data
        newData.fastPush(arg); //add a double at the end
        return new NativeArray(newData);
    }

    /**
     * ECMA 15.4.4.4 Array.prototype.concat ( [ item1 [ , item2 [ , ... ] ] ] )
     *
     * @param self self reference
     * @param arg argument
     * @return resulting NativeArray
     */
    @SpecializedFunction(linkLogic=ConcatLinkLogic.class)
    public static NativeArray concat(final Object self, final Object arg) {
        //arg is [NativeArray] of same type.
        final ContinuousArrayData selfData = getContinuousArrayDataCCE(self);
        final ContinuousArrayData newData;

        if (arg instanceof NativeArray) {
            final ContinuousArrayData argData = (ContinuousArrayData)((NativeArray)arg).getArray();
            if (argData.isEmpty()) {
                newData = selfData.copy();
            } else if (selfData.isEmpty()) {
                newData = argData.copy();
            } else {
                final Class<?> widestElementType = selfData.widest(argData).getBoxedElementType();
                newData = ((ContinuousArrayData)selfData.convert(widestElementType)).fastConcat((ContinuousArrayData)argData.convert(widestElementType));
            }
        } else {
            newData = getContinuousArrayDataCCE(self, Object.class).copy();
            newData.fastPush(arg);
        }

        return new NativeArray(newData);
    }

    /**
     * ECMA 15.4.4.4 Array.prototype.concat ( [ item1 [ , item2 [ , ... ] ] ] )
     *
     * @param self self reference
     * @param args arguments
     * @return resulting NativeArray
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static NativeArray concat(final Object self, final Object... args) {
        final ArrayList<Object> list = new ArrayList<>();

        concatToList(list, Global.toObject(self));

        for (final Object obj : args) {
            concatToList(list, obj);
        }

        return new NativeArray(list.toArray());
    }

    private static void concatToList(final ArrayList<Object> list, final Object obj) {
        final boolean isScriptArray  = isArray(obj);
        final boolean isScriptObject = isScriptArray || obj instanceof ScriptObject;
        if (isScriptArray || obj instanceof Iterable || (obj != null && obj.getClass().isArray())) {
            final Iterator<Object> iter = arrayLikeIterator(obj, true);
            if (iter.hasNext()) {
                for (int i = 0; iter.hasNext(); ++i) {
                    final Object value = iter.next();
                    final boolean lacksIndex = obj != null && !((ScriptObject)obj).has(i);
                    if (value == ScriptRuntime.UNDEFINED && isScriptObject && lacksIndex) {
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
    public static String join(final Object self, final Object separator) {
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
     * Specialization of pop for ContinuousArrayData
     *   The link guard checks that the array is continuous AND not empty.
     *   The runtime guard checks that the guard is continuous (CCE otherwise)
     *
     * Primitive specialization, {@link LinkLogic}
     *
     * @param self self reference
     * @return element popped
     * @throws ClassCastException if array is empty, facilitating Undefined return value
     */
    @SpecializedFunction(name="pop", linkLogic=PopLinkLogic.class)
    public static int popInt(final Object self) {
        //must be non empty IntArrayData
        return getContinuousNonEmptyArrayDataCCE(self, IntElements.class).fastPopInt();
    }

    /**
     * Specialization of pop for ContinuousArrayData
     *
     * Primitive specialization, {@link LinkLogic}
     *
     * @param self self reference
     * @return element popped
     * @throws ClassCastException if array is empty, facilitating Undefined return value
     */
    @SpecializedFunction(name="pop", linkLogic=PopLinkLogic.class)
    public static double popDouble(final Object self) {
        //must be non empty int long or double array data
        return getContinuousNonEmptyArrayDataCCE(self, NumericElements.class).fastPopDouble();
    }

    /**
     * Specialization of pop for ContinuousArrayData
     *
     * Primitive specialization, {@link LinkLogic}
     *
     * @param self self reference
     * @return element popped
     * @throws ClassCastException if array is empty, facilitating Undefined return value
     */
    @SpecializedFunction(name="pop", linkLogic=PopLinkLogic.class)
    public static Object popObject(final Object self) {
        //can be any data, because the numeric ones will throw cce and force relink
        return getContinuousArrayDataCCE(self, null).fastPopObject();
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
                sobj.set("length", 0, CALLSITE_STRICT);
                return ScriptRuntime.UNDEFINED;
            }

            final long   index   = len - 1;
            final Object element = sobj.get(index);

            sobj.delete(index, true);
            sobj.set("length", index, CALLSITE_STRICT);

            return element;
        } catch (final ClassCastException | NullPointerException e) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(self));
        }
    }

    /**
     * ECMA 15.4.4.7 Array.prototype.push (args...)
     *
     * Primitive specialization, {@link LinkLogic}
     *
     * @param self self reference
     * @param arg a primitive to push
     * @return array length after push
     */
    @SpecializedFunction(linkLogic=PushLinkLogic.class)
    public static double push(final Object self, final int arg) {
        return getContinuousArrayDataCCE(self, Integer.class).fastPush(arg);
    }

    /**
     * ECMA 15.4.4.7 Array.prototype.push (args...)
     *
     * Primitive specialization, {@link LinkLogic}
     *
     * @param self self reference
     * @param arg a primitive to push
     * @return array length after push
     */
    @SpecializedFunction(linkLogic=PushLinkLogic.class)
    public static double push(final Object self, final long arg) {
        return getContinuousArrayDataCCE(self, Long.class).fastPush(arg);
    }

    /**
     * ECMA 15.4.4.7 Array.prototype.push (args...)
     *
     * Primitive specialization, {@link LinkLogic}
     *
     * @param self self reference
     * @param arg a primitive to push
     * @return array length after push
     */
    @SpecializedFunction(linkLogic=PushLinkLogic.class)
    public static double push(final Object self, final double arg) {
        return getContinuousArrayDataCCE(self, Double.class).fastPush(arg);
    }

    /**
     * ECMA 15.4.4.7 Array.prototype.push (args...)
     *
     * Primitive specialization, {@link LinkLogic}
     *
     * @param self self reference
     * @param arg a primitive to push
     * @return array length after push
     */
    @SpecializedFunction(name="push", linkLogic=PushLinkLogic.class)
    public static double pushObject(final Object self, final Object arg) {
        return getContinuousArrayDataCCE(self, Object.class).fastPush(arg);
    }

    /**
     * ECMA 15.4.4.7 Array.prototype.push (args...)
     *
     * @param self self reference
     * @param args arguments to push
     * @return array length after pushes
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object push(final Object self, final Object... args) {
        try {
            final ScriptObject sobj   = (ScriptObject)self;

            if (bulkable(sobj) && sobj.getArray().length() + args.length <= JSType.MAX_UINT) {
                final ArrayData newData = sobj.getArray().push(true, args);
                sobj.setArray(newData);
                return JSType.toNarrowestNumber(newData.length());
            }

            long len = JSType.toUint32(sobj.getLength());
            for (final Object element : args) {
                sobj.set(len++, element, CALLSITE_STRICT);
            }
            sobj.set("length", len, CALLSITE_STRICT);

            return JSType.toNarrowestNumber(len);
        } catch (final ClassCastException | NullPointerException e) {
            throw typeError(Context.getGlobal(), e, "not.an.object", ScriptRuntime.safeToString(self));
        }
    }

    /**
     * ECMA 15.4.4.7 Array.prototype.push (args...) specialized for single object argument
     *
     * @param self self reference
     * @param arg argument to push
     * @return array after pushes
     */
    @SpecializedFunction
    public static double push(final Object self, final Object arg) {
        try {
            final ScriptObject sobj = (ScriptObject)self;
            final ArrayData arrayData = sobj.getArray();
            final long length = arrayData.length();
            if (bulkable(sobj) && length < JSType.MAX_UINT) {
                sobj.setArray(arrayData.push(true, arg));
                return length + 1;
            }

            long len = JSType.toUint32(sobj.getLength());
            sobj.set(len++, arg, CALLSITE_STRICT);
            sobj.set("length", len, CALLSITE_STRICT);
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
                    sobj.set(lower, upperValue, CALLSITE_STRICT);
                    sobj.set(upper, lowerValue, CALLSITE_STRICT);
                } else if (!lowerExists && upperExists) {
                    sobj.set(lower, upperValue, CALLSITE_STRICT);
                    sobj.delete(upper, true);
                } else if (lowerExists && !upperExists) {
                    sobj.delete(lower, true);
                    sobj.set(upper, lowerValue, CALLSITE_STRICT);
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
                boolean hasPrevious = true;
                for (long k = 1; k < len; k++) {
                    final boolean hasCurrent = sobj.has(k);
                    if (hasCurrent) {
                        sobj.set(k - 1, sobj.get(k), CALLSITE_STRICT);
                    } else if (hasPrevious) {
                        sobj.delete(k - 1, true);
                    }
                    hasPrevious = hasCurrent;
                }
            }
            sobj.delete(--len, true);
        } else {
            len = 0;
        }

        sobj.set("length", len, CALLSITE_STRICT);

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
        if (!(obj instanceof ScriptObject)) {
            return ScriptRuntime.UNDEFINED;
        }

        final ScriptObject sobj                = (ScriptObject)obj;
        final long         len                 = JSType.toUint32(sobj.getLength());
        final long         relativeStart       = JSType.toLong(start);
        final long         relativeEnd         = end == ScriptRuntime.UNDEFINED ? len : JSType.toLong(end);

        long k = relativeStart < 0 ? Math.max(len + relativeStart, 0) : Math.min(relativeStart, len);
        final long finale = relativeEnd < 0 ? Math.max(len + relativeEnd, 0) : Math.min(relativeEnd, len);

        if (k >= finale) {
            return new NativeArray(0);
        }

        if (bulkable(sobj)) {
            return new NativeArray(sobj.getArray().slice(k, finale));
        }

        // Construct array with proper length to have a deleted filter on undefined elements
        final NativeArray copy = new NativeArray(finale - k);
        for (long n = 0; k < finale; n++, k++) {
            if (sobj.has(k)) {
                copy.defineOwnProperty(ArrayIndex.getArrayIndex(n), sobj.get(k));
            }
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

        try {
            Collections.sort(list, new Comparator<Object>() {
                private final MethodHandle call_cmp = getCALL_CMP();
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
                            return (int)Math.signum((double)call_cmp.invokeExact(cmp, cmpThis, x, y));
                        } catch (final RuntimeException | Error e) {
                            throw e;
                        } catch (final Throwable t) {
                            throw new RuntimeException(t);
                        }
                    }

                    return JSType.toString(x).compareTo(JSType.toString(y));
                }
            });
        } catch (final IllegalArgumentException iae) {
            // Collections.sort throws IllegalArgumentException when
            // Comparison method violates its general contract

            // See ECMA spec 15.4.4.11 Array.prototype.sort (comparefn).
            // If "comparefn" is not undefined and is not a consistent
            // comparison function for the elements of this array, the
            // behaviour of sort is implementation-defined.
        }

        return list.toArray(new Object[0]);
    }

    /**
     * ECMA 15.4.4.11 Array.prototype.sort ( comparefn )
     *
     * @param self       self reference
     * @param comparefn  element comparison function
     * @return sorted array
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static ScriptObject sort(final Object self, final Object comparefn) {
        try {
            final ScriptObject sobj    = (ScriptObject) self;
            final long         len     = JSType.toUint32(sobj.getLength());
            ArrayData          array   = sobj.getArray();

            if (len > 1) {
                // Get only non-missing elements. Missing elements go at the end
                // of the sorted array. So, just don't copy these to sort input.
                final ArrayList<Object> src = new ArrayList<>();

                for (final Iterator<Long> iter = array.indexIterator(); iter.hasNext(); ) {
                    final long index = iter.next();
                    if (index >= len) {
                        break;
                    }
                    src.add(array.getObject((int)index));
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

        final Object start = args.length > 0 ? args[0] : ScriptRuntime.UNDEFINED;
        final Object deleteCount = args.length > 1 ? args[1] : ScriptRuntime.UNDEFINED;

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

        NativeArray returnValue;

        if (actualStart <= Integer.MAX_VALUE && actualDeleteCount <= Integer.MAX_VALUE && bulkable(sobj)) {
            try {
                returnValue =  new NativeArray(sobj.getArray().fastSplice((int)actualStart, (int)actualDeleteCount, items.length));

                // Since this is a dense bulkable array we can use faster defineOwnProperty to copy new elements
                int k = (int) actualStart;
                for (int i = 0; i < items.length; i++, k++) {
                    sobj.defineOwnProperty(k, items[i]);
                }
            } catch (final UnsupportedOperationException uoe) {
                returnValue = slowSplice(sobj, actualStart, actualDeleteCount, items, len);
            }
        } else {
            returnValue = slowSplice(sobj, actualStart, actualDeleteCount, items, len);
        }

        return returnValue;
    }

    private static NativeArray slowSplice(final ScriptObject sobj, final long start, final long deleteCount, final Object[] items, final long len) {

        final NativeArray array = new NativeArray(deleteCount);

        for (long k = 0; k < deleteCount; k++) {
            final long from = start + k;

            if (sobj.has(from)) {
                array.defineOwnProperty(ArrayIndex.getArrayIndex(k), sobj.get(from));
            }
        }

        if (items.length < deleteCount) {
            for (long k = start; k < len - deleteCount; k++) {
                final long from = k + deleteCount;
                final long to   = k + items.length;

                if (sobj.has(from)) {
                    sobj.set(to, sobj.get(from), CALLSITE_STRICT);
                } else {
                    sobj.delete(to, true);
                }
            }

            for (long k = len; k > len - deleteCount + items.length; k--) {
                sobj.delete(k - 1, true);
            }
        } else if (items.length > deleteCount) {
            for (long k = len - deleteCount; k > start; k--) {
                final long from = k + deleteCount - 1;
                final long to   = k + items.length - 1;

                if (sobj.has(from)) {
                    final Object fromValue = sobj.get(from);
                    sobj.set(to, fromValue, CALLSITE_STRICT);
                } else {
                    sobj.delete(to, true);
                }
            }
        }

        long k = start;
        for (int i = 0; i < items.length; i++, k++) {
            sobj.set(k, items[i], CALLSITE_STRICT);
        }

        final long newLength = len - deleteCount + items.length;
        sobj.set("length", newLength, CALLSITE_STRICT);

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
                    sobj.set(to, fromValue, CALLSITE_STRICT);
                } else {
                    sobj.delete(to, true);
                }
            }

            for (int j = 0; j < items.length; j++) {
                sobj.set(j, items[j], CALLSITE_STRICT);
            }
        }

        final long newLength = len + items.length;
        sobj.set("length", newLength, CALLSITE_STRICT);

        return JSType.toNarrowestNumber(newLength);
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
    public static double indexOf(final Object self, final Object searchElement, final Object fromIndex) {
        try {
            final ScriptObject sobj = (ScriptObject)Global.toObject(self);
            final long         len  = JSType.toUint32(sobj.getLength());
            if (len == 0) {
                return -1;
            }

            final long         n = JSType.toLong(fromIndex);
            if (n >= len) {
                return -1;
            }


            for (long k = Math.max(0, n < 0 ? len - Math.abs(n) : n); k < len; k++) {
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
    public static double lastIndexOf(final Object self, final Object... args) {
        try {
            final ScriptObject sobj = (ScriptObject)Global.toObject(self);
            final long         len  = JSType.toUint32(sobj.getLength());

            if (len == 0) {
                return -1;
            }

            final Object searchElement = args.length > 0 ? args[0] : ScriptRuntime.UNDEFINED;
            final long   n             = args.length > 1 ? JSType.toLong(args[1]) : len - 1;

            for (long k = n < 0 ? len - Math.abs(n) : Math.min(n, len - 1); k >= 0; k--) {
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
    public static boolean every(final Object self, final Object callbackfn, final Object thisArg) {
        return applyEvery(Global.toObject(self), callbackfn, thisArg);
    }

    private static boolean applyEvery(final Object self, final Object callbackfn, final Object thisArg) {
        return new IteratorAction<Boolean>(Global.toObject(self), callbackfn, thisArg, true) {
            private final MethodHandle everyInvoker = getEVERY_CALLBACK_INVOKER();

            @Override
            protected boolean forEach(final Object val, final double i) throws Throwable {
                return result = (boolean)everyInvoker.invokeExact(callbackfn, thisArg, val, i, self);
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
    public static boolean some(final Object self, final Object callbackfn, final Object thisArg) {
        return new IteratorAction<Boolean>(Global.toObject(self), callbackfn, thisArg, false) {
            private final MethodHandle someInvoker = getSOME_CALLBACK_INVOKER();

            @Override
            protected boolean forEach(final Object val, final double i) throws Throwable {
                return !(result = (boolean)someInvoker.invokeExact(callbackfn, thisArg, val, i, self));
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
            private final MethodHandle forEachInvoker = getFOREACH_CALLBACK_INVOKER();

            @Override
            protected boolean forEach(final Object val, final double i) throws Throwable {
                forEachInvoker.invokeExact(callbackfn, thisArg, val, i, self);
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
    public static NativeArray map(final Object self, final Object callbackfn, final Object thisArg) {
        return new IteratorAction<NativeArray>(Global.toObject(self), callbackfn, thisArg, null) {
            private final MethodHandle mapInvoker = getMAP_CALLBACK_INVOKER();

            @Override
            protected boolean forEach(final Object val, final double i) throws Throwable {
                final Object r = mapInvoker.invokeExact(callbackfn, thisArg, val, i, self);
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
    public static NativeArray filter(final Object self, final Object callbackfn, final Object thisArg) {
        return new IteratorAction<NativeArray>(Global.toObject(self), callbackfn, thisArg, new NativeArray()) {
            private long to = 0;
            private final MethodHandle filterInvoker = getFILTER_CALLBACK_INVOKER();

            @Override
            protected boolean forEach(final Object val, final double i) throws Throwable {
                if ((boolean)filterInvoker.invokeExact(callbackfn, thisArg, val, i, self)) {
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
            private final MethodHandle reduceInvoker = getREDUCE_CALLBACK_INVOKER();

            @Override
            protected boolean forEach(final Object val, final double i) throws Throwable {
                // TODO: why can't I declare the second arg as Undefined.class?
                result = reduceInvoker.invokeExact(callbackfn, ScriptRuntime.UNDEFINED, result, val, i, self);
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

    @Override
    public String toString() {
        return "NativeArray@" + Debug.id(this) + " [" + getArray().getClass().getSimpleName() + ']';
    }

    @Override
    public SpecializedFunction.LinkLogic getLinkLogic(final Class<? extends LinkLogic> clazz) {
        if (clazz == PushLinkLogic.class) {
            return PushLinkLogic.INSTANCE;
        } else if (clazz == PopLinkLogic.class) {
            return PopLinkLogic.INSTANCE;
        } else if (clazz == ConcatLinkLogic.class) {
            return ConcatLinkLogic.INSTANCE;
        }
        return null;
    }

    @Override
    public boolean hasPerInstanceAssumptions() {
        return true; //length writable switchpoint
    }

    /**
     * This is an abstract super class that contains common functionality for all
     * specialized optimistic builtins in NativeArray. For example, it handles the
     * modification switchpoint which is touched when length is written.
     */
    private static abstract class ArrayLinkLogic extends SpecializedFunction.LinkLogic {
        protected ArrayLinkLogic() {
        }

        protected static ContinuousArrayData getContinuousArrayData(final Object self) {
            try {
                //cast to NativeArray, to avoid cases like x = {0:0, 1:1}, x.length = 2, where we can't use the array push/pop
                return (ContinuousArrayData)((NativeArray)self).getArray();
            } catch (final Exception e) {
                return null;
            }
        }

        /**
         * Push and pop callsites can throw ClassCastException as a mechanism to have them
         * relinked - this enabled fast checks of the kind of ((IntArrayData)arrayData).push(x)
         * for an IntArrayData only push - if this fails, a CCE will be thrown and we will relink
         */
        @Override
        public Class<? extends Throwable> getRelinkException() {
            return ClassCastException.class;
        }
    }

    /**
     * This is linker logic for optimistic concatenations
     */
    private static final class ConcatLinkLogic extends ArrayLinkLogic {
        private static final LinkLogic INSTANCE = new ConcatLinkLogic();

        @Override
        public boolean canLink(final Object self, final CallSiteDescriptor desc, final LinkRequest request) {
            final Object[] args = request.getArguments();

            if (args.length != 3) { //single argument check
                return false;
            }

            final ContinuousArrayData selfData = getContinuousArrayData(self);
            if (selfData == null) {
                return false;
            }

            final Object arg = args[2];
            //args[2] continuousarray or non arraydata, let past non array datas
            if (arg instanceof NativeArray) {
                final ContinuousArrayData argData = getContinuousArrayData(arg);
                if (argData == null) {
                    return false;
                }
            }

            return true;
        }
    }

    /**
     * This is linker logic for optimistic pushes
     */
    private static final class PushLinkLogic extends ArrayLinkLogic {
        private static final LinkLogic INSTANCE = new PushLinkLogic();

        @Override
        public boolean canLink(final Object self, final CallSiteDescriptor desc, final LinkRequest request) {
            return getContinuousArrayData(self) != null;
        }
    }

    /**
     * This is linker logic for optimistic pops
     */
    private static final class PopLinkLogic extends ArrayLinkLogic {
        private static final LinkLogic INSTANCE = new PopLinkLogic();

        /**
         * We need to check if we are dealing with a continuous non empty array data here,
         * as pop with a primitive return value returns undefined for arrays with length 0
         */
        @Override
        public boolean canLink(final Object self, final CallSiteDescriptor desc, final LinkRequest request) {
            final ContinuousArrayData data = getContinuousNonEmptyArrayData(self);
            if (data != null) {
                final Class<?> elementType = data.getElementType();
                final Class<?> returnType  = desc.getMethodType().returnType();
                final boolean  typeFits    = JSType.getAccessorTypeIndex(returnType) >= JSType.getAccessorTypeIndex(elementType);
                return typeFits;
            }
            return false;
        }

        private static ContinuousArrayData getContinuousNonEmptyArrayData(final Object self) {
            final ContinuousArrayData data = getContinuousArrayData(self);
            if (data != null) {
                return data.length() == 0 ? null : data;
            }
            return null;
        }
    }

    //runtime calls for push and pops. they could be used as guards, but they also perform the runtime logic,
    //so rather than synthesizing them into a guard method handle that would also perform the push on the
    //retrieved receiver, we use this as runtime logic

    //TODO - fold these into the Link logics, but I'll do that as a later step, as I want to do a checkin
    //where everything works first

    private static <T> ContinuousArrayData getContinuousNonEmptyArrayDataCCE(final Object self, final Class<T> clazz) {
        try {
            @SuppressWarnings("unchecked")
            final ContinuousArrayData data = (ContinuousArrayData)(T)((NativeArray)self).getArray();
            if (data.length() != 0L) {
                return data; //if length is 0 we cannot pop and have to relink, because then we'd have to return an undefined, which is a wider type than e.g. int
           }
        } catch (final NullPointerException e) {
            //fallthru
        }
        throw new ClassCastException();
    }

    private static ContinuousArrayData getContinuousArrayDataCCE(final Object self) {
        try {
            return (ContinuousArrayData)((NativeArray)self).getArray();
         } catch (final NullPointerException e) {
             throw new ClassCastException();
         }
    }

    private static ContinuousArrayData getContinuousArrayDataCCE(final Object self, final Class<?> elementType) {
        try {
           return (ContinuousArrayData)((NativeArray)self).getArray(elementType); //ensure element type can fit "elementType"
        } catch (final NullPointerException e) {
            throw new ClassCastException();
        }
    }
}
