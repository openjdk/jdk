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

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

/*
 * @test
 * @bug 8315655
 * @requires (os.family == "mac")
 * @key headful
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual MouseDragPopupTest
 */
public class MouseDragPopupTest {
    static JFrame frame;
    static JPanel panel;
    static JPanel innerPanel;
    static JPopupMenu menu;

    public static void main(String[] args) throws Exception {
        String instructions = """
                1) Press and hold the right mouse button down on the JLabel with the text
                "Right click and drag from here"
                
                2) Move the mouse to the JLabel with the text "to here"
                
                3) Observe that the popup menu assigned to the inner JPanel appears
                (macOS) or does not appear (Windows, Linux)
                """;

        PassFailJFrame.builder()
                .title("FocusablePopupDismissTest")
                .instructions(instructions)
                .rows(10)
                .columns(45)
                .testUI(MouseDragPopupTest::createAndShowGUI)
                .build()
                .awaitAndCheck();
    }

    static JFrame createAndShowGUI() {
        frame = new JFrame("MouseDragPopupTest");
        panel = new JPanel();
        innerPanel = new JPanel();
        menu = new JPopupMenu();

        menu.add("This should not appear (and does not under Linux/Windows)");
        innerPanel.setComponentPopupMenu(menu);

        panel.add(new JLabel("Right click and drag from here"));
        panel.add(innerPanel);
        panel.add(new JLabel("to here"));

        frame.add(panel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();

        return frame;
    }
}