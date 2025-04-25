/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @key headful
 * @bug 4369355
 * @summary To verify if SwingUtilities.convertPointToScreen() (for invisible frame)
 *          and SwingUtilities.convertPointFromScreen() return correct values
 * @run main bug4369355
 */

import java.awt.Point;
import java.awt.Robot;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class bug4369355 {
    private static JFrame frame;

    private static volatile Point frameToScreenLoc;
    private static volatile Point frameFromScreenLoc;

    private static final Point EXPECTED_FROM_SCREEN_LOC = new Point(0, 0);
    private static final Point EXPECTED_TO_SCREEN_LOC = new Point(100, 100);

    public static void main (String[] args) throws Exception {
        try {
            Robot robot = new Robot();
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame("bug4369355");
                frame.setBounds(100, 100, 100, 100);
            });
            robot.waitForIdle();
            robot.delay(1000);

            SwingUtilities.invokeAndWait(() -> {
                frameToScreenLoc = new Point(0, 0);
                SwingUtilities.convertPointToScreen(frameToScreenLoc, frame);
            });
            robot.delay(100);

            if (!frameToScreenLoc.equals(EXPECTED_TO_SCREEN_LOC)) {
                throw new RuntimeException("SwingUtilities.convertPointToScreen()"
                        + " returns incorrect point " + frameToScreenLoc + "\n"
                        + "Should be " + EXPECTED_TO_SCREEN_LOC);
            }

            SwingUtilities.invokeAndWait(() -> frame.setVisible(true));
            robot.delay(500);

            SwingUtilities.invokeAndWait(() -> {
                frameFromScreenLoc = frame.getLocationOnScreen();
                SwingUtilities.convertPointFromScreen(frameFromScreenLoc, frame);
            });
            robot.delay(100);

            if (!frameFromScreenLoc.equals(EXPECTED_FROM_SCREEN_LOC)) {
                throw new RuntimeException("SwingUtilities.convertPointFromScreen()"
                        + " returns incorrect point " + frameFromScreenLoc + "\n"
                        + "Should be " + EXPECTED_FROM_SCREEN_LOC);
            }
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }
}
