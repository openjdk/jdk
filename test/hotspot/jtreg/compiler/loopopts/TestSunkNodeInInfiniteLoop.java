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
 * @bug 8336830
 * @summary C2: assert(get_loop(lca)->_nest < n_loop->_nest || lca->in(0)->is_NeverBranch()) failed: must not be moved into inner loop
 * @library /test/lib
 * @run main/othervm -XX:CompileCommand=compileonly,TestSunkNodeInInfiniteLoop::* -Xcomp TestSunkNodeInInfiniteLoop
 *
 */

import jdk.test.lib.Utils;

public class TestSunkNodeInInfiniteLoop {
    public static void main(String[] args) throws InterruptedException {
        byte[] a = new byte[1];
        Thread thread = new Thread(() -> test(a));
        thread.setDaemon(true);
        thread.start();
        Thread.sleep(Utils.adjustTimeout(4000));
    }

    static void test(byte[] a) {
        // L0:
        while(true) {
            int i1 = a.length;
            // L3:
            while(true) {
                int i2 = 0;
                if ((i1--) <= 0) { break; /* ifle L0 */}
                a[i2++] = -1;
                // goto L3
            }
        }
    }
}
