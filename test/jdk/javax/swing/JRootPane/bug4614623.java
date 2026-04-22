/*
 * Copyright (c) 2002, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4614623
 * @requires (os.family == "windows")
 * @summary Tests that w2k mnemonic underlining works when there's no
            focus owner
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4614623
 */

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.UIManager;

public class bug4614623 {
    private static final String INSTRUCTIONS = """
            This test verifies if the short-cut character
            (menu mnemonic) is underlined when the ALT key is held down.

            Check if the following is true.
            1) Press Alt key. The letter 'F' (menu mnemonic) of
            the "File" menu should now be underlined.
            2) Release the Alt key, the selection background (light grey)
            should appear around the "File" menu. Compare "About" menu
            with "File" menu to see the light grey selection background.

            If the above is true, press PASS else FAIL.
            """;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");

        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(62)
                .rows(12)
                .testUI(bug4614623::createAndShowUI)
                .build()
                .awaitAndCheck();
    }

    public static JFrame createAndShowUI() {
        JFrame frame = new JFrame("bug4614623 - File menu test");
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic('F');
        menuBar.add(fileMenu);

        JMenu about = new JMenu("About");
        menuBar.add(about);
        menuBar.setSize(300, 100);

        frame.setJMenuBar(menuBar);
        menuBar.requestFocus();
        frame.setSize(300, 200);
        return frame;
    }
}
