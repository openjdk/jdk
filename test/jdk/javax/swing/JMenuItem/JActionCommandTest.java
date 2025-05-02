/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.BorderLayout;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/*
 * @test
 * @bug 4159610
 * @key headful
 * @summary Verifies that JMenuItem's shortcuts are not inserted in JTextField
 * @run main JActionCommandTest
 */

public class JActionCommandTest {

    private static Robot robot;
    private static JMenu m;
    private static JMenuItem mi;
    private static JFrame f;
    private static JTextField tf;
    private static volatile Point menuLoc;
    private static volatile Point menuItemLoc;
    private static volatile Point textFieldLoc;
    private static volatile int menuWidth;
    private static volatile int menuHeight;
    private static volatile int menuItemWidth;
    private static volatile int menuItemHeight;
    private static volatile int textFieldWidth;
    private static volatile int textFieldHeight;
    private static volatile boolean passed = false;

    public static void main(String[] args) throws Exception {
        robot = new Robot();
        robot.setAutoDelay(50);
        robot.setAutoWaitForIdle(true);
        try {
            SwingUtilities.invokeAndWait(JActionCommandTest::createAndShowUI);
            robot.waitForIdle();
            robot.delay(1000);
            SwingUtilities.invokeAndWait(() -> {
                menuLoc = m.getLocationOnScreen();
                menuWidth = m.getWidth();
                menuHeight = m.getHeight();

                textFieldLoc = tf.getLocationOnScreen();
                textFieldWidth = tf.getWidth();
                textFieldHeight = tf.getHeight();
            });
            moveAndPressMouse(menuLoc.x, menuLoc.y, menuWidth, menuHeight);

            SwingUtilities.invokeAndWait(() -> {
                menuItemLoc = mi.getLocationOnScreen();
                menuItemWidth = mi.getWidth();
                menuItemHeight = mi.getHeight();
            });
            moveAndPressMouse(menuItemLoc.x, menuItemLoc.y, menuItemWidth, menuItemHeight);
            System.out.println("passed is: "+passed);
            if (!passed) {
                throw new RuntimeException("Test Failed: JMenuItem label is not" +
                        " equals to 'Testitem'.");
            }
            passed = false;
            moveAndPressMouse(textFieldLoc.x, textFieldLoc.y, textFieldWidth, textFieldHeight);
            robot.keyPress(KeyEvent.VK_ALT);
            robot.keyPress(KeyEvent.VK_T);
            robot.keyRelease(KeyEvent.VK_T);
            robot.keyRelease(KeyEvent.VK_ALT);
            robot.waitForIdle();

            System.out.println("passed is: "+passed);
            System.out.println("tf.getText() is: "+tf.getText());
            if (!passed && tf.getText().equals("t")) {
                throw new RuntimeException("Test Failed: Either JMenuItem label is not" +
                        " equal to 'Testitem' or JTextField contains text 't'. ");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (f != null) {
                    f.dispose();
                }
            });
        }
    }

    private static void createAndShowUI() {
        CustomActionListener customListener = new CustomActionListener();
        f = new JFrame("Test JMenuItem Shortcut");
        f.setLayout(new BorderLayout());
        tf = new JTextField(12);
        tf.addActionListener(customListener);
        JMenuBar mb = new JMenuBar();
        m = new JMenu("Test");
        mi = new JMenuItem("Testitem");
        KeyStroke ks = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_T,
                java.awt.Event.ALT_MASK, false);
        mi.setAccelerator(ks);
        mi.addActionListener(customListener);
        m.add(mi);
        mb.add(m);
        f.setJMenuBar(mb);
        f.add("South", tf);
        f.setSize(200, 200);
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setVisible(true);
    }

    public static void moveAndPressMouse(int x, int y, int width, int height) {
        robot.mouseMove(x + width / 2, y + height / 2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
    }

    static class CustomActionListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (e.getSource() == mi && e.getActionCommand().equals("Testitem")) {
                System.out.println("MenuItem's label: " + e.getActionCommand());
                passed = true;
            }
        }
    }
}
