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
 * bug 8318826
 * @summary C2: "Bad graph detected in build_loop_late" with incremental inlining
 * @requires vm.compiler2.enabled
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:+AlwaysIncrementalInline -XX:-BackgroundCompilation TestNullAtCallAfterLateInline
 */


public class TestNullAtCallAfterLateInline {
    private static final C c = new C();
    private static final A a = new A();
    private static volatile int volatileField;
    private static B b= new B();

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            testHelper1(0, true);
            testHelper1(1, true);
            testHelper1(2, true);
            test1(false);
            testHelper2(0, true, b);
            testHelper2(1, true, c);
            testHelper2(2, true, a);
            inlined2(null, 3);
            test2(false, null);
        }
    }

    private static int test1(boolean flag) {
        for (int i = 0; i < 10; i++) {
        }
        return testHelper1(3, flag);
    }

    private static int testHelper1(int i, boolean flag) {
        int v;
        if (flag) {
            A a = inlined1(i);
            a.notInlined();
            v = a.field;
        } else {
            volatileField = 42;
            v = volatileField;
        }
        return v;
    }

    private static A inlined1(int i) {
        if (i == 0) {
            return b;
        } else if (i == 1) {
            return c;
        } else if (i == 2) {
            return a;
        }
        return null;
    }

    private static int test2(boolean flag, A a) {
        for (int i = 0; i < 10; i++) {
        }
        return testHelper2(3, flag, a);
    }

    private static int testHelper2(int i, boolean flag, A a) {
        int v;
        if (flag) {
            inlined2(a, i);
            a.notInlined();
            v = a.field;
        } else {
            volatileField = 42;
            v = volatileField;
        }
        return v;
    }

    private static void inlined2(Object a, int i) {
        if (i == 0) {
            if (!(a instanceof B)) {
            }
        } else if (i == 1) {
            if (!(a instanceof C)) {

            }
        } else if (i == 2) {
            if (!(a instanceof A)) {

            }
        } else {
            if (!(a instanceof D)) {
            }
        }
    }

    private static class A {
        public int field;

        void notInlined() {}
    }

    private static class B extends A {
        void notInlined() {}
    }

    private static class C extends A {
        void notInlined() {}
    }

    private static class D {
    }
}
