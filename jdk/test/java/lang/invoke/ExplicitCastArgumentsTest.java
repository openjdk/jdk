/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.invoke;

import sun.invoke.util.Wrapper;

/* @test
 * @summary unit tests for MethodHandles.explicitCastArguments()
 *
 * @run main/bootclasspath java.lang.invoke.ExplicitCastArgumentsTest
 */
public class ExplicitCastArgumentsTest {
    private static final boolean VERBOSE = Boolean.getBoolean("verbose");
    private static final Class<?> THIS_CLASS = ExplicitCastArgumentsTest.class;

    public static void main(String[] args) throws Throwable {
        testVarargsCollector();
        testRef2Prim();
        System.out.println("TEST PASSED");
    }

    public static String[] f(String... args) { return args; }

    public static void testVarargsCollector() throws Throwable {
        MethodType mt = MethodType.methodType(String[].class, String[].class);
        MethodHandle mh = MethodHandles.publicLookup().findStatic(THIS_CLASS, "f", mt);
        mh = MethodHandles.explicitCastArguments(mh, MethodType.methodType(Object.class, Object.class));
        mh.invokeWithArguments((Object)(new String[] {"str1", "str2"}));
    }

    public static void testRef2Prim() throws Throwable {
        for (Wrapper from : Wrapper.values()) {
            for (Wrapper to : Wrapper.values()) {
                if (from == Wrapper.VOID || to == Wrapper.VOID) continue;
                testRef2Prim(from, to);
            }
        }
    }

    public static void testRef2Prim(Wrapper from, Wrapper to) throws Throwable {
        // MHs.eCA javadoc:
        //    If T0 is a reference and T1 a primitive, and if the reference is null at runtime, a zero value is introduced.
        test(from.wrapperType(), to.primitiveType(), null, false);
    }

    public static void test(Class<?> from, Class<?> to, Object param, boolean failureExpected) throws Throwable {
        if (VERBOSE) System.out.printf("%-10s => %-10s: %5s: ", from.getSimpleName(), to.getSimpleName(), param);

        MethodHandle original = MethodHandles.identity(from);
        MethodType newType = original.type().changeReturnType(to);

        try {
            MethodHandle target = MethodHandles.explicitCastArguments(original, newType);
            Object result = target.invokeWithArguments(param);

            if (VERBOSE) {
                String resultStr;
                if (result != null) {
                    resultStr = String.format("%10s (%10s)", "'"+result+"'", result.getClass().getSimpleName());
                } else {
                    resultStr = String.format("%10s", result);
                }
                System.out.println(resultStr);
            }

            if (failureExpected) {
                String msg = String.format("No exception thrown: %s => %s; parameter: %s", from, to, param);
                throw new AssertionError(msg);
            }
        } catch (AssertionError e) {
            throw e; // report test failure
        } catch (Throwable e) {
            if (VERBOSE) System.out.printf("%s: %s\n", e.getClass(), e.getMessage());
            if (!failureExpected) {
                String msg = String.format("Unexpected exception was thrown: %s => %s; parameter: %s", from, to, param);
                throw new AssertionError(msg, e);
            }
        }
    }
}
