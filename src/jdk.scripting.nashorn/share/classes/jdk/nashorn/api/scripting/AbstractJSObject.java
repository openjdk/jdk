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

package jdk.nashorn.api.scripting;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * This is the base class for nashorn ScriptObjectMirror class.
 *
 * This class can also be subclassed by an arbitrary Java class. Nashorn will
 * treat objects of such classes just like nashorn script objects. Usual nashorn
 * operations like obj[i], obj.foo, obj.func(), delete obj.foo will be delegated
 * to appropriate method call of this class.
 *
 * @deprecated Nashorn JavaScript script engine and APIs, and the jjs tool
 * are deprecated with the intent to remove them in a future release.
 *
 * @since 1.8u40
 */
@Deprecated(since="11", forRemoval=true)
public abstract class AbstractJSObject implements JSObject {
    /**
     * The default constructor.
     */
    public AbstractJSObject() {}

    /**
     * @implSpec This implementation always throws UnsupportedOperationException
     */
    @Override
    public Object call(final Object thiz, final Object... args) {
        throw new UnsupportedOperationException("call");
    }

    /**
     * @implSpec This implementation always throws UnsupportedOperationException
     */
    @Override
    public Object newObject(final Object... args) {
        throw new UnsupportedOperationException("newObject");
    }

    /**
     * @implSpec This imlementation always throws UnsupportedOperationException
     */
    @Override
    public Object eval(final String s) {
        throw new UnsupportedOperationException("eval");
    }

    /**
     * @implSpec This implementation always returns null
     */
    @Override
    public Object getMember(final String name) {
        Objects.requireNonNull(name);
        return null;
    }

    /**
     * @implSpec This implementation always returns null
     */
    @Override
    public Object getSlot(final int index) {
        return null;
    }

    /**
     * @implSpec This implementation always returns false
     */
    @Override
    public boolean hasMember(final String name) {
        Objects.requireNonNull(name);
        return false;
    }

    /**
     * @implSpec This implementation always returns false
     */
    @Override
    public boolean hasSlot(final int slot) {
        return false;
    }

    /**
     * @implSpec This implementation is a no-op
     */
    @Override
    public void removeMember(final String name) {
        Objects.requireNonNull(name);
        //empty
    }

    /**
     * @implSpec This implementation is a no-op
     */
    @Override
    public void setMember(final String name, final Object value) {
        Objects.requireNonNull(name);
        //empty
    }

    /**
     * @implSpec This implementation is a no-op
     */
    @Override
    public void setSlot(final int index, final Object value) {
        //empty
    }

    // property and value iteration

    /**
     * @implSpec This implementation returns empty set
     */
    @Override
    public Set<String> keySet() {
        return Collections.emptySet();
    }

    /**
     * @implSpec This implementation returns empty set
     */
    @Override
    public Collection<Object> values() {
        return Collections.emptySet();
    }

    // JavaScript instanceof check

    /**
     * @implSpec This implementation always returns false
     */
    @Override
    public boolean isInstance(final Object instance) {
        return false;
    }

    @Override
    public boolean isInstanceOf(final Object clazz) {
        if (clazz instanceof JSObject) {
            return ((JSObject)clazz).isInstance(this);
        }

        return false;
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    /**
     * @implSpec This implementation always returns false
     */
    @Override
    public boolean isFunction() {
        return false;
    }

    /**
     * @implSpec This implementation always returns false
     */
    @Override
    public boolean isStrictFunction() {
        return false;
    }

    /**
     * @implSpec This implementation always returns false
     */
    @Override
    public boolean isArray() {
        return false;
    }

    /**
     * Returns this object's numeric value.
     *
     * @return this object's numeric value.
     * @deprecated use {@link #getDefaultValue(Class)} with {@link Number} hint instead.
     */
    @Override @Deprecated
    public double toNumber() {
        return Double.NaN;
    }

    /**
     * When passed an {@link AbstractJSObject}, invokes its {@link #getDefaultValue(Class)} method. When passed any
     * other {@link JSObject}, it will obtain its {@code [[DefaultValue]]} method as per ECMAScript 5.1 section
     * 8.6.2.
     *
     * @param jsobj the {@link JSObject} whose {@code [[DefaultValue]]} is obtained.
     * @param hint the type hint. Should be either {@code null}, {@code Number.class} or {@code String.class}.
     * @return this object's default value.
     * @throws UnsupportedOperationException if the conversion can't be performed. The engine will convert this
     * exception into a JavaScript {@code TypeError}.
     * @deprecated use {@link JSObject#getDefaultValue(Class)} instead.
     */
    @Deprecated
    public static Object getDefaultValue(final JSObject jsobj, final Class<?> hint) {
        return jsobj.getDefaultValue(hint);
    }
}
