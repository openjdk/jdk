/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8336726
 * @summary Test that post-parse call devirtualization works when call does not have an IO projection.
 * @run main/othervm -XX:-TieredCompilation -Xcomp -XX:CompileCommand=compileonly,TestCallDevirtualizationWithInfiniteLoop::test
 *                   TestCallDevirtualizationWithInfiniteLoop
 */

public class TestCallDevirtualizationWithInfiniteLoop {

    static interface I {
        public void method();
    }

    static final class A implements I {
        @Override
        public void method() { };
    }

    static final class B implements I {
        @Override
        public void method() { };
    }

    static final A a = new A();
    static final B b = new B();

    public static void test(boolean flag) {
        // Avoid executing endless loop
        if (flag) {
            return;
        }

        // We only know after loop opts that the receiver type is B.
        I recv = a;
        for (int i = 0; i < 3; ++i) {
            if (i > 1) {
                recv = b;
            }
        }
        // Post-parse call devirtualization will then convert below
        // virtual call to a static call.
        recv.method();

        // Endless loop which does not use IO. As a result the IO
        // projection of the call is removed unexpectedly.
        while (true) { }
    }

    public static void main(String[] args) {
        test(true);
    }
}
