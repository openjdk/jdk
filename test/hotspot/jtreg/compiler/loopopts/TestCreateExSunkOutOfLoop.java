/*
 * Copyright (c) 2025 IBM Corporation. All rights reserved.
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
 * @bug 8373508
 * @summary C2: sinking CreateEx out of loop breaks the graph
 * @library /test/lib
 * @run main/othervm -Xbatch ${test.main.class}
 * @run main ${test.main.class}
 */

package compiler.loopopts;

import jdk.test.lib.Utils;

public class TestCreateExSunkOutOfLoop {
    volatile boolean _mutatorToggle;

    boolean _mutatorFlip() {
        _mutatorToggle = !_mutatorToggle;
        return _mutatorToggle;
    }

    void test() {
        int idx = -845;
        for (;;) {
            try {
                try {
                    for (Object temp = new byte[idx];;) {;}
                } finally {
                    boolean flag = _mutatorFlip();
                    _mutatorFlip();
                    for (;;) {
                        if (flag) {
                            break;
                        }
                    }
                }
            } catch (Throwable $) {;}
        }
    }

    public static void main(String[] strArr) throws InterruptedException {
        Thread thread = new Thread(() -> {
            TestCreateExSunkOutOfLoop t = new TestCreateExSunkOutOfLoop();
            t.test();
        });
        thread.setDaemon(true);
        thread.start();
        Thread.sleep(Utils.adjustTimeout(4000));
    }
}
