/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary An example test that shows how to use the Generators library.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver testlibrary_tests.generators.tests.ExampleTest
 */

package testlibrary_tests.generators.tests;

import compiler.lib.generators.Generator;

import static compiler.lib.generators.Generators.G;


public class ExampleTest {
    static class FakeException extends RuntimeException {}

    static class UnderTest {
        private enum State { STAND_BY, FIRST, SECOND };

        private State state = State.STAND_BY;

        void doIt(int x) {
            state = switch (state) {
                case State.STAND_BY -> x == (1 << 10) + 3 ? State.FIRST : State.STAND_BY;
                case State.FIRST -> x == (1 << 5) - 2 ? State.SECOND : State.STAND_BY;
                case State.SECOND -> {
                    if (x == (1 << 4)) throw new FakeException();
                    yield State.STAND_BY;
                }
            };
        }
    }

    public static void main(String[] args) {
        // This test should print "Assertion triggered by special" (see the math below) but almost never
        // "Assertion triggered by uniform" as the chance of triggering is about 2^-96.
        try {
            test(G.uniformInts());
        } catch (FakeException e) {
            System.out.println("Assertion triggered by uniform");
        }
        try {
            // 408 ints => 1/408 * 1/408 * 1/408 => 1/67_917_312 => with 70_000_000 loop iterations we should trigger
            test(G.powerOfTwoInts(3));
        } catch (FakeException e) {
            System.out.println("Assertion triggered by special");
        }
    }

    public static void test(Generator<Integer> g) {
        UnderTest underTest = new UnderTest();
        for (int i = 0; i < 70_000_000 * 3; i++) {
            underTest.doIt(g.next());
        }
    }
}
