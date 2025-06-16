/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Frame;

/**
 * @test
 * @bug 8346952
 * @summary dispose the frame while flooding the EDT with Notify events.
 * @key headful
 */

public final class NotifyStressTest {
    static volatile Throwable failed;

    public static void main(final String[] args) throws Exception {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> failed = e);

        for (int i = 0; i < 100; i++) {
            test();
        }

        // let the system recover.
        Thread.sleep(5000);
    }

    private static void test() throws Exception {
        Frame f = new Frame();
        f.setSize(100, 100);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        f.dispose();

        Thread thread1 = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                f.removeNotify();
                f.addNotify();
            }
        });
        thread1.start();
        thread1.join();

        if (failed != null) {
            System.err.println("Test failed");
            failed.printStackTrace();
            throw new RuntimeException(failed);
        }
    }
}
