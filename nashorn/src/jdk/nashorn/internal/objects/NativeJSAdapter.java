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

import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;
import static jdk.nashorn.internal.lookup.Lookup.MH;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import jdk.internal.dynalink.CallSiteDescriptor;
import jdk.internal.dynalink.linker.GuardedInvocation;
import jdk.internal.dynalink.linker.LinkRequest;
import jdk.nashorn.internal.objects.annotations.Constructor;
import jdk.nashorn.internal.objects.annotations.ScriptClass;
import jdk.nashorn.internal.runtime.FindProperty;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.arrays.ArrayLikeIterator;
import jdk.nashorn.internal.lookup.Lookup;
import jdk.nashorn.internal.scripts.JO;

/**
 * This class is the implementation of the Nashorn-specific global object named {@code JSAdapter}. It can be
 * thought of as the {@link java.lang.reflect.Proxy} equivalent for JavaScript. NativeJSAdapter calls specially named
 * JavaScript methods on an adaptee object when property access/update/call/new/delete is attempted on it. Example:
 *<pre>
 *    var y = {
 *                __get__    : function (name) { ... }
 *                __has__    : function (name) { ... }
 *                __put__    : function (name, value) {...}
 *                __call__   : function (name, arg1, arg2) {...}
 *                __new__    : function (arg1, arg2) {...}
 *                __delete__ : function (name) { ... }
 *                __getIds__ : function () { ... }
 *            };
 *
 *    var x = new JSAdapter(y);
 *
 *    x.i;                        // calls y.__get__
 *    x.foo();                    // calls y.__call__
 *    new x();                    // calls y.__new__
 *    i in x;                     // calls y.__has__
 *    x.p = 10;                   // calls y.__put__
 *    delete x.p;                 // calls y.__delete__
 *    for (i in x) { print(i); }  // calls y.__getIds__
 * </pre>
 * <p>
 * JavaScript caller of adapter object is isolated from the fact that the property access/mutation/deletion are really
 * calls to JavaScript methods on adaptee.
 * </p>
 * <p>
 * JSAdapter constructor can optionally receive an "overrides" object. Properties of overrides object is copied to
 * JSAdapter instance. When user accessed property is one of these, then adaptee's methods like {@code __get__},
 * {@code __put__} etc. are not called for those. This can be used to make certain "preferred" properties that can be
 * accessed in the usual/faster way avoiding proxy mechanism. Example:
 * </p>
 * <pre>
 *     var x = new JSAdapter({ foo: 444, bar: 6546 }) {
 *          __get__: function(name) { return name; }
 *      };
 *
 *     x.foo;           // 444 directly retrieved without __get__ call
 *     x.bar = 'hello'; // "bar" directly set without __put__ call
 *     x.prop           // calls __get__("prop") as 'prop' is not overridden
 * </pre>
 * It is possible to pass a specific prototype for JSAdapter instance by passing three arguments to JSAdapter
 * constructor. So exact signature of JSAdapter constructor is as follows:
 * <pre>
 *     JSAdapter([proto], [overrides], adaptee);
 * </pre>
 * Both proto and overrides are optional - but adaptee is not. When proto is not passed {@code JSAdapter.prototype} is
 * used.
 */
@ScriptClass("JSAdapter")
public final class NativeJSAdapter extends ScriptObject {
    /** object get operation */
    public static final String __get__       = "__get__";
    /** object out operation */
    public static final String __put__       = "__put__";
    /** object call operation */
    public static final String __call__      = "__call__";
    /** object new operation */
    public static final String __new__       = "__new__";
    /** object getIds operation */
    public static final String __getIds__    = "__getIds__";
    /** object getKeys operation */
    public static final String __getKeys__   = "__getKeys__";
    /** object getValues operation */
    public static final String __getValues__ = "__getValues__";
    /** object has operation */
    public static final String __has__       = "__has__";
    /** object delete operation */
    public static final String __delete__    = "__delete__";

    // the new extensibility, sealing and freezing operations

    /** prevent extensions operation */
    public static final String __preventExtensions__ = "__preventExtensions__";
    /** isExtensible extensions operation */
    public static final String __isExtensible__      = "__isExtensible__";
    /** seal operation */
    public static final String __seal__              = "__seal__";
    /** isSealed extensions operation */
    public static final String __isSealed__          = "__isSealed__";
    /** freeze operation */
    public static final String __freeze__            = "__freeze__";
    /** isFrozen extensions operation */
    public static final String __isFrozen__          = "__isFrozen__";

    private final ScriptObject adaptee;
    private final boolean overrides;

