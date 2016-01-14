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
import java.util.Set;

/**
 * This is the base class for nashorn ScriptObjectMirror class.
 *
 * This class can also be subclassed by an arbitrary Java class. Nashorn will
 * treat objects of such classes just like nashorn script objects. Usual nashorn
 * operations like obj[i], obj.foo, obj.func(), delete obj.foo will be glued
 * to appropriate method call of this class.
 *
 * @since 1.8u40
 */
public abstract class AbstractJSObject implements JSObject {
    /**
     * Call this object as a JavaScript function. This is equivalent to
     * 'func.apply(thiz, args)' in JavaScript.
     *
     * @param thiz 'this' object to be passed to the function
     * @param args arguments to method
     * @return result of call
     */
    @Override
    public Object call(final Object thiz, final Object... args) {
        throw new UnsupportedOperationException("call");
    }

    /**
     * Call this 'constructor' JavaScript function to create a new object.
     * This is equivalent to 'new func(arg1, arg2...)' in JavaScript.
     *
     * @param args arguments to method
     * @return result of constructor call
     */
    @Override
    public Object newObject(final Object... args) {
        throw new UnsupportedOperationException("newObject");
    }

    /**
     * Evaluate a JavaScript expression.
     *
     * @param s JavaScript expression to evaluate
     * @return evaluation result
     */
    @Override
    public Object eval(final String s) {
        throw new UnsupportedOperationException("eval");
    }

    /**
     * Retrieves a named member of this JavaScript object.
     *
     * @param name of member
     * @return member
     */
    @Override
    public Object getMember(final String name) {
        return null;
    }

    /**
     * Retrieves an indexed member of this JavaScript object.
     *
     * @param index index slot to retrieve
     * @return member
     */
    @Override
    public Object getSlot(final int index) {
        return null;
    }

    /**
     * Does this object have a named member?
     *
     * @param name name of member
     * @return true if this object has a member of the given name
     */
    @Override
    public boolean hasMember(final String name) {
        return false;
    }

    /**
     * Does this object have a indexed property?
     *
     * @param slot index to check
     * @return true if this object has a slot
     */
    @Override
    public boolean hasSlot(final int slot) {
        return false;
    }

    /**
     * Remove a named member from this JavaScript object
     *
     * @param name name of the member
     */
    @Override
    public void removeMember(final String name) {
        //empty
    }

    /**
     * Set a named member in this JavaScript object
     *
     * @param name  name of the member
     * @param value value of the member
     */
    @Override
    public void setMember(final String name, final Object value) {
        //empty
    }

    /**
     * Set an indexed member in this JavaScript object
     *
     * @param index index of the member slot
     * @param value value of the member
     */
    @Override
    public void setSlot(final int index, final Object value) {
        //empty
    }

    // property and value iteration

    /**
     * Returns the set of all property names of this object.
     *
     * @return set of property names
     */
    @Override
    public Set<String> keySet() {
        return Collections.emptySet();
    }

    /**
     * Returns the set of all property values of this object.
     *
     * @return set of property values.
     */
    @Override
    public Collection<Object> values() {
        return Collections.emptySet();
    }

    // JavaScript instanceof check

    /**
     * Checking whether the given object is an instance of 'this' object.
     *
     * @param instance instance to check
     * @return true if the given 'instance' is an instance of this 'function' object
     */
    @Override
    public boolean isInstance(final Object instance) {
        return false;
    }

    /**
     * Checking whether this object is an instance of the given 'clazz' object.
     *
     * @param clazz clazz to check
     * @return true if this object is an instance of the given 'clazz'
     */
    @Override
    public boolean isInstanceOf(final Object clazz) {
        if (clazz instanceof JSObject) {
            return ((JSObject)clazz).isInstance(this);
        }

        return false;
    }

    /**
     * ECMA [[Class]] property
     *
     * @return ECMA [[Class]] property value of this object
     */
    @Override
    public String getClassName() {
        return getClass().getName();
    }

    /**
     * Is this a function object?
     *
     * @return if this mirror wraps a ECMAScript function instance
     */
    @Override
    public boolean isFunction() {
        return false;
    }

    /**
     * Is this a 'use strict' function object?
     *
     * @return true if this mirror represents a ECMAScript 'use strict' function
     */
    @Override
    public boolean isStrictFunction() {
        return false;
    }

    /**
     * Is this an array object?
     *
     * @return if this mirror wraps a ECMAScript array object
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
