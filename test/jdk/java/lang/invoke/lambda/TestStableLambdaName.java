/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @summary Test if the names of the lambda classes are stable when {@code -Djdk.internal.lambda.stableLambdaName}
 *          flag is set to true. This test directly calls java.lang.invoke.LambdaMetafactory#altMetafactory
 *          method to create multilple lambda instances and then checks their names stability. We created a
 *          multidimensional space of possible values for each parameter that
 *          {@link java.lang.invoke.LambdaMetafactory#altMetafactory} takes and then search that space by combining
 *          different values of those parameters. There is a rule we have to follow:
 *          Alternative methods of the specific method must have the same signature with difference in parameter types
 *          as long as the parameter of the alternative method is the superclass type of the type of corresponding parameter in
 *          original method
 * @run main/othervm -Djdk.internal.lambda.generateStableLambdaNames=true TestStableLambdaName
 */

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.rmi.Remote;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class TestStableLambdaName {
    private static final MethodHandles.Lookup lookup = MethodHandles.lookup();

    /* Different types of lambda classes based on value of flags parameter in the
     * java.lang.invoke.LambdaMetafactory#altMetafactory.
     * java.lang.invoke.LambdaMetafactory#altMetafactory uses bitwise and with this
     * parameter and predefined values to determine if lambda is serializable, has
     * altMethods and altInterfaces etc.
     */
    private enum lambdaType {
        NOT_SERIALIZABLE_NO_ALT_METHODS_NO_ALT_INTERFACES (0),
        SERIALIZABLE_ONLY (1),
        NOT_SERIALIZABLE_HAS_ALT_INTERFACES(2),
        SERIALIZABLE_HAS_ALT_INTERFACES(3),
        NOT_SERIALIZABLE_HAS_ALT_METHODS(4),
        SERIALIZABLE_HAS_ALT_METHODS(5),
        NOT_SERIALIZABLE_HAS_ALT_METHODS_HAS_ALT_INTERFACES(6),
        SERIALIZABLE_HAS_ALT_METHODS_HAS_ALT_INTERFACES(7);

        private final int index;
        lambdaType(int i) {
            index = i;
        }
    }

    private static final String[] interfaceMethods = new String[]{"accept", "consume", "apply", "supply", "get", "test", "getAsBoolean"};
    private static final Class<?>[] interfaces = new Class<?>[]{Consumer.class, Function.class, Predicate.class, Supplier.class, BooleanSupplier.class};
    // List of method types for defined methods
    private static final MethodType[] methodTypes = new MethodType[]{MethodType.methodType(String.class, Integer.class), MethodType.methodType(Throwable.class, AssertionError.class)};
    private static final Class<?>[] altInterfaces = new Class<?>[]{Cloneable.class, Remote.class};
    // Alternative methods that corresponds to method1
    private static final MethodType[] altMethodsMethod1 = new MethodType[]{MethodType.methodType(String.class, Number.class)};
    // Alternative methods that corresponds to method2
    private static final MethodType[] altMethodsMethod2 = new MethodType[]{MethodType.methodType(Throwable.class, Error.class), MethodType.methodType(Throwable.class, Throwable.class)};

    private static String method1(Number number) {
        return String.valueOf(number);
    }

    private static String method1(Integer number) { return String.valueOf(number); }

    private static Throwable method2(AssertionError error) {
        return error;
    }

    private static Throwable method2(Error error) {
        return error;
    }

    private static Throwable method2(Throwable throwable) {
        return throwable;
    }

    private static String removeHashFromLambdaName(String name) {
        return name.substring(0, name.indexOf("/0x"));
    }

    private static void createPlainLambdas(Set<String> lambdaNames, int flags, MethodHandle[] methodHandles) throws Throwable {
        for (String interfaceMethod : interfaceMethods) {
            for (Class<?> interfaceClass : interfaces) {
                for (int i = 0; i < methodTypes.length; i++) {
                    Object lambda = LambdaMetafactory.altMetafactory(lookup, interfaceMethod, MethodType.methodType(interfaceClass),
                            methodTypes[i], methodHandles[i], methodTypes[i], flags).getTarget().invoke();
                    lambdaNames.add(removeHashFromLambdaName(lambda.getClass().getName()));
                }
            }
        }
    }

    private static Object lambdaWithOneAltInterface(String interfaceMethod, Class<?> interfaceClass, MethodType methodType, MethodHandle methodHandle, int flags, Class<?> altInterface) throws Throwable {
        int numOfAltInterfaces = 1;
        return LambdaMetafactory.altMetafactory(lookup, interfaceMethod, MethodType.methodType(interfaceClass),
                methodType, methodHandle, methodType, flags, numOfAltInterfaces, altInterface).getTarget().invoke();
    }

    private static Object lambdaWithMultipleAltInterfaces(String interfaceMethod, Class<?> interfaceClass,  MethodType methodType, MethodHandle methodHandle, int flags) throws Throwable {
        int numOfAltInterfaces = 2;
        int altInterfacesIndex = 0;
        return LambdaMetafactory.altMetafactory(lookup, interfaceMethod, MethodType.methodType(interfaceClass),
                methodType, methodHandle, methodType, flags, numOfAltInterfaces, altInterfaces[altInterfacesIndex++], altInterfaces[altInterfacesIndex]).getTarget().invoke();
    }

    private static void createLambdasWithAltInterfaces(Set<String> lambdaNames, int flags, MethodHandle[] methodHandles) throws Throwable {
        Object lambda;
        for (String interfaceMethod : interfaceMethods) {
            for (Class<?> interfaceClass : interfaces) {
                for (int i = 0; i < methodTypes.length; i++) {
                    for (Class<?> altInterface : altInterfaces) {
                        lambda = lambdaWithOneAltInterface(interfaceMethod, interfaceClass, methodTypes[i], methodHandles[i], flags, altInterface);
                        lambdaNames.add(removeHashFromLambdaName(lambda.getClass().getName()));
                    }

                    lambda = lambdaWithMultipleAltInterfaces(interfaceMethod, interfaceClass, methodTypes[i], methodHandles[i], flags);
                    lambdaNames.add(removeHashFromLambdaName(lambda.getClass().getName()));
                }
            }
        }
    }

    private static Object lambdaWithOneAltMethod(String interfaceMethod, Class<?> interfaceClass,  MethodType methodType, MethodHandle methodHandle,
                                                 int flags, MethodType altMethod, MethodHandle[] methodHandles) throws Throwable {
        int numOfAltMethods = 1;
        return LambdaMetafactory.altMetafactory(lookup, interfaceMethod, MethodType.methodType(interfaceClass),
                methodType, methodHandle, methodType, flags, numOfAltMethods, altMethod).getTarget().invoke();
    }

    private static Object lambdaWithMultipleAltMethods(String interfaceMethod, Class<?> interfaceClass, int flags, MethodHandle[] methodHandles) throws Throwable {
        int numOfAltMethods = 2;
        int indexOfAltMethod = 0;
        MethodType methodTypeMethod2 = methodTypes[1];
        MethodHandle methodHandleMethod2 = methodHandles[1];
        return LambdaMetafactory.altMetafactory(lookup, interfaceMethod, MethodType.methodType(interfaceClass),
                methodTypeMethod2, methodHandleMethod2, methodTypeMethod2, flags, numOfAltMethods, altMethodsMethod2[indexOfAltMethod++],
                altMethodsMethod2[indexOfAltMethod]).getTarget().invoke();
    }

    private static void createLambdasWithAltMethods(Set<String> lambdaNames, int flags, MethodHandle[] methodHandles) throws Throwable {
        int indexOfMethodWithOneAltMethod = 0;
        int indexOfMethodWithTwoAltMethods = 1;
        int altMethodIndex = 0;
        Object lambda;
        for (String interfaceMethod : interfaceMethods) {
            for (Class<?> interfaceClass : interfaces) {
                lambda = lambdaWithOneAltMethod(interfaceMethod, interfaceClass, methodTypes[indexOfMethodWithOneAltMethod], methodHandles[indexOfMethodWithOneAltMethod],
                        flags, altMethodsMethod1[altMethodIndex], methodHandles);
                lambdaNames.add(removeHashFromLambdaName(lambda.getClass().getName()));

                for (MethodType altMethod : altMethodsMethod2) {
                    lambda = lambdaWithOneAltMethod(interfaceMethod, interfaceClass, methodTypes[indexOfMethodWithTwoAltMethods], methodHandles[indexOfMethodWithTwoAltMethods],
                            flags, altMethod, methodHandles);
                    lambdaNames.add(removeHashFromLambdaName(lambda.getClass().getName()));
                }

                lambda = lambdaWithMultipleAltMethods(interfaceMethod, interfaceClass, flags, methodHandles);
            }
        }
    }

    private static Object lambdaWithOneAltInterfaceAndOneAltMethod(String interfaceMethod, Class<?> interfaceClass, MethodType methodType, MethodHandle methodHandle, int flags,
                                                                   Class<?> altInterface, MethodType altMethod) throws Throwable {
        int numOfAltInterfaces = 1;
        int numOfAltMethods = 1;
        return LambdaMetafactory.altMetafactory(lookup, interfaceMethod, MethodType.methodType(interfaceClass),
                methodType, methodHandle, methodType, flags, numOfAltInterfaces, altInterface, numOfAltMethods, altMethod).getTarget().invoke();
    }

    private static Object lambdaWithOneAltInterfaceAndMultipleAltMethods(String interfaceMethod, Class<?> interfaceClass, int flags, Class<?> altInterface,
                                                                         MethodHandle[] methodHandles) throws Throwable {
        int numOfAltInterfaces = 1;
        int numOfAltMethods = 2;
        int indexOfAltMethod = 0;
        MethodType methodTypeMethod2 = methodTypes[1];
        MethodHandle methodHandleMethod2 = methodHandles[1];

        return LambdaMetafactory.altMetafactory(lookup, interfaceMethod, MethodType.methodType(interfaceClass),
                methodTypeMethod2, methodHandleMethod2, methodTypeMethod2, flags, numOfAltInterfaces, altInterface, numOfAltMethods, altMethodsMethod2[indexOfAltMethod++],
                altMethodsMethod2[indexOfAltMethod]).getTarget().invoke();
    }

    private static Object lambdaWithMultipleAltInterfaceAndMultipleAltMethods(String interfaceMethod, Class<?> interfaceClass, int flags, MethodHandle[] methodHandles) throws Throwable {
        int numOfAltInterfaces = 2;
        int numOfAltMethods = 2;
        int indexOfAltInterface = 0;
        int indexOfAltMethod = 0;
        MethodType methodTypeMethod2 = methodTypes[1];
        MethodHandle methodHandleMethod2 = methodHandles[1];

        return LambdaMetafactory.altMetafactory(lookup, interfaceMethod, MethodType.methodType(interfaceClass), methodTypeMethod2, methodHandleMethod2, methodTypeMethod2,
                flags, numOfAltInterfaces, altInterfaces[indexOfAltInterface++], altInterfaces[indexOfAltInterface], numOfAltMethods, altMethodsMethod2[indexOfAltMethod++],
                altMethodsMethod2[indexOfAltMethod]).getTarget().invoke();
    }
    private static void createLambdasWithAltInterfacesAndAltMethods(Set<String> lambdaNames, int flags, MethodHandle[] methodHandles) throws Throwable {
        int indexOfMethodWithOneAltMethod = 0;
        int indexOfMethodWithTwoAltMethods = 1;
        int altMethodIndex = 0;
        Object lambda;

        for (String interfaceMethod : interfaceMethods) {
            for (Class<?> interfaceClass : interfaces) {
                for (Class<?> altInterface : altInterfaces) {
                    lambda = lambdaWithOneAltInterfaceAndOneAltMethod(interfaceMethod, interfaceClass, methodTypes[indexOfMethodWithOneAltMethod], methodHandles[indexOfMethodWithOneAltMethod], flags,
                            altInterface, altMethodsMethod1[altMethodIndex]);
                    lambdaNames.add(removeHashFromLambdaName(lambda.getClass().getName()));
                    lambda = lambdaWithOneAltInterfaceAndMultipleAltMethods(interfaceMethod, interfaceClass, flags, altInterface, methodHandles);
                    lambdaNames.add(removeHashFromLambdaName(lambda.getClass().getName()));

                    for (MethodType altMethod : altMethodsMethod2) {
                        lambda = lambdaWithOneAltInterfaceAndOneAltMethod(interfaceMethod, interfaceClass, methodTypes[indexOfMethodWithTwoAltMethods], methodHandles[indexOfMethodWithTwoAltMethods], flags,
                                altInterface, altMethod);
                        lambdaNames.add(removeHashFromLambdaName(lambda.getClass().getName()));
                    }
                }
                lambda = lambdaWithMultipleAltInterfaceAndMultipleAltMethods(interfaceMethod, interfaceClass, flags, methodHandles);
                lambdaNames.add(removeHashFromLambdaName(lambda.getClass().getName()));
            }
        }
    }

    private static void createMethodReferencesLambdas(Set<String> lambdaNames) {
        Runnable lambda = TestStableLambdaName::foo;
        lambdaNames.add(removeHashFromLambdaName(lambda.getClass().getName()));
    }

    private static void foo() {
        System.out.println("Hello world!");
    }

    private static void createLambdasWithDifferentParameters(Set<String> lambdaNames, MethodHandle[] methodHandles) throws Throwable {
        // All lambdas with flags 0
        createPlainLambdas(lambdaNames, lambdaType.NOT_SERIALIZABLE_NO_ALT_METHODS_NO_ALT_INTERFACES.index, methodHandles);

        // All lambdas with flags 1
        createPlainLambdas(lambdaNames, lambdaType.SERIALIZABLE_ONLY.index, methodHandles);

        // All lambdas with flags 2
        createLambdasWithAltInterfaces(lambdaNames, lambdaType.NOT_SERIALIZABLE_HAS_ALT_INTERFACES.index, methodHandles);

        // All lambdas with flags 3
        createLambdasWithAltInterfaces(lambdaNames, lambdaType.SERIALIZABLE_HAS_ALT_INTERFACES.index, methodHandles);

        // All lambdas with flags 4
        createLambdasWithAltMethods(lambdaNames, lambdaType.NOT_SERIALIZABLE_HAS_ALT_METHODS.index, methodHandles);

        // All lambdas with flags 5
        createLambdasWithAltMethods(lambdaNames, lambdaType.SERIALIZABLE_HAS_ALT_METHODS.index, methodHandles);

        // All lambdas with flags 6
        createLambdasWithAltInterfacesAndAltMethods(lambdaNames, lambdaType.NOT_SERIALIZABLE_HAS_ALT_METHODS_HAS_ALT_INTERFACES.index, methodHandles);

        // All lambdas with flags 7
        createLambdasWithAltInterfacesAndAltMethods(lambdaNames, lambdaType.SERIALIZABLE_HAS_ALT_METHODS_HAS_ALT_INTERFACES.index, methodHandles);

        // Method reference lambdas
        createMethodReferencesLambdas(lambdaNames);
    }

    public static void main(String[] args) throws Throwable {
        MethodType methodTypeForMethod1 = methodTypes[0];
        MethodType methodTypeForMethod2 = methodTypes[1];
        MethodHandle[] methodHandles = new MethodHandle[]{lookup.findStatic(TestStableLambdaName.class, "method1", methodTypeForMethod1),
                lookup.findStatic(TestStableLambdaName.class, "method2", methodTypeForMethod2)};

        Set<String> lambdaClassStableNames = new HashSet<>();
        createLambdasWithDifferentParameters(lambdaClassStableNames, methodHandles);

        Set<String> lambdaClassStableNamesTest = new HashSet<>();
        createLambdasWithDifferentParameters(lambdaClassStableNamesTest, methodHandles);

        if (lambdaClassStableNames.size() != lambdaClassStableNamesTest.size()) {
            throw new RuntimeException(lambdaClassStableNames.size() + " names was created during name creation run, but " + lambdaClassStableNamesTest.size() + " names were created during test run. " +
                    "Number of created names must be the same.");
        }

        if (!lambdaClassStableNamesTest.containsAll(lambdaClassStableNames)) {
            throw new RuntimeException("Different names for lambda classes were created during name creation run and test run. All the created names in both runs must be the same.");
        }
    }
}
