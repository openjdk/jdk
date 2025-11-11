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

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

/*
 * @test
 * @bug 4171819
 * @summary Tests that JComboBox uses a lower bevel border in windows
 * @requires (os.family == "windows")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4171819
 */

public class bug4171819 {
    static boolean lafOk = true;

    private static final String INSTRUCTIONS = """
            This test is for Windows L&F only. If you see
            "No Windows L&F installed" label just press "Pass".

            Look at the combo box.  If the border around it looks like it's
            lowered rather than raised, it passes the test.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("Instructions")
                .instructions(INSTRUCTIONS)
                .columns(50)
                .testUI(bug4171819::createTestUI)
                .build()
                .awaitAndCheck();
    }

    public static JFrame createTestUI() {
        try {
            UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
            System.out.println("succeeded");
        } catch (Exception e) {
            System.err.println("Couldn't load the Windows Look and Feel");
            lafOk = false;
        }

        JFrame frame = new JFrame("bug4171819");
        JPanel panel = new JPanel();
        JComboBox comboBox;

        if (lafOk) {
            comboBox = new JComboBox(new Object[]{
                    "Coma Berenices",
                    "Triangulum",
                    "Camelopardis",
                    "Cassiopea"});
            panel.add(comboBox);
        } else {
            JLabel label = new JLabel("No Windows L&F installed");
            panel.add(label);
        }
        frame.add(panel);
        frame.pack();
        return frame;
    }
}
