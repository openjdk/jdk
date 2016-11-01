/*
 * Copyright (c) 2010, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.nashorn.internal.lookup.Lookup.MH;
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;
import static jdk.nashorn.internal.runtime.UnwarrantedOptimismException.INVALID_PROGRAM_POINT;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import jdk.dynalink.CallSiteDescriptor;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.LinkRequest;
import jdk.nashorn.internal.lookup.Lookup;
import jdk.nashorn.internal.objects.annotations.Constructor;
import jdk.nashorn.internal.objects.annotations.ScriptClass;
import jdk.nashorn.internal.runtime.FindProperty;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.arrays.ArrayLikeIterator;
import jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor;
import jdk.nashorn.internal.scripts.JO;

/**
 * This class is the implementation of the Nashorn-specific global object named {@code JSAdapter}. It can be thought of
 * as the {@link java.lang.reflect.Proxy} equivalent for JavaScript. A {@code NativeJSAdapter} calls specially named
 * JavaScript methods on an adaptee object when property access/update/call/new/delete is attempted on it. Example:
 *<pre>
 *    var y = {
 *                __get__     : function (name) { ... }
 *                __has__     : function (name) { ... }
 *                __put__     : function (name, value) {...}
 *                __call__    : function (name, arg1, arg2) {...}
 *                __new__     : function (arg1, arg2) {...}
 *                __delete__  : function (name) { ... }
 *                __getKeys__ : function () { ... }
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
 *    for (i in x) { print(i); }  // calls y.__getKeys__
 * </pre>
 * <p>
 * The {@code __getKeys__} and {@code __getIds__} properties are mapped to the same operation. Concrete
 * {@code JSAdapter} implementations are expected to use only one of these. As {@code __getIds__} exists for
 * compatibility reasons only, use of {@code __getKeys__} is recommended.
 * </p>
 * <p>
 * The JavaScript caller of an adapter object is oblivious of the property access/mutation/deletion's being adapted.
 * </p>
 * <p>
 * The {@code JSAdapter} constructor can optionally receive an "overrides" object. The properties of overrides object
 * are copied to the {@code JSAdapter} instance. In case user-accessed properties are among these, the adaptee's methods
 * like {@code __get__}, {@code __put__} etc. are not called for them. This can be used to make certain "preferred"
 * properties that can be accessed in the usual/faster way avoiding the proxy mechanism. Example:
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
 * It is possible to pass a specific prototype for the {@code JSAdapter} instance by passing three arguments to the
 * {@code JSAdapter} constructor. The exact signature of the {@code JSAdapter} constructor is as follows:
 * <pre>
 *     JSAdapter([proto], [overrides], adaptee);
 * </pre>
 * Both the {@code proto} and {@code overrides} arguments are optional - but {@code adaptee} is not. When {@code proto}
 * is not passed, {@code JSAdapter.prototype} is used.
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
    /** object getIds operation (provided for compatibility reasons; use of getKeys is preferred) */
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

    private static final MethodHandle IS_JSADAPTER = findOwnMH("isJSAdapter", boolean.class, Object.class, Object.class, MethodHandle.class, Object.class, ScriptFunction.class);

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

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
        return new JO(adaptee);
    }

    @Override
    public String getClassName() {
        return "JSAdapter";
    }

    @Override
    public int getInt(final Object key, final int programPoint) {
        return (overrides && super.hasOwnProperty(key)) ? super.getInt(key, programPoint) : callAdapteeInt(programPoint, __get__, key);
    }

    @Override
    public int getInt(final double key, final int programPoint) {
        return (overrides && super.hasOwnProperty(key)) ? super.getInt(key, programPoint) : callAdapteeInt(programPoint, __get__, key);
    }

    @Override
    public int getInt(final int key, final int programPoint) {
        return (overrides && super.hasOwnProperty(key)) ? super.getInt(key, programPoint) : callAdapteeInt(programPoint, __get__, key);
    }

    @Override
    public double getDouble(final Object key, final int programPoint) {
        return (overrides && super.hasOwnProperty(key)) ? super.getDouble(key, programPoint) : callAdapteeDouble(programPoint, __get__, key);
    }

    @Override
    public double getDouble(final double key, final int programPoint) {
        return (overrides && super.hasOwnProperty(key)) ? super.getDouble(key, programPoint) : callAdapteeDouble(programPoint, __get__, key);
    }

    @Override
    public double getDouble(final int key, final int programPoint) {
        return (overrides && super.hasOwnProperty(key)) ? super.getDouble(key, programPoint) : callAdapteeDouble(programPoint, __get__, key);
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
    public Object get(final int key) {
        return (overrides && super.hasOwnProperty(key)) ? super.get(key) : callAdaptee(__get__, key);
    }

    @Override
    public void set(final Object key, final int value, final int flags) {
        if (overrides && super.hasOwnProperty(key)) {
            super.set(key, value, flags);
        } else {
            callAdaptee(__put__, key, value, flags);
        }
    }

    @Override
    public void set(final Object key, final double value, final int flags) {
        if (overrides && super.hasOwnProperty(key)) {
            super.set(key, value, flags);
        } else {
            callAdaptee(__put__, key, value, flags);
        }
    }

    @Override
    public void set(final Object key, final Object value, final int flags) {
        if (overrides && super.hasOwnProperty(key)) {
            super.set(key, value, flags);
        } else {
            callAdaptee(__put__, key, value, flags);
        }
    }

    @Override
    public void set(final double key, final int value, final int flags) {
        if (overrides && super.hasOwnProperty(key)) {
            super.set(key, value, flags);
        } else {
            callAdaptee(__put__, key, value, flags);
        }
    }

    @Override
    public void set(final double key, final double value, final int flags) {
        if (overrides && super.hasOwnProperty(key)) {
            super.set(key, value, flags);
        } else {
            callAdaptee(__put__, key, value, flags);
        }
    }

    @Override
    public void set(final double key, final Object value, final int flags) {
        if (overrides && super.hasOwnProperty(key)) {
            super.set(key, value, flags);
        } else {
            callAdaptee(__put__, key, value, flags);
        }
    }

    @Override
    public void set(final int key, final int value, final int flags) {
        if (overrides && super.hasOwnProperty(key)) {
            super.set(key, value, flags);
        } else {
            callAdaptee(__put__, key, value, flags);
        }
    }

    @Override
    public void set(final int key, final double value, final int flags) {
        if (overrides && super.hasOwnProperty(key)) {
            super.set(key, value, flags);
        } else {
            callAdaptee(__put__, key, value, flags);
        }
    }

    @Override
    public void set(final int key, final Object value, final int flags) {
        if (overrides && super.hasOwnProperty(key)) {
            super.set(key, value, flags);
        } else {
            callAdaptee(__put__, key, value, flags);
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
            obj = ScriptRuntime.apply((ScriptFunction)func, this);
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
    public static NativeJSAdapter construct(final boolean isNew, final Object self, final Object... args) {
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

        return new NativeJSAdapter(overrides, (ScriptObject)adaptee, (ScriptObject)proto, $nasgenmap$);
    }

    @Override
    protected GuardedInvocation findNewMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        return findHook(desc, __new__, false);
    }

    @Override
    protected GuardedInvocation findGetMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        final String name = NashornCallSiteDescriptor.getOperand(desc);
        if (overrides && super.hasOwnProperty(name)) {
            try {
                final GuardedInvocation inv = super.findGetMethod(desc, request);
                if (inv != null) {
                    return inv;
                }
            } catch (final Exception e) {
                //ignored
            }
        }

        if (!NashornCallSiteDescriptor.isMethodFirstOperation(desc)) {
            return findHook(desc, __get__);
        } else {
            final FindProperty find = adaptee.findProperty(__call__, true);
            if (find != null) {
                final Object value = find.getObjectValue();
                if (value instanceof ScriptFunction) {
                    final ScriptFunction func = (ScriptFunction)value;
                    // TODO: It's a shame we need to produce a function bound to this and name, when we'd only need it bound
                    // to name. Probably not a big deal, but if we can ever make it leaner, it'd be nice.
                    return new GuardedInvocation(MH.dropArguments(MH.constant(Object.class,
                            func.createBound(this, new Object[] { name })), 0, Object.class),
                            testJSAdapter(adaptee, null, null, null),
                            adaptee.getProtoSwitchPoints(__call__, find.getOwner()), null);
                }
            }
            throw typeError("no.such.function", name, ScriptRuntime.safeToString(this));
        }
    }

    @Override
    protected GuardedInvocation findSetMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        if (overrides && super.hasOwnProperty(NashornCallSiteDescriptor.getOperand(desc))) {
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

    private double callAdapteeDouble(final int programPoint, final String name, final Object... args) {
        return JSType.toNumberMaybeOptimistic(callAdaptee(name, args), programPoint);
    }

    private int callAdapteeInt(final int programPoint, final String name, final Object... args) {
        return JSType.toInt32MaybeOptimistic(callAdaptee(name, args), programPoint);
    }

    private Object callAdaptee(final Object retValue, final String name, final Object... args) {
        final Object func = adaptee.get(name);
        if (func instanceof ScriptFunction) {
            return ScriptRuntime.apply((ScriptFunction)func, this, args);
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
            final String name = NashornCallSiteDescriptor.getOperand(desc);
            final Object value = findData.getObjectValue();
            if (value instanceof ScriptFunction) {
                final ScriptFunction func = (ScriptFunction)value;

                final MethodHandle methodHandle = getCallMethodHandle(findData, type,
                    useName ? name : null);
                if (methodHandle != null) {
                    return new GuardedInvocation(
                            methodHandle,
                            testJSAdapter(adaptee, findData.getGetter(Object.class, INVALID_PROGRAM_POINT, null), findData.getOwner(), func),
                            adaptee.getProtoSwitchPoints(hook, findData.getOwner()), null);
                }
             }
        }

        switch (hook) {
        case __call__:
            throw typeError("no.such.function", NashornCallSiteDescriptor.getOperand(desc), ScriptRuntime.safeToString(this));
        default:
            final MethodHandle methodHandle = hook.equals(__put__) ?
            MH.asType(Lookup.EMPTY_SETTER, type) :
            Lookup.emptyGetter(type.returnType());
            return new GuardedInvocation(methodHandle, testJSAdapter(adaptee, null, null, null), adaptee.getProtoSwitchPoints(hook, null), null);
        }
    }

    private static MethodHandle testJSAdapter(final Object adaptee, final MethodHandle getter, final Object where, final ScriptFunction func) {
        return MH.insertArguments(IS_JSADAPTER, 1, adaptee, getter, where, func);
    }

    @SuppressWarnings("unused")
    private static boolean isJSAdapter(final Object self, final Object adaptee, final MethodHandle getter, final Object where, final ScriptFunction func) {
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
