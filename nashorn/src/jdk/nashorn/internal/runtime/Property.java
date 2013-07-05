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

import static jdk.nashorn.internal.runtime.PropertyDescriptor.CONFIGURABLE;
import static jdk.nashorn.internal.runtime.PropertyDescriptor.ENUMERABLE;
import static jdk.nashorn.internal.runtime.PropertyDescriptor.WRITABLE;

import java.lang.invoke.MethodHandle;
import java.util.Objects;
import jdk.nashorn.internal.codegen.ObjectClassGenerator;
import jdk.nashorn.internal.codegen.types.Type;

/**
 * This is the abstract superclass representing a JavaScript Property.
 * The {@link PropertyMap} map links keys to properties, and consequently
 * instances of this class make up the values in the PropertyMap
 *
 * @see PropertyMap
 * @see AccessorProperty
 * @see UserAccessorProperty
 */
public abstract class Property {
    /*
     * ECMA 8.6.1 Property Attributes
     *
     * We use negative flags because most properties are expected to
     * be 'writable', 'configurable' and 'enumerable'. With negative flags,
     * we can use leave flag byte initialized with (the default) zero value.
     */

    /** Mask for property being both writable, enumerable and configurable */
    public static final int WRITABLE_ENUMERABLE_CONFIGURABLE = 0b0000_0000_0000;

    /** ECMA 8.6.1 - Is this property not writable? */
    public static final int NOT_WRITABLE     = 0b0000_0000_0001;

    /** ECMA 8.6.1 - Is this property not enumerable? */
    public static final int NOT_ENUMERABLE   = 0b0000_0000_0010;

    /** ECMA 8.6.1 - Is this property not configurable? */
    public static final int NOT_CONFIGURABLE = 0b0000_0000_0100;

    private static final int MODIFY_MASK     = 0b0000_0000_1111;

    /** Is this a spill property? See {@link AccessorProperty} */
    public static final int IS_SPILL         = 0b0000_0001_0000;

    /** Is this a function parameter? */
    public static final int IS_PARAMETER     = 0b0000_0010_0000;

    /** Is parameter accessed thru arguments? */
    public static final int HAS_ARGUMENTS    = 0b0000_0100_0000;

    /** Is this property always represented as an Object? See {@link ObjectClassGenerator} and dual fields flag. */
    public static final int IS_ALWAYS_OBJECT = 0b0000_1000_0000;

    /** Can this property be primitive? */
    public static final int CAN_BE_PRIMITIVE = 0b0001_0000_0000;

    /** Can this property be undefined? */
    public static final int CAN_BE_UNDEFINED = 0b0010_0000_0000;

    /** Property key. */
    private final String key;

    /** Property flags. */
    protected int flags;

    /** Property field number or spill slot. */
    private final int slot;

    /**
     * Constructor
     *
     * @param key   property key
     * @param flags property flags
     * @param slot  property field number or spill slot
     */
    public Property(final String key, final int flags, final int slot) {
        assert key != null;
        this.key   = key;
        this.flags = flags;
        this.slot  = slot;
    }

    /**
     * Copy constructor
     *
     * @param property source property
     */
    protected Property(final Property property) {
        this.key   = property.key;
        this.flags = property.flags;
        this.slot  = property.slot;
    }

    /**
     * Copy function
     *
     * @return cloned property
     */
    protected abstract Property copy();

    /**
     * Property flag utility method for {@link PropertyDescriptor}s. Given two property descriptors,
     * return the result of merging their flags.
     *
     * @param oldDesc  first property descriptor
     * @param newDesc  second property descriptor
     * @return merged flags.
     */
    static int mergeFlags(final PropertyDescriptor oldDesc, final PropertyDescriptor newDesc) {
        int     propFlags = 0;
        boolean value;

        value = newDesc.has(CONFIGURABLE) ? newDesc.isConfigurable() : oldDesc.isConfigurable();
        if (!value) {
            propFlags |= NOT_CONFIGURABLE;
        }

        value = newDesc.has(ENUMERABLE) ? newDesc.isEnumerable() : oldDesc.isEnumerable();
        if (!value) {
            propFlags |= NOT_ENUMERABLE;
        }

        value = newDesc.has(WRITABLE) ? newDesc.isWritable() : oldDesc.isWritable();
        if (!value) {
            propFlags |= NOT_WRITABLE;
        }

        return propFlags;
    }

    /**
     * Property flag utility method for {@link PropertyDescriptor}. Get the property flags
     * conforming to any Property using this PropertyDescriptor
     *
     * @param desc property descriptor
     * @return flags for properties that conform to property descriptor
     */
    static int toFlags(final PropertyDescriptor desc) {
        int propFlags = 0;

        if (!desc.isConfigurable()) {
            propFlags |= NOT_CONFIGURABLE;
        }
        if (!desc.isEnumerable()) {
            propFlags |= NOT_ENUMERABLE;
        }
        if (!desc.isWritable()) {
            propFlags |= NOT_WRITABLE;
        }

        return propFlags;
    }

