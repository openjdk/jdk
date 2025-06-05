/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4189244
 * @summary Swing Popup menu is not being refreshed (cleared) under a Dialog
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @requires (os.family == "windows")
 * @run main/manual bug4189244
*/

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

public class bug4189244 {

    private static final String INSTRUCTIONS = """
         This is Windows only test!

         Click right button on frame to show popup menu.
         (menu should be placed inside frame otherwise bug is not reproducible)
         click on any menu item (dialog will be shown).
         close dialog.
         if you see part of popupmenu, under dialog, before it is closed,
         then test failed, else passed.""";

    public static void main(String[] args) throws Exception {
         PassFailJFrame.builder()
                .title("bug4189244 Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int)INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(bug4189244::createTestUI)
                .build()
                .awaitAndCheck();
    }


    private static JFrame createTestUI() {
        RefreshBug panel = new RefreshBug();
        JFrame frame = new JFrame("Popup refresh bug");

        frame.add(panel, BorderLayout.CENTER);
        panel.init();
        frame.setSize(400, 400);
        return frame;
    }
}

class RefreshBug extends JPanel implements ActionListener {
    JPopupMenu _jPopupMenu = new JPopupMenu();

    public void init() {
        JMenuItem menuItem;
        JButton jb = new JButton("Bring the popup here and select an item");

        this.add(jb, BorderLayout.CENTER);

        for(int i = 1; i < 10; i++) {
            menuItem = new JMenuItem("Item " + i);
            menuItem.addActionListener(this);
            _jPopupMenu.add(menuItem);
        }

        MouseListener ml = new MouseAdapter() {
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                        _jPopupMenu.show(e.getComponent(),
                                         e.getX(), e.getY());
                }
            }
        };
        this.addMouseListener(ml);

        jb.addMouseListener(ml);

    }

    // An action is requested by the user
    public void actionPerformed(java.awt.event.ActionEvent e) {
        JOptionPane.showMessageDialog(this,
                                      "Check if there is some popup left under me\n"+
                                      "if not, retry and let the popup appear where i am",
                                      "WARNING",
                                      JOptionPane.WARNING_MESSAGE);

    }
}
