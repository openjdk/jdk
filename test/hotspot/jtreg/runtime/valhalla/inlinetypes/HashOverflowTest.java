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
 */

/*
 * @test
 * @summary Tests that JVM_IHashCode does not cache when a SOE occurs.
 * @comment This test runs with the interpreter such that the value is always
 *          buffered, meaning that the identity hash code computed will be
 *          saved in the markWord.
 * @enablePreview
 * @requires vm.flagless
 * @compile HashOverflowTest.java
 * @run main/othervm -Xint -Xss256K
 *                   runtime.valhalla.inlinetypes.HashOverflowTest
 */

package runtime.valhalla.inlinetypes;

public class HashOverflowTest {
    private static final int N_ELEMS = 1000;

    public static void main(String[] args) {
        Cons list = makeLargeDataStructure();
        try {
            System.identityHashCode(list);
            throw new RuntimeException("expected to stack overflow when computing identity hash");
        } catch (StackOverflowError expected) {
            // Expected, continue execution.
        }
        try {
            System.identityHashCode(list);
            throw new RuntimeException("expected subsequent identity hash calls to also overflow");
        } catch (StackOverflowError expected) {
            // Expected, test passes!
        }
    }

    private static Cons makeLargeDataStructure() {
        Cons prev = new Cons(N_ELEMS - 1, null);
        for (int i = N_ELEMS - 2; i >= 0; i--) {
            Cons curr = new Cons(i, prev);
            prev = curr;
        }
        return prev;
    }

    public static value record Cons(int x, Cons y) {}
}