    private static final MethodHandle IS_JSADAPTOR = findOwnMH("isJSAdaptor", boolean.class, Object.class, Object.class, MethodHandle.class, Object.class, ScriptFunction.class);

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    static PropertyMap getInitialMap() {
        return $nasgenmap$;
    }

    NativeJSAdapter(final Object overrides, final ScriptObject adaptee, final ScriptObject proto, final PropertyMap map) {
        super(proto, map);
        this.adaptee = wrapAdaptee(adaptee);
        if (overrides instanceof ScriptObject) {
            this.overrides = true;
            final ScriptObject sobj = (ScriptObject)overrides;
            this.addBoundProperties(sobj);
        } else {
            this.overrides = false;
        }
    }

    private static ScriptObject wrapAdaptee(final ScriptObject adaptee) {
        return new JO(adaptee, Global.instance().getObjectMap());
    }

    @Override
    public String getClassName() {
        return "JSAdapter";
    }

    @Override
    public int getInt(final Object key) {
        return (overrides && super.hasOwnProperty(key)) ? super.getInt(key) : callAdapteeInt(__get__, key);
    }

    @Override
    public int getInt(final double key) {
        return (overrides && super.hasOwnProperty(key)) ? super.getInt(key) : callAdapteeInt(__get__, key);
    }

    @Override
    public int getInt(final long key) {
        return (overrides && super.hasOwnProperty(key)) ? super.getInt(key) : callAdapteeInt(__get__, key);
    }

    @Override
    public int getInt(final int key) {
        return (overrides && super.hasOwnProperty(key)) ? super.getInt(key) : callAdapteeInt(__get__, key);
    }

    @Override
    public long getLong(final Object key) {
        return (overrides && super.hasOwnProperty(key)) ? super.getLong(key) : callAdapteeLong(__get__, key);
    }

    @Override
    public long getLong(final double key) {
        return (overrides && super.hasOwnProperty(key)) ? super.getLong(key) : callAdapteeLong(__get__, key);
    }

    @Override
    public long getLong(final long key) {
        return (overrides && super.hasOwnProperty(key)) ? super.getLong(key) : callAdapteeLong(__get__, key);
    }

    @Override
    public long getLong(final int key) {
        return (overrides && super.hasOwnProperty(key)) ? super.getLong(key) : callAdapteeLong(__get__, key);
    }

    @Override
    public double getDouble(final Object key) {
        return (overrides && super.hasOwnProperty(key)) ? super.getDouble(key) : callAdapteeDouble(__get__, key);
    }

    @Override
    public double getDouble(final double key) {
        return (overrides && super.hasOwnProperty(key)) ? super.getDouble(key) : callAdapteeDouble(__get__, key);
    }

    @Override
    public double getDouble(final long key) {
        return (overrides && super.hasOwnProperty(key)) ? super.getDouble(key) : callAdapteeDouble(__get__, key);
    }

    @Override
    public double getDouble(final int key) {
        return (overrides && super.hasOwnProperty(key)) ? super.getDouble(key) : callAdapteeDouble(__get__, key);
    }

    @Override
    public Object get(final Object key) {
        return (overrides && super.hasOwnProperty(key)) ? super.get(key) : callAdaptee(__get__, key);
    }

    @Override
    public Object get(final double key) {
        return (overrides && super.hasOwnProperty(key)) ? super.get(key) : callAdaptee(__get__, key);
    }

    @Override
    public Object get(final long key) {
        return (overrides && super.hasOwnProperty(key)) ? super.get(key) : callAdaptee(__get__, key);
    }

    @Override
    public Object get(final int key) {
        return (overrides && super.hasOwnProperty(key)) ? super.get(key) : callAdaptee(__get__, key);
    }

    @Override
    public void set(final Object key, final int value, final boolean strict) {
        if (overrides && super.hasOwnProperty(key)) {
            super.set(key, value, strict);
        } else {
            callAdaptee(__put__, key, value, strict);
        }
    }

    @Override
    public void set(final Object key, final long value, final boolean strict) {
        if (overrides && super.hasOwnProperty(key)) {
            super.set(key, value, strict);
        } else {
            callAdaptee(__put__, key, value, strict);
        }
    }

    @Override
    public void set(final Object key, final double value, final boolean strict) {
        if (overrides && super.hasOwnProperty(key)) {
            super.set(key, value, strict);
        } else {
            callAdaptee(__put__, key, value, strict);
        }
    }

    @Override
    public void set(final Object key, final Object value, final boolean strict) {
        if (overrides && super.hasOwnProperty(key)) {
            super.set(key, value, strict);
        } else {
            callAdaptee(__put__, key, value, strict);
        }
    }

