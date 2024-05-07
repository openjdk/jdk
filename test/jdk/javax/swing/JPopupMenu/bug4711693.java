/*
 * Copyright (c) 2002, 2023, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Robot;
import java.awt.Window;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.InputEvent;

/*
 * @test
 * @bug 4711693
 * @summary Pop-up doesn't stay up
 * @key headful
 * @run main bug4711693
 */

public class bug4711693 {
    static JFrame fr;
    static Robot robot;
    static volatile boolean passed = true;
    static volatile Dimension scr;

    public static void main(String[] args) throws Exception {
        try {
            robot = new Robot();
            SwingUtilities.invokeAndWait(() -> {
                fr = new JFrame("Test 4711693");
                scr = new Dimension();
                fr.setSize(600, 600);
                GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
                GraphicsDevice[] gs = ge.getScreenDevices();
                GraphicsConfiguration gc = null;

                for (int j = 0; j < gs.length; j++) {
                    GraphicsDevice gd = gs[j];
                    gc = gd.getDefaultConfiguration();
                    if (gc.getBounds().contains(100, 100)) break;
                }
                scr = Toolkit.getDefaultToolkit().getScreenSize();
                Insets ins = Toolkit.getDefaultToolkit().getScreenInsets(gc);
                scr.width -= ins.right;
                scr.height -= ins.bottom;
                fr.setLocation(scr.width - 400, scr.height - 400);
                fr.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);
            SwingUtilities.invokeAndWait(() -> {
                final JPopupMenu popupMenu = new JPopupMenu();
                final Component pane = fr.getContentPane();
                for (int i = 1; i < 10; i++) {
                    final String itemName = "Item " + i;
                    JMenuItem it = popupMenu.add(new JMenuItem(itemName));
                    it.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent a) {
                            passed = false;
                        }
                    });
                }

                pane.addMouseListener(new MouseAdapter() {
                    public void mousePressed(MouseEvent e) {
                        if ((e.isAltDown() ||
                                ((e.getModifiersEx() &
                                        InputEvent.BUTTON3_DOWN_MASK) != 0))) {
                            Component parent = e.getComponent();
                            while (parent != null && !(parent instanceof Window)) {
                                parent = parent.getParent();
                            }
                            popupMenu.show(pane, e.getX(), e.getY());
                        }
                    }
                });
            });

            robot.mouseMove(scr.width - 55, scr.height - 55);
            robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (fr != null) {
                    fr.dispose();
                }
            });
        }
        if (!passed) {
            throw new RuntimeException("Test failed. Popup disposed on mouse release.");
        } else {
            System.out.println("Test Passed!");
        }
    }
}
