/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4374578
 * @summary Test robot wheel scrolling of Text
 * @requires (os.family == "Windows") | (os.family == "linux")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual RobotScrollTest
*/

import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.TextArea;

public class RobotScrollTest {

    static TextArea ta;
    static Robot robot;

    private static final String INSTRUCTIONS = """
         0. DON'T TOUCH ANYTHING!
         1. This test is for Windows and Linux only.
         2. Just sit back, and watch the Robot move the mouse to the TextArea.
         3. Once the pointer is on the text area, the Robot will use the mouse wheel
            to scroll the text.
            If the text scrolled, press PASS, else, press FAIL.""";

    public static void main(String[] args) throws Exception {
        robot = new Robot();
        robot.setAutoDelay(100);
        PassFailJFrame passFail = new PassFailJFrame(INSTRUCTIONS);
        createTestUI();
        passFail.awaitAndCheck();
    }

    private static void createTestUI() {
        Frame f = new Frame("RobotScrollTest");
        ta = new TextArea();
        for (int i = 0; i < 100; i++) {
            ta.append(i + "\n");
        }
        f.add(ta);
        f.setLocation(0, 400);
        f.pack();
        PassFailJFrame.addTestWindow(f);
        PassFailJFrame.positionTestWindow(f, PassFailJFrame.Position.HORIZONTAL);
        f.setVisible(true);
        doTest();
    }

    private static void doTest() {
        robot.waitForIdle();
        robot.delay(1000);
        // get loc of TextArea
        Point taAt = ta.getLocationOnScreen();
        // get bounds of button
        Rectangle bounds = ta.getBounds();

        // move mouse to middle of button
        robot.mouseMove(taAt.x + bounds.width / 2,
                        taAt.y + bounds.height / 2);

        // rotate wheel a few times
        for (int j = 1; j < 8; j++) {
            for (int k = 0; k < 5; k++) {
                robot.mouseWheel(j);
            }

            for (int k = 0; k < 5; k++) {
                robot.mouseWheel(-1 * j);
            }
        }
    }

}

