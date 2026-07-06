/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test
 * @bug 8376421
 * @summary "C2: Missing branch on SubTypeCheck node"
 *
 * @run main/othervm -Xbatch -XX:+IgnoreUnrecognizedVMOptions
 *                   -XX:+KillPathsReachableByDeadTypeNode
 *                   compiler.types.TestSubTypeCheckInterfaceNotImplemented
 *
 * @run main/othervm -Xbatch -XX:+IgnoreUnrecognizedVMOptions
 *                   -XX:-KillPathsReachableByDeadTypeNode
 *                   compiler.types.TestSubTypeCheckInterfaceNotImplemented
 */

package compiler.types;

public class TestSubTypeCheckInterfaceNotImplemented {
    static abstract class A           {}
    static abstract class B extends A {}
    static final    class C extends B {}

    interface I {}
    static final class BJ1 extends A implements I {}
    static final class BJ2 extends A implements I {}

    static boolean testHelper2(B o) {
        return true;
    }
    static boolean testHelper1(Object o) {
        if (o instanceof B) {
            return testHelper2((B)o); // a call to place "o" on JVMS, so the map is updated after the check
        } else {
            return false;
        }
    }

    static boolean test(A a) {
        if (a instanceof I) {
            return testHelper1((I)a); // "a" always fails instanceof check against B
        } else {
            return false;
        }
    }

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            testHelper1(new C()); // pollute profile

            test(new BJ1()); test(new BJ2()); test(new C());
        }
    }
}
