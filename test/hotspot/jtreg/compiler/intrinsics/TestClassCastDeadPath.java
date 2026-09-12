/*
 * Copyright (c) 2026 IBM Corporation. All rights reserved.
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
 * @bug 8390467
 * @summary C2: _map != nullptr assert failure in LibraryCallKit::inline_Class_cast()
 * @run main/othervm  -XX:CompileOnly=${test.main.class}::test1 -Xcomp ${test.main.class}
 */

package compiler.intrinsics;

public class TestClassCastDeadPath {
    public static void main(String[] args) {
        B b = new B();
        C c = new C();
        A.class.cast(b);
        try {
            test1(c);
        } catch (ClassCastException cce) {
        }
    }

    private static void test1(Object o) {
        if (!(o instanceof I)) {
            throw new RuntimeException("never taken");
        }
        A.class.cast(o);
    }

    static abstract class A {

    }

    static class B extends A {

    }

    interface I {

    }

    static class C implements I {

    }

}
