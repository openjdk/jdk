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

import java.lang.invoke.MethodHandle;
import jdk.nashorn.internal.codegen.ObjectClassGenerator;

/**
 * This class represents the result from a find property search.
 */
public final class FindProperty {
    /** Object where search began. */
    private final ScriptObject self;

    /** Object where search finish. */
    private final ScriptObject prototype;

    /** Found property. */
    private final Property     property;

    /**
     * Constructor
     *
     * @param self      script object where search began
     * @param prototype prototype where property was found, may be {@code self} if not inherited
     * @param property  property that was search result
     */
    public FindProperty(final ScriptObject self, final ScriptObject prototype, final Property property) {
        this.self      = self;
        this.prototype = prototype;
        this.property  = property;
    }

    /**
     * Ask for a getter that returns the given type. The type has nothing to do with the
     * internal representation of the property. It may be an Object (boxing primitives) or
     * a primitive (primitive fields with -Dnashorn.fields.dual=true)
     * @see ObjectClassGenerator
     *
     * @param type type of getter, e.g. int.class if we want a function with {@code get()I} signature
     * @return method handle for the getter
     */
    public MethodHandle getGetter(final Class<?> type) {
        MethodHandle getter = property.getGetter(type);
        if (property instanceof UserAccessorProperty) {
            final UserAccessorProperty uc = (UserAccessorProperty)property;
            getter = MH.insertArguments(getter, 0, isInherited() ? getOwner() : null, uc.getGetterSlot());
        }
        return getter;
    }

    /**
     * Ask for a setter that sets the given type. The type has nothing to do with the
     * internal representation of the property. It may be an Object (boxing primitives) or
     * a primitive (primitive fields with -Dnashorn.fields.dual=true)
     * @see ObjectClassGenerator
     *
     * @param type type of setter, e.g. int.class if we want a function with {@code set(I)V} signature
     * @param strict are we in strict mode
     *
     * @return method handle for the getter
     */
    public MethodHandle getSetter(final Class<?> type, final boolean strict) {
        MethodHandle setter = property.getSetter(type, getOwner().getMap());
        if (property instanceof UserAccessorProperty) {
            final UserAccessorProperty uc = (UserAccessorProperty) property;
            setter = MH.insertArguments(setter, 0, isInherited() ? getOwner() : null,
                    uc.getSetterSlot(), strict? property.getKey() : null);
        }

        return setter;
    }

    /**
     * Return the {@code ScriptObject} owning of the property:  this means the prototype.
     * @return owner of property
     */
    public ScriptObject getOwner() {
        return prototype;
    }

    /**
     * Return the appropriate receiver for a getter.
     * @return appropriate receiver
     */
    public ScriptObject getGetterReceiver() {
        return property != null && property.hasGetterFunction(prototype) ? self : prototype;
    }

   /**
     * Return the appropriate receiver for a setter.
     * @return appropriate receiver
     */
    public ScriptObject getSetterReceiver() {
        return property != null && property.hasSetterFunction(prototype) ? self : prototype;
    }

    /**
     * Return the property that was found
     * @return property
     */
    public Property getProperty() {
        return property;
    }

    /**
     * Check if the property found was inherited, i.e. not directly in the self
     * @return true if inherited property
     */
    public boolean isInherited() {
        return self != prototype;
    }

    /**
     * Check if the property found was NOT inherited, i.e. defined in the script
     * object, rather than in the prototype
     * @return true if not inherited
     */
    public boolean isSelf() {
        return self == prototype;
    }

    /**
     * Check if the property is in the scope
     * @return true if on scope
     */
    public boolean isScope() {
        return prototype.isScope();
    }

    /**
     * Get the property value from self as object.
     *
     * @return the property value
     */
    public Object getObjectValue() {
        return property.getObjectValue(getGetterReceiver(), getOwner());
    }

    /**
     * Set the property value in self.
     *
     * @param value the new value
     * @param strict strict flag
     */
    public void setObjectValue(final Object value, final boolean strict) {
        property.setObjectValue(getSetterReceiver(), getOwner(), value, strict);
    }

}

