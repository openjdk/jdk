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
 * @bug 4251592
 * @summary JToolBar should have ability to set custom layout.
 * @key headful
 * @run main bug4251592
 */

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;

public class bug4251592 {
    private static final int OFFSET = 3;
    private static volatile Point loc;
    private static JFrame frame;
    private static JToolBar toolBar;
    private static GridLayout customLayout;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(100);
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame("Toolbar Layout Save Test");
                toolBar = new JToolBar();
                customLayout = new GridLayout();
                frame.setLayout(new BorderLayout());
                frame.add(toolBar, BorderLayout.NORTH);

                toolBar.setLayout(customLayout);
                toolBar.add(new JButton("Button1"));
                toolBar.add(new JButton("Button2"));
                toolBar.add(new JButton("Button3"));
                toolBar.setFloatable(true);

                frame.setSize(200, 200);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });

            robot.waitForIdle();
            robot.delay(1000);

            SwingUtilities.invokeAndWait(() -> loc = toolBar.getLocationOnScreen());

            robot.mouseMove(loc.x + OFFSET, loc.y + OFFSET);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseMove(loc.x + OFFSET, loc.y + 50);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            if (toolBar.getLayout() != customLayout) {
                throw new RuntimeException("Custom layout not saved...");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}
