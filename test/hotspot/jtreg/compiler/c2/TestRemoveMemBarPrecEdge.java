/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8287432
 * @summary Test removal of precedence edge of MemBarAcquire together with other now dead input nodes which visits a
 *          top node. This resulted in a crash before as it disconnected top from the graph which is unexpected.
 *
 * @run main/othervm -Xbatch compiler.c2.TestRemoveMemBarPrecEdge
 */
package compiler.c2;

public class TestRemoveMemBarPrecEdge {
    static boolean flag = false;

    public static void main(String[] args) {
        for (int i = 0; i < 10000; i++) {
            test();
            flag = !flag;
        }
    }

    public static void test() {
        // currentThread() is intrinsified and C2 emits a special AddP node with a base that is top.
        Thread t = Thread.currentThread();
        // getName() returns the volatile _name field. The method is inlined and we just emit a LoadN + DecodeN which
        // is a precedence edge input into both MemBarAcquire nodes below for the volatile field _name.
        if (flag) {
            t.getName();
        } else {
            t.getName();
        }
    }
}
