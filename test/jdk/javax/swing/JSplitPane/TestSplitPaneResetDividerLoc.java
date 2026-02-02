/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8356594
 * @key headful
 * @summary Verifies if JSplitPane loses divider location when
 *          reopened via JOptionPane.createDialog()
 * @run main TestSplitPaneResetDividerLoc
 */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

public class TestSplitPaneResetDividerLoc {

    private static JPanel lazyPanel;
    private static JSplitPane splitPane;
    private static JButton openDialogButton;
    private static JDialog dialog;
    private static JFrame frame;
    private static volatile Point point;
    private static volatile Rectangle size;
    private static volatile int setLoc;
    private static volatile int curLoc;

    private static boolean setLookAndFeel(UIManager.LookAndFeelInfo laf) {
        try {
            UIManager.setLookAndFeel(laf.getClassName());
            return true;
        } catch (UnsupportedLookAndFeelException ignored) {
            System.out.println("Unsupported LAF: " + laf.getClassName());
            return false;
        } catch (ClassNotFoundException | InstantiationException
                 | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        for (UIManager.LookAndFeelInfo laf : UIManager.getInstalledLookAndFeels()) {
            System.out.println("Testing LAF : " + laf.getClassName());
            try {
                if (!setLookAndFeel(laf)) {
                    continue;
                }
                SwingUtilities.invokeAndWait(TestSplitPaneResetDividerLoc::createAndShowUI);

                Robot robot = new Robot();
                robot.waitForIdle();
                robot.delay(1000);
                SwingUtilities.invokeAndWait(() -> {
                    point = openDialogButton.getLocationOnScreen();
                    size = openDialogButton.getBounds();
                });
                robot.mouseMove(point.x + size.width / 2, point.y + size.height / 2);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                robot.waitForIdle();
                robot.delay(1000);

                SwingUtilities.invokeAndWait(() -> {
                    int divLoc = splitPane.getDividerLocation();
                    splitPane.setDividerLocation(divLoc + 200);
                });
                robot.waitForIdle();
                robot.delay(1000);
                robot.keyPress(KeyEvent.VK_ESCAPE);
                robot.keyRelease(KeyEvent.VK_ESCAPE);

                SwingUtilities.invokeAndWait(() -> {
                    setLoc = splitPane.getDividerLocation();
                    System.out.println(setLoc);
                });

                robot.waitForIdle();
                robot.delay(1000);
                robot.mouseMove(point.x + size.width / 2, point.y + size.height / 2);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                robot.waitForIdle();
                robot.delay(1000);

                SwingUtilities.invokeAndWait(() -> {
                    curLoc = splitPane.getDividerLocation();
                    System.out.println(curLoc);
                });

                robot.keyPress(KeyEvent.VK_ESCAPE);
                robot.keyRelease(KeyEvent.VK_ESCAPE);

                if (curLoc != setLoc) {
                    throw new RuntimeException("Divider location is not preserved");
                }
            } finally {
                SwingUtilities.invokeAndWait(() -> {
                    if (frame != null) {
                        frame.dispose();
                    }
                    lazyPanel = null;
                });
            }
        }
    }

    private static void createAndShowUI() {
        frame = new JFrame("JSplitPane Divider Location");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

         openDialogButton = new JButton(new AbstractAction("Open Dialog") {
            public void actionPerformed(ActionEvent e) {
                openDialogFromOptionPane(frame);
            }
        });

        frame.getContentPane().add(openDialogButton, BorderLayout.CENTER);
        frame.setSize(400, 100);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void openDialogFromOptionPane(JFrame parent) {
        if (lazyPanel == null) {
            System.out.println("Creating lazy panel...");
            lazyPanel = new JPanel(new BorderLayout());

            JPanel left = new JPanel();
            left.setBackground(Color.ORANGE);
            JPanel right = new JPanel();
            right.setBackground(Color.LIGHT_GRAY);

            splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
            splitPane.setPreferredSize(new Dimension(400, 200));

            // Set initial divider location — not preserved across dialog openings in OpenJDK 24
            splitPane.setDividerLocation(120);

            lazyPanel.add(splitPane, BorderLayout.CENTER);
        }

        JOptionPane optionPane = new JOptionPane(lazyPanel, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION);
        dialog = optionPane.createDialog(parent, "SplitPane Dialog (JOptionPane)");
        dialog.setModal(false);
        dialog.setVisible(true);
    }
}
