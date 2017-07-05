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

import static jdk.nashorn.internal.runtime.ScriptRuntime.sameValue;

import java.util.Objects;
import jdk.nashorn.internal.objects.annotations.Property;
import jdk.nashorn.internal.objects.annotations.ScriptClass;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.PropertyDescriptor;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;

/**
 * Data Property descriptor is used to represent attributes an object property
 * that has data value (instead of a getter or setter function).
 *
 * See ECMA 8.10 The Property Descriptor and Property Identifier Specification Types
 *
 */
@ScriptClass("DataPropertyDescriptor")
public final class DataPropertyDescriptor extends ScriptObject implements PropertyDescriptor {
    /** is this property configurable */
    @Property
    public Object configurable;

    /** is this property enumerable */
    @Property
    public Object enumerable;

    /** is this property writable */
    @Property
    public Object writable;

    /** value of this property */
    @Property
    public Object value;

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    DataPropertyDescriptor(final boolean configurable, final boolean enumerable, final boolean writable, final Object value, final Global global) {
        super(global.getObjectPrototype(), $nasgenmap$);
        this.configurable = configurable;
        this.enumerable   = enumerable;
        this.writable     = writable;
        this.value        = value;
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
        return JSType.toBoolean(writable);
    }

    @Override
    public Object getValue() {
        return value;
    }

    @Override
    public ScriptFunction getGetter() {
        throw new UnsupportedOperationException("getter");
    }

    @Override
    public ScriptFunction getSetter() {
        throw new UnsupportedOperationException("setter");
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
        this.writable = flag;
    }

    @Override
    public void setValue(final Object value) {
        this.value = value;
    }

    @Override
    public void setGetter(final Object getter) {
        throw new UnsupportedOperationException("set getter");
    }

    @Override
    public void setSetter(final Object setter) {
        throw new UnsupportedOperationException("set setter");
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

        if (sobj.has(WRITABLE)) {
            this.writable = JSType.toBoolean(sobj.get(WRITABLE));
        } else {
            delete(WRITABLE, false);
        }

        if (sobj.has(VALUE)) {
            this.value = sobj.get(VALUE);
        } else {
            delete(VALUE, false);
        }

        return this;
    }

    @Override
    public int type() {
        return DATA;
    }

    @Override
    public boolean hasAndEquals(final PropertyDescriptor otherDesc) {
        if (! (otherDesc instanceof DataPropertyDescriptor)) {
            return false;
        }

        final DataPropertyDescriptor other = (DataPropertyDescriptor)otherDesc;
        return (!has(CONFIGURABLE) || sameValue(configurable, other.configurable)) &&
               (!has(ENUMERABLE) || sameValue(enumerable, other.enumerable)) &&
               (!has(WRITABLE) || sameValue(writable, other.writable)) &&
               (!has(VALUE) || sameValue(value, other.value));
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (! (obj instanceof DataPropertyDescriptor)) {
            return false;
        }

        final DataPropertyDescriptor other = (DataPropertyDescriptor)obj;
        return sameValue(configurable, other.configurable) &&
               sameValue(enumerable, other.enumerable) &&
               sameValue(writable, other.writable) &&
               sameValue(value, other.value);
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 43 * hash + Objects.hashCode(this.configurable);
        hash = 43 * hash + Objects.hashCode(this.enumerable);
        hash = 43 * hash + Objects.hashCode(this.writable);
        hash = 43 * hash + Objects.hashCode(this.value);
        return hash;
    }
}
