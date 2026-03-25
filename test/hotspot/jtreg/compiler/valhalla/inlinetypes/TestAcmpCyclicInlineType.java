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

package compiler.valhalla.inlinetypes;

/*
 * @test
 * @bug 8376213
 * @summary Test acmp with cyclic value classes
 * @enablePreview
 * @run main ${test.main.class}
 * @run main/othervm -Xbatch -XX:+UseFieldFlattening ${test.main.class}
 * @run main/othervm -Xbatch -XX:-UseFieldFlattening ${test.main.class}
 */
public class TestAcmpCyclicInlineType {

    static value class Node {
        Node prev;
        int v;

        Node(Node prev, int v) {
            this.prev = prev;
            this.v = v;
        }
    }

    static value class ObjectHolder {
        Object o;
        int v;

        ObjectHolder(Object o, int v) {
            this.o = o;
            this.v = v;
        }
    }

    public static void main(String[] args) {
        Node prev = new Node(null, 0);
        Node n1 = new Node(prev, 0);
        Node n2 = new Node(prev, 1);
        for (int i = 0; i < 20000; i++) {
            compareNode(n1, n2);
            compareObjectHolder(1, n1);
        }
    }

    static boolean compareNode(Node n1, Node n2) {
        return n1 == n2;
    }

    static boolean compareObjectHolder(Integer o1, Node o2) {
        ObjectHolder h1 = new ObjectHolder(o1, 0);
        ObjectHolder h2 = new ObjectHolder(o2, 0);
        return h1 == h2;
    }
}
