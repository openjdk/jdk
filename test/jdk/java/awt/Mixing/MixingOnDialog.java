/*
 * Copyright (c) 2007, 2022, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @key headful
  @bug 4811096
  @summary Tests whether mixing works on Dialogs
  @author anthony.petrov@...: area=awt.mixing
  @run main MixingOnDialog
*/


/*
 * MixingOnDialog.java
 *
 * summary:  Tests whether awt.Button and swing.JButton mix correctly
 */

import java.awt.AWTException;
import java.awt.Button;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.lang.reflect.InvocationTargetException;
import javax.swing.JButton;
import javax.swing.SwingUtilities;

public class MixingOnDialog {
    static volatile boolean lightClicked = false;
    static Dialog d;
    static volatile Button heavy;

    private static void init() {
        // Create components
        d = new Dialog((Frame)null, "Button-JButton mix test");
        heavy = new Button("  Heavyweight Button  ");
        final JButton light = new JButton("  LW Button  ");

        // Actions for the buttons add appropriate number to the test sequence
        light.addActionListener(e -> lightClicked = true);

        // Overlap the buttons
        heavy.setBounds(230, 230, 200, 200);
        light.setBounds(210, 210, 50, 50);

        // Put the components into the frame
        d.setLayout(null);
        d.add(light);
        d.add(heavy);
        d.setBounds(250, 250, 400, 400);
        d.setVisible(true);
    }

    public static void main(String[] args) throws InterruptedException, InvocationTargetException, AWTException {
        SwingUtilities.invokeAndWait(MixingOnDialog::init);

        Robot robot = new Robot();
        robot.setAutoDelay(50);
        robot.waitForIdle();
        robot.delay(500);

        // Move the mouse pointer to the position where both
        //    buttons overlap
        Point heavyLoc = heavy.getLocationOnScreen();
        robot.mouseMove(heavyLoc.x + 5, heavyLoc.y + 5);

        // Now perform the click at this point
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();

        SwingUtilities.invokeAndWait(() -> d.dispose());

        if (!lightClicked) {
            throw new RuntimeException("The lightweight component left behind the heavyweight one.");
        }
    }
}
