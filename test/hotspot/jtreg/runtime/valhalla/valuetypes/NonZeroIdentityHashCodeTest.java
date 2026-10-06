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
 * @bug 8391990
 * @summary Tests that identityHashCode does not return zero for value classes,
 *          this ensures the value is cached and then retrieved from the cache.
 * @enablePreview
 * @run main/othervm -Xint
 *                   ${test.main.class}
 */

package runtime.valhalla.valuetypes;

public class NonZeroIdentityHashCodeTest {
    static value class Node {
        final Node left;
        final Node right;
        final int payload;

        public Node(boolean alternative) {
            this.left = null;
            this.right = null;
            this.payload = (alternative ? 0 : Integer.MIN_VALUE) -31 * System.identityHashCode(Node.class);
        }

        public Node(Node child) {
            this.left = child;
            this.right = child;
            this.payload = -31 * System.identityHashCode(Node.class);
        }
    }

    static Node makeDeepFakeTree(int depth) {
        Node node = new Node(false);
        for (int i = 1; i < depth; ++i) {
            node = new Node(node);
        }
        return node;
    }

    static void test_deep() {
        Node n = makeDeepFakeTree(50);

        // That performs one computation by object (50), but an approach without cache
        // would take 2^50 computations (and would time out).
        int actual = System.identityHashCode(n);
        if (actual == 0) {
            throw new RuntimeException("identityHashCode of a value object should never be zero");
        }
    }

    static void test_value(boolean alternative) {
        Node n = new Node(alternative);
        int expected = System.identityHashCode(Node.class);
        if (expected == 0) {
            // That would be really broken
            throw new RuntimeException("identityHashCode of an identity object should never be zero");
        }

        int actual = System.identityHashCode(n);
        if (actual == 0) {
            throw new RuntimeException("identityHashCode of a value object should never be zero");
        }

        if (actual != expected) {
            throw new RuntimeException("identityHashCode should be the hash of the class");
        }
    }

    public static void main(String[] args) {
        test_value(false);
        test_value(true);
        test_deep();
    }
}
