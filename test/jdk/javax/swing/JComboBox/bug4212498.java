/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

/*
 * @test
 * @bug 4212498
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4212498
 */

public class bug4212498 {
    static JPanel panel = new JPanel();
    static JComboBox comboBox = new JComboBox(new Object[]{
            "Coma Berenices",
            "Triangulum",
            "Camelopardis",
            "Cassiopea"});

    private static final String INSTRUCTIONS = """
            Edit the value in the text field (without using the popup)
            and then press the tab key. If the number doesn't increase,
            then test fails.

            Also, try tabbing out without making a change. The number
            should NOT increase unless the user changes something.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("Instructions")
                .instructions(INSTRUCTIONS)
                .columns(50)
                .testUI(bug4212498::createTestUI)
                .build()
                .awaitAndCheck();
    }

    public static JFrame createTestUI() {
        JFrame frame = new JFrame("bug4212498");
        comboBox.setEditable(true);

        final JLabel label = new JLabel("0");

        ActionListener actionListener =
                e -> label.setText("" + (Integer.parseInt(label.getText()) + 1));

        comboBox.addActionListener(actionListener);

        panel.add(comboBox);
        panel.add(label);
        panel.add(new JButton("B"));

        frame.getContentPane().add(panel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        return frame;
    }
}
