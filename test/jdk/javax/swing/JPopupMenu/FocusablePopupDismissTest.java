/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
  * @key headful
  * @bug 8319103 8342096
  * @requires (os.family == "linux")
  * @library /java/awt/regtesthelpers /test/lib
  * @build PassFailJFrame jtreg.SkippedException
  * @summary Tests if the focusable popup can be dismissed when the parent
  *          window or the popup itself loses focus in Wayland.
  * @run main/manual FocusablePopupDismissTest
  */

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import java.awt.Window;
import java.util.List;

import jtreg.SkippedException;

public class FocusablePopupDismissTest {
    private static final String INSTRUCTIONS = """
            A frame with a "Click me" button should appear next to the window
            with this instruction.

            Click on the "Click me" button.

            A menu should appear next to the window. If you move the cursor over
            the first menu, the JTextField popup should appear on the screen.
            If it doesn't, click Fail.

            The following steps require some focusable system window to be displayed
            on the screen. This could be a system settings window, file manager, etc.

            Click on the "Click me" button if the popup is not displayed
            on the screen, move the mouse pointer over the menu.

            While the popup is displayed, click on some other window on the desktop.
            If the popup does not disappear, click Fail.

            Open the menu again, move the mouse cursor over the following:
            "Focusable 1" -> "Focusable 2" -> "Editor Focusable 2"
            Move the mouse to the focusable system window
            (keeping the "Editor Focusable 2" JTextField open) and click on it.

            If the popup does not disappear, click Fail, otherwise click Pass.
            """;

    public static void main(String[] args) throws Exception {
        if (System.getenv("WAYLAND_DISPLAY") == null) {
            throw new SkippedException("XWayland only test");
        }

        PassFailJFrame.builder()
                .title("FocusablePopupDismissTest")
                .instructions(INSTRUCTIONS)
                .columns(45)
                .testUI(FocusablePopupDismissTest::createTestUI)
                .build()
                .awaitAndCheck();
    }

    static JMenu getMenuWithMenuItem(boolean isSubmenuItemFocusable, String text) {
        JMenu menu = new JMenu(text);
        menu.add(isSubmenuItemFocusable
                ? new JTextField("Editor " + text, 11)
                : new JMenuItem("Menu item" + text)
        );
        return menu;
    }

    static List<Window> createTestUI() {
        JFrame frame = new JFrame("FocusablePopupDismissTest");
        JButton button = new JButton("Click me");

        JPanel wrapper = new JPanel();
        wrapper.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        wrapper.add(button);

        frame.add(wrapper);

        button.addActionListener(e -> {
            JPopupMenu popupMenu = new JPopupMenu();

            JMenu menu1 = new JMenu("Menu 1");
            menu1.add(new JTextField("Some text", 10));
            JMenu menu2 = new JMenu("Menu 2");
            menu2.add(new JTextField("Some text", 10));

            popupMenu.add(getMenuWithMenuItem(true, "Focusable 1"));
            popupMenu.add(getMenuWithMenuItem(true, "Focusable 2"));
            popupMenu.add(getMenuWithMenuItem(false, "Non-Focusable 1"));
            popupMenu.add(getMenuWithMenuItem(false, "Non-Focusable 2"));
            popupMenu.show(button, 0, button.getHeight());
        });
        frame.pack();

        return List.of(frame);
    }
}
