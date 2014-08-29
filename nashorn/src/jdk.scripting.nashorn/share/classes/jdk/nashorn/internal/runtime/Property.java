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

import java.io.Serializable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.SwitchPoint;
import java.util.Objects;
import jdk.nashorn.internal.codegen.ObjectClassGenerator;

/**
 * This is the abstract superclass representing a JavaScript Property.
 * The {@link PropertyMap} map links keys to properties, and consequently
 * instances of this class make up the values in the PropertyMap
 *
 * @see PropertyMap
 * @see AccessorProperty
 * @see UserAccessorProperty
 */
public abstract class Property implements Serializable {
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
    public static final int NOT_WRITABLE     = 1 << 0;

    /** ECMA 8.6.1 - Is this property not enumerable? */
    public static final int NOT_ENUMERABLE   = 1 << 1;

    /** ECMA 8.6.1 - Is this property not configurable? */
    public static final int NOT_CONFIGURABLE = 1 << 2;

    private static final int MODIFY_MASK     = NOT_WRITABLE | NOT_ENUMERABLE | NOT_CONFIGURABLE;

    /** Is this a function parameter? */
    public static final int IS_PARAMETER     = 1 << 3;

    /** Is parameter accessed thru arguments? */
    public static final int HAS_ARGUMENTS    = 1 << 4;

    /** Is this a function declaration property ? */
    public static final int IS_FUNCTION_DECLARATION = 1 << 5;

    /**
     * Is this is a primitive field given to us by Nasgen, i.e.
     * something we can be sure remains a constant whose type
     * is narrower than object, e.g. Math.PI which is declared
     * as a double
     */
    public static final int IS_NASGEN_PRIMITIVE = 1 << 6;

    /** Is this property bound to a receiver? This means get/set operations will be delegated to
     *  a statically defined object instead of the object passed as callsite parameter. */
    public static final int IS_BOUND = 1 << 8;

    /** Property key. */
    private final String key;

    /** Property flags. */
    private int flags;

    /** Property field number or spill slot. */
    private final int slot;

    /** SwitchPoint that is invalidated when property is changed, optional */
    protected SwitchPoint changeCallback;

    private static final long serialVersionUID = 2099814273074501176L;

