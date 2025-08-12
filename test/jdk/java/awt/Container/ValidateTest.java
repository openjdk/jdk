/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Panel;

/*
 * @test
 * @bug 4136190
 * @requires (os.family == "windows")
 * @summary Recursive validation calls would cause major USER resource leakage
 * @key headful
 * @run main/timeout=30 ValidateTest
 */

public class ValidateTest {
    static Frame frame;

    public static void main(String args[]) throws Exception {
        try {
            EventQueue.invokeAndWait(() -> {
                createGUI();
            });
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    public static void createGUI() {
        frame = new Frame("Test for 4136190 : JVM and win95 resource leakage issues");
        frame.setLayout(new GridLayout(1, 1));
        MyPanel panel = new MyPanel();
        frame.add(panel);
        frame.invalidate();
        frame.validate();
        frame.setSize(500, 400);
        frame.setVisible(true);
    }

    static class MyPanel extends Panel {
        int recurseCounter = 0;

        public void validate() {
            recurseCounter++;
            if (recurseCounter >= 100) {
                return;
            }
            getParent().validate();
            super.validate();
        }
    }
}