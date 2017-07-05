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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.SwitchPoint;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Wrapper for all method handle related functions used in Nashorn. This interface only exists
 * so that instrumentation can be added to all method handle operations.
 */

public interface MethodHandleFunctionality {
    /**
     * Wrapper for {@link MethodHandles#filterArguments(MethodHandle, int, MethodHandle...)}
     *
     * @param target  target method handle
     * @param pos     start argument index
     * @param filters filters
     *
     * @return filtered handle
     */
    public MethodHandle filterArguments(MethodHandle target, int pos, MethodHandle... filters);

    /**
     * Wrapper for {@link MethodHandles#filterReturnValue(MethodHandle, MethodHandle)}
     *
     * @param target  target method handle
     * @param filter  filter
     *
     * @return filtered handle
     */
    public MethodHandle filterReturnValue(MethodHandle target, MethodHandle filter);

    /**
     * Wrapper for {@link MethodHandles#guardWithTest(MethodHandle, MethodHandle, MethodHandle)}
     *
     * @param test     test method handle
     * @param target   target method handle when test is true
     * @param fallback fallback method handle when test is false
     *
     * @return guarded handles
     */
    public MethodHandle guardWithTest(MethodHandle test, MethodHandle target, MethodHandle fallback);

    /**
     * Wrapper for {@link MethodHandles#insertArguments(MethodHandle, int, Object...)}
     *
     * @param target target method handle
     * @param pos    start argument index
     * @param values values to insert
     *
     * @return handle with bound arguments
     */
    public MethodHandle insertArguments(MethodHandle target, int pos, Object... values);

    /**
     * Wrapper for {@link MethodHandles#dropArguments(MethodHandle, int, Class...)}
     *
     * @param target     target method handle
     * @param pos        start argument index
     * @param valueTypes valueTypes of arguments to drop
     *
     * @return handle with dropped arguments
     */
    public MethodHandle dropArguments(MethodHandle target, int pos, Class<?>... valueTypes);

    /**
     * Wrapper for {@link MethodHandles#dropArguments(MethodHandle, int, List)}
     *
     * @param target     target method handle
     * @param pos        start argument index
     * @param valueTypes valueTypes of arguments to drop
     *
     * @return handle with dropped arguments
     */
    public MethodHandle dropArguments(final MethodHandle target, final int pos, final List<Class<?>> valueTypes);

    /**
     * Wrapper for {@link MethodHandles#foldArguments(MethodHandle, MethodHandle)}
     *
     * @param target   target method handle
     * @param combiner combiner to apply for fold
     *
     * @return folded method handle
     */
    public MethodHandle foldArguments(MethodHandle target, MethodHandle combiner);

    /**
     * Wrapper for {@link MethodHandles#explicitCastArguments(MethodHandle, MethodType)}
     *
     * @param target  target method handle
     * @param type    type to cast to
     *
     * @return modified method handle
     */
    public MethodHandle explicitCastArguments(MethodHandle target, MethodType type);

    /**
     * Wrapper for {@link java.lang.invoke.MethodHandles#arrayElementGetter(Class)}
     *
     * @param arrayClass class for array
     *
     * @return array element getter
     */
    public MethodHandle arrayElementGetter(Class<?> arrayClass);

    /**
     * Wrapper for {@link java.lang.invoke.MethodHandles#arrayElementSetter(Class)}
     *
     * @param arrayClass class for array
     *
     * @return array element setter
     */
    public MethodHandle arrayElementSetter(Class<?> arrayClass);

    /**
     * Wrapper for {@link java.lang.invoke.MethodHandles#throwException(Class, Class)}
     *
     * @param returnType ignored, but method signature will use it
     * @param exType     exception type that will be thrown
     *
     * @return exception thrower method handle
     */
    public MethodHandle throwException(Class<?> returnType, Class<? extends Throwable> exType);

    /**
     * Wrapper for {@link java.lang.invoke.MethodHandles#catchException(MethodHandle, Class, MethodHandle)}
     *
     * @param target  target method
     * @param exType  exception type
     * @param handler the method handle to call when exception is thrown
     *
     * @return exception thrower method handle
     */
    public MethodHandle catchException(final MethodHandle target, final Class<? extends Throwable> exType, final MethodHandle handler);

    /**
     * Wrapper for {@link java.lang.invoke.MethodHandles#constant(Class, Object)}
     *
     * @param type  type of constant
     * @param value constant value
     *
     * @return method handle that returns said constant
     */
    public MethodHandle constant(Class<?> type, Object value);

    /**
     * Wrapper for {@link java.lang.invoke.MethodHandles#identity(Class)}
     *
     * @param type  type of value
     *
     * @return method handle that returns identity argument
     */
    public MethodHandle identity(Class<?> type);

    /**
     * Wrapper for {@link java.lang.invoke.MethodHandle#asType(MethodType)}
     *
     * @param handle  method handle for type conversion
     * @param type    type to convert to
     *
     * @return method handle with given type conversion applied
     */
    public MethodHandle asType(MethodHandle handle, MethodType type);

    /**
     * Wrapper for {@link java.lang.invoke.MethodHandle#asCollector(Class, int)}
     *
     * @param handle      handle to convert
     * @param arrayType   array type for collector array
     * @param arrayLength length of collector array
     *
     * @return method handle with collector
     */
    public MethodHandle asCollector(MethodHandle handle, Class<?> arrayType, int arrayLength);

