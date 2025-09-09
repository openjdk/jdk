/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4139900
 * @summary height of combobox may differ
 * @key headful
 * @run main bug4139900
*/

import java.awt.Dimension;
import java.awt.Robot;
import java.awt.event.ActionListener;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class bug4139900 {
    static JButton button;
    static JFrame frame;
    static JComboBox<String> comboBox;
    static int initialHeight;

    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(bug4139900::init);
            test();
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void test() throws Exception {
        Robot robot = new Robot();
        robot.waitForIdle();
        robot.delay(500);

        SwingUtilities.invokeAndWait(() -> initialHeight = comboBox.getHeight());

        for (int i = 0; i < 10; i++) {
            SwingUtilities.invokeAndWait(() -> button.doClick());
            robot.waitForIdle();
            robot.delay(200);
            SwingUtilities.invokeAndWait(() -> {
                if (comboBox.getHeight() != initialHeight) {
                    throw new RuntimeException(
                            "Test failed: height differs from initial %d != %d"
                                    .formatted(comboBox.getHeight(), initialHeight)
                    );
                }
            });
        }
    }

    public static void init() {
        frame = new JFrame("bug4139900");

        DefaultComboBoxModel<String> model =
                new DefaultComboBoxModel<>(new String[]{
                        "Coma Berenices",
                        "Triangulum",
                        "Camelopardis",
                        "Cassiopea"
                });

        comboBox = new JComboBox<>();
        comboBox.setEditable(true);

        button = new JButton("Add/Remove Items");

        ActionListener actionListener = e -> {
            if (comboBox.getModel() == model) {
                comboBox.setModel(new DefaultComboBoxModel<>());
            } else {
                comboBox.setModel(model);
            }
        };

        button.addActionListener(actionListener);

        JPanel panel = new JPanel();
        panel.setPreferredSize(new Dimension(300, 100));
        panel.add(comboBox);
        panel.add(button);

        frame.add(panel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
