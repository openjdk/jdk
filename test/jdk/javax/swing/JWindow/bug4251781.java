/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4251781
 * @summary Tests that JWindow repaint is optimized (background is not
            cleared).
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4251781
 */

import java.awt.Color;
import java.awt.Container;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JWindow;

public class bug4251781 {
    private static final String INSTRUCTIONS = """
            Press the button at the bottom-right corner of the gray
            window with the mouse.
            If the window DOES NOT flicker when you press and/or release
            the mouse button press PASS else FAIL.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(bug4251781::createAndShowUI)
                .build()
                .awaitAndCheck();
    }

    private static JWindow createAndShowUI() {
        JWindow w = new JWindow();
        final Container pane = w.getContentPane();
        pane.setLayout(null);
        pane.setBackground(Color.GRAY.darker());

        final JPopupMenu popup = new JPopupMenu();
        popup.add(new JMenuItem("item 1"));
        popup.add(new JMenuItem("exit"));

        JButton b = new JButton("menu");
        b.setBounds(350, 250, 50, 50);
        b.addActionListener(ev -> popup.show(pane, 0, 0));
        pane.add(b);

        w.setSize(400, 300);
        return w;
    }
}
