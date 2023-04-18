/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8294217
 * @summary Assertion failure: parsing found no loops but there are some
 * @library /test/lib
 *
 * @run main/othervm -Xmx1G -XX:-BackgroundCompilation TestInfiniteLoopNest
 *
 */

import jdk.test.lib.Utils;

public class TestInfiniteLoopNest {
    long l;

    void q() {
        if (b) {
            Object o = new Object();
            return;
        }

        do {
            l++;
            while (l != 1) --l;
            l = 9;
        } while (l != 5);

    }

    public static void main(String[] p) throws Exception {
        Thread thread = new Thread() {
            public void run() {
                TestInfiniteLoopNest t = new TestInfiniteLoopNest();
                for (int i = 524; i < 19710; i += 1) {
                    b = true;
                    t.q();
                    b = false;
                }
                t.q();
            }
        };
        // Give thread some time to trigger compilation
        thread.setDaemon(true);
        thread.start();
        Thread.sleep(Utils.adjustTimeout(500));
    }

    static Boolean b;
}
