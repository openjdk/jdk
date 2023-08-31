/*
 * Copyright (c) 2022, 2023, Arm Limited. All rights reserved.
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

package compiler.vectorization.runner;

import compiler.lib.ir_framework.*;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import jdk.test.lib.Utils;

import jdk.test.whitebox.WhiteBox;

public class VectorizationTestRunner {

    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    private static final int COMP_LEVEL_INTP = 0;
    private static final int COMP_LEVEL_C2 = 4;

    private static final int NMETHOD_COMP_LEVEL_IDX = 1;
    private static final int NMETHOD_INSTS_IDX = 2;

    protected void run() {
        Class klass = getClass();

        // 1) Vectorization correctness test
        // For each method annotated with "@Test" in test classes, this test runner
        // invokes it twice - first time in the interpreter and second time compiled
        // by C2. Then this runner compares the two return values. Hence we require
        // each test method returning a primitive value or an array of primitive type.
        for (Method method : klass.getDeclaredMethods()) {
            try {
                if (method.isAnnotationPresent(Test.class)) {
                    verifyTestMethod(method);
                    runTestOnMethod(method);
                }
            } catch (Exception e) {
                throw new RuntimeException("Test failed in " + klass.getName() +
                        "." + method.getName() + ": " + e.getMessage());
            }
        }

        // 2) Vectorization ability test
        // To test vectorizability, invoke the IR test framework to check existence of
        // expected C2 IR node.
        TestFramework irTest = new TestFramework(klass);
        irTest.start();
    }

    private void verifyTestMethod(Method method) {
        // Check method parameter count
        if (method.getParameterCount() > 0) {
            fail("Test method should have no parameter.");
        }

        // Check method modifiers
        int modifiers = method.getModifiers();
        if (!Modifier.isPublic(modifiers) || Modifier.isStatic(modifiers)) {
            fail("Test method should be public and non-static.");
        }

        // Check method return type
        Class retType = method.getReturnType();
        if (retType.isPrimitive()) {
            if (retType == Void.TYPE) {
                fail("Test method should return non-void.");
            }
        } else if (retType.isArray()) {
            Class elemType = retType.getComponentType();
            if (!elemType.isPrimitive()) {
                fail("Only primitive array types are supported.");
            }
        } else {
            fail("Test method should not return Object type.");
        }
    }

    private void runTestOnMethod(Method method) throws InterruptedException {
        Object expected = null;
        Object actual = null;

        // Temporarily disable the compiler and invoke the method to get reference
        // result from the interpreter
        WB.setBooleanVMFlag("UseCompiler", false);
        try {
            expected = method.invoke(this);
        } catch (Exception e) {
            e.printStackTrace();
            fail("Exception is thrown in test method invocation (interpreter).");
        }
        assert(WB.getMethodCompilationLevel(method) == COMP_LEVEL_INTP);
        WB.setBooleanVMFlag("UseCompiler", true);

        // Compile the method and invoke it again
        long enqueueTime = System.currentTimeMillis();
        WB.enqueueMethodForCompilation(method, COMP_LEVEL_C2);
        while (WB.getMethodCompilationLevel(method) != COMP_LEVEL_C2) {
            Thread.sleep(100 /*ms*/);
        }
        try {
            actual = method.invoke(this);
        } catch (Exception e) {
            e.printStackTrace();
            fail("Exception is thrown in test method invocation (C2).");
        }
        assert(WB.getMethodCompilationLevel(method) == COMP_LEVEL_C2);

        // Check if two invocations return the same
        Class retType = method.getReturnType();
        if (retType.isArray()) {
            // Method invocations from Java reflection API always return a boxed object.
            // Hence, for methods return primitive array, we can only use reflection API
            // to check the consistency of the elements one by one.
            if (expected == null && actual == null) {
                return;
            }
            if (expected == null ^ actual == null) {
                fail("Inconsistent return value: null/non-null.");
            }
            int length = Array.getLength(expected);
            if (Array.getLength(actual) != length) {
                fail("Inconsistent array length: expected = " + length + ", actual = " +
                        Array.getLength(actual));
            }
            for (int idx = 0; idx < length; idx++) {
                Object e1 = Array.get(expected, idx);
                Object e2 = Array.get(actual, idx);
                if (!e1.equals(e2)) {
                    fail("Inconsistent value at array index [" + idx + "], expected = " +
                            e1 + ", actual = " + e2);
                }
            }
        } else {
            // Method invocations from Java reflection API always return a boxed object.
            // Hence, we should use equals() to check the consistency for methods which
            // return primitive type.
            if (!expected.equals(actual)) {
                fail("Inconsistent return value: expected = " + expected
                        + ", actual = " + actual);
            }
        }
    }

    private static VectorizationTestRunner createTestInstance(String testName) {
        if (!testName.toLowerCase().endsWith(".java")) {
            fail("Invalid test file name " + testName);
        }
        testName = testName.substring(0, testName.length() - 5);
        testName = testName.replace('/', '.');

        VectorizationTestRunner instance = null;
        try {
            Class klass = Class.forName(testName);
            Constructor ctor = klass.getConstructor();
            instance = (VectorizationTestRunner) ctor.newInstance();
        } catch (Exception e) {
            e.printStackTrace();
            fail("Cannot create test instance for class " + testName);
        }

        return instance;
    }

    private static void fail(String reason) {
        throw new RuntimeException(reason);
    }

    public static void main(String[] args) {
        VectorizationTestRunner testObj = createTestInstance(Utils.TEST_NAME);
        testObj.run();
    }
}
