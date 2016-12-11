/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.jtt.optimize;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 */
@SuppressWarnings("unused")
public class List_reorder_bug extends JTTTest {

    private static class TestClass {
        String s;

        private void print(String s2) {
            this.s = s2;
        }

        private void match(Object a, int src, int id, int seq) {
            print("match: " + src + ", " + id);
            List item = list;
            List itemPrev = null;
            while (item != null) {
                if (item.id == id) {
                    if (item.bool) {
                        outcall(item.id);
                    }
                    if (itemPrev != null) {
                        itemPrev.next = item.next;
                    } else {
                        list = item.next;
                    }

                    item.next = null;
                    return;
                }

                itemPrev = item;
                item = item.next;
            }
        }
    }

    static class List {

        List(int id) {
            this.id = id;
        }

        List next;
        int id;
        boolean bool = true;
    }

    private static List list;

    public static boolean test(int i) {
        list = new List(5);
        list.next = new List(6);
        new TestClass().match(new Object(), 27, 6, 0);
        return list.next == null;
    }

    static int globalId;

    private static void outcall(int id) {
        globalId = id;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", 0);
    }

}
