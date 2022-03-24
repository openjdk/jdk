/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8000326
 * @key headful
 * @summary Focus unable to traverse within the menubar
 * @run main SetFocusTraversalKeysEnabledTest
 */
import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.ContainerOrderFocusTraversalPolicy;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class SetFocusTraversalKeysEnabledTest {

    private static volatile JFrame jFrame;
    private static volatile Component currentFocusOwner;

    private static void doTest()
        throws InvocationTargetException, InterruptedException, AWTException {
        try {
            SwingUtilities.invokeAndWait(() -> createGUI());
            Robot robot = new Robot();
            robot.setAutoDelay(500);
            Component lastFocusOwner = null;
            do {
                robot.waitForIdle();
                SwingUtilities.invokeAndWait(() -> currentFocusOwner = jFrame.getFocusOwner());

                System.out.println("Focus owner is : " + currentFocusOwner.getClass().getName());
                if (currentFocusOwner == lastFocusOwner) {
                    throw new RuntimeException(
                        "Problem moving focus from " + currentFocusOwner.getClass().getName());
                }
                lastFocusOwner = currentFocusOwner;
                robot.keyPress(KeyEvent.VK_TAB);
                robot.keyRelease(KeyEvent.VK_TAB);
            } while (currentFocusOwner != jFrame);
        } finally {
            SwingUtilities.invokeAndWait(() -> jFrame.dispose());
        }
    }

    private static void createGUI() {
        jFrame = new JFrame("Focus Traversal Test");
        jFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JMenuBar jMenuBar = new JMenuBar();
        jMenuBar.setFocusTraversalKeysEnabled(true);
        jMenuBar.add(new JMenu("First Menu").add(new JMenuItem("First MenuItem")));

        JButton northButton = new JButton("North Button");
        JButton southButton = new JButton("South Button");

        JPanel jPanel = new JPanel(new BorderLayout());
        jPanel.add(northButton);
        jPanel.add(jMenuBar, BorderLayout.NORTH);
        jPanel.add(southButton, BorderLayout.SOUTH);

        jFrame.getContentPane().add(jPanel);
        jFrame.setFocusTraversalPolicy(new ContainerOrderFocusTraversalPolicy());
        jFrame.pack();
        northButton.requestFocusInWindow();
        jFrame.setLocationRelativeTo(null);
        jFrame.setVisible(true);
    }

    public static void main(String[] args) throws Exception {
        doTest();
    }
}

