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

/*
 * @test
 * @bug 8888888
 * @run testng TestRecursive
 */

import org.testng.annotations.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodType.methodType;
import static org.testng.Assert.assertEquals;

public class TestRecursive {
    static class Snippet1 {
        // classic recursive implementation of the factorial function
        static int base(MethodHandle recur, int k) throws Throwable {
            if (k <= 1)  return 1;
            return k * (int) recur.invokeExact(k - 1);
        }
        static void doTest() throws Throwable {
            var MT_base = methodType(int.class, MethodHandle.class, int.class);
            var MH_base = lookup().findStatic(Snippet1.class, "base", MT_base);
            // assume MH_base is a handle to the above method
            MethodHandle recur = MethodHandles.recursive(MH_base);
            assertEquals(120, (int) recur.invoke(5));
        }
    }

    @Test
    public void testSingleRecursion() throws Throwable {
        Snippet1.doTest();
    }

    static class DoubleRecursion {
        static long entryPoint(MethodHandle entryPoint,
                               MethodHandle factorialOdd,
                               MethodHandle factorialEven,
                               long k) throws Throwable {
            if ((k & 1) == 0)
                return (long) factorialEven.invokeExact(k, "even0", 2.2f);
            else
                return (long) factorialOdd.invokeExact(k, "odd0");
        }
        static long factorialOdd(MethodHandle entryPoint,
                                 MethodHandle factorialOdd,
                                 MethodHandle factorialEven,
                                 long k,
                                 // change up the signature:
                                 String ignore) throws Throwable {
            assertEquals(k & 1, 1);
            if (k < 3)  return 1;
            return k * (long) factorialEven.invokeExact(k - 1, "even1", 3.3f);
        }
        static long factorialEven(MethodHandle entryPoint,
                                  MethodHandle factorialOdd,
                                  MethodHandle factorialEven,
                                  long k,
                                 // change up the signature again:
                                  String ignore, float ig2) throws Throwable {
            assertEquals(k & 1, 0);
            if (k < 2)  return 1;
            return k * (long) factorialOdd.invokeExact(k - 1, "odd1");
        }
        static void doTest() throws Throwable {
            var mt = methodType(long.class,
                                MethodHandle.class,
                                MethodHandle.class,
                                MethodHandle.class,
                                long.class);
            var MH_entryPoint = lookup().findStatic(DoubleRecursion.class,
                                                    "entryPoint", mt);
            mt = mt.appendParameterTypes(String.class);
            var MH_factorialOdd = lookup().findStatic(DoubleRecursion.class,
                                                      "factorialOdd", mt);
            mt = mt.appendParameterTypes(float.class);
            var MH_factorialEven = lookup().findStatic(DoubleRecursion.class,
                                                       "factorialEven", mt);
            MethodHandle recur = MethodHandles.recursive(MH_entryPoint,
                                                         MH_factorialOdd,
                                                         MH_factorialEven);
            long fact = 1;
            for (long k = 0; k < 20; k++) {
                assertEquals(fact, (long) recur.invoke(k));
                fact *= k+1;
            }
        }
    }

    @Test
    public void testDoubleRecursion() throws Throwable {
        DoubleRecursion.doTest();
    }
}
