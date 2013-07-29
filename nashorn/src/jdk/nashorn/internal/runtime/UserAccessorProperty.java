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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import jdk.nashorn.internal.codegen.CompilerConstants;
import jdk.nashorn.internal.lookup.Lookup;
import jdk.nashorn.internal.runtime.linker.Bootstrap;

import static jdk.nashorn.internal.codegen.CompilerConstants.staticCall;
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

/**
 * Property with user defined getters/setters. Actual getter and setter
 * functions are stored in underlying ScriptObject. Only the 'slot' info is
 * stored in the property.
 *
 * The slots here denote either ScriptObject embed field number or spill
 * array index. For spill array index, we use slot value of
 * (index + ScriptObject.embedSize). See also ScriptObject.getEmbedOrSpill
 * method. Negative slot value means that the corresponding getter or setter
 * is null. Note that always two slots are allocated in ScriptObject - but
 * negative (less by 1) slot number is stored for null getter or setter.
 * This is done so that when the property is redefined with a different
 * getter and setter (say, both non-null), we'll have spill slots to store
 * those. When a slot is negative, (-slot - 1) is the embed/spill index.
 */
public final class UserAccessorProperty extends Property {

    /** User defined getter function slot. */
    private final int getterSlot;

    /** User defined setter function slot. */
    private final int setterSlot;

    /** Getter method handle */
    private final static CompilerConstants.Call USER_ACCESSOR_GETTER = staticCall(MethodHandles.lookup(), UserAccessorProperty.class,
            "userAccessorGetter", Object.class, ScriptObject.class, int.class, Object.class);

    /** Setter method handle */
    private final static CompilerConstants.Call USER_ACCESSOR_SETTER = staticCall(MethodHandles.lookup(), UserAccessorProperty.class,
            "userAccessorSetter", void.class, ScriptObject.class, int.class, String.class, Object.class, Object.class);

    /** Dynamic invoker for getter */
    private static final MethodHandle INVOKE_UA_GETTER = Bootstrap.createDynamicInvoker("dyn:call", Object.class,
            Object.class, Object.class);

    /** Dynamic invoker for setter */
    private static final MethodHandle INVOKE_UA_SETTER = Bootstrap.createDynamicInvoker("dyn:call", void.class,
            Object.class, Object.class, Object.class);

    /**
     * Constructor
     *
     * @param key        property key
     * @param flags      property flags
     * @param getterSlot getter slot, starting at first embed
     * @param setterSlot setter slot, starting at first embed
     */
    UserAccessorProperty(final String key, final int flags, final int getterSlot, final int setterSlot) {
        super(key, flags, -1);
        this.getterSlot = getterSlot;
        this.setterSlot = setterSlot;
    }

    private UserAccessorProperty(final UserAccessorProperty property) {
        super(property);
        this.getterSlot = property.getterSlot;
        this.setterSlot = property.setterSlot;
    }

    /**
     * Return getter spill slot for this UserAccessorProperty.
     * @return getter slot
     */
    public int getGetterSlot() {
        return getterSlot;
    }

    /**
     * Return setter spill slot for this UserAccessorProperty.
     * @return setter slot
     */
    public int getSetterSlot() {
        return setterSlot;
    }

    @Override
    protected Property copy() {
        return new UserAccessorProperty(this);
    }

    @Override
    public boolean equals(final Object other) {
        if (!super.equals(other)) {
            return false;
        }

        final UserAccessorProperty uc = (UserAccessorProperty) other;
        return getterSlot == uc.getterSlot && setterSlot == uc.setterSlot;
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ getterSlot ^ setterSlot;
    }

    /*
     * Accessors.
     */
    @Override
    public int getSpillCount() {
        return 2;
    }

    @Override
    public boolean hasGetterFunction(final ScriptObject obj) {
        return obj.getSpill(getterSlot) != null;
    }

    @Override
    public boolean hasSetterFunction(final ScriptObject obj) {
        return obj.getSpill(setterSlot) != null;
    }

    @Override
    public Object getObjectValue(final ScriptObject self, final ScriptObject owner) {
        return userAccessorGetter(owner, getGetterSlot(), self);
    }

    @Override
    public void setObjectValue(final ScriptObject self, final ScriptObject owner, final Object value, final boolean strict) {
        userAccessorSetter(owner, getSetterSlot(), strict ? getKey() : null, self, value);
    }

    @Override
    public MethodHandle getGetter(final Class<?> type) {
        return Lookup.filterReturnType(USER_ACCESSOR_GETTER.methodHandle(), type);
    }

    @Override
    public ScriptFunction getGetterFunction(final ScriptObject obj) {
        final Object value = obj.getSpill(getterSlot);
        return (value instanceof ScriptFunction) ? (ScriptFunction) value : null;
    }

    @Override
    public MethodHandle getSetter(final Class<?> type, final PropertyMap currentMap) {
        return USER_ACCESSOR_SETTER.methodHandle();
    }

    @Override
    public ScriptFunction getSetterFunction(final ScriptObject obj) {
        final Object value = obj.getSpill(setterSlot);
        return (value instanceof ScriptFunction) ? (ScriptFunction) value : null;
    }

    // User defined getter and setter are always called by "dyn:call". Note that the user
    // getter/setter may be inherited. If so, proto is bound during lookup. In either
    // inherited or self case, slot is also bound during lookup. Actual ScriptFunction
    // to be called is retrieved everytime and applied.
    static Object userAccessorGetter(final ScriptObject proto, final int slot, final Object self) {
        final ScriptObject container = (proto != null) ? proto : (ScriptObject)self;
        final Object       func      = container.getSpill(slot);

        if (func instanceof ScriptFunction) {
            try {
                return INVOKE_UA_GETTER.invokeExact(func, self);
            } catch(final Error|RuntimeException t) {
                throw t;
            } catch(final Throwable t) {
                throw new RuntimeException(t);
            }
        }

        return UNDEFINED;
    }

    static void userAccessorSetter(final ScriptObject proto, final int slot, final String name, final Object self, final Object value) {
        final ScriptObject container = (proto != null) ? proto : (ScriptObject)self;
        final Object       func      = container.getSpill(slot);

        if (func instanceof ScriptFunction) {
            try {
                INVOKE_UA_SETTER.invokeExact(func, self, value);
            } catch(final Error|RuntimeException t) {
                throw t;
            } catch(final Throwable t) {
                throw new RuntimeException(t);
            }
        }  else if (name != null) {
            throw typeError("property.has.no.setter", name, ScriptRuntime.safeToString(self));
        }
    }

}
