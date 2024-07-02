/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, JetBrains s.r.o.. All rights reserved.
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

/* @test
 * @key headful
 * @bug 8326497
 * @summary Verifies that an iconified window is restored with Window.toFront()
 * @library /test/lib
 * @run main IconifiedToFront
 */

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Label;
import java.awt.Robot;
import java.awt.Toolkit;

public class IconifiedToFront {
    private static final int PAUSE_MS = 500;
    private static Robot robot;

    public static void main(String[] args) throws Exception {
        if (!Toolkit.getDefaultToolkit().isFrameStateSupported(Frame.ICONIFIED)) {
            return; // Nothing to test
        }

        robot = new Robot();
        IconifiedToFront.test1();
        IconifiedToFront.test2();
    }

    private static void test1() {
        Frame frame1 = new Frame("IconifiedToFront Test 1");
        try {
            frame1.setLayout(new FlowLayout());
            frame1.setSize(400, 300);
            frame1.setBackground(Color.green);
            frame1.add(new Label("test"));
            frame1.setVisible(true);
            pause();
            frame1.setExtendedState(Frame.ICONIFIED);
            pause();
            frame1.toFront();
            pause();
            int state = frame1.getExtendedState();
            if ((state & Frame.ICONIFIED) != 0) {
                throw new RuntimeException("Test Failed: state is still ICONIFIED: " + state);
            }
        } finally {
            frame1.dispose();
        }
    }

    private static void test2() {
        Frame frame1 = new Frame("IconifiedToFront Test 3");
        try {
            frame1.setLayout(new FlowLayout());
            frame1.setSize(400, 300);
            frame1.setBackground(Color.green);
            frame1.add(new Label("test"));
            frame1.setUndecorated(true);
            frame1.setVisible(true);
            pause();
            frame1.setExtendedState(Frame.ICONIFIED);
            pause();
            frame1.toFront();
            pause();
            int state = frame1.getExtendedState();
            if ((state & Frame.ICONIFIED) != 0) {
                throw new RuntimeException("Test Failed: state is still ICONIFIED: " + state);
            }
        } finally {
            frame1.dispose();
        }
    }

    private static void pause() {
        robot.delay(PAUSE_MS);
        robot.waitForIdle();
    }
}
