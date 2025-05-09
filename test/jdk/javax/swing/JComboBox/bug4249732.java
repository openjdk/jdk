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

import java.awt.BorderLayout;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.UIManager;

/*
 * @test
 * @bug 4249732
 * @requires (os.family == "windows")
 * @summary Tests that Windows editable combo box selects text picked from its list
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4249732
 */

public class bug4249732 {
    private static final String INSTRUCTIONS = """
            Click on combo box arrow button to open its dropdown list, and
            select an item from there. The text in the combo box editor should
            be selected, otherwise test fails.
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
                .columns(40)
                .testUI(bug4249732::createTestUI)
                .build()
                .awaitAndCheck();
    }

    public static JFrame createTestUI() {
        JFrame frame = new JFrame("bug4249732");

        JComboBox cb = new JComboBox(new Object[]{"foo", "bar", "baz"});
        cb.setEditable(true);

        frame.add(cb, BorderLayout.NORTH);
        frame.pack();
        return frame;
    }
}
