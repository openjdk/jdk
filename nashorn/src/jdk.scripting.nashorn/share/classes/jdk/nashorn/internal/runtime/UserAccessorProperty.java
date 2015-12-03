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

package jdk.nashorn.internal.runtime;
import static jdk.nashorn.internal.lookup.Lookup.MH;
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;
import static jdk.nashorn.internal.runtime.UnwarrantedOptimismException.INVALID_PROGRAM_POINT;
import static jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor.CALLSITE_PROGRAM_POINT_SHIFT;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.Callable;
import jdk.nashorn.internal.lookup.Lookup;
import jdk.nashorn.internal.runtime.linker.Bootstrap;
import jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor;

/**
 * Property with user defined getters/setters. Actual getter and setter
 * functions are stored in underlying ScriptObject. Only the 'slot' info is
 * stored in the property.
 */
public final class UserAccessorProperty extends SpillProperty {

    private static final long serialVersionUID = -5928687246526840321L;

    static final class Accessors {
        Object getter;
        Object setter;

        Accessors(final Object getter, final Object setter) {
            set(getter, setter);
        }

        final void set(final Object getter, final Object setter) {
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        public String toString() {
            return "[getter=" + getter + " setter=" + setter + ']';
        }
    }

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    /** Getter method handle */
    private final static MethodHandle INVOKE_OBJECT_GETTER = findOwnMH_S("invokeObjectGetter", Object.class, Accessors.class, MethodHandle.class, Object.class);
    private final static MethodHandle INVOKE_INT_GETTER  = findOwnMH_S("invokeIntGetter", int.class, Accessors.class, MethodHandle.class, int.class, Object.class);
    private final static MethodHandle INVOKE_LONG_GETTER  = findOwnMH_S("invokeLongGetter", long.class, Accessors.class, MethodHandle.class, int.class, Object.class);
    private final static MethodHandle INVOKE_NUMBER_GETTER  = findOwnMH_S("invokeNumberGetter", double.class, Accessors.class, MethodHandle.class, int.class, Object.class);

    /** Setter method handle */
    private final static MethodHandle INVOKE_OBJECT_SETTER = findOwnMH_S("invokeObjectSetter", void.class, Accessors.class, MethodHandle.class, String.class, Object.class, Object.class);
    private final static MethodHandle INVOKE_INT_SETTER = findOwnMH_S("invokeIntSetter", void.class, Accessors.class, MethodHandle.class, String.class, Object.class, int.class);
    private final static MethodHandle INVOKE_LONG_SETTER = findOwnMH_S("invokeLongSetter", void.class, Accessors.class, MethodHandle.class, String.class, Object.class, long.class);
    private final static MethodHandle INVOKE_NUMBER_SETTER = findOwnMH_S("invokeNumberSetter", void.class, Accessors.class, MethodHandle.class, String.class, Object.class, double.class);

    private static final Object OBJECT_GETTER_INVOKER_KEY = new Object();
    private static MethodHandle getObjectGetterInvoker() {
        return Context.getGlobal().getDynamicInvoker(OBJECT_GETTER_INVOKER_KEY, new Callable<MethodHandle>() {
            @Override
            public MethodHandle call() throws Exception {
                return getINVOKE_UA_GETTER(Object.class, INVALID_PROGRAM_POINT);
            }
        });
    }

    static MethodHandle getINVOKE_UA_GETTER(final Class<?> returnType, final int programPoint) {
        if (UnwarrantedOptimismException.isValid(programPoint)) {
            final int flags = NashornCallSiteDescriptor.CALL | NashornCallSiteDescriptor.CALLSITE_OPTIMISTIC | programPoint << CALLSITE_PROGRAM_POINT_SHIFT;
            return Bootstrap.createDynamicInvoker("", flags, returnType, Object.class, Object.class);
        } else {
            return Bootstrap.createDynamicCallInvoker(Object.class, Object.class, Object.class);
        }
    }

    private static final Object OBJECT_SETTER_INVOKER_KEY = new Object();
    private static MethodHandle getObjectSetterInvoker() {
        return Context.getGlobal().getDynamicInvoker(OBJECT_SETTER_INVOKER_KEY, new Callable<MethodHandle>() {
            @Override
            public MethodHandle call() throws Exception {
                return getINVOKE_UA_SETTER(Object.class);
            }
        });
    }

    static MethodHandle getINVOKE_UA_SETTER(final Class<?> valueType) {
        return Bootstrap.createDynamicCallInvoker(void.class, Object.class, Object.class, valueType);
    }