    /**
     * Constructor
     *
     * @param key   property key
     * @param flags property flags
     * @param slot  property field number or spill slot
     */
    Property(final String key, final int flags, final int slot) {
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
    Property(final Property property, final int flags) {
        this.key            = property.key;
        this.slot           = property.slot;
        this.changeCallback = property.changeCallback;
        this.flags          = flags;
    }

    /**
     * Copy function
     *
     * @return cloned property
     */
    public abstract Property copy();

    /**
     * Copy function
     *
     * @param  newType new type
     * @return cloned property with new type
     */
    public abstract Property copy(final Class<?> newType);

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
     * Set the change callback for this property, i.e. a SwitchPoint
     * that will be invalidated when the value of the property is
     * changed
     * @param sp SwitchPoint to use for change callback
     */
    public final void setChangeCallback(final SwitchPoint sp) {
        this.changeCallback = sp;
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
        return false;
    }

    /**
     * Is this property bound to a receiver? If this method returns {@code true} get and set operations
     * will be delegated to a statically bound object instead of the object passed as parameter.
     *
     * @return true if this is a bound property
     */
    public boolean isBound() {
        return (flags & IS_BOUND) == IS_BOUND;
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
     * Check if a flag is set for a property
     * @param property property
     * @param flag     flag to check
     * @return true if flag is set
     */
    public static boolean checkFlag(final Property property, final int flag) {
        return (property.getFlags() & flag) == flag;
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
     * Get an optimistic getter that throws an exception if type is not the known given one
     * @param type          type
     * @param programPoint  program point
     * @return getter
     */
    public abstract MethodHandle getOptimisticGetter(final Class<?> type, final int programPoint);

    /**
     * Hook to initialize method handles after deserialization.
     *
     * @param structure the structure class
     */
    abstract void initMethodHandles(final Class<?> structure);

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
     * get the Object value of this property from {@code owner}. This allows to bypass creation of the
     * getter MethodHandle for spill and user accessor properties.
     *
     * @param self the this object
     * @param owner the owner of the property
     * @return  the property value
     */
    public abstract int getIntValue(final ScriptObject self, final ScriptObject owner);

    /**
     * get the Object value of this property from {@code owner}. This allows to bypass creation of the
     * getter MethodHandle for spill and user accessor properties.
     *
     * @param self the this object
     * @param owner the owner of the property
     * @return  the property value
     */
    public abstract long getLongValue(final ScriptObject self, final ScriptObject owner);

    /**
     * get the Object value of this property from {@code owner}. This allows to bypass creation of the
     * getter MethodHandle for spill and user accessor properties.
     *
     * @param self the this object
     * @param owner the owner of the property
     * @return  the property value
     */
    public abstract double getDoubleValue(final ScriptObject self, final ScriptObject owner);

    /**
     * get the Object value of this property from {@code owner}. This allows to bypass creation of the
     * getter MethodHandle for spill and user accessor properties.
     *
     * @param self the this object
     * @param owner the owner of the property
     * @return  the property value
     */
    public abstract Object getObjectValue(final ScriptObject self, final ScriptObject owner);

    /**
     * Set the value of this property in {@code owner}. This allows to bypass creation of the
     * setter MethodHandle for spill and user accessor properties.
     *
     * @param self the this object
     * @param owner the owner object
     * @param value the new property value
     * @param strict is this a strict setter?
     */
    public abstract void setValue(final ScriptObject self, final ScriptObject owner, final int value, final boolean strict);

    /**
     * Set the value of this property in {@code owner}. This allows to bypass creation of the
     * setter MethodHandle for spill and user accessor properties.
     *
     * @param self the this object
     * @param owner the owner object
     * @param value the new property value
     * @param strict is this a strict setter?
     */
    public abstract void setValue(final ScriptObject self, final ScriptObject owner, final long value, final boolean strict);

    /**
     * Set the value of this property in {@code owner}. This allows to bypass creation of the
     * setter MethodHandle for spill and user accessor properties.
     *
     * @param self the this object
     * @param owner the owner object
     * @param value the new property value
     * @param strict is this a strict setter?
     */
    public abstract void setValue(final ScriptObject self, final ScriptObject owner, final double value, final boolean strict);

    /**
     * Set the value of this property in {@code owner}. This allows to bypass creation of the
     * setter MethodHandle for spill and user accessor properties.
     *
     * @param self the this object
     * @param owner the owner object
     * @param value the new property value
     * @param strict is this a strict setter?
     */
    public abstract void setValue(final ScriptObject self, final ScriptObject owner, final Object value, final boolean strict);

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

        return equalsWithoutType(otherProperty) &&
               getCurrentType() == otherProperty.getCurrentType();
    }

    boolean equalsWithoutType(final Property otherProperty) {
        return getFlags() == otherProperty.getFlags() &&
                getSlot() == otherProperty.getSlot() &&
                getKey().equals(otherProperty.getKey());
    }

    private static final String type(final Class<?> type) {
        if (type == null) {
            return "undef";
        } else if (type == int.class) {
            return "i";
        } else if (type == long.class) {
            return "j";
        } else if (type == double.class) {
            return "d";
        } else {
            return "o";
        }
    }

    /**
     * Short toString version
     * @return short toString
     */
    public final String toStringShort() {
        final StringBuilder sb   = new StringBuilder();
        final Class<?>      type = getCurrentType();
        sb.append(getKey()).append(" (").append(type(type)).append(')');
        return sb.toString();
    }

    private static String indent(final String str, final int indent) {
        final StringBuilder sb = new StringBuilder();
        sb.append(str);
        for (int i = 0; i < indent - str.length(); i++) {
            sb.append(' ');
        }
        return sb.toString();
     }

    @Override
    public String toString() {
        final StringBuilder sb   = new StringBuilder();
        final Class<?>      type = getCurrentType();

        sb.append(indent(getKey(), 20)).
            append(" id=").
            append(Debug.id(this)).
            append(" (0x").
            append(indent(Integer.toHexString(flags), 4)).
            append(") ").
            append(getClass().getSimpleName()).
            append(" {").
            append(indent(type(type), 5)).
            append('}');

        if (slot != -1) {
            sb.append(" [").
               append("slot=").
               append(slot).
               append(']');
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
    public abstract Class<?> getCurrentType();

    /**
     * Reset the current type of this property
     * @param currentType new current type
     */
    public abstract void setCurrentType(final Class<?> currentType);

    /**
     * Check whether this Property can ever change its type. The default is false, and if
     * you are not running with dual fields, the type is always object and can never change
     * @return true if this property can change types
     */
    public boolean canChangeType() {
        return false;
    }

    /**
     * Check whether this property represents a function declaration.
     * @return whether this property is a function declaration or not.
     */
    public boolean isFunctionDeclaration() {
        return (flags & IS_FUNCTION_DECLARATION) == IS_FUNCTION_DECLARATION;
    }
}