    @Override
    public void set(final double key, final int value, final boolean strict) {
        if (overrides && super.hasOwnProperty(key)) {
            super.set(key, value, strict);
        } else {
            callAdaptee(__put__, key, value, strict);
        }
    }

    @Override
    public void set(final double key, final long value, final boolean strict) {
        if (overrides && super.hasOwnProperty(key)) {
            super.set(key, value, strict);
        } else {
            callAdaptee(__put__, key, value, strict);
        }
    }

    @Override
    public void set(final double key, final double value, final boolean strict) {
        if (overrides && super.hasOwnProperty(key)) {
            super.set(key, value, strict);
        } else {
            callAdaptee(__put__, key, value, strict);
        }
    }

    @Override
    public void set(final double key, final Object value, final boolean strict) {
        if (overrides && super.hasOwnProperty(key)) {
            super.set(key, value, strict);
        } else {
            callAdaptee(__put__, key, value, strict);
        }
    }

    @Override
    public void set(final long key, final int value, final boolean strict) {
        if (overrides && super.hasOwnProperty(key)) {
            super.set(key, value, strict);
        } else {
            callAdaptee(__put__, key, value, strict);
        }
    }

    @Override
    public void set(final long key, final long value, final boolean strict) {
        if (overrides && super.hasOwnProperty(key)) {
            super.set(key, value, strict);
        } else {
            callAdaptee(__put__, key, value, strict);
        }
    }

    @Override
    public void set(final long key, final double value, final boolean strict) {
        if (overrides && super.hasOwnProperty(key)) {
            super.set(key, value, strict);
        } else {
            callAdaptee(__put__, key, value, strict);
        }
    }

    @Override
    public void set(final long key, final Object value, final boolean strict) {
        if (overrides && super.hasOwnProperty(key)) {
            super.set(key, value, strict);
        } else {
            callAdaptee(__put__, key, value, strict);
        }
    }

    @Override
    public void set(final int key, final int value, final boolean strict) {
        if (overrides && super.hasOwnProperty(key)) {
            super.set(key, value, strict);
        } else {
            callAdaptee(__put__, key, value, strict);
        }
    }

    @Override
    public void set(final int key, final long value, final boolean strict) {
        if (overrides && super.hasOwnProperty(key)) {
            super.set(key, value, strict);
        } else {
            callAdaptee(__put__, key, value, strict);
        }
    }

    @Override
    public void set(final int key, final double value, final boolean strict) {
        if (overrides && super.hasOwnProperty(key)) {
            super.set(key, value, strict);
        } else {
            callAdaptee(__put__, key, value, strict);
        }
    }

    @Override
    public void set(final int key, final Object value, final boolean strict) {
        if (overrides && super.hasOwnProperty(key)) {
            super.set(key, value, strict);
        } else {
            callAdaptee(__put__, key, value, strict);
        }
    }

    @Override
    public boolean has(final Object key) {
        if (overrides && super.hasOwnProperty(key)) {
            return true;
        }

        return JSType.toBoolean(callAdaptee(Boolean.FALSE, __has__, key));
    }

    @Override
    public boolean has(final int key) {
        if (overrides && super.hasOwnProperty(key)) {
            return true;
        }

        return JSType.toBoolean(callAdaptee(Boolean.FALSE, __has__, key));
    }

    @Override
    public boolean has(final long key) {
        if (overrides && super.hasOwnProperty(key)) {
            return true;
        }

        return JSType.toBoolean(callAdaptee(Boolean.FALSE, __has__, key));
    }

    @Override
    public boolean has(final double key) {
        if (overrides && super.hasOwnProperty(key)) {
            return true;
        }

        return JSType.toBoolean(callAdaptee(Boolean.FALSE, __has__, key));
    }

    @Override
    public boolean delete(final int key, final boolean strict) {
        if (overrides && super.hasOwnProperty(key)) {
            return super.delete(key, strict);
        }

        return JSType.toBoolean(callAdaptee(Boolean.TRUE, __delete__, key, strict));
    }

    @Override
    public boolean delete(final long key, final boolean strict) {
        if (overrides && super.hasOwnProperty(key)) {
            return super.delete(key, strict);
        }

        return JSType.toBoolean(callAdaptee(Boolean.TRUE, __delete__, key, strict));
    }

    @Override
    public boolean delete(final double key, final boolean strict) {
        if (overrides && super.hasOwnProperty(key)) {
            return super.delete(key, strict);
        }

        return JSType.toBoolean(callAdaptee(Boolean.TRUE, __delete__, key, strict));
    }

