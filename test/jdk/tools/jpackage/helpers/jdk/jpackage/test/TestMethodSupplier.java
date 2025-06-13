/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.Annotations.ParameterGroup;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.ParameterSupplierGroup;
import jdk.jpackage.test.Annotations.Parameters;
import jdk.jpackage.test.Annotations.Test;
import static jdk.jpackage.internal.util.function.ThrowingFunction.toFunction;
import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;
import static jdk.jpackage.test.MethodCall.mapArgs;

final class TestMethodSupplier {

    TestMethodSupplier(OperatingSystem os) {
        Objects.requireNonNull(os);
        this.os = os;
    }

    record MethodQuery(String className, String methodName) {

        List<Method> lookup(ClassLoader classLoader) throws ClassNotFoundException {
            final Class<?> methodClass = Class.forName(className, true, classLoader);

            // Get the list of all public methods as need to deal with overloads.
            return Stream.of(methodClass.getMethods()).filter(method -> {
                return method.getName().equals(methodName);
            }).toList();
        }

        static MethodQuery fromQualifiedMethodName(String qualifiedMethodName) {
            int lastDotIdx = qualifiedMethodName.lastIndexOf('.');
            if (lastDotIdx == -1) {
                throw new IllegalArgumentException("Class name not specified");
            }

            var className = qualifiedMethodName.substring(0, lastDotIdx);
            var methodName = qualifiedMethodName.substring(lastDotIdx + 1);

            return new MethodQuery(className, methodName);
        }
    }

    List<Method> findNullaryLikeMethods(MethodQuery query, ClassLoader classLoader) throws NoSuchMethodException {
        List<Method> methods;

        try {
            methods = query.lookup(classLoader);
        } catch (ClassNotFoundException ex) {
            throw new NoSuchMethodException(
                    String.format("Class [%s] not found", query.className()));
        }

        if (methods.isEmpty()) {
            throw new NoSuchMethodException(String.format(
                    "Public method [%s] not found in [%s] class",
                    query.methodName(), query.className()));
        }

        methods = methods.stream().filter(method -> {
            if (isParameterized(method) && isTest(method)) {
                // Always accept test method with annotations producing arguments for its invocation.
                return true;
            } else {
                return method.getParameterCount() == 0;
            }
        }).filter(this::isEnabled).toList();

        if (methods.isEmpty()) {
            throw new NoSuchMethodException(String.format(
                    "Suitable public method [%s] not found in [%s] class",
                    query.methodName(), query.className()));
        }

        return methods;
    }

    boolean isTestClass(Class<?> type) {
        var typeStatus = processedTypes.get(type);
        if (typeStatus == null) {
            typeStatus = Verifier.isTestClass(type) ? TypeStatus.TEST_CLASS : TypeStatus.NOT_TEST_CLASS;
            processedTypes.put(type, typeStatus);
        }

        return !TypeStatus.NOT_TEST_CLASS.equals(typeStatus);
    }

    void verifyTestClass(Class<?> type) throws InvalidAnnotationException {
        var typeStatus = processedTypes.get(type);
        if (typeStatus == null) {
            // The "type" has not been verified yet.
            try {
                Verifier.verifyTestClass(type);
                processedTypes.put(type, TypeStatus.VALID_TEST_CLASS);
                return;
            } catch (InvalidAnnotationException ex) {
                processedTypes.put(type, TypeStatus.TEST_CLASS);
                throw ex;
            }
        }

        switch (typeStatus) {
            case NOT_TEST_CLASS -> Verifier.throwNotTestClassException(type);
            case TEST_CLASS -> Verifier.verifyTestClass(type);
            case VALID_TEST_CLASS -> {}
        }
    }

    boolean isEnabled(Method method) {
        return Stream.of(Test.class, Parameters.class)
                .filter(method::isAnnotationPresent)
                .findFirst()
                .map(method::getAnnotation)
                .map(this::canRunOnTheOperatingSystem)
                .orElse(true);
    }

    Stream<MethodCall> mapToMethodCalls(Method method) throws
            IllegalAccessException, InvocationTargetException {
        return toCtorArgs(method).map(v -> toMethodCalls(v, method)).flatMap(x -> x);
    }

