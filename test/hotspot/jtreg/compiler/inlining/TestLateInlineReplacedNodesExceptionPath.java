/*
 * Copyright (c) 2023, Red Hat, Inc. All rights reserved.
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
 * @bug 8319764
 * @summary C2 compilation asserts during incremental inlining because Phi input is out of bounds
 * @requires vm.compiler2.enabled
 * @run main/othervm -XX:-TieredCompilation -XX:-BackgroundCompilation -XX:CompileCommand=dontinline,TestLateInlineReplacedNodesExceptionPath::notInlined
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN -XX:StressSeed=1246687813 TestLateInlineReplacedNodesExceptionPath
 * @run main/othervm -XX:-TieredCompilation -XX:-BackgroundCompilation -XX:CompileCommand=dontinline,TestLateInlineReplacedNodesExceptionPath::notInlined
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN TestLateInlineReplacedNodesExceptionPath
 */

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class TestLateInlineReplacedNodesExceptionPath {
    private static A fieldA;
    private static C fieldC = new C();

    public static void main(String[] args) throws Throwable {
        A a = new A();
        B b = new B();
        for (int i = 0; i < 20_000; i++) {
            fieldA = a;
            test1(true);
            fieldA = b;
            test1(true);
            inlined1(true);
            inlined1(false);
            inlined2(true);
            inlined2(false);
        }
    }

    static final MethodHandle mh1;
    static MethodHandle mh2;
    static final MethodHandle mh3;
    static MethodHandle mh4;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            mh1 = lookup.findStatic(TestLateInlineReplacedNodesExceptionPath.class, "lateInlined1", MethodType.methodType(void.class, C.class));
            mh2 = mh1;
            mh3 = lookup.findStatic(TestLateInlineReplacedNodesExceptionPath.class, "lateInlined2", MethodType.methodType(void.class, C.class));
            mh4 = mh3;
        } catch (NoSuchMethodException | IllegalAccessException e) {
            e.printStackTrace();
            throw new RuntimeException("Method handle lookup failed");
        }
    }

    private static void lateInlined1(C c) {
        fieldA.m(c);
        c.field++;
        fieldA.m(c);
    }

    private static void lateInlined2(C c) {
        c.field++;
    }

    private static void test1(boolean flag) throws Throwable {
        final C c = fieldC;
        MethodHandle mh = null;
        if (flag) {
            mh = inlined1(flag);
        }
        mh.invokeExact(c);
        mh = null;
        if (flag) {
            mh = inlined2(flag);
        }
        mh.invokeExact(c);
    }

    private static MethodHandle inlined1(boolean flag) {
        if (flag) {
            return mh1;
        }
        return mh2;
    }

    private static MethodHandle inlined2(boolean flag) {
        if (flag) {
            return mh3;
        }
        return mh4;
    }

    private static void notInlined() {
    }

    private static class A {
        public void m(C c) {
            c.field++;
            notInlined();
        }

    }

    private static class B extends A {

        public void m(C c) {
            notInlined();
        }
    }

    private static class C {
        public int field;
    }
}