    /**
     * Constructor
     *
     * @param key   property key
     * @param flags property flags
     * @param slot  spill slot
     */
    UserAccessorProperty(final Object key, final int flags, final int slot) {
        super(key, flags, slot);
    }

    private UserAccessorProperty(final UserAccessorProperty property) {
        super(property);
    }

    private UserAccessorProperty(final UserAccessorProperty property, final Class<?> newType) {
        super(property, newType);
    }

    @Override
    public Property copy() {
        return new UserAccessorProperty(this);
    }

    @Override
    public Property copy(final Class<?> newType) {
        return new UserAccessorProperty(this, newType);
    }

    void setAccessors(final ScriptObject sobj, final PropertyMap map, final Accessors gs) {
        try {
            //invoke the getter and find out
            super.getSetter(Object.class, map).invokeExact((Object)sobj, (Object)gs);
        } catch (final Error | RuntimeException t) {
            throw t;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    //pick the getter setter out of the correct spill slot in sobj
    Accessors getAccessors(final ScriptObject sobj) {
        try {
            //invoke the super getter with this spill slot
            //get the getter setter from the correct spill slot
            final Object gs = super.getGetter(Object.class).invokeExact((Object)sobj);
            return (Accessors)gs;
        } catch (final Error | RuntimeException t) {
            throw t;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @Override
    protected Class<?> getLocalType() {
        return Object.class;
    }

    @Override
    public boolean hasGetterFunction(final ScriptObject sobj) {
        return getAccessors(sobj).getter != null;
    }

    @Override
    public boolean hasSetterFunction(final ScriptObject sobj) {
        return getAccessors(sobj).setter != null;
    }

    @Override
    public int getIntValue(final ScriptObject self, final ScriptObject owner) {
        return (int)getObjectValue(self, owner);
    }

    @Override
    public long getLongValue(final ScriptObject self, final ScriptObject owner) {
        return (long)getObjectValue(self, owner);
    }

    @Override
    public double getDoubleValue(final ScriptObject self, final ScriptObject owner) {
        return (double)getObjectValue(self, owner);
    }

    @Override
    public Object getObjectValue(final ScriptObject self, final ScriptObject owner) {
        try {
            return invokeObjectGetter(getAccessors((owner != null) ? owner : self), getObjectGetterInvoker(), self);
        } catch (final Error | RuntimeException t) {
            throw t;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @Override
    public void setValue(final ScriptObject self, final ScriptObject owner, final int value, final boolean strict) {
        setValue(self, owner, (Object) value, strict);
    }

    @Override
    public void setValue(final ScriptObject self, final ScriptObject owner, final long value, final boolean strict) {
        setValue(self, owner, (Object) value, strict);
    }

    @Override
    public void setValue(final ScriptObject self, final ScriptObject owner, final double value, final boolean strict) {
        setValue(self, owner, (Object) value, strict);
    }

    @Override
    public void setValue(final ScriptObject self, final ScriptObject owner, final Object value, final boolean strict) {
        try {
            invokeObjectSetter(getAccessors((owner != null) ? owner : self), getObjectSetterInvoker(), strict ? getKey().toString() : null, self, value);
        } catch (final Error | RuntimeException t) {
            throw t;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @Override
    public MethodHandle getGetter(final Class<?> type) {
        //this returns a getter on the format (Accessors, Object receiver)
        return Lookup.filterReturnType(INVOKE_OBJECT_GETTER, type);
    }

    @Override
    public MethodHandle getOptimisticGetter(final Class<?> type, final int programPoint) {
        if (type == int.class) {
            return INVOKE_INT_GETTER;
        } else if (type == long.class) {
            return INVOKE_LONG_GETTER;
        } else if (type == double.class) {
            return INVOKE_NUMBER_GETTER;
        } else {
            assert type == Object.class;
            return INVOKE_OBJECT_GETTER;
        }
    }

    @Override
    void initMethodHandles(final Class<?> structure) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScriptFunction getGetterFunction(final ScriptObject sobj) {
        final Object value = getAccessors(sobj).getter;
        return (value instanceof ScriptFunction) ? (ScriptFunction)value : null;
    }

    @Override
    public MethodHandle getSetter(final Class<?> type, final PropertyMap currentMap) {
        if (type == int.class) {
            return INVOKE_INT_SETTER;
        } else if (type == long.class) {
            return INVOKE_LONG_SETTER;
        } else if (type == double.class) {
            return INVOKE_NUMBER_SETTER;
        } else {
            assert type == Object.class;
            return INVOKE_OBJECT_SETTER;
        }
    }

    @Override
    public ScriptFunction getSetterFunction(final ScriptObject sobj) {
        final Object value = getAccessors(sobj).setter;
        return (value instanceof ScriptFunction) ? (ScriptFunction)value : null;
    }

    /**
     * Get the getter for the {@code Accessors} object.
     * This is the the super {@code Object} type getter with {@code Accessors} return type.
     *
     * @return The getter handle for the Accessors
     */
    MethodHandle getAccessorsGetter() {
        return super.getGetter(Object.class).asType(MethodType.methodType(Accessors.class, Object.class));
    }

    // User defined getter and setter are always called by StandardOperation.CALL. Note that the user
    // getter/setter may be inherited. If so, proto is bound during lookup. In either
    // inherited or self case, slot is also bound during lookup. Actual ScriptFunction
    // to be called is retrieved everytime and applied.
    @SuppressWarnings("unused")
    private static Object invokeObjectGetter(final Accessors gs, final MethodHandle invoker, final Object self) throws Throwable {
        final Object func = gs.getter;
        if (func instanceof ScriptFunction) {
            return invoker.invokeExact(func, self);
        }

        return UNDEFINED;
    }

    @SuppressWarnings("unused")
    private static int invokeIntGetter(final Accessors gs, final MethodHandle invoker, final int programPoint, final Object self) throws Throwable {
        final Object func = gs.getter;
        if (func instanceof ScriptFunction) {
            return (int) invoker.invokeExact(func, self);
        }

        throw new UnwarrantedOptimismException(UNDEFINED, programPoint);
    }

    @SuppressWarnings("unused")
    private static long invokeLongGetter(final Accessors gs, final MethodHandle invoker, final int programPoint, final Object self) throws Throwable {
        final Object func = gs.getter;
        if (func instanceof ScriptFunction) {
            return (long) invoker.invokeExact(func, self);
        }

        throw new UnwarrantedOptimismException(UNDEFINED, programPoint);
    }

    @SuppressWarnings("unused")
    private static double invokeNumberGetter(final Accessors gs, final MethodHandle invoker, final int programPoint, final Object self) throws Throwable {
        final Object func = gs.getter;
        if (func instanceof ScriptFunction) {
            return (double) invoker.invokeExact(func, self);
        }

        throw new UnwarrantedOptimismException(UNDEFINED, programPoint);
    }

    @SuppressWarnings("unused")
    private static void invokeObjectSetter(final Accessors gs, final MethodHandle invoker, final String name, final Object self, final Object value) throws Throwable {
        final Object func = gs.setter;
        if (func instanceof ScriptFunction) {
            invoker.invokeExact(func, self, value);
        } else if (name != null) {
            throw typeError("property.has.no.setter", name, ScriptRuntime.safeToString(self));
        }
    }

    @SuppressWarnings("unused")
    private static void invokeIntSetter(final Accessors gs, final MethodHandle invoker, final String name, final Object self, final int value) throws Throwable {
        final Object func = gs.setter;
        if (func instanceof ScriptFunction) {
            invoker.invokeExact(func, self, value);
        } else if (name != null) {
            throw typeError("property.has.no.setter", name, ScriptRuntime.safeToString(self));
        }
    }

    @SuppressWarnings("unused")
    private static void invokeLongSetter(final Accessors gs, final MethodHandle invoker, final String name, final Object self, final long value) throws Throwable {
        final Object func = gs.setter;
        if (func instanceof ScriptFunction) {
            invoker.invokeExact(func, self, value);
        } else if (name != null) {
            throw typeError("property.has.no.setter", name, ScriptRuntime.safeToString(self));
        }
    }

    @SuppressWarnings("unused")
    private static void invokeNumberSetter(final Accessors gs, final MethodHandle invoker, final String name, final Object self, final double value) throws Throwable {
        final Object func = gs.setter;
        if (func instanceof ScriptFunction) {
            invoker.invokeExact(func, self, value);
        } else if (name != null) {
            throw typeError("property.has.no.setter", name, ScriptRuntime.safeToString(self));
        }
    }

    private static MethodHandle findOwnMH_S(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findStatic(LOOKUP, UserAccessorProperty.class, name, MH.type(rtype, types));
    }

}
