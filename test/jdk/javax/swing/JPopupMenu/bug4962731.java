/*
 * Copyright (c) 2004, 2023, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.PopupMenuUI;
import java.awt.BorderLayout;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/*
 * @test
 * @bug 4962731
 * @summary The PopupMenu is not repainted if the LAF is changed.
 * @key headful
 * @run main bug4962731
 */

public class bug4962731 {

    public static volatile boolean passed = false;
    public static boolean isLafOk = true;
    public static JFrame mainFrame;
    public static JButton button;
    public static MyPopup popup;
    public static Robot robot;

    public static void main(String[] args) throws Exception {

        try {
            UIManager.setLookAndFeel("com.sun.java.swing.plaf.motif.MotifLookAndFeel");
        } catch (Exception ex) {
            System.err.println("Can not initialize Motif L&F. Testing skipped.");
            isLafOk = false;
        }

        try {
            UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
        } catch (Exception ex) {
            System.err.println("Can not initialize Metal L&F. Testing skipped.");
            isLafOk = false;
        }

        if (isLafOk) {
            try {
                robot = new Robot();
                SwingUtilities.invokeAndWait(() -> {
                    mainFrame = new JFrame("Bug4962731");
                    button = new JButton("Popup!");
                    popup = new MyPopup();
                    popup.add("one");
                    popup.add("two");
                    button.setComponentPopupMenu(popup);
                    button.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            popup.show(button, 300, 300);
                            popup.engage();
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e1) {
                            }
                            try {
                                UIManager.setLookAndFeel
                                        ("com.sun.java.swing.plaf.motif.MotifLookAndFeel");
                            } catch (Exception ex) {
                            }
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e1) {
                            }
                            SwingUtilities.updateComponentTreeUI(mainFrame);
                            passed = popup.check();
                        }
                    });
                    mainFrame.setLayout(new BorderLayout());
                    mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                    mainFrame.add(button, BorderLayout.CENTER);
                    mainFrame.pack();
                    mainFrame.setVisible(true);
                });

                robot.delay(1000);
                SwingUtilities.invokeAndWait(() -> {
                    button.doClick();
                });

                if (!passed) {
                    throw new RuntimeException("The UI of popup was not changed");
                }
            } finally {
                SwingUtilities.invokeAndWait(() -> {
                    if (mainFrame != null) {
                        mainFrame.dispose();
                    }
                });
            }
        }
        System.out.println("test Passed!");
    }

    public static class MyPopup extends JPopupMenu {
        PopupMenuUI thisUI;

        public void engage() {
            thisUI = getUI();
        }

        public boolean check() {
            return getUI() != thisUI;
        }
    }
}
