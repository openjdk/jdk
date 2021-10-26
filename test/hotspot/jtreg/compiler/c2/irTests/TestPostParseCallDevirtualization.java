/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package compiler.c2.irTests;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8273409
 * @summary Test that post-parse call devirtualization works as intended.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestPostParseCallDevirtualization
 */
public class TestPostParseCallDevirtualization {

    public static void main(String[] args) {
        TestFramework framework = new TestFramework();
        Scenario noOSR = new Scenario(0, "-XX:-UseOnStackReplacement");
        Scenario alwaysIncremental = new Scenario(1, "-XX:-UseOnStackReplacement", "-XX:+IgnoreUnrecognizedVMOptions", "-XX:+AlwaysIncrementalInline");
        framework.addScenarios(noOSR, alwaysIncremental).start();
    }

    static interface I {
        public int method();
    }

    static final class A implements I {
        @Override
        public int method() { return 0; };
    }

    static final class B implements I {
        @Override
        public int method() { return 42; };
    }

    static final class C implements I {
        @Override
        public int method() { return -1; };
    }

    static final A a = new A();
    static final B b = new B();
    static final C c = new C();

    static int callHelper(I recv) {
        // Receiver profile is polluted
        return recv.method();
    }

    @Test
    @IR(failOn = {IRNode.DYNAMIC_CALL_OF_METHOD, "method"},
        counts = {IRNode.STATIC_CALL_OF_METHOD, "method", "= 1"})
    public int testDynamicCallWithLoop(B val) {
        // Make sure val is non-null below
        if (val == null) {
          return 0;
        }
        // Loop that triggers loop opts
        I recv = a;
        for (int i = 0; i < 3; ++i) {
            if (i > 1) {
                recv = val;
            }
        }
        // We only know after loop opts that the receiver type is non-null B.
        // Post-parse call devirtualization should then convert the
        // virtual call in the helper method to a static call.
        return callHelper(recv);
    }

    @Run(test = "testDynamicCallWithLoop")
    public void checkTestDynamicCallWithLoop() {
        // Pollute receiver profile with three different
        // types to prevent (bimorphic) inlining.
        callHelper(a);
        callHelper(b);
        callHelper(c);
        Asserts.assertEquals(testDynamicCallWithLoop(b), 42);
    }

    @Test
    @IR(failOn = {IRNode.DYNAMIC_CALL_OF_METHOD, "method"},
        counts = {IRNode.STATIC_CALL_OF_METHOD, "method", "= 1"})
    public int testDynamicCallWithCCP(B val) {
        // Make sure val is non-null below
        if (val == null) {
          return 0;
        }
        // Loop that triggers CCP
        I recv = a;
        for (int i = 0; i < 100; i++) {
            if ((i % 2) == 0) {
                recv = val;
            }
        }
        // We only know after CCP that the receiver type is non-null B.
        // Post-parse call devirtualization should then convert the
        // virtual call in the helper method to a static call.
        return callHelper(recv);
    }

    @Run(test = "testDynamicCallWithCCP")
    public void checkTestDynamicCallWithCCP() {
        // Pollute receiver profile with three different
        // types to prevent (bimorphic) inlining.
        callHelper(a);
        callHelper(b);
        callHelper(c);
        Asserts.assertEquals(testDynamicCallWithCCP(b), 42);
    }

    static final MethodHandle mh1;
    static final MethodHandle mh2;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            mh1 = lookup.findStatic(TestPostParseCallDevirtualization.class, "method1", MethodType.methodType(int.class));
            mh2 = lookup.findStatic(TestPostParseCallDevirtualization.class, "method2", MethodType.methodType(int.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            e.printStackTrace();
            throw new RuntimeException("Method handle lookup failed");
        }
    }

    static int method1() { return 0; }
    static int method2() { return 42; }

    @Test
    @IR(failOn = {IRNode.STATIC_CALL_OF_METHOD, "invokeBasic"},
        counts = {IRNode.STATIC_CALL_OF_METHOD, "invokeStatic", "= 1"})
    public int testMethodHandleCallWithLoop() throws Throwable {
        MethodHandle mh = mh1;
        for (int i = 0; i < 3; ++i) {
            if (i > 1) {
                mh = mh2;
            }
        }
        // We only know after loop opts that the receiver is mh2.
        // Post-parse call devirtualization should then convert the
        // virtual call to a static call.
        return (int)mh.invokeExact();
    }

    @Run(test = "testMethodHandleCallWithLoop")
    public void checkTestMethodHandleCallWithLoop() throws Throwable {
        Asserts.assertEquals(testMethodHandleCallWithLoop(), 42);
    }

    @Test
    @IR(failOn = {IRNode.STATIC_CALL_OF_METHOD, "invokeBasic"},
        counts = {IRNode.STATIC_CALL_OF_METHOD, "invokeStatic", "= 1"})
    public int testMethodHandleCallWithCCP() throws Throwable {
        MethodHandle mh = mh1;
        int limit = 0;
        for (int i = 0; i < 100; i++) {
            if ((i % 2) == 0) {
                limit = 1;
            }
        }
        for (int i = 0; i < limit; ++i) {
            mh = mh2;
        }
        // We only know after CCP that the receiver is mh2.
        // Post-parse call devirtualization should then convert the
        // virtual call to a static call.
        return (int)mh.invokeExact();
    }

    @Run(test = "testMethodHandleCallWithCCP")
    public void checkTestMethodHandleCallWithCCP() throws Throwable {
        Asserts.assertEquals(testMethodHandleCallWithCCP(), 42);
    }
}
