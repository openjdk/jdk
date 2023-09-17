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

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Robot;

/*
 * @test
 * @bug 4680204
 * @summary JSpinner shows ToolTipText only on it's border
 * @key headful
 * @run main bug4680204
 */

public class bug4680204 {

    private static JSpinner sp1, sp2;
    private static final String TOOL_TIP_TEXT = "ToolTipText";
    private static JFrame frame;
    private static Robot robot;
    private static volatile boolean failed = false;

    public static void main(String[] args) throws Exception {
        try {
            robot = new Robot();
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame();
                frame.getContentPane().setLayout(new FlowLayout());

                sp1 = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
                sp1.setToolTipText(TOOL_TIP_TEXT);
                frame.getContentPane().add(sp1);

                sp2 = new JSpinner();
                sp2.setToolTipText(TOOL_TIP_TEXT);
                frame.getContentPane().add(sp2);
                sp2.setModel(new SpinnerNumberModel(1, 1, 100, 1));
                frame.setLocationRelativeTo(null);
                frame.pack();
                frame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);
            SwingUtilities.invokeAndWait(() -> {
                Component[] children = sp1.getComponents();
                for (int i = 0; i < children.length; i++) {
                    if (children[i] instanceof JSpinner.DefaultEditor) {
                        JTextField tf = ((JSpinner.DefaultEditor) children[i]).getTextField();
                        if (!TOOL_TIP_TEXT.equals(tf.getToolTipText())) {
                            failed = true;
                        }
                    } else if (children[i] instanceof JComponent) {
                        String text = ((JComponent) children[i]).getToolTipText();
                        if (!TOOL_TIP_TEXT.equals(text)) {
                            failed = true;
                        }
                    }
                }

                children = sp2.getComponents();
                for (int i = 0; i < children.length; i++) {
                    if (children[i] instanceof JSpinner.DefaultEditor) {
                        JTextField tf = ((JSpinner.DefaultEditor) children[i]).getTextField();
                        if (!TOOL_TIP_TEXT.equals(tf.getToolTipText())) {
                            failed = true;
                        }
                    } else if (children[i] instanceof JComponent) {
                        String text = ((JComponent) children[i]).getToolTipText();
                        if (!TOOL_TIP_TEXT.equals(text)) {
                            failed = true;
                        }
                    }
                }
            });
            robot.waitForIdle();
            robot.delay(1000);
            if (failed) {
                throw new RuntimeException("The tooltip text is not correctly set for JSpinner");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
        System.out.println("Test Passed!");
    }
}
