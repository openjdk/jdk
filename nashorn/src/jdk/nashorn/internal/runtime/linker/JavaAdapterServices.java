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

package jdk.nashorn.internal.runtime.linker;

import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.Undefined;

/**
 * Provides static utility services to generated Java adapter classes.
 */
public final class JavaAdapterServices {
    private static final ThreadLocal<ScriptObject> classOverrides = new ThreadLocal<>();

    private JavaAdapterServices() {
    }

    /**
     * Given a JS script function, binds it to null JS "this", and adapts its parameter types, return types, and arity
     * to the specified type and arity. This method is public mainly for implementation reasons, so the adapter classes
     * can invoke it from their constructors that take a ScriptFunction in its first argument to obtain the method
     * handles for their abstract method implementations.
     * @param fn the script function
     * @param type the method type it has to conform to
     * @return the appropriately adapted method handle for invoking the script function.
     */
    public static MethodHandle getHandle(final ScriptFunction fn, final MethodType type) {
        // JS "this" will be global object or undefined depending on if 'fn' is strict or not
        return adaptHandle(fn.getBoundInvokeHandle(fn.isStrict()? ScriptRuntime.UNDEFINED : Context.getGlobal()), type);
    }

    /**
     * Given a JS script object, retrieves a function from it by name, binds it to the script object as its "this", and
     * adapts its parameter types, return types, and arity to the specified type and arity. This method is public mainly
     * for implementation reasons, so the adapter classes can invoke it from their constructors that take a Object
     * in its first argument to obtain the method handles for their method implementations.
     * @param obj the script obj
     * @param name the name of the property that contains the function
     * @param type the method type it has to conform to
     * @return the appropriately adapted method handle for invoking the script function, or null if the value of the
     * property is either null or undefined, or "toString" was requested as the name, but the object doesn't directly
     * define it but just inherits it through prototype.
     */
    public static MethodHandle getHandle(final Object obj, final String name, final MethodType type) {
        if (! (obj instanceof ScriptObject)) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(obj));
        }

        final ScriptObject sobj = (ScriptObject)obj;
        // Since every JS Object has a toString, we only override "String toString()" it if it's explicitly specified
        if ("toString".equals(name) && !sobj.hasOwnProperty("toString")) {
            return null;
        }

        final Object fnObj = sobj.get(name);
        if (fnObj instanceof ScriptFunction) {
            return adaptHandle(((ScriptFunction)fnObj).getBoundInvokeHandle(sobj), type);
        } else if(fnObj == null || fnObj instanceof Undefined) {
            return null;
        } else {
            throw typeError("not.a.function", name);
        }
    }

    /**
     * Returns a thread-local JS object used to define methods for the adapter class being initialized on the current
     * thread. This method is public solely for implementation reasons, so the adapter classes can invoke it from their
     * static initializers.
     * @return the thread-local JS object used to define methods for the class being initialized.
     */
    public static ScriptObject getClassOverrides() {
        final ScriptObject overrides = classOverrides.get();
        assert overrides != null;
        return overrides;
    }

    static void setClassOverrides(ScriptObject overrides) {
        classOverrides.set(overrides);
    }

    private static MethodHandle adaptHandle(final MethodHandle handle, final MethodType type) {
        return Bootstrap.getLinkerServices().asType(ScriptObject.pairArguments(handle, type, false), type);
    }
}