    /**
     * Check whether this property has a user defined getter function. See {@link UserAccessorProperty}
     * @param obj object containing getter
     * @return true if getter function exists, false is default
     */
    public boolean hasGetterFunction(final ScriptObject obj) {
        return false;
    }

    /**
     * Check whether this property has a user defined setter function. See {@link UserAccessorProperty}
     * @param obj object containing setter
     * @return true if getter function exists, false is default
     */
    public boolean hasSetterFunction(final ScriptObject obj) {
        return false;
    }

    /**
     * Check whether this property is writable (see ECMA 8.6.1)
     * @return true if writable
     */
    public boolean isWritable() {
        return (flags & NOT_WRITABLE) == 0;
    }

    /**
     * Check whether this property is writable (see ECMA 8.6.1)
     * @return true if configurable
     */
    public boolean isConfigurable() {
        return (flags & NOT_CONFIGURABLE) == 0;
    }

    /**
     * Check whether this property is enumerable (see ECMA 8.6.1)
     * @return true if enumerable
     */
    public boolean isEnumerable() {
        return (flags & NOT_ENUMERABLE) == 0;
    }

    /**
     * Check whether this property is used as a function parameter
     * @return true if parameter
     */
    public boolean isParameter() {
        return (flags & IS_PARAMETER) == IS_PARAMETER;
    }

    /**
     * Check whether this property is in an object with arguments field
     * @return true if has arguments
     */
    public boolean hasArguments() {
        return (flags & HAS_ARGUMENTS) == HAS_ARGUMENTS;
    }

    /**
     * Check whether this is a spill property, i.e. one that will not
     * be stored in a specially generated field in the property class.
     * The spill pool is maintained separately, as a growing Object array
     * in the {@link ScriptObject}.
     *
     * @return true if spill property
     */
    public boolean isSpill() {
        return (flags & IS_SPILL) == IS_SPILL;
    }

    /**
     * Does this property use any slots in the spill array described in
     * {@link Property#isSpill}? In that case how many. Currently a property
     * only uses max one spill slot, but this may change in future representations
     * Only {@link AccessorProperty} instances use spill slots
     *
     * @return number of spill slots a property is using
     */
    public int getSpillCount() {
        return isSpill() ? 1 : 0;
    }

    /**
     * Add more property flags to the property. Properties are immutable here,
     * so any property change that results in a larger flag set results in the
     * property being cloned. Use only the return value
     *
     * @param propertyFlags flags to be OR:ed to the existing property flags
     * @return new property if property set was changed, {@code this} otherwise
     */
    public Property addFlags(final int propertyFlags) {
        if ((this.flags & propertyFlags) != propertyFlags) {
            final Property cloned = this.copy();
            cloned.flags |= propertyFlags;
            return cloned;
        }
        return this;
    }

    /**
     * Get the flags for this property
     * @return property flags
     */
    public int getFlags() {
        return flags;
    }

    /**
     * Get the modify flags for this property. The modify flags are the ECMA 8.6.1
     * flags that decide if the Property is writable, configurable and/or enumerable.
     *
     * @return modify flags for property
     */
    public int getModifyFlags() {
        return flags & MODIFY_MASK;
    }

    /**
     * Remove property flags from the property. Properties are immutable here,
     * so any property change that results in a smaller flag set results in the
     * property being cloned. Use only the return value
     *
     * @param propertyFlags flags to be subtracted from the existing property flags
     * @return new property if property set was changed, {@code this} otherwise
     */
    public Property removeFlags(final int propertyFlags) {
        if ((this.flags & propertyFlags) != 0) {
            final Property cloned = this.copy();
            cloned.flags &= ~propertyFlags;
            return cloned;
        }
        return this;
    }

    /**
     * Reset the property for this property. Properties are immutable here,
     * so any property change that results in a different flag sets results in the
     * property being cloned. Use only the return value
     *
     * @param propertyFlags flags that are replacing from the existing property flags
     * @return new property if property set was changed, {@code this} otherwise
     */
    public Property setFlags(final int propertyFlags) {
        if (this.flags != propertyFlags) {
            final Property cloned = this.copy();
            cloned.flags &= ~MODIFY_MASK;
            cloned.flags |= propertyFlags & MODIFY_MASK;
            return cloned;
        }
        return this;
    }

    /**
     * Abstract method for retrieving the getter for the property. We do not know
     * anything about the internal representation when we request the getter, we only
     * know that the getter will return the property as the given type.
     *
     * @param type getter return value type
     * @return a getter for this property as {@code type}
     */
    public abstract MethodHandle getGetter(final Class<?> type);

    /**
     * Get the key for this property. This key is an ordinary string. The "name".
     * @return key for property
     */
    public String getKey() {
        return key;
    }

    /**
     * Get the field number or spill slot
     * @return number/slot, -1 if none exists
     */
    public int getSlot() {
        return slot;
    }

