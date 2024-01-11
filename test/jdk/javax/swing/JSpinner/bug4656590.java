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

import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JSpinner;
import javax.swing.SpinnerDateModel;
import javax.swing.SpinnerListModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Robot;

/*
 * @test
 * @bug 4656590
 * @summary JSpinner.setFont() does nothing
 * @key headful
 * @run main bug4656590
 */

public class bug4656590 {
    private static JSpinner[] spinner = new JSpinner[6];
    private static Font font = new Font("Arial", Font.BOLD, 24);
    private static volatile boolean failed = false;
    private static JFrame frame;
    private static Robot robot;

    public static void main(String[] args) throws Exception {
        try {
            robot = new Robot();
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame();
                frame.getContentPane().setLayout(new GridLayout(3, 2));
                spinner[0] = new JSpinner();
                spinner[0].setModel(new SpinnerNumberModel());
                spinner[0].setFont(font);
                frame.getContentPane().add(spinner[0]);

                spinner[1] = new JSpinner();
                spinner[1].setModel(new SpinnerDateModel());
                spinner[1].setFont(font);
                frame.getContentPane().add(spinner[1]);

                spinner[2] = new JSpinner();
                spinner[2].setModel(new SpinnerListModel
                        (new Object[]{"one", "two", "three"}));
                spinner[2].setFont(font);
                frame.getContentPane().add(spinner[2]);

                spinner[3] = new JSpinner();
                spinner[3].setFont(font);
                spinner[3].setModel(new SpinnerNumberModel());
                frame.getContentPane().add(spinner[3]);

                spinner[4] = new JSpinner();
                spinner[4].setFont(font);
                spinner[4].setModel(new SpinnerDateModel());
                frame.getContentPane().add(spinner[4]);

                spinner[5] = new JSpinner();
                spinner[5].setFont(font);
                spinner[5].setModel(new SpinnerListModel
                        (new Object[]{"one", "two", "three"}));
                frame.getContentPane().add(spinner[5]);
                frame.pack();
                frame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);
            SwingUtilities.invokeAndWait(() -> {
                JFormattedTextField ftf;
                for (int i = 1; i < 6; i++) {
                    ftf = ((JSpinner.DefaultEditor)
                            spinner[i].getEditor()).getTextField();
                    if (!ftf.getFont().equals(font)) {
                        failed = true;
                    }
                }
            });
            robot.waitForIdle();
            robot.delay(1000);
            if (failed) {
                throw new RuntimeException("JSpinner.setFont() " +
                        "doesn't set the font properly");
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
}