    private Stream<Object[]> toCtorArgs(Method method) throws
            IllegalAccessException, InvocationTargetException {

        if ((method.getModifiers() & Modifier.STATIC) != 0) {
            // Static method, no instance
            return Stream.ofNullable(DEFAULT_CTOR_ARGS);
        }

        final var type = method.getDeclaringClass();

        final var paremeterSuppliers = filterParameterSuppliers(type)
                .filter(m -> m.isAnnotationPresent(Parameters.class))
                .filter(this::isEnabled)
                .sorted(Comparator.comparing(Method::getName)).toList();
        if (paremeterSuppliers.isEmpty()) {
            // Single instance using the default constructor.
            return Stream.ofNullable(DEFAULT_CTOR_ARGS);
        }

        // Construct collection of arguments for test class instances.
        return createArgs(paremeterSuppliers.toArray(Method[]::new));
    }

    private Stream<MethodCall> toMethodCalls(Object[] ctorArgs, Method method) {
        if (!isParameterized(method)) {
            return Stream.of(new MethodCall(ctorArgs, method));
        }

        var fromParameter = Stream.of(getMethodParameters(method)).map(a -> {
            return createArgsForAnnotation(method, a);
        }).flatMap(List::stream);

        var fromParameterSupplier = Stream.of(getMethodParameterSuppliers(method)).map(a -> {
            return toSupplier(() -> createArgsForAnnotation(method, a)).get();
        }).flatMap(List::stream);

        return Stream.concat(fromParameter, fromParameterSupplier).map(args -> {
            return new MethodCall(ctorArgs, method, args);
        });
    }

    private List<Object[]> createArgsForAnnotation(Executable exec, Parameter a) {
        if (!canRunOnTheOperatingSystem(a)) {
            return List.of();
        }

        final var annotationArgs = a.value();
        final var execParameterTypes = exec.getParameterTypes();

        if (execParameterTypes.length > annotationArgs.length) {
            if (execParameterTypes.length - annotationArgs.length == 1 && exec.isVarArgs()) {
            } else {
                throw new RuntimeException(String.format(
                        "Not enough annotation values %s for [%s]",
                        List.of(annotationArgs), exec));
            }
        }

        final Class<?>[] argTypes;
        if (exec.isVarArgs()) {
            List<Class<?>> argTypesBuilder = new ArrayList<>();
            var lastExecParameterTypeIdx = execParameterTypes.length - 1;
            argTypesBuilder.addAll(List.of(execParameterTypes).subList(0,
                    lastExecParameterTypeIdx));
            argTypesBuilder.addAll(Collections.nCopies(
                    Integer.max(0, annotationArgs.length - lastExecParameterTypeIdx),
                    execParameterTypes[lastExecParameterTypeIdx].componentType()));
            argTypes = argTypesBuilder.toArray(Class[]::new);
        } else {
            argTypes = execParameterTypes;
        }

        if (argTypes.length < annotationArgs.length) {
            throw new RuntimeException(String.format(
                    "Too many annotation values %s for [%s]",
                    List.of(annotationArgs), exec));
        }

        var args = mapArgs(exec, IntStream.range(0, argTypes.length).mapToObj(idx -> {
            return fromString(annotationArgs[idx], argTypes[idx]);
        }).toArray(Object[]::new));

        return List.<Object[]>of(args);
    }

