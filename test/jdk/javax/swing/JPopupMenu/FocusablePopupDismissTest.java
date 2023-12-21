/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
  * @bug 8319103
  * @requires (os.family == "linux")
  * @library /java/awt/regtesthelpers
  * @build PassFailJFrame
  * @summary Tests if the focusable popup can be dismissed when the parent
  *          window or the popup itself loses focus in Wayland.
  * @run main/manual FocusablePopupDismissTest
  */

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import java.awt.Window;
import java.util.List;

public class FocusablePopupDismissTest {
    private static final String INSTRUCTIONS = """
            A frame with a "Click me" button should appear next to the window
            with this instruction.

            Click on the "Click me" button.

            If the JTextField popup with "Some text" is not showing on the screen,
            click Fail.

            The following steps require some focusable system window to be displayed
            on the screen. This could be a system settings window, file manager, etc.

            Click on the "Click me" button if the popup is not displayed
            on the screen.

            While the popup is displayed, click on some other window on the desktop.
            If the popup has disappeared, click Pass, otherwise click Fail.
            """;

    public static void main(String[] args) throws Exception {
        if (System.getenv("WAYLAND_DISPLAY") == null) {
            //test is valid only when running on Wayland.
            return;
        }

        PassFailJFrame.builder()
                .title("FocusablePopupDismissTest")
                .instructions(INSTRUCTIONS)
                .rows(20)
                .columns(45)
                .testUI(FocusablePopupDismissTest::createTestUI)
                .build()
                .awaitAndCheck();
    }

    static List<Window> createTestUI() {
        JFrame frame = new JFrame("FocusablePopupDismissTest");
        JButton button = new JButton("Click me");
        frame.add(button);

        button.addActionListener(e -> {
            JPopupMenu popupMenu = new JPopupMenu();
            JTextField textField = new JTextField("Some text", 10);
            popupMenu.add(textField);
            popupMenu.show(button, 0, button.getHeight());
        });
        frame.pack();

        return List.of(frame);
    }
}
