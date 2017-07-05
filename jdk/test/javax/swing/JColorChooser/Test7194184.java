/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
 * Portions Copyright (c) 2012 IBM Corporation
 */

/*
 * @test
 * @bug 7194184
 * @summary Tests JColorChooser Swatch keyboard accessibility.
 * @author Sean Chou
 * @library ../regtesthelpers
 * @build Util
 * @run main Test7194184
 */

import java.awt.Component;
import java.awt.AWTException;
import java.awt.Color;
import java.awt.Robot;
import java.awt.event.KeyEvent;

import javax.swing.JColorChooser;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import java.util.concurrent.Callable;

public class Test7194184 implements Runnable {
    private static JFrame frame;
    private static JColorChooser colorChooser;
    private static Color selectedColor;

    public static void main(String[] args) throws Exception {
        testKeyBoardAccess();
    }

    private static void testKeyBoardAccess() throws Exception {
        Robot robot = new Robot();

        SwingUtilities.invokeLater(new Test7194184());
        robot.waitForIdle();

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                selectedColor = colorChooser.getColor();

                Component recentSwatchPanel = Util.findSubComponent(colorChooser, "RecentSwatchPanel");
                if (recentSwatchPanel == null) {
                    throw new RuntimeException("RecentSwatchPanel not found");
                }
                recentSwatchPanel.requestFocusInWindow();
            }
        });

        robot.waitForIdle();

        // Tab to move the focus to MainSwatch
        Util.hitKeys(robot, KeyEvent.VK_SHIFT, KeyEvent.VK_TAB);

        // Select the color on right
        Util.hitKeys(robot, KeyEvent.VK_RIGHT);
        Util.hitKeys(robot, KeyEvent.VK_RIGHT);
        Util.hitKeys(robot, KeyEvent.VK_SPACE);
        robot.waitForIdle();

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                frame.dispose();
                if (selectedColor == colorChooser.getColor()) {
                    throw new RuntimeException("JColorChooser misses keyboard accessibility");
                }
            }
        });
    }

    public void run() {
        String title = getClass().getName();
        frame = new JFrame(title);
        colorChooser = new JColorChooser();

        frame.add(colorChooser);
        frame.pack();
        frame.setVisible(true);
    }

}