    private List<Object[]> createArgsForAnnotation(Executable exec,
            ParameterSupplier a) throws IllegalAccessException,
            InvocationTargetException {
        if (!canRunOnTheOperatingSystem(a)) {
            return List.of();
        }

        final Class<?> execClass = exec.getDeclaringClass();
        final String supplierFuncName;
        if (a.value().isEmpty()) {
            supplierFuncName = exec.getName();
        } else {
            supplierFuncName = a.value();
        }

        final MethodQuery methodQuery;
        if (!supplierFuncName.contains(".")) {
            // No class name specified
            methodQuery = new MethodQuery(execClass.getName(), supplierFuncName);
        } else {
            methodQuery = MethodQuery.fromQualifiedMethodName(supplierFuncName);
        }

        final Method supplierMethod;
        try {
            final var parameterSupplierCandidates = findNullaryLikeMethods(methodQuery, execClass.getClassLoader());
            final Function<String, Class<?>> classForName = toFunction(name -> {
                return Class.forName(name, true, execClass.getClassLoader());
            });
            final var supplierMethodClass = classForName.apply(methodQuery.className());
            if (parameterSupplierCandidates.isEmpty()) {
                throw new RuntimeException(String.format(
                        "No parameter suppliers in [%s] class",
                        supplierMethodClass.getName()));
            }

            var allParameterSuppliers = filterParameterSuppliers(supplierMethodClass).toList();

            supplierMethod = findNullaryLikeMethods(methodQuery, execClass.getClassLoader())
                    .stream()
                    .filter(allParameterSuppliers::contains)
                    .findFirst().orElseThrow(() -> {
                        var msg = String.format(
                                "No suitable parameter supplier found for %s(%s) annotation",
                                a, supplierFuncName);
                        trace(String.format(
                                "%s. Parameter suppliers of %s class:", msg,
                                execClass.getName()));
                        IntStream.range(0, allParameterSuppliers.size()).mapToObj(idx -> {
                            return String.format("  [%d/%d] %s()", idx + 1,
                                    allParameterSuppliers.size(),
                                    allParameterSuppliers.get(idx).getName());
                        }).forEachOrdered(TestMethodSupplier::trace);

                        return new RuntimeException(msg);
                    });
        } catch (NoSuchMethodException ex) {
            throw new RuntimeException(String.format(
                    "Method not found for %s(%s) annotation", a, supplierFuncName));
        }

        return createArgs(supplierMethod).map(args -> {
            return mapArgs(exec, args);
        }).toList();
    }

    private boolean canRunOnTheOperatingSystem(Annotation a) {
        switch (a) {
            case Test t -> {
                return canRunOnTheOperatingSystem(os, t.ifOS(), t.ifNotOS());
            }
            case Parameters t -> {
                return canRunOnTheOperatingSystem(os, t.ifOS(), t.ifNotOS());
            }
            case Parameter t -> {
                return canRunOnTheOperatingSystem(os, t.ifOS(), t.ifNotOS());
            }
            case ParameterSupplier t -> {
                return canRunOnTheOperatingSystem(os, t.ifOS(), t.ifNotOS());
            }
            default -> {
                return true;
            }
        }
    }

    private static boolean isParameterized(Method method) {
        return Stream.of(
                Parameter.class, ParameterGroup.class,
                ParameterSupplier.class, ParameterSupplierGroup.class
        ).anyMatch(method::isAnnotationPresent);
    }

    private static boolean isTest(Method method) {
        return method.isAnnotationPresent(Test.class);
    }

    private static boolean canRunOnTheOperatingSystem(OperatingSystem value,
            OperatingSystem[] include, OperatingSystem[] exclude) {
        Set<OperatingSystem> suppordOperatingSystems = new HashSet<>();
        suppordOperatingSystems.addAll(List.of(include));
        suppordOperatingSystems.removeAll(List.of(exclude));
        return suppordOperatingSystems.contains(value);
    }

    private static Parameter[] getMethodParameters(Method method) {
        if (method.isAnnotationPresent(ParameterGroup.class)) {
            return method.getAnnotation(ParameterGroup.class).value();
        }

        if (method.isAnnotationPresent(Parameter.class)) {
            return new Parameter[]{method.getAnnotation(Parameter.class)};
        }

        return new Parameter[0];
    }

    private static ParameterSupplier[] getMethodParameterSuppliers(Method method) {
        if (method.isAnnotationPresent(ParameterSupplierGroup.class)) {
            return (method.getAnnotation(ParameterSupplierGroup.class)).value();
        }

        if (method.isAnnotationPresent(ParameterSupplier.class)) {
            return new ParameterSupplier[]{method.getAnnotation(ParameterSupplier.class)};
        }

        return new ParameterSupplier[0];
    }

    private static Stream<Method> filterParameterSuppliers(Class<?> type) {
        return Stream.of(type.getMethods())
                .filter(m -> m.getParameterCount() == 0)
                .filter(m -> (m.getModifiers() & Modifier.STATIC) != 0)
                .sorted(Comparator.comparing(Method::getName));
    }

