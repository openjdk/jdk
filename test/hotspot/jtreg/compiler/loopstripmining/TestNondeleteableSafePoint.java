/*
 * Copyright (c) 2023, Red Hat, Inc. All rights reserved.
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
 * @bug 8307131
 * @summary C2: assert(false) failed: malformed control flow
 * @library /test/lib
 * @run main/othervm -Xcomp -XX:CompileOnly=TestNondeleteableSafePoint -XX:-TieredCompilation TestNondeleteableSafePoint
 */

import jdk.test.lib.Utils;

public class TestNondeleteableSafePoint {
    static int N;

    public static void main(String[] strArr) throws Exception {
        Thread thread = new Thread() {
                public void run() {
                    test();
                }
            };
        // Give thread some time to trigger compilation
        thread.setDaemon(true);
        thread.start();
        Thread.sleep(Utils.adjustTimeout(500));
    }

    static void test() {
        int i19, i21, iArr1[] = new int[N];
        while (true) {
            for (i19 = i21 = 2; i21 > i19; --i21) {
            }
        }
    }
}
