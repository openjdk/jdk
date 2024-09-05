/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import javax.swing.UIManager;

/*
 * @test
 * @bug 6672644
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Tests JComboBox scrollbar behavior when alt-tabbing
 * @run main/manual JComboBoxScrollFocusTest
 */

public class JComboBoxScrollFocusTest {
    private static final String INSTRUCTIONS =
            """
             Click on the dropdown button for the JComboBox in the test frame.
             Then, press and hold left click on the down arrow button in the
             popup list. While holding left click, the list should be scrolling
             down. Press ALT + TAB while holding down left click to switch
             focus to a different window. Focus the test frame again and click
             the dropdown button for the JComboBox again. The list should not
             be automatically scrolling.

             If you are able to execute all steps successfully then the test
             passes, otherwise it fails.
            """;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
        PassFailJFrame
                .builder()
                .title("JComboBoxScrollFocusTest Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(40)
                .testUI(JComboBoxScrollFocusTest::createAndShowGUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createAndShowGUI() {
        JFrame frame = new JFrame("JComboBoxScrollFocusTest Test Frame");
        JComboBox combobox = new JComboBox();
        for (int i = 0; i < 100; i ++) {
            combobox.addItem(String.valueOf(i));
        }
        frame.add(combobox);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(400, 200);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        return frame;
    }
}
