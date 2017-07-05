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
 */
public abstract class JSObject {
    /**
     * Call this object as a JavaScript function. This is equivalent to
     * 'func.apply(thiz, args)' in JavaScript.
     *
     * @param thiz 'this' object to be passed to the function
     * @param args arguments to method
     * @return result of call
     */
    public Object call(Object thiz, Object... args) {
        throw new UnsupportedOperationException("call");
    }

    /**
     * Call this 'constructor' JavaScript function to create a new object.
     * This is equivalent to 'new func(arg1, arg2...)' in JavaScript.
     *
     * @param args arguments to method
     * @return result of constructor call
     */
    public Object newObject(Object... args) {
        throw new UnsupportedOperationException("newObject");
    }

    /**
     * Evaluate a JavaScript expression.
     *
     * @param s JavaScript expression to evaluate
     * @return evaluation result
     */
    public Object eval(String s) {
        throw new UnsupportedOperationException("eval");
    }

    /**
     * Call a JavaScript function member of this object.
     *
     * @param name name of the member function to call
     * @param args arguments to be passed to the member function
     * @return result of call
     */
    public Object callMember(String name, Object... args) {
        throw new UnsupportedOperationException("call");
    }

    /**
     * Retrieves a named member of this JavaScript object.
     *
     * @param name of member
     * @return member
     */
    public Object getMember(String name) {
        return null;
    }

    /**
     * Retrieves an indexed member of this JavaScript object.
     *
     * @param index index slot to retrieve
     * @return member
     */
    public Object getSlot(int index) {
        return null;
    }

    /**
     * Does this object have a named member?
     *
     * @param name name of member
     * @return true if this object has a member of the given name
     */
    public boolean hasMember(String name) {
        return false;
    }

    /**
     * Does this object have a indexed property?
     *
     * @param slot index to check
     * @return true if this object has a slot
     */
    public boolean hasSlot(int slot) {
        return false;
    }

    /**
     * Remove a named member from this JavaScript object
     *
     * @param name name of the member
     */
    public void removeMember(String name) {
    }

    /**
     * Set a named member in this JavaScript object
     *
     * @param name  name of the member
     * @param value value of the member
     */
    public void setMember(String name, Object value) {
    }

    /**
     * Set an indexed member in this JavaScript object
     *
     * @param index index of the member slot
     * @param value value of the member
     */
    public void setSlot(int index, Object value) {
    }

    // property and value iteration

    /**
     * Returns the set of all property names of this object.
     *
     * @return set of property names
     */
    @SuppressWarnings("unchecked")
    public Set<String> keySet() {
        return Collections.EMPTY_SET;
    }

    /**
     * Returns the set of all property values of this object.
     *
     * @return set of property values.
     */
    @SuppressWarnings("unchecked")
    public Collection<Object> values() {
        return Collections.EMPTY_SET;
    }

    // JavaScript instanceof check

    /**
     * Checking whether the given object is an instance of 'this' object.
     *
     * @param instance instace to check
     * @return true if the given 'instance' is an instance of this 'function' object
     */
    public boolean isInstance(final Object instance) {
        return false;
    }

    /**
     * Checking whether this object is an instance of the given 'clazz' object.
     *
     * @param clazz clazz to check
     * @return true if this object is an instance of the given 'clazz'
     */
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
    public String getClassName() {
        return getClass().getName();
    }

    /**
     * Is this a function object?
     *
     * @return if this mirror wraps a ECMAScript function instance
     */
    public boolean isFunction() {
        return false;
    }

    /**
     * Is this a 'use strict' function object?
     *
     * @return true if this mirror represents a ECMAScript 'use strict' function
     */
    public boolean isStrictFunction() {
        return false;
    }

    /**
     * Is this an array object?
     *
     * @return if this mirror wraps a ECMAScript array object
     */
    public boolean isArray() {
        return false;
    }
}
