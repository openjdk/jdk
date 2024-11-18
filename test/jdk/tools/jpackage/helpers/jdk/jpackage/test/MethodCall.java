/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package jdk.jpackage.test;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.function.ThrowingConsumer;
import jdk.jpackage.test.TestInstance.TestDesc;

class MethodCall implements ThrowingConsumer {

    MethodCall(Object[] instanceCtorArgs, Method method, Object ... args) {
        Objects.requireNonNull(instanceCtorArgs);
        Objects.requireNonNull(method);

        this.ctorArgs = instanceCtorArgs;
        this.method = method;
        this.methodArgs = args;
    }

    TestDesc createDescription() {
        var descBuilder = TestDesc.createBuilder().method(method);
        if (methodArgs.length != 0) {
            descBuilder.methodArgs(methodArgs);
        }

        if (ctorArgs.length != 0) {
            descBuilder.ctorArgs(ctorArgs);
        }

        return descBuilder.get();
    }

    Method getMethod() {
        return method;
    }

    Object newInstance() throws NoSuchMethodException, InstantiationException,
            IllegalAccessException, IllegalArgumentException,
            InvocationTargetException {
        if ((method.getModifiers() & Modifier.STATIC) != 0) {
            return null;
        }

        var ctor = findMatchingConstructor(method.getDeclaringClass(), ctorArgs);

        return ctor.newInstance(mapArgs(ctor, ctorArgs));
    }

    static Object[] mapArgs(Executable executable, final Object ... args) {
        return mapPrimitiveTypeArgs(executable, mapVarArgs(executable, args));
    }

    void checkRequiredConstructor() throws NoSuchMethodException {
        if ((method.getModifiers() & Modifier.STATIC) == 0) {
            findMatchingConstructor(method.getDeclaringClass(), ctorArgs);
        }
    }

    private static Constructor findMatchingConstructor(Class type, Object... ctorArgs)
            throws NoSuchMethodException {

        var ctors = filterMatchingExecutablesForParameterValues(Stream.of(
                type.getConstructors()), ctorArgs).toList();

        if (ctors.size() != 1) {
            // No public constructors that can handle the given arguments.
            throw new NoSuchMethodException(String.format(
                    "No public contructor in %s for %s arguments", type,
                    Arrays.deepToString(ctorArgs)));
        }

        return ctors.get(0);
    }

    @Override
    public void accept(Object thiz) throws Throwable {
        method.invoke(thiz, methodArgs);
    }

    private static Object[] mapVarArgs(Executable executable, final Object ... args) {
        if (executable.isVarArgs()) {
            var paramTypes = executable.getParameterTypes();
            Class varArgParamType = paramTypes[paramTypes.length - 1];

            Object[] newArgs;
            if (paramTypes.length - args.length == 1) {
                // Empty var args

                // "args" can be of type String[] if the "executable" is "foo(String ... str)"
                newArgs = Arrays.copyOf(args, args.length + 1, Object[].class);
                newArgs[newArgs.length - 1] = Array.newInstance(varArgParamType.componentType(), 0);
            } else {
                var varArgs = Arrays.copyOfRange(args, paramTypes.length - 1,
                        args.length, varArgParamType);

                // "args" can be of type String[] if the "executable" is "foo(String ... str)"
                newArgs = Arrays.copyOfRange(args, 0, paramTypes.length, Object[].class);
                newArgs[newArgs.length - 1] = varArgs;
            }
            return newArgs;
        }

        return args;
    }

