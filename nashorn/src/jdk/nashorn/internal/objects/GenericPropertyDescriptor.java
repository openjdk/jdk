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
 * Generic Property descriptor is used to represent attributes an object property
 * that is neither a data property descriptor nor an accessor property descriptor.
 *
 * See ECMA 8.10 The Property Descriptor and Property Identifier Specification Types
 *
 */
@ScriptClass("GenericPropertyDescriptor")
public final class GenericPropertyDescriptor extends ScriptObject implements PropertyDescriptor {
    /** Is the property configurable? */
    @Property
    public Object configurable;

    /** Is the property writable? */
    @Property
    public Object enumerable;

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    GenericPropertyDescriptor() {
        this(false, false);
    }

    GenericPropertyDescriptor(final boolean configurable, final boolean enumerable) {
        super(Global.objectPrototype(), $nasgenmap$);
        this.configurable = configurable;
        this.enumerable   = enumerable;
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
        return false;
    }

    @Override
    public Object getValue() {
        throw new UnsupportedOperationException("value");
    }

    @Override
    public ScriptFunction getGetter() {
        throw new UnsupportedOperationException("get");
    }

    @Override
    public ScriptFunction getSetter() {
        throw new UnsupportedOperationException("set");
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

        return this;
    }

    @Override
    public int type() {
        return GENERIC;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof GenericPropertyDescriptor)) {
            return false;
        }

        final GenericPropertyDescriptor other = (GenericPropertyDescriptor)obj;
        return ScriptRuntime.sameValue(configurable, other.configurable) &&
               ScriptRuntime.sameValue(enumerable, other.enumerable);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(this.configurable);
        hash = 97 * hash + Objects.hashCode(this.enumerable);
        return hash;
    }
}