    @Override
    public boolean delete(final Object key, final boolean strict) {
        if (overrides && super.hasOwnProperty(key)) {
            return super.delete(key, strict);
        }

        return JSType.toBoolean(callAdaptee(Boolean.TRUE, __delete__, key, strict));
    }

    @Override
    public Iterator<String> propertyIterator() {
        // Try __getIds__ first, if not found then try __getKeys__
        // In jdk6, we had added "__getIds__" so this is just for compatibility.
        Object func = adaptee.get(__getIds__);
        if (!(func instanceof ScriptFunction)) {
            func = adaptee.get(__getKeys__);
        }

        Object obj;
        if (func instanceof ScriptFunction) {
            obj = ScriptRuntime.apply((ScriptFunction)func, adaptee);
        } else {
            obj = new NativeArray(0);
        }

        final List<String> array = new ArrayList<>();
        for (final Iterator<Object> iter = ArrayLikeIterator.arrayLikeIterator(obj); iter.hasNext(); ) {
            array.add((String)iter.next());
        }

        return array.iterator();
    }


    @Override
    public Iterator<Object> valueIterator() {
        final Object obj = callAdaptee(new NativeArray(0), __getValues__);
        return ArrayLikeIterator.arrayLikeIterator(obj);
    }

    @Override
    public ScriptObject preventExtensions() {
        callAdaptee(__preventExtensions__);
        return this;
    }

    @Override
    public boolean isExtensible() {
        return JSType.toBoolean(callAdaptee(Boolean.TRUE, __isExtensible__));
    }

    @Override
    public ScriptObject seal() {
        callAdaptee(__seal__);
        return this;
    }

    @Override
    public boolean isSealed() {
        return JSType.toBoolean(callAdaptee(Boolean.FALSE, __isSealed__));
    }

    @Override
    public ScriptObject freeze() {
        callAdaptee(__freeze__);
        return this;
    }

    @Override
    public boolean isFrozen() {
        return JSType.toBoolean(callAdaptee(Boolean.FALSE, __isFrozen__));
    }