    private static Object[] mapPrimitiveTypeArgs(Executable executable, final Object ... args) {
        var paramTypes = executable.getParameterTypes();
        if (paramTypes.length != args.length) {
            throw new IllegalArgumentException(
                    "The number of arguments must be equal to the number of parameters of the executable");
        }

        if (IntStream.range(0, args.length).allMatch(idx -> {
            return Optional.ofNullable(args[idx]).map(Object::getClass).map(paramTypes[idx]::isAssignableFrom).orElse(true);
        })) {
            return args;
        } else {
            final var newArgs = Arrays.copyOf(args, args.length, Object[].class);
            for (var idx = 0; idx != args.length; ++idx) {
                final var paramType = paramTypes[idx];
                final var argValue = args[idx];
                newArgs[idx] = Optional.ofNullable(argValue).map(Object::getClass).map(argType -> {
                    if(argType.isArray() && !paramType.isAssignableFrom(argType)) {
                        var length = Array.getLength(argValue);
                        var newArray = Array.newInstance(paramType.getComponentType(), length);
                        for (var arrayIdx = 0; arrayIdx != length; ++arrayIdx) {
                            Array.set(newArray, arrayIdx, Array.get(argValue, arrayIdx));
                        }
                        return newArray;
                    } else {
                        return argValue;
                    }
                }).orElse(argValue);
            }

            return newArgs;
        }
    }

    private static <T extends Executable> Stream<T> filterMatchingExecutablesForParameterValues(
            Stream<T> executables, Object... args) {
        return filterMatchingExecutablesForParameterTypes(
                executables,
                Stream.of(args)
                        .map(arg -> arg != null ? arg.getClass() : null)
                        .toArray(Class[]::new));
    }

    private static <T extends Executable> Stream<T> filterMatchingExecutablesForParameterTypes(
            Stream<T> executables, Class<?>... argTypes) {
        return executables.filter(executable -> {
            var parameterTypes = executable.getParameterTypes();

            final int checkArgTypeCount;
            if (parameterTypes.length <= argTypes.length) {
                checkArgTypeCount = parameterTypes.length;
            } else if (parameterTypes.length - argTypes.length == 1 && executable.isVarArgs()) {
                // Empty optional arguments.
                checkArgTypeCount = argTypes.length;
            } else {
                // Not enough mandatory arguments.
                return false;
            }

            var unmatched = IntStream.range(0, checkArgTypeCount).dropWhile(idx -> {
                return new ParameterTypeMatcher(parameterTypes[idx]).test(argTypes[idx]);
            }).toArray();

            if (argTypes.length == parameterTypes.length && unmatched.length == 0) {
                // Number of argument types equals to the number of parameters
                // of the executable and all types match.
                return true;
            }

            if (executable.isVarArgs()) {
                var varArgType = parameterTypes[parameterTypes.length - 1].componentType();
                return IntStream.of(unmatched).allMatch(idx -> {
                    return new ParameterTypeMatcher(varArgType).test(argTypes[idx]);
                });
            }

            return false;
        });
    }

    private static final class ParameterTypeMatcher implements Predicate<Class<?>> {
        ParameterTypeMatcher(Class<?> parameterType) {
            Objects.requireNonNull(parameterType);
            this.parameterType = NORM_TYPES.getOrDefault(parameterType, parameterType);
        }

        @Override
        public boolean test(Class<?> paramaterValueType) {
            if (paramaterValueType == null) {
                return true;
            }

            paramaterValueType = NORM_TYPES.getOrDefault(paramaterValueType, paramaterValueType);
            return parameterType.isAssignableFrom(paramaterValueType);
        }

        private final Class<?> parameterType;
    }

    private final Object[] methodArgs;
    private final Method method;
    private final Object[] ctorArgs;

    private static final Map<Class<?>, Class<?>> NORM_TYPES;

    static {
        Map<Class<?>, Class<?>> primitives = Map.of(
            boolean.class, Boolean.class,
            byte.class, Byte.class,
            short.class, Short.class,
            int.class, Integer.class,
            long.class, Long.class,
            float.class, Float.class,
            double.class, Double.class);

        Map<Class<?>, Class<?>> primitiveArrays = Map.of(
            boolean[].class, Boolean[].class,
            byte[].class, Byte[].class,
            short[].class, Short[].class,
            int[].class, Integer[].class,
            long[].class, Long[].class,
            float[].class, Float[].class,
            double[].class, Double[].class);

        Map<Class<?>, Class<?>> combined = new HashMap<>(primitives);
        combined.putAll(primitiveArrays);

        NORM_TYPES = Collections.unmodifiableMap(combined);
    }
}
