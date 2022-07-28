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

import java.awt.Font;
import java.awt.Robot;
import javax.swing.DebugGraphics;
import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicReference;

/* @test
 * @bug 6521141
 * @key headful
 * @summary Test to check if NPE does not occur when graphics is not
 *  initialized and DebugGraphics instance is created with default
 *  Constructor and used.
 * @run main DebugGraphicsNPETest
 */

public class DebugGraphicsNPETest {
    private static final AtomicReference<Throwable> exception =
            new AtomicReference<>();

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                Thread.currentThread().setUncaughtExceptionHandler(
                        new DebugGraphicsNPETest.ExceptionCheck());
                runTest();
            }
        });

        robot.delay(2000);
        robot.waitForIdle();

        if (exception.get() != null) {
            throw new RuntimeException("Test Case Failed. NPE raised!",
                    exception.get());
        }
        System.out.println("Test Pass!");
    }

    static void runTest() {
        DebugGraphics dg = new DebugGraphics();
        Font font = new Font("Currier", Font.PLAIN, 10);
        dg.setFont(font);
    }

    static class ExceptionCheck implements Thread.UncaughtExceptionHandler {
        public void uncaughtException(Thread t, Throwable e)
        {
            exception.set(e);
            System.err.println("Uncaught Exception Handled : " + e);
        }
    }
}
