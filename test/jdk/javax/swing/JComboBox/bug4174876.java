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
 * @bug 4174876
 * @summary JComboBox tooltips do not work properly
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4174876
 */

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;

public class bug4174876 {
    private static final String INSTRUCTIONS = """
            Hold the mouse over both combo boxes.
            A tool tip should appear over every area of both of them.
            Notably, if you hold the mouse over the button on the right one,
            a tool tip should appear.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("TransparentTitleTest Instructions")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .splitUIBottom(bug4174876::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JComponent createTestUI() {
        JComboBox<String> comboBox1 = new JComboBox<>(new String[]{
                "Coma Berenices",
                "Triangulum",
                "Camelopardis",
                "Cassiopea"
        });
        JComboBox<String> comboBox2 = new JComboBox<>(new String[]{
                "Coma Berenices",
                "Triangulum",
                "Camelopardis",
                "Cassiopea"
        });

        comboBox1.setToolTipText("Combo Box #1");
        comboBox2.setToolTipText("Combo Box #2");
        comboBox2.setEditable(true);

        JPanel panel = new JPanel();
        panel.add(comboBox1);
        panel.add(comboBox2);
        return panel;
    }
}
