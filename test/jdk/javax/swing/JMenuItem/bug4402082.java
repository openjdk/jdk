/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4402082
 * @requires (os.family == "windows")
 * @summary Tests that JMenuItem accelerator is rendered correctly.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4402082
 */

import java.awt.Container;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.swing.UIManager;

public class bug4402082 {

    private static final String INSTRUCTIONS = """
        You see three menu items, each having different look-and-feels.
        Each menu item should display accelerator "F1" on its right side.
        The accelerator should be fully visible. If it is partially
        offscreen, or not visible at all, test fails.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("bug4402082 Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(bug4402082::createTestUI)
                .build()
                .awaitAndCheck();
    }

    static JMenuItem getMenuItem(String lnf) {
        try {
            UIManager.setLookAndFeel(lnf);
        } catch (Exception exc) {
            System.err.println("Could not load LookAndFeel: " + lnf);
        }
        JMenuItem mi = new JMenuItem("Bad Item");
        mi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0));
        return mi;
    }

    private static JFrame createTestUI() {
        JFrame frame = new JFrame("bug4402082");
        Container pane = frame.getContentPane();
        pane.setLayout(new GridLayout(3,1));
        pane.add(getMenuItem("javax.swing.plaf.metal.MetalLookAndFeel"));
        pane.add(getMenuItem("com.sun.java.swing.plaf.motif.MotifLookAndFeel"));
        pane.add(getMenuItem("com.sun.java.swing.plaf.windows.WindowsLookAndFeel"));
        frame.pack();
        return frame;
    }
}