    /**
     * Set the value of this property in {@code owner}. This allows to bypass creation of the
     * setter MethodHandle for spill and user accessor properties.
     *
     * @param self the this object
     * @param owner the owner object
     * @param value the new property value
     * @param strict is this a strict setter?
     */
    public abstract void setObjectValue(ScriptObject self, ScriptObject owner, Object value, boolean strict);

    /**
     * Set the Object value of this property from {@code owner}. This allows to bypass creation of the
     * getter MethodHandle for spill and user accessor properties.
     *
     * @param self the this object
     * @param owner the owner object
     * @return  the property value
     */
    public abstract Object getObjectValue(ScriptObject self, ScriptObject owner);

    /**
     * Abstract method for retrieving the setter for the property. We do not know
     * anything about the internal representation when we request the setter, we only
     * know that the setter will take the property as a parameter of the given type.
     * <p>
     * Note that we have to pass the current property map from which we retrieved
     * the property here. This is necessary for map guards if, e.g. the internal
     * representation of the field, and consequently also the setter, changes. Then
     * we automatically get a map guard that relinks the call site so that the
     * older setter will never be used again.
     * <p>
     * see {@link ObjectClassGenerator#createSetter(Class, Class, MethodHandle, MethodHandle)}
     * if you are interested in the internal details of this. Note that if you
     * are running in default mode, with {@code -Dnashorn.fields.dual=true}, disabled, the setters
     * will currently never change, as all properties are represented as Object field,
     * the Object fields are Initialized to {@code ScriptRuntime.UNDEFINED} and primitives are
     * boxed/unboxed upon every access, which is not necessarily optimal
     *
     * @param type setter parameter type
     * @param currentMap current property map for property
     * @return a getter for this property as {@code type}
     */
    public abstract MethodHandle getSetter(final Class<?> type, final PropertyMap currentMap);

    /**
     * Get the user defined getter function if one exists. Only {@link UserAccessorProperty} instances
     * can have user defined getters
     * @param obj the script object
     * @return user defined getter function, or {@code null} if none exists
     */
    public ScriptFunction getGetterFunction(final ScriptObject obj) {
        return null;
    }

    /**
     * Get the user defined setter function if one exists. Only {@link UserAccessorProperty} instances
     * can have user defined getters
     * @param obj the script object
     * @return user defined getter function, or {@code null} if none exists
     */
    public ScriptFunction getSetterFunction(final ScriptObject obj) {
        return null;
    }

    @Override
    public int hashCode() {
        final Class<?> type = getCurrentType();
        return Objects.hashCode(this.key) ^ flags ^ getSlot() ^ (type == null ? 0 : type.hashCode());
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || this.getClass() != other.getClass()) {
            return false;
        }

        final Property otherProperty = (Property)other;

        return getFlags()       == otherProperty.getFlags() &&
               getSlot()        == otherProperty.getSlot() &&
               getCurrentType() == otherProperty.getCurrentType() &&
               getKey().equals(otherProperty.getKey());
    }

    @Override
    public String toString() {
        final StringBuilder sb   = new StringBuilder();
        final Class<?>      type = getCurrentType();

        sb.append(getKey()).
            append("(0x").
            append(Integer.toHexString(flags)).
            append(") ").
            append(getClass().getSimpleName()).
            append(" {").
            append(type == null ? "UNDEFINED" : Type.typeFor(type).getDescriptor()).
            append('}');

        if (slot != -1) {
            sb.append('[');
            sb.append(slot);
            sb.append(']');
        }

        return sb.toString();
    }

    /**
     * Get the current type of this field. If you are not running with dual fields enabled,
     * this will always be Object.class. See the value representation explanation in
     * {@link Property#getSetter(Class, PropertyMap)} and {@link ObjectClassGenerator}
     * for more information.
     *
     * @return current type of property, null means undefined
     */
    public Class<?> getCurrentType() {
        return Object.class;
    }

    /**
     * Check whether this Property can ever change its type. The default is false, and if
     * you are not running with dual fields, the type is always object and can never change
     * @return true if this property can change types
     */
    public boolean canChangeType() {
        return false;
    }

    /**
     * Check whether this Property is ever used as anything but an Object. If this is used only
     * as an object, dual fields mode need not even try to represent it as a primitive at any
     * callsite, saving map rewrites for performance.
     *
     * @return true if representation should always be an object field
     */
    public boolean isAlwaysObject() {
        return (flags & IS_ALWAYS_OBJECT) == IS_ALWAYS_OBJECT;
    }

    /**
     * Check whether this property can be primitive. This is a conservative
     * analysis result, so {@code false} might mean that it can still be
     * primitive
     *
     * @return can be primitive status
     */
    public boolean canBePrimitive() {
        return (flags & CAN_BE_PRIMITIVE) == CAN_BE_PRIMITIVE;
    }

    /**
     * Check whether this property can be primitive. This is a conservative
     * analysis result, so {@code true} might mean that it can still be
     * defined, but it will never say that a property can not be undefined
     * if it can
     *
     * @return can be undefined status
     */
    public boolean canBeUndefined() {
        return (flags & CAN_BE_UNDEFINED) == CAN_BE_UNDEFINED;
    }
}
