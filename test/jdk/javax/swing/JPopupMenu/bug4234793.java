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

/* @test
 * @bug 4234793
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary PopupMenuListener popupMenuCanceled is never called
 * @run main/manual bug4234793
 */

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

/**
 * For all 3 components (JPopupMenu, JComboBox, JPopup) when the popup is visible,
 * the popupMenuCanceled should be invoked in these two circumstances:
 *
 * 1. The ESCAPE key is pressed while the popup is open.
 *
 * 2. The mouse is clicked on another component.
 *
 */

public class bug4234793 extends JFrame implements PopupMenuListener {

    static final String INSTRUCTIONS = """
        The test window will contain several kinds of menus.

        * A menu bar with two menus labeled "1 - First Menu" and "2 - Second Menu"
        * A drop down combo box - ie a button which pops up a menu when clicked
        * Clicking any where on the background of the window will display a popup menu

        That is 4 menus in total.

        For each case, verify that the menu can be cancelled (hidden) in two ways
        1) Click to display the menu, then to hide it, press the ESCAPE key.
        2) Click to display the menu, then to hide it, LEFT click on the window background.
        Note : the popup menu must be displayed using RIGHT click, the others use LEFT click.

        Notice each time you perform a hide/cancel action an appropriate message should
        appear in the log area
        If this is true for all 8 combinations of menus + hide actions the test PASSES
    """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
            .instructions(INSTRUCTIONS)
            .columns(60)
            .testUI(bug4234793::createUI)
            .logArea()
            .build()
            .awaitAndCheck();
    }

    private static String[] numData = {
        "One", "Two", "Three", "Four", "Five", "Six", "Seven"
    };

    private static String[] dayData = {
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
    };

    private static char[] mnDayData = {
        'M', 'T', 'W', 'R', 'F', 'S', 'U'
    };

    bug4234793(String title) {
        super(title);
    }

    static volatile JPopupMenu popupMenu;
    static volatile bug4234793 frame;

    static JFrame createUI() {
        frame = new bug4234793("bug4234793");
        frame.setJMenuBar(createMenuBar());
        JPanel panel = createContentPane();
        frame.add(panel);

        // CTRL-down will show the popup.
        panel.getInputMap().put(KeyStroke.getKeyStroke(
                   KeyEvent.VK_DOWN, InputEvent.CTRL_MASK), "OPEN_POPUP");
        panel.getActionMap().put("OPEN_POPUP", new PopupHandler());
        panel.addMouseListener(new PopupListener(popupMenu));
        panel.setPreferredSize(new Dimension(400, 300));
        frame.setSize(400, 300);
        return frame;
    }

    static class PopupListener extends MouseAdapter {
        private JPopupMenu popup;

        public PopupListener(JPopupMenu popup) {
            this.popup = popup;
        }

        public void mousePressed(MouseEvent e) {
            maybeShowPopup(e);
        }

        public void mouseReleased(MouseEvent e) {
            maybeShowPopup(e);
        }

        public void mouseClicked(MouseEvent ex) {
        }

        private void maybeShowPopup(MouseEvent e) {
            if (e.isPopupTrigger()) {
                popup.show(e.getComponent(), e.getX(), e.getY());
            }
        }
    }

    static class PopupHandler extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (!popupMenu.isVisible())
                popupMenu.show((Component)e.getSource(), 40, 40);
        }
    }

    static JPanel createContentPane() {
        popupMenu = new JPopupMenu();
        JMenuItem item;
        for (int i = 0; i < dayData.length; i++) {
            item = popupMenu.add(new JMenuItem(dayData[i], mnDayData[i]));
        }
        popupMenu.addPopupMenuListener(frame);

        JComboBox combo = new JComboBox(numData);
        combo.addPopupMenuListener(frame);
        JPanel comboPanel = new JPanel();
        comboPanel.add(combo);

        JPanel panel = new JPanel(new BorderLayout());

        panel.add(new JLabel("Right click on the panel to show the PopupMenu"), BorderLayout.NORTH);
        panel.add(comboPanel, BorderLayout.CENTER);

        return panel;
    }

    static JMenuBar createMenuBar() {
        JMenuBar menubar = new JMenuBar();
        JMenuItem menuitem;

        JMenu menu = new JMenu("1 - First Menu");
        menu.setMnemonic('1');
        menu.getPopupMenu().addPopupMenuListener(frame);

        menubar.add(menu);
        for (int i = 0; i < 10; i ++) {
            menuitem = new JMenuItem("1 JMenuItem" + i);
            menuitem.setMnemonic('0' + i);
            menu.add(menuitem);
        }

        // second menu
        menu = new JMenu("2 - Second Menu");
        menu.getPopupMenu().addPopupMenuListener(frame);
        menu.setMnemonic('2');

        menubar.add(menu);
        for (int i = 0; i < 5; i++) {
            menuitem = new JMenuItem("2 JMenuItem" + i);
            menuitem.setMnemonic('0' + i);
            menu.add(menuitem);
        }

        JMenu submenu = new JMenu("Sub Menu");
        submenu.setMnemonic('S');
        submenu.getPopupMenu().addPopupMenuListener(frame);
        for (int i = 0; i < 5; i++) {
            menuitem = new JMenuItem("S JMenuItem" + i);
            menuitem.setMnemonic('0' + i);
            submenu.add(menuitem);
        }
        menu.add(new JSeparator());
        menu.add(submenu);

        return menubar;
    }

    // PopupMenuListener methods.

    public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
        Object source = e.getSource();
        PassFailJFrame.log("popupmenu visible: " + source.getClass().getName());
    }

    public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
        Object source = e.getSource();
        PassFailJFrame.log("popupMenuWillBecomeInvisible: " + source.getClass().getName());
    }

    public void popupMenuCanceled(PopupMenuEvent e) {
        Object source = e.getSource();
        PassFailJFrame.log("POPUPMENU CANCELED: " + source.getClass().getName());
    }
}
