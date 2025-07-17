/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/*
 * @test
 * @bug 4159745
 * @key headful
 * @summary Mediumweight popup dragging broken
 * @run main MouseEnteredTest
 */

public class MouseEnteredTest extends JFrame implements ActionListener {
    static volatile MouseEnteredTest test;
    static volatile Point p;
    static volatile Point p2;

    static String strMotif = "Motif";
    static String motifClassName = "com.sun.java.swing.plaf.motif.MotifLookAndFeel";
    static char cMotif = 'o';

    static String strWindows = "Windows";
    static String windowsClassName = "com.sun.java.swing.plaf.windows.WindowsLookAndFeel";
    static char cWindows = 'W';

    static String strMetal = "Metal";
    static String metalClassName = "javax.swing.plaf.metal.MetalLookAndFeel";
    static char cMetal = 'M';

    static JMenu m;
    static JMenu menu;

    static MouseListener ml = new MouseEnteredTest.MouseEventListener();

    public MouseEnteredTest() {
        setTitle("MouseEnteredTest");
        JPopupMenu.setDefaultLightWeightPopupEnabled(false);
        setJMenuBar(getMyMenuBar());
        getContentPane().add("Center", new JTextArea());
        setSize(400, 500);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public static void main(String[] args) throws Exception {
        try {
            EventQueue.invokeAndWait(() -> {
                test = new MouseEnteredTest();
            });

            Robot robot = new Robot();
            robot.setAutoWaitForIdle(true);
            robot.waitForIdle();
            robot.delay(1000);

            EventQueue.invokeAndWait(() -> {
                p = m.getLocationOnScreen();
                p2 = menu.getLocationOnScreen();
            });
            robot.waitForIdle();
            robot.delay(250);
            robot.mouseMove(p.x + 5, p.y + 10);
            robot.waitForIdle();

            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            for (int i = p.x; i < p2.x + 10; i = i + 2) {
                robot.mouseMove(i, p2.y + 10);
            }
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(2000);

            if (m.isPopupMenuVisible()) {
                throw new RuntimeException("First menu is showing. Test Failed.");
            }
        } finally {
            if (test != null) {
                EventQueue.invokeAndWait(test::dispose);
            }
        }
    }

    public JMenuBar getMyMenuBar() {
        JMenuBar menubar;
        JMenuItem menuItem;

        menubar = GetLNFMenuBar();

        menu = menubar.add(new JMenu("Test"));
        menu.setName("Test");
        menu.addMouseListener(ml);
        menu.setMnemonic('T');
        menuItem = menu.add(new JMenuItem("Menu Item"));
        menuItem.addActionListener(this);
        menuItem.setMnemonic('M');
        menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M, ActionEvent.ALT_MASK));

        JRadioButtonMenuItem mi = new JRadioButtonMenuItem("Radio Button");
        mi.addActionListener(this);
        mi.setMnemonic('R');
        mi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, ActionEvent.ALT_MASK));
        menu.add(mi);

        JCheckBoxMenuItem mi1 = new JCheckBoxMenuItem("Check Box");
        mi1.addActionListener(this);
        mi1.setMnemonic('C');
        mi1.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.ALT_MASK));
        menu.add(mi1);
        return menubar;
    }

    public void actionPerformed(ActionEvent e) {
        String str = e.getActionCommand();
        if (str.equals(metalClassName) || str.equals(windowsClassName) || str.equals(motifClassName)) {
            changeLNF(str);
        } else {
            System.out.println("ActionEvent: " + str);
        }
    }

    public void changeLNF(String str) {
        System.out.println("Changing LNF to " + str);
        try {
            UIManager.setLookAndFeel(str);
            SwingUtilities.updateComponentTreeUI(this);
            pack();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public JMenuBar GetLNFMenuBar() {
        JMenuBar mbar = new JMenuBar();
        m = new JMenu("Look and Feel");
        m.setName("Look and Feel");
        m.addMouseListener(ml);
        m.setMnemonic('L');
        ButtonGroup bg = new ButtonGroup();

        JRadioButtonMenuItem mi;

        mi = new JRadioButtonMenuItem(strMetal);
        mi.addActionListener(this);
        mi.setActionCommand(metalClassName);
        mi.setMnemonic(cMetal);
        mi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_1, ActionEvent.ALT_MASK));
        mi.setSelected(true);
        bg.add(mi);
        m.add(mi);

        mi = new JRadioButtonMenuItem(strWindows);
        mi.addActionListener(this);
        mi.setActionCommand(windowsClassName);
        mi.setMnemonic(cWindows);
        mi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_2, ActionEvent.ALT_MASK));
        bg.add(mi);
        m.add(mi);

        mi = new JRadioButtonMenuItem(strMotif);
        mi.addActionListener(this);
        mi.setActionCommand(motifClassName);
        mi.setMnemonic(cMotif);
        mi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_3, ActionEvent.ALT_MASK));
        bg.add(mi);
        m.add(mi);

        mbar.add(m);
        return mbar;
    }

    static class MouseEventListener implements MouseListener, MouseMotionListener {
        public void mouseClicked(MouseEvent e) {
            System.out.println("In mouseClicked for " + e.getComponent().getName());
        }

        public void mousePressed(MouseEvent e) {
            Component c = e.getComponent();
            System.out.println("In mousePressed for " + c.getName());
        }

        public void mouseReleased(MouseEvent e) {
            System.out.println("In mouseReleased for " + e.getComponent().getName());
        }

        public void mouseEntered(MouseEvent e) {
            System.out.println("In mouseEntered for " + e.getComponent().getName());
            System.out.println("MouseEvent:" + e.getComponent());
        }

        public void mouseExited(MouseEvent e) {
            System.out.println("In mouseExited for " + e.getComponent().getName());
        }

        public void mouseDragged(MouseEvent e) {
            System.out.println("In mouseDragged for " + e.getComponent().getName());
        }

        public void mouseMoved(MouseEvent e) {
            System.out.println("In mouseMoved for " + e.getComponent().getName());
        }
    }
}
