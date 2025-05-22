/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/*
 * @test
 * @bug 8322754
 * @key headful
 * @summary Verifies clicking JComboBox during frame closure causes Exception
 * @run main ComboPopupBug
 */

public class ComboPopupBug {
    private static JFrame frame;
    private static JButton closeButton;
    private static JComboBox<String> comboBox;
    private static Robot robot;
    private static final int PADDING = 10;

    public static void main(String[] args) throws Exception {
        try {
            robot = new Robot();
            robot.setAutoDelay(50);

            SwingUtilities.invokeAndWait(ComboPopupBug::createUI);

            robot.waitForIdle();
            robot.delay(1000);

            SwingUtilities.invokeAndWait(() -> closeButton.doClick());

            robot.waitForIdle();
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void clickComboBox() {
        Point comboBoxLocation = comboBox.getLocationOnScreen();
        Dimension comboBoxSize = comboBox.getSize();

        robot.mouseMove(comboBoxLocation.x + comboBoxSize.width - PADDING,
                comboBoxLocation.y + comboBoxSize.height / 2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    private static void createUI() {
        frame = new JFrame("ComboPopup");

        comboBox = new JComboBox<>();
        comboBox.setEditable(true);
        comboBox.addItem("test");
        comboBox.addItem("test2");
        comboBox.addItem("test3");

        closeButton = new JButton("Close");
        closeButton.addActionListener((e) -> {
            clickComboBox();
            frame.setVisible(false);
        });

        frame.getContentPane().add(comboBox, "North");
        frame.getContentPane().add(closeButton, "South");
        frame.setSize(200, 200);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
