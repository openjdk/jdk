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

import static jdk.nashorn.internal.codegen.CompilerConstants.staticCall;
import static jdk.nashorn.internal.lookup.Lookup.MH;
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.JSType.CONVERT_OBJECT_OPTIMISTIC;
import static jdk.nashorn.internal.runtime.JSType.getAccessorTypeIndex;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.Callable;
import jdk.nashorn.internal.codegen.CompilerConstants;
import jdk.nashorn.internal.lookup.Lookup;
import jdk.nashorn.internal.runtime.linker.Bootstrap;

/**
 * Property with user defined getters/setters. Actual getter and setter
 * functions are stored in underlying ScriptObject. Only the 'slot' info is
 * stored in the property.
 */
public final class UserAccessorProperty extends SpillProperty {

    private static final long serialVersionUID = -5928687246526840321L;

    static class Accessors {
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

    /** Getter method handle */
    private final static CompilerConstants.Call USER_ACCESSOR_GETTER = staticCall(MethodHandles.lookup(), UserAccessorProperty.class,
            "userAccessorGetter", Object.class, Accessors.class, Object.class);

    /** Setter method handle */
    private final static CompilerConstants.Call USER_ACCESSOR_SETTER = staticCall(MethodHandles.lookup(), UserAccessorProperty.class,
            "userAccessorSetter", void.class, Accessors.class, String.class, Object.class, Object.class);

    /** Dynamic invoker for getter */
    private static final Object INVOKE_UA_GETTER = new Object();

    private static MethodHandle getINVOKE_UA_GETTER() {

        return Context.getGlobal().getDynamicInvoker(INVOKE_UA_GETTER,
                new Callable<MethodHandle>() {
                    @Override
                    public MethodHandle call() {
                        return Bootstrap.createDynamicInvoker("dyn:call", Object.class,
                            Object.class, Object.class);
                    }
                });
    }

    /** Dynamic invoker for setter */
    private static Object INVOKE_UA_SETTER = new Object();

    private static MethodHandle getINVOKE_UA_SETTER() {
        return Context.getGlobal().getDynamicInvoker(INVOKE_UA_SETTER,
                new Callable<MethodHandle>() {
                    @Override
                    public MethodHandle call() {
                        return Bootstrap.createDynamicInvoker("dyn:call", void.class,
                            Object.class, Object.class, Object.class);
                    }
                });
    }

    /**
     * Constructor
     *
     * @param key   property key
     * @param flags property flags
     * @param slot  spill slot
     */
    UserAccessorProperty(final String key, final int flags, final int slot) {
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
    public Class<?> getCurrentType() {
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
        return userAccessorGetter(getAccessors((owner != null) ? owner : self), self);
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
        userAccessorSetter(getAccessors((owner != null) ? owner : self), strict ? getKey() : null, self, value);
    }

    @Override
    public MethodHandle getGetter(final Class<?> type) {
        //this returns a getter on the format (Accessors, Object receiver)
        return Lookup.filterReturnType(USER_ACCESSOR_GETTER.methodHandle(), type);
    }

    @Override
    public MethodHandle getOptimisticGetter(final Class<?> type, final int programPoint) {
        //fortype is always object, but in the optimistic world we have to throw
        //unwarranted optimism exception for narrower types. We can improve this
        //by checking for boxed types and unboxing them, but it is doubtful that
        //this gives us any performance, as UserAccessorProperties are typically not
        //primitives. Are there? TODO: investigate later. For now we just throw an
        //exception for narrower types than object

        if (type.isPrimitive()) {
            final MethodHandle getter = getGetter(Object.class);
            final MethodHandle mh =
                    MH.asType(
                            MH.filterReturnValue(
                                    getter,
                                    MH.insertArguments(
                                            CONVERT_OBJECT_OPTIMISTIC.get(getAccessorTypeIndex(type)),
                                            1,
                                            programPoint)),
                                    getter.type().changeReturnType(type));

            return mh;
        }

        assert type == Object.class;
        return getGetter(type);
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
        return USER_ACCESSOR_SETTER.methodHandle();
    }

    @Override
    public ScriptFunction getSetterFunction(final ScriptObject sobj) {
        final Object value = getAccessors(sobj).setter;
        return (value instanceof ScriptFunction) ? (ScriptFunction)value : null;
    }

    // User defined getter and setter are always called by "dyn:call". Note that the user
    // getter/setter may be inherited. If so, proto is bound during lookup. In either
    // inherited or self case, slot is also bound during lookup. Actual ScriptFunction
    // to be called is retrieved everytime and applied.
    static Object userAccessorGetter(final Accessors gs, final Object self) {
        final Object func = gs.getter;
        if (func instanceof ScriptFunction) {
            try {
                return getINVOKE_UA_GETTER().invokeExact(func, self);
            } catch (final Error | RuntimeException t) {
                throw t;
            } catch (final Throwable t) {
                throw new RuntimeException(t);
            }
        }

        return UNDEFINED;
    }

    static void userAccessorSetter(final Accessors gs, final String name, final Object self, final Object value) {
        final Object func = gs.setter;
        if (func instanceof ScriptFunction) {
            try {
                getINVOKE_UA_SETTER().invokeExact(func, self, value);
            } catch (final Error | RuntimeException t) {
                throw t;
            } catch (final Throwable t) {
                throw new RuntimeException(t);
            }
        } else if (name != null) {
            throw typeError("property.has.no.setter", name, ScriptRuntime.safeToString(self));
        }
    }

}
