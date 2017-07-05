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

package jdk.nashorn.internal.lookup;

import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.ScriptRuntime;

/**
 * MethodHandle Lookup management for Nashorn.
 */
public final class Lookup {

    /**
     * A global singleton that points to the {@link MethodHandleFunctionality}. This is basically
     * a collection of wrappers to the standard methods in {@link MethodHandle}, {@link MethodHandles} and
     * {@link java.lang.invoke.MethodHandles.Lookup}, but instrumentation and debugging purposes we need
     * intercept points.
     * <p>
     * All method handle operations in Nashorn should go through this field, not directly to the classes
     * in {@code java.lang.invoke}
     */
    public static final MethodHandleFunctionality MH = MethodHandleFactory.getFunctionality();

    /** Method handle to the empty getter */
    public static final MethodHandle EMPTY_GETTER = findOwnMH("emptyGetter", Object.class, Object.class);

    /** Method handle to the empty setter */
    public static final MethodHandle EMPTY_SETTER = findOwnMH("emptySetter", void.class, Object.class, Object.class);

    /** Method handle to a getter that only throws type error */
    public static final MethodHandle TYPE_ERROR_THROWER_GETTER = findOwnMH("typeErrorThrowerGetter", Object.class, Object.class);

    /** Method handle to a setter that only throws type error */
    public static final MethodHandle TYPE_ERROR_THROWER_SETTER = findOwnMH("typeErrorThrowerSetter", void.class, Object.class, Object.class);

    /** Method handle to the most generic of getters, the one that returns an Object */
    public static final MethodType GET_OBJECT_TYPE = MH.type(Object.class, Object.class);

    /** Method handle to the most generic of setters, the one that takes an Object */
    public static final MethodType SET_OBJECT_TYPE = MH.type(void.class, Object.class, Object.class);

    private Lookup() {
    }

    /**
     * Empty getter implementation. Nop
     * @param self self reference
     * @return undefined
     */
    public static Object emptyGetter(final Object self) {
        return UNDEFINED;
    }

    /**
     * Empty setter implementation. Nop
     * @param self  self reference
     * @param value value (ignored)
     */
    public static void emptySetter(final Object self, final Object value) {
        // do nothing!!
    }

    /**
     * Return a method handle to the empty getter, with a different
     * return type value. It will still be undefined cast to whatever
     * return value property was specified
     *
     * @param type return value type
     *
     * @return undefined as return value type
     */
    public static MethodHandle emptyGetter(final Class<?> type) {
        return filterReturnType(EMPTY_GETTER, type);
    }

    /**
     * Getter function that always throws type error
     *
     * @param self  self reference
     * @return undefined (but throws error before return point)
     */
    public static Object typeErrorThrowerGetter(final Object self) {
        throw typeError("strict.getter.setter.poison", ScriptRuntime.safeToString(self));
    }

    /**
     * Getter function that always throws type error
     *
     * @param self  self reference
     * @param value (ignored)
     */
    public static void typeErrorThrowerSetter(final Object self, final Object value) {
        throw typeError("strict.getter.setter.poison", ScriptRuntime.safeToString(self));
    }

    /**
     * This method filters primitive return types using JavaScript semantics. For example,
     * an (int) cast of a double in Java land is not the same thing as invoking toInt32 on it.
     * If you are returning values to JavaScript that have to be of a specific type, this is
     * the correct return value filter to use, as the explicitCastArguments just uses the
     * Java boxing equivalents
     *
     * @param mh   method handle for which to filter return value
     * @param type new return type
     * @return method handle for appropriate return type conversion
     */
    public static MethodHandle filterReturnType(final MethodHandle mh, final Class<?> type) {
        final Class<?> retType = mh.type().returnType();

        if (retType == int.class) {
            //fallthru
        } else if (retType == long.class) {
            //fallthru
        } else if (retType == double.class) {
            if (type == int.class) {
                return MH.filterReturnValue(mh, JSType.TO_INT32_D.methodHandle());
            } else if (type == long.class) {
                return MH.filterReturnValue(mh, JSType.TO_UINT32_D.methodHandle());
            }
            //fallthru
        } else if (!retType.isPrimitive()) {
            if (type == int.class) {
                return MH.filterReturnValue(mh, JSType.TO_INT32.methodHandle());
            } else if (type == long.class) {
                return MH.filterReturnValue(mh, JSType.TO_UINT32.methodHandle());
            } else if (type == double.class) {
                return MH.filterReturnValue(mh, JSType.TO_NUMBER.methodHandle());
            } else if (!type.isPrimitive()) {
                return mh;
            }

            assert false : "unsupported Lookup.filterReturnType type " + retType + " -> " + type;
        }

        //use a standard cast - we don't need to check JavaScript special cases
        return MH.explicitCastArguments(mh, mh.type().changeReturnType(type));
    }

    private static MethodHandle findOwnMH(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findStatic(MethodHandles.lookup(), Lookup.class, name, MH.type(rtype, types));
    }

}
