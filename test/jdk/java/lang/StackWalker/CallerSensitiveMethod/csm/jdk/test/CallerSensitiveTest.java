/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.CSM.Result;
import java.util.function.Supplier;

/**
 * This test invokes StackWalker::getCallerClass via static reference,
 * reflection, MethodHandle, lambda.  Also verify that
 * StackWalker::getCallerClass can't be called from @CallerSensitive method.
 */
public class CallerSensitiveTest {
    private static final String NON_CSM_CALLER_METHOD = "getCallerClass";
    private static final String REFLECTIVE_GET_CALLER_METHOD = "getCallerClassReflectively";
    private static final String CSM_CALLER_METHOD = "caller";

    public static void main(String... args) throws Throwable {

        CallerSensitiveTest cstest = new CallerSensitiveTest();
        // test static call to java.util.CSM::caller and CSM::getCallerClass
        cstest.staticMethodCall();
        // test reflective call to StackWalker::getCallerClass
        cstest.invokeMethod();
        // test java.lang.reflect.Method call
        cstest.reflectMethodCall();
        // test java.lang.invoke.MethodHandle
        cstest.invokeMethodHandle(Lookup1.lookup);
        cstest.invokeMethodHandle(Lookup2.lookup);
        // test method ref
        cstest.lambda();

        LambdaTest.lambda();

        if (failed > 0) {
            throw new RuntimeException(failed + " test cases failed.");
        }
    }

    void staticMethodCall() {
        java.util.CSM.caller();

        Result result = java.util.CSM.getCallerClass();
        checkNonCSMCaller(CallerSensitiveTest.class, result);
    }

    void reflectMethodCall() throws Throwable {
        Method method1 = java.util.CSM.class.getMethod(CSM_CALLER_METHOD);
        method1.invoke(null);

        Method method2 = java.util.CSM.class.getMethod(NON_CSM_CALLER_METHOD);
        Result result2 = (Result) method2.invoke(null);
        checkNonCSMCaller(CallerSensitiveTest.class, result2);

        Method method3 = java.util.CSM.class.getMethod(REFLECTIVE_GET_CALLER_METHOD);
        Result result3 = (Result) method3.invoke(null);
        checkNonCSMCaller(CallerSensitiveTest.class, result3);
    }

    void invokeMethod() throws Throwable {
        Result result = java.util.CSM.getCallerClassReflectively();
        checkNonCSMCaller(CallerSensitiveTest.class, result);
    }

    void invokeMethodHandle(Lookup lookup) throws Throwable {
        MethodHandle mh1 = lookup.findStatic(java.util.CSM.class, CSM_CALLER_METHOD,
                                             MethodType.methodType(Class.class));
        Class<?> c = (Class<?>)mh1.invokeExact();

        MethodHandle mh2 = lookup.findStatic(java.util.CSM.class, NON_CSM_CALLER_METHOD,
                                             MethodType.methodType(Result.class));
        Result result2 = (Result)mh2.invokeExact();
        checkNonCSMCaller(CallerSensitiveTest.class, result2);

        MethodHandle mh3 = lookup.findStatic(java.util.CSM.class, REFLECTIVE_GET_CALLER_METHOD,
                                             MethodType.methodType(Result.class));
        Result result3 = (Result)mh3.invokeExact();
        checkNonCSMCaller(CallerSensitiveTest.class, result3);
    }

    void lambda() {
        Result result = LambdaTest.getCallerClass.get();
        checkNonCSMCaller(CallerSensitiveTest.class, result);

        LambdaTest.caller.get();
    }

    static int failed = 0;

    static void checkNonCSMCaller(Class<?> expected, Result result) {
        if (result.callers.size() != 1) {
            throw new RuntimeException("Expected result.callers contain one element");
        }
        if (expected != result.callers.get(0)) {
            System.err.format("ERROR: Expected %s but got %s%n", expected,
                result.callers);
            result.frames.stream()
                .forEach(f -> System.err.println("   " + f));
            failed++;
        }
    }

    static class Lookup1 {
        static Lookup lookup = MethodHandles.lookup();
    }

    static class Lookup2 {
        static Lookup lookup = MethodHandles.lookup();
    }

    static class LambdaTest {
        static Supplier<Class<?>> caller = java.util.CSM::caller;
        static Supplier<Result> getCallerClass = java.util.CSM::getCallerClass;

        static void caller() {
            caller.get();
        }
        static Result getCallerClass() {
            return getCallerClass.get();
        }

        static void lambda() {
            Result result = LambdaTest.getCallerClass();
            checkNonCSMCaller(LambdaTest.class, result);

            LambdaTest.caller();
        }
    }
}