    /**
     * Constructor
     *
     * @param isNew is this NativeJSAdapter instantiated with the new operator
     * @param self  self reference
     * @param args  arguments ([adaptee], [overrides, adaptee] or [proto, overrides, adaptee]
     * @return new NativeJSAdapter
     */
    @Constructor
    public static Object construct(final boolean isNew, final Object self, final Object... args) {
        Object proto     = UNDEFINED;
        Object overrides = UNDEFINED;
        Object adaptee;

        if (args == null || args.length == 0) {
            throw typeError("not.an.object", "null");
        }

        switch (args.length) {
        case 1:
            adaptee = args[0];
            break;

        case 2:
            overrides = args[0];
            adaptee   = args[1];
            break;

        default:
            //fallthru
        case 3:
            proto = args[0];
            overrides = args[1];
            adaptee = args[2];
            break;
        }

        if (!(adaptee instanceof ScriptObject)) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(adaptee));
        }

        final Global global = Global.instance();
        if (proto != null && !(proto instanceof ScriptObject)) {
            proto = global.getJSAdapterPrototype();
        }

        return new NativeJSAdapter(overrides, (ScriptObject)adaptee, (ScriptObject)proto, global.getJSAdapterMap());
    }

    @Override
    protected GuardedInvocation findNewMethod(final CallSiteDescriptor desc) {
        return findHook(desc, __new__, false);
    }

    @Override
    protected GuardedInvocation findCallMethodMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        if (overrides && super.hasOwnProperty(desc.getNameToken(2))) {
            try {
                final GuardedInvocation inv = super.findCallMethodMethod(desc, request);
                if (inv != null) {
                    return inv;
                }
            } catch (final Exception e) {
                //ignored
            }
        }

        return findHook(desc, __call__);
    }

    @Override
    protected GuardedInvocation findGetMethod(final CallSiteDescriptor desc, final LinkRequest request, final String operation) {
        final String name = desc.getNameToken(CallSiteDescriptor.NAME_OPERAND);
        if (overrides && super.hasOwnProperty(name)) {
            try {
                final GuardedInvocation inv = super.findGetMethod(desc, request, operation);
                if (inv != null) {
                    return inv;
                }
            } catch (final Exception e) {
                //ignored
            }
        }

        switch(operation) {
        case "getProp":
        case "getElem":
            return findHook(desc, __get__);
        case "getMethod":
            final FindProperty find = adaptee.findProperty(__call__, true);
            if (find != null) {
                final ScriptFunctionImpl func = (ScriptFunctionImpl)getObjectValue(find);
                // TODO: It's a shame we need to produce a function bound to this and name, when we'd only need it bound
                // to name. Probably not a big deal, but if we can ever make it leaner, it'd be nice.
                return new GuardedInvocation(MH.dropArguments(MH.constant(Object.class,
                        func.makeBoundFunction(this, new Object[] { name })), 0, Object.class),
                        adaptee.getMap().getProtoGetSwitchPoint(adaptee.getProto(), __call__), testJSAdaptor(adaptee, null, null, null));
            }
            throw typeError("no.such.function", desc.getNameToken(2), ScriptRuntime.safeToString(this));
        default:
            break;
        }

        throw new AssertionError("should not reach here");
    }

    @Override
    protected GuardedInvocation findSetMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        if (overrides && super.hasOwnProperty(desc.getNameToken(CallSiteDescriptor.NAME_OPERAND))) {
            try {
                final GuardedInvocation inv = super.findSetMethod(desc, request);
                if (inv != null) {
                    return inv;
                }
            } catch (final Exception e) {
                //ignored
            }
        }

        return findHook(desc, __put__);
    }

    // -- Internals only below this point
    private Object callAdaptee(final String name, final Object... args) {
        return callAdaptee(UNDEFINED, name, args);
    }

    private double callAdapteeDouble(final String name, final Object... args) {
        return JSType.toNumber(callAdaptee(name, args));
    }

    private long callAdapteeLong(final String name, final Object... args) {
        return JSType.toLong(callAdaptee(name, args));
    }

    private int callAdapteeInt(final String name, final Object... args) {
        return JSType.toInt32(callAdaptee(name, args));
    }

    private Object callAdaptee(final Object retValue, final String name, final Object... args) {
        final Object func = adaptee.get(name);
        if (func instanceof ScriptFunction) {
            return ScriptRuntime.apply((ScriptFunction)func, adaptee, args);
        }
        return retValue;
    }

    private GuardedInvocation findHook(final CallSiteDescriptor desc, final String hook) {
        return findHook(desc, hook, true);
    }

    private GuardedInvocation findHook(final CallSiteDescriptor desc, final String hook, final boolean useName) {
        final FindProperty findData = adaptee.findProperty(hook, true);
        final MethodType type = desc.getMethodType();
        if (findData != null) {
            final String name = desc.getNameTokenCount() > 2 ? desc.getNameToken(2) : null;
            final ScriptFunction func = (ScriptFunction)getObjectValue(findData);

            final MethodHandle methodHandle = getCallMethodHandle(findData, type,
                    useName ? name : null);
            if (methodHandle != null) {
                return new GuardedInvocation(
                        methodHandle,
                        adaptee.getMap().getProtoGetSwitchPoint(adaptee.getProto(), hook),
                        testJSAdaptor(adaptee, findData.getGetter(Object.class), findData.getOwner(), func));
            }
        }

        switch (hook) {
        case __call__:
            throw typeError("no.such.function", desc.getNameToken(2), ScriptRuntime.safeToString(this));
        default:
            final MethodHandle methodHandle = hook.equals(__put__) ?
            MH.asType(Lookup.EMPTY_SETTER, type) :
            Lookup.emptyGetter(type.returnType());
            return new GuardedInvocation(methodHandle, adaptee.getMap().getProtoGetSwitchPoint(adaptee.getProto(), hook), testJSAdaptor(adaptee, null, null, null));
        }
    }

    private static MethodHandle testJSAdaptor(final Object adaptee, final MethodHandle getter, final Object where, final ScriptFunction func) {
        return MH.insertArguments(IS_JSADAPTOR, 1, adaptee, getter, where, func);
    }

    @SuppressWarnings("unused")
    private static boolean isJSAdaptor(final Object self, final Object adaptee, final MethodHandle getter, final Object where, final ScriptFunction func) {
        final boolean res = self instanceof NativeJSAdapter && ((NativeJSAdapter)self).getAdaptee() == adaptee;
        if (res && getter != null) {
            try {
                return getter.invokeExact(where) == func;
            } catch (final RuntimeException | Error e) {
                throw e;
            } catch (final Throwable t) {
                throw new RuntimeException(t);
            }
        }

        return res;
    }

    /**
     * Get the adaptee
     * @return adaptee ScriptObject
     */
    public ScriptObject getAdaptee() {
        return adaptee;
    }

    private static MethodHandle findOwnMH(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findStatic(MethodHandles.lookup(), NativeJSAdapter.class, name, MH.type(rtype, types));
    }
}
