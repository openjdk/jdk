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
 * @bug 4305725
 * @requires (os.family == "windows")
 * @summary Tests if in Win LAF the JOptionPane.showInternalMessageDialog() is
 * not maximized to match background maximized internal frame.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4305725
 */

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JLayeredPane;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.UIManager;

public class bug4305725 implements ActionListener {
    private static JDesktopPane desktop ;

    public static void main(String[] args) throws Exception {
        try {
            UIManager.setLookAndFeel
                ("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        } catch (Exception e) {
            throw new RuntimeException("Failed to set Windows LAF");
        }

        String INSTRUCTIONS = """
            Maximize the internal frame, then call Exit from File menu.
            You will see a message box. If message box is also the size of
            internal frame, then test failed. If it is of usual size,
            then test is passed.
            """;
        PassFailJFrame.builder()
            .instructions(INSTRUCTIONS)
            .columns(50)
            .testUI(bug4305725::initialize)
            .build()
            .awaitAndCheck();
    }

    private static JFrame initialize() {
        JFrame frame = new JFrame("bug4305725");
        frame.add(desktop = new JDesktopPane());
        JMenuBar mb = new JMenuBar() ;
        JMenu menu = new JMenu("File");
        mb.add(menu) ;
        JMenuItem menuItem = menu.add(new JMenuItem("Exit"));
        menuItem.addActionListener(new bug4305725()) ;
        frame.setJMenuBar(mb) ;
        Dimension sDim = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setSize(sDim.width / 2, sDim.height / 2) ;
        JInternalFrame internalFrame = new JInternalFrame
            ("Internal", true, true, true, true);
        internalFrame.setSize(frame.getWidth(), frame.getHeight() / 2);
        internalFrame.setVisible(true);
        desktop.add(internalFrame, JLayeredPane.FRAME_CONTENT_LAYER);
        return frame;
    }

    @Override
    public void actionPerformed(ActionEvent aEvent) {
        JOptionPane.showInternalMessageDialog(desktop, "Exiting test app");
    }
}
