/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8374506
 * @summary Verify if arrow icon positioning is correct in
 *          parent JMenu in Windows L&F
 * @requires (os.family == "windows")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual LargeMenuTextArrowIconPosition
 */

import java.awt.BorderLayout;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.UIManager;

public class LargeMenuTextArrowIconPosition {

    private static final String INSTRUCTIONS = """
        A frame will be shown with a label.
        Right click on the label.

        Check the arrow icon at the end of
        "Really long Menu-Text" text.
        If it overlaps with the menu text,
        press Fail else press Pass.""";

    public static void main(String[] args) throws Throwable {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

         PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(LargeMenuTextArrowIconPosition::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createTestUI() {

        JFrame frame = new JFrame("LargeMenuTextArrowIcon");
        frame.setSize(300, 150);
        frame.setLayout(new BorderLayout());

        JPopupMenu popupMenu = new JPopupMenu();
        popupMenu.add(new JCheckBoxMenuItem("CheckBox On", true));
        popupMenu.add(new JCheckBoxMenuItem("CheckBox Icon On",
                          UIManager.getIcon("FileView.floppyDriveIcon"), true));
        popupMenu.add(new JCheckBoxMenuItem("CheckBox Icon Off",
                          UIManager.getIcon("FileView.floppyDriveIcon"), false));

        JMenu menu = new JMenu("Really long Menu-Text");
        menu.add(new JMenuItem("Sub-MenuItem"));
        menu.add(new JCheckBoxMenuItem("Sub-CheckBox On", true));

        popupMenu.add(menu);

        JLabel lbl = new JLabel("Right click to invoke popupMenu");
        lbl.setComponentPopupMenu(popupMenu);
        frame.add(lbl, BorderLayout.CENTER);

        return frame;
    }

}
