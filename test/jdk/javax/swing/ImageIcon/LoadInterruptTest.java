/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.MediaTracker;

import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/*
 * @test
 * @bug 8236987 8320328
 * @summary Verifies ImageIcon constructor produces no output when the
 *          thread is interrupted
 * @run main LoadInterruptTest
 */

public class LoadInterruptTest {
    private static ByteArrayOutputStream testOut;
    private static PrintStream prevSysOut;

    public static void main(String[] args) throws Exception {

        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    loadImageIcon();
                }
            });
        } finally {
            unsetOutput();
        }
    }

    public static void setUpOutput() {
        prevSysOut = System.out;
        testOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(testOut, true, StandardCharsets.UTF_8));
    }

    public static void unsetOutput() {
        if (prevSysOut != null) {
            System.setOut(prevSysOut);
        }
        testOut = null;
    }

    private static void loadImageIcon() {
        setUpOutput();

        Thread.currentThread().interrupt();
        ImageIcon i = new ImageIcon("https://openjdk.org/images/openjdk.png");
        int status = i.getImageLoadStatus();
        boolean interrupted = Thread.currentThread().isInterrupted();

        System.out.flush();
        String outString = testOut.toString(StandardCharsets.UTF_8);

        if (!outString.isEmpty()) {
            throw new RuntimeException("Test Case Failed!!! System.out is not empty : " + outString);
        }

        if (status == MediaTracker.LOADING) {
            throw new RuntimeException("Test Case Failed!!! LOADING... status!!!");
        }

        if (!interrupted) {
            throw new RuntimeException("Interrupted state of the thread is not preserved");
        }
    }
}
