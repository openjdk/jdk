/*
 * Copyright (c) 2024, Red Hat and/or its affiliates. All rights reserved.
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
 * @bug 8327741
 * @summary JVM crash in hotspot/share/opto/compile.cpp - failed: missing inlining msg
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:-BackgroundCompilation -XX:+PrintCompilation -XX:+PrintInlining TestPrintInliningLateVirtualCall
 */

public class TestPrintInliningLateVirtualCall {
    static final A fieldA = new A();
    static final B fieldB = new B();
    static final C fieldC = new C();
    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            testHelper(0);
            testHelper(10);
            testHelper(100);
            test();
        }
    }

    private static void testHelper(int i) {
        A a;
        if (i == 10) {
            a = fieldB;
        } else if (i > 10) {
            a = fieldA;
        } else {
            a = fieldC;
        }
        a.m();
    }

    private static void test() {
        int i;
        for (i = 0; i < 10; i++) {

        }
        testHelper(i);
    }

    static class A {
        void m() {

        }
    }

    static class B extends A {
        void m() {

        }
    }

    static class C extends A {
        void m() {

        }
    }
}
