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
import static jdk.nashorn.internal.runtime.ScriptRuntime.sameValue;

import java.util.Objects;
import jdk.nashorn.internal.objects.annotations.Property;
import jdk.nashorn.internal.objects.annotations.ScriptClass;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.PropertyDescriptor;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;

/**
 * Accessor Property descriptor is used to represent attributes an object property
 * that either has a getter or a setter.
 *
 * See ECMA 8.10 The Property Descriptor and Property Identifier Specification Types
 *
 */
@ScriptClass("AccessorPropertyDescriptor")
public final class AccessorPropertyDescriptor extends ScriptObject implements PropertyDescriptor {
    /** is this property configurable? */
    @Property
    public Object configurable;

    /** is this property enumerable? */
    @Property
    public Object enumerable;

    /** getter for property */
    @Property
    public Object get;

    /** setter for property */
    @Property
    public Object set;

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    AccessorPropertyDescriptor(final boolean configurable, final boolean enumerable, final Object get, final Object set, final Global global) {
        super(global.getObjectPrototype(), $nasgenmap$);
        this.configurable = configurable;
        this.enumerable   = enumerable;
        this.get          = get;
        this.set          = set;
    }

    @Override
    public boolean isConfigurable() {
        return JSType.toBoolean(configurable);
    }

    @Override
    public boolean isEnumerable() {
        return JSType.toBoolean(enumerable);
    }

    @Override
    public boolean isWritable() {
        // Not applicable for this. But simplifies flag calculations.
        return true;
    }

    @Override
    public Object getValue() {
        throw new UnsupportedOperationException("value");
    }

    @Override
    public ScriptFunction getGetter() {
        return (get instanceof ScriptFunction) ? (ScriptFunction)get : null;
    }

    @Override
    public ScriptFunction getSetter() {
        return (set instanceof ScriptFunction) ? (ScriptFunction)set : null;
    }

    @Override
    public void setConfigurable(final boolean flag) {
        this.configurable = flag;
    }

    @Override
    public void setEnumerable(final boolean flag) {
        this.enumerable = flag;
    }

    @Override
    public void setWritable(final boolean flag) {
        throw new UnsupportedOperationException("set writable");
    }

    @Override
    public void setValue(final Object value) {
        throw new UnsupportedOperationException("set value");
    }

    @Override
    public void setGetter(final Object getter) {
        this.get = getter;
    }

    @Override
    public void setSetter(final Object setter) {
        this.set = setter;
    }

    @Override
    public PropertyDescriptor fillFrom(final ScriptObject sobj) {
        if (sobj.has(CONFIGURABLE)) {
            this.configurable = JSType.toBoolean(sobj.get(CONFIGURABLE));
        } else {
            delete(CONFIGURABLE, false);
        }

        if (sobj.has(ENUMERABLE)) {
            this.enumerable = JSType.toBoolean(sobj.get(ENUMERABLE));
        } else {
            delete(ENUMERABLE, false);
        }

        if (sobj.has(GET)) {
            final Object getter = sobj.get(GET);
            if (getter == UNDEFINED || getter instanceof ScriptFunction) {
                this.get = getter;
            } else {
                throw typeError("not.a.function", ScriptRuntime.safeToString(getter));
            }
        } else {
            delete(GET, false);
        }

        if (sobj.has(SET)) {
            final Object setter = sobj.get(SET);
            if (setter == UNDEFINED || setter instanceof ScriptFunction) {
                this.set = setter;
            } else {
                throw typeError("not.a.function", ScriptRuntime.safeToString(setter));
            }
        } else {
            delete(SET, false);
        }

        return this;
    }

    @Override
    public int type() {
        return ACCESSOR;
    }

    @Override
    public boolean hasAndEquals(final PropertyDescriptor otherDesc) {
        if (! (otherDesc instanceof AccessorPropertyDescriptor)) {
            return false;
        }
        final AccessorPropertyDescriptor other = (AccessorPropertyDescriptor)otherDesc;
        return (!has(CONFIGURABLE) || sameValue(configurable, other.configurable)) &&
               (!has(ENUMERABLE) || sameValue(enumerable, other.enumerable)) &&
               (!has(GET) || sameValue(get, other.get)) &&
               (!has(SET) || sameValue(set, other.set));
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (! (obj instanceof AccessorPropertyDescriptor)) {
            return false;
        }

        final AccessorPropertyDescriptor other = (AccessorPropertyDescriptor)obj;
        return sameValue(configurable, other.configurable) &&
               sameValue(enumerable, other.enumerable) &&
               sameValue(get, other.get) &&
               sameValue(set, other.set);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 41 * hash + Objects.hashCode(this.configurable);
        hash = 41 * hash + Objects.hashCode(this.enumerable);
        hash = 41 * hash + Objects.hashCode(this.get);
        hash = 41 * hash + Objects.hashCode(this.set);
        return hash;
    }
}
