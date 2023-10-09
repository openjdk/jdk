/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.JFrame;
import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;
import javax.swing.plaf.ButtonUI;
import javax.swing.plaf.metal.MetalRadioButtonUI;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Robot;

/*
 * @test
 * @bug 4823809
 * @summary No Mnemonic or Focus Indicator when using HTML for a Component Text
 * @key headful
 * @run main bug4823809
 */

public class bug4823809 {
    private static ButtonUI testUI;
    private static volatile boolean passed = false;
    private static JFrame frame;
    private static Robot robot;

    public static void main(String[] args) throws Exception {
        try {
            robot = new Robot();
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame("RadioButton Test");
                testUI = new TestRadioButtonUI();
                JRadioButton radio = new TestRadioButton("<html>This is a radiobutton test!</html>");

                frame.getContentPane().add(radio);
                frame.pack();
                frame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);

            if (!passed) {
                throw new Error("Focus isn't painted for JRadioButton with HTML text.");
            }
            System.out.println("Test Passed!");
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    static class TestRadioButton extends JRadioButton {
        public TestRadioButton(String s) {
            super(s);
        }

        public void setUI(ButtonUI ui) {
            super.setUI(testUI);
        }
    }

    static class TestRadioButtonUI extends MetalRadioButtonUI {
        protected void paintFocus(Graphics g, Rectangle t, Dimension d) {
            super.paintFocus(g, t, d);
            passed = true;
        }
    }

}
