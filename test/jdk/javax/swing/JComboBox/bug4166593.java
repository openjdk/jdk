/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Robot;
import java.awt.event.ActionListener;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/*
 * @test
 * @bug 4166593
 * @summary Tests that JComboBox fires action events every time the user does an action
 * @key headful
 * @run main bug4166593
 */

public class bug4166593 {
    static JFrame frame;
    static JComboBox comboBox;
    static volatile int numberOfActionEvents = 0;

    public static void main(String[] args) throws Exception {
        try {
            Robot robot = new Robot();
            SwingUtilities.invokeAndWait(() -> createTestUI());
            robot.waitForIdle();
            robot.delay(250);

            // change selected index 3 times
            SwingUtilities.invokeAndWait(() -> {
                comboBox.setSelectedIndex(1);
                comboBox.setSelectedIndex(3);
                comboBox.setSelectedIndex(2);
            });
            robot.waitForIdle();
            robot.delay(250);

            if (numberOfActionEvents != 3) {
                throw new RuntimeException("Unexpected number of Action Events!\n" +
                        "Expected: 3\nActual: " + numberOfActionEvents);
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    public static void createTestUI() {
        comboBox = new JComboBox(new Object[]{
                "Bob", "Fred", "Hank", "Joe", "Mildred", "Agatha", "Buffy"
        });
        JPanel panel = new JPanel();
        JLabel label = new JLabel("0");
        frame = new JFrame("bug4166593");
        comboBox.setEditable(true);

        ActionListener actionCounter = e -> {
            ++numberOfActionEvents;
            label.setText(Integer.toString(numberOfActionEvents));
        };

        comboBox.addActionListener(actionCounter);

        panel.add(comboBox);
        panel.add(label);

        frame.add(panel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
