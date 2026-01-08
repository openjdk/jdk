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
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

/*
 * @test
 * @bug 4180054
 * @summary Tests that DefaultComboBoxModel doesn't fire a "contents changed" unnecessarily
 * @key headful
 * @run main bug4180054
 */

public class bug4180054 {
    static JFrame frame;
    static JComboBox comboBox;
    static volatile int numberOfContentsChangedEvents = 0;

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
                comboBox.setSelectedIndex(2);
            });
            robot.waitForIdle();
            robot.delay(250);

            if (numberOfContentsChangedEvents != 3) {
                throw new RuntimeException("Unexpected number of Contents Changed Events!\n" +
                        "Expected: 3\nActual: " + numberOfContentsChangedEvents);
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
        frame = new JFrame("bug4180054");
        JPanel panel = new JPanel();
        JLabel label = new JLabel("0");

        DefaultComboBoxModel model = new DefaultComboBoxModel();
        for (int i = 0; i < 100; ++i) {
            model.addElement(Integer.toString(i));
        }
        comboBox = new JComboBox(model);
        comboBox.setEditable(true);

        ListDataListener contentsCounter = new ListDataListener() {
            public void contentsChanged(ListDataEvent e) {
                ++numberOfContentsChangedEvents;
                label.setText(Integer.toString(numberOfContentsChangedEvents));
            }

            public void intervalAdded(ListDataEvent e) {
            }

            public void intervalRemoved(ListDataEvent e) {
            }
        };

        comboBox.getModel().addListDataListener(contentsCounter);

        panel.add(comboBox);
        panel.add(label);

        frame.add(panel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