    /**
     * Wrapper for {@link java.lang.invoke.MethodHandle#asSpreader(Class, int)}
     *
     * @param handle      handle to convert
     * @param arrayType   array type for spread
     * @param arrayLength length of spreader
     *
     * @return method handle as spreader
     */
    public MethodHandle asSpreader(MethodHandle handle, Class<?> arrayType, int arrayLength);

    /**
     * Wrapper for {@link java.lang.invoke.MethodHandle#bindTo(Object)}
     *
     * @param handle a handle to which to bind a receiver
     * @param x      the receiver
     *
     * @return the bound handle
     */
    public MethodHandle bindTo(MethodHandle handle, Object x);

    /**
     * Wrapper for {@link java.lang.invoke.MethodHandles.Lookup#findGetter(Class, String, Class)}
      *
     * @param explicitLookup explicit lookup to be used
     * @param clazz          class to look in
     * @param name           name of field
     * @param type           type of field
     *
     * @return getter method handle for virtual field
     */
    public MethodHandle getter(MethodHandles.Lookup explicitLookup, Class<?> clazz, String name, Class<?> type);

    /**
     * Wrapper for {@link java.lang.invoke.MethodHandles.Lookup#findStaticGetter(Class, String, Class)}
      *
     * @param explicitLookup explicit lookup to be used
     * @param clazz          class to look in
     * @param name           name of field
     * @param type           type of field
     *
     * @return getter method handle for static field
     */
    public MethodHandle staticGetter(MethodHandles.Lookup explicitLookup, Class<?> clazz, String name, Class<?> type);

    /**
     * Wrapper for {@link java.lang.invoke.MethodHandles.Lookup#findSetter(Class, String, Class)}
      *
     * @param explicitLookup explicit lookup to be used
     * @param clazz          class to look in
     * @param name           name of field
     * @param type           type of field
     *
     * @return setter method handle for virtual field
     */
    public MethodHandle setter(MethodHandles.Lookup explicitLookup, Class<?> clazz, String name, Class<?> type);

    /**
     * Wrapper for {@link java.lang.invoke.MethodHandles.Lookup#findStaticSetter(Class, String, Class)}
      *
     * @param explicitLookup explicit lookup to be used
     * @param clazz          class to look in
     * @param name           name of field
     * @param type           type of field
     *
     * @return setter method handle for static field
     */
    public MethodHandle staticSetter(MethodHandles.Lookup explicitLookup, Class<?> clazz, String name, Class<?> type);

    /**
     * Wrapper for {@link java.lang.invoke.MethodHandles.Lookup#unreflect(Method)}
     *
     * Unreflect a method as a method handle
     *
     * @param method method to unreflect
     * @return unreflected method as method handle
     */
    public MethodHandle find(Method method);

    /**
     * Wrapper for {@link java.lang.invoke.MethodHandles.Lookup#findStatic(Class, String, MethodType)}
     *
     * @param explicitLookup explicit lookup to be used
     * @param clazz          class to look in
     * @param name           name of method
     * @param type           method type
     *
     * @return method handle for static method
     */
    public MethodHandle findStatic(MethodHandles.Lookup explicitLookup, Class<?> clazz, String name, MethodType type);

    /**
     * Wrapper for {@link java.lang.invoke.MethodHandles.Lookup#findVirtual(Class, String, MethodType)}
     *
     * @param explicitLookup explicit lookup to be used
     * @param clazz          class to look in
     * @param name           name of method
     * @param type           method type
     *
     * @return method handle for virtual method
     */
    public MethodHandle findVirtual(MethodHandles.Lookup explicitLookup, Class<?> clazz, String name, MethodType type);

    /**
     * Wrapper for {@link java.lang.invoke.MethodHandles.Lookup#findSpecial(Class, String, MethodType, Class)}
     *
     * @param explicitLookup explicit lookup to be used
     * @param clazz          class to look in
     * @param name           name of method
     * @param type           method type
     * @param thisClass      thisClass
     *
     * @return method handle for virtual method
     */
    public MethodHandle findSpecial(MethodHandles.Lookup explicitLookup, Class<?> clazz, String name, MethodType type, final Class<?> thisClass);

    /**
     * Wrapper for SwitchPoint creation. Just like {@code new SwitchPoint()} but potentially
     * tracked
     *
     * @return new switch point
     */
    public SwitchPoint createSwitchPoint();

    /**
     * Wrapper for {@link SwitchPoint#guardWithTest(MethodHandle, MethodHandle)}
     *
     * @param sp     switch point
     * @param before method handle when switchpoint is valid
     * @param after  method handle when switchpoint is invalidated
     *
     * @return guarded method handle
     */
    public MethodHandle guardWithTest(SwitchPoint sp, MethodHandle before, MethodHandle after);

    /**
     * Wrapper for {@link MethodType#methodType(Class, Class...)}
     *
     * @param returnType  return type for method type
     * @param paramTypes  parameter types for method type
     *
     * @return the method type
     */
    public MethodType type(Class<?> returnType, Class<?>... paramTypes);

}

