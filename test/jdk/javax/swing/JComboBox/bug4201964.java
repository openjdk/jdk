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

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.UIManager;

/*
 * @test
 * @bug 4201964
 * @summary Tests that JComboBox's arrow button isn't drawn too wide in Windows Look&Feel
 * @requires (os.family == "windows")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4201964
 */

public class bug4201964 {
    private static final String INSTRUCTIONS = """
            Does the arrow look too large?  If not, it passes.
            """;

    public static void main(String[] args) throws Exception {
        try {
            UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        } catch (Exception e) {
            PassFailJFrame.forceFail("Couldn't load the Windows look and feel.");
        }

        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .rows(8)
                .columns(30)
                .testUI(bug4201964::createTestUI)
                .build()
                .awaitAndCheck();
    }

    public static JFrame createTestUI() {
        JFrame frame = new JFrame("bug4201964");
        JPanel panel = new JPanel();
        JComboBox comboBox;

        comboBox = new JComboBox(new Object[]{
                "Coma Berenices",
                "Triangulum",
                "Camelopardis",
                "Cassiopea"});

        panel.add(comboBox);

        frame.add(panel);
        frame.pack();
        return frame;
    }
}