    @SuppressWarnings("unchecked")
    private static Stream<Object[]> createArgs(Method ... parameterSuppliers) throws
            IllegalAccessException, InvocationTargetException {
        List<Object[]> args = new ArrayList<>();
        for (var parameterSupplier : parameterSuppliers) {
            args.addAll((Collection<Object[]>) parameterSupplier.invoke(null));
        }
        return args.stream();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object fromString(String value, Class<?> toType) {
        if (toType.isEnum()) {
            return Enum.valueOf((Class<? extends Enum>)toType, value);
        }
        Function<String, Object> converter = FROM_STRING.get(toType);
        if (converter == null) {
            throw new RuntimeException(String.format(
                    "Failed to find a conversion of [%s] string to %s type",
                    value, toType.getName()));
        }
        return converter.apply(value);
    }

    private static void trace(String msg) {
        if (TKit.VERBOSE_TEST_SETUP) {
            TKit.log(msg);
        }
    }

    static class InvalidAnnotationException extends Exception {
        InvalidAnnotationException(String msg) {
            super(msg);
        }
        private static final long serialVersionUID = 1L;
    }

    private static class Verifier {
        static boolean isTestClass(Class<?> type) {
            for (var method : type.getDeclaredMethods()) {
                if (isParameterized(method) || isTest(method)) {
                    return true;
                }
            }
            return false;
        }

        static void verifyTestClass(Class<?> type) throws InvalidAnnotationException {
            boolean withTestAnnotations = false;
            for (var method : type.getDeclaredMethods()) {
                if (!withTestAnnotations && (isParameterized(method) || isTest(method))) {
                    withTestAnnotations = true;
                }
                verifyAnnotationsCorrect(method);
            }

            if (!withTestAnnotations) {
                throwNotTestClassException(type);
            }
        }

        static void throwNotTestClassException(Class<?> type) throws InvalidAnnotationException {
            throw new InvalidAnnotationException(String.format(
                    "Type [%s] is not a test class", type.getName()));
        }

        private static void verifyAnnotationsCorrect(Method method) throws
                InvalidAnnotationException {
            var parameterized = isParameterized(method);
            if (parameterized && !isTest(method)) {
                throw new InvalidAnnotationException(String.format(
                        "Missing %s annotation on [%s] method", Test.class.getName(), method));
            }

            var isPublic = Modifier.isPublic(method.getModifiers());

            if (isTest(method) && !isPublic) {
                throw new InvalidAnnotationException(String.format(
                        "Non-public method [%s] with %s annotation",
                        method, Test.class.getName()));
            }

            if (method.isAnnotationPresent(Parameters.class) && !isPublic) {
                throw new InvalidAnnotationException(String.format(
                        "Non-public method [%s] with %s annotation",
                        method, Test.class.getName()));
            }
        }
    }

    private enum TypeStatus {
        NOT_TEST_CLASS,
        TEST_CLASS,
        VALID_TEST_CLASS,
    }

    private final OperatingSystem os;
    private final Map<Class<?>, TypeStatus> processedTypes = new HashMap<>();

    private static final Object[] DEFAULT_CTOR_ARGS = new Object[0];

    private static final Map<Class<?>, Function<String, Object>> FROM_STRING;

    static {
        Map<Class<?>, Function<String, Object>> primitives = Map.of(
            boolean.class, Boolean::valueOf,
            byte.class, Byte::valueOf,
            short.class, Short::valueOf,
            int.class, Integer::valueOf,
            long.class, Long::valueOf,
            float.class, Float::valueOf,
            double.class, Double::valueOf);

        Map<Class<?>, Function<String, Object>> boxed = Map.of(
            Boolean.class, Boolean::valueOf,
            Byte.class, Byte::valueOf,
            Short.class, Short::valueOf,
            Integer.class, Integer::valueOf,
            Long.class, Long::valueOf,
            Float.class, Float::valueOf,
            Double.class, Double::valueOf);

        Map<Class<?>, Function<String, Object>> other = Map.of(
            String.class, String::valueOf,
            Path.class, Path::of);

        Map<Class<?>, Function<String, Object>> combined = new HashMap<>(primitives);
        combined.putAll(other);
        combined.putAll(boxed);

        FROM_STRING = Collections.unmodifiableMap(combined);
    }
}
