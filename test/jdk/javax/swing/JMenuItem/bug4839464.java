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

/*
    @test
    @bug 4839464
    @summary Shortcoming in the way JMenuItem handles 'propertyChange()' events.
    @key headful
    @run main bug4839464
*/

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.Action;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

public class bug4839464 {

    // Global variables
    public static volatile boolean passed = true;
    public static volatile String reason = "\nSome actions did not worked:";

    public static AbstractAction action= new AbstractAction() {
        public void actionPerformed(ActionEvent e) {
            System.out.println("An action has performed");
        }
    };

    public static KeyStroke ks1 =
            KeyStroke.getKeyStroke(KeyEvent.VK_F1,
                                   KeyEvent.SHIFT_DOWN_MASK);

    public static KeyStroke ks2 =
            KeyStroke.getKeyStroke(KeyEvent.VK_F1,
                                   KeyEvent.CTRL_DOWN_MASK);

    public static JFrame frame;

    public static JFrame control;
    public static JButton changeNameButton;
    public static JButton changeMnemonicButton;
    public static JButton changeAcceleratorButton;
    public static JButton changeShortDescButton;

    public static JMenuItem item;

    public static Robot r;

    public static volatile int btnWidth, btnHeight;
    public static volatile Point p;

    public static void main(String[] args) throws Exception {
        try {
            r = new Robot();
            SwingUtilities.invokeAndWait(() -> {
                changeNameButton = new JButton("Change name");
                changeMnemonicButton = new JButton("On/Off mnemonic");
                changeAcceleratorButton = new JButton("Change accelerator");
                changeShortDescButton = new JButton("Change short desc.");

                JMenuBar mb = new JMenuBar();
                JMenu test = new JMenu("Test");
                mb.add(test);
                item = new JMenuItem(action);
                test.add(item);
                frame = new JFrame("Action tester");
                frame.setJMenuBar(mb);
                frame.setLayout(new BorderLayout());
                frame.add(new JButton(action), BorderLayout.CENTER);
                frame.pack();
                frame.setLocation(100, 10);
                frame.setVisible(true);
                r.delay(100);

                control = new JFrame("Controls");
                control.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                control.setLayout(new GridLayout(2, 10));
                control.add(changeNameButton);
                control.add(changeMnemonicButton);
                control.add(changeAcceleratorButton);
                control.add(changeShortDescButton);
                control.pack();
                control.setLocation(100, 500);
                control.setVisible(true);
                r.delay(100);

                changeNameButton.addActionListener(new AbstractAction() {
                    public void actionPerformed(ActionEvent e) {
                        if ("First Name".equals(action.getValue(Action.NAME))) {
                            action.putValue(Action.NAME, "Second Name");
                        } else {
                            action.putValue(Action.NAME, "First Name");
                        }
                    }
                });

                changeMnemonicButton.addActionListener(new AbstractAction() {
                    public void actionPerformed(ActionEvent e) {
                        Integer mnem = (Integer) action.getValue(Action.MNEMONIC_KEY);
                        if (mnem.intValue() == 0) {
                            action.putValue(Action.MNEMONIC_KEY, new Integer('N'));
                        } else {
                            action.putValue(Action.MNEMONIC_KEY, new Integer(0));
                        }
                    }
                });

                changeAcceleratorButton.addActionListener(new AbstractAction() {
                    public void actionPerformed(ActionEvent e) {
                        if (action.getValue(Action.ACCELERATOR_KEY) == ks1) {
                            action.putValue(Action.ACCELERATOR_KEY, ks2);
                        } else {
                            action.putValue(Action.ACCELERATOR_KEY, ks1);
                        }
                    }
                });

                changeShortDescButton.addActionListener(new AbstractAction() {
                    public void actionPerformed(ActionEvent e) {
                        String shortDescr = (String) action.getValue(Action.SHORT_DESCRIPTION);
                        if ("Just a text".equals(shortDescr)) {
                            action.putValue(Action.SHORT_DESCRIPTION, null);
                        } else {
                            action.putValue(Action.SHORT_DESCRIPTION, "Just a text");
                        }
                    }
                });

                action.putValue(Action.NAME, "Second Name");
                action.putValue(Action.MNEMONIC_KEY, new Integer('N'));
                action.putValue(Action.ACCELERATOR_KEY, ks1);
                action.putValue(Action.SHORT_DESCRIPTION, null);
            });

            r.delay(1000);
            r.waitForIdle();
            // Run tests
            test();
            r.delay(1000);
            r.waitForIdle();

            if (!passed) {
                throw new RuntimeException(reason + "\nTest failed.");
            }
        } finally {
            if (frame != null) {
                frame.dispose();
            }
            if (control != null) {
                control.dispose();
            }
        }
    }

    public static boolean compareObjects(Object a, Object b) {
        if (a == null) {
            return (b == null);
        }
        return a.equals(b);
    }

    // Actual tests
    public static void test() throws Exception {
        r.delay(500);
        Object tmpResult;

        // Check Action.NAME handling
        tmpResult = item.getText();
        SwingUtilities.invokeAndWait(() -> {
            p = changeNameButton.getLocationOnScreen();
            btnWidth = changeNameButton.getWidth();
            btnHeight = changeNameButton.getHeight();
        });

        doMouseMove(p, btnWidth, btnHeight);
        if (compareObjects(tmpResult, item.getText())) {
            passed = false;
            reason = reason + "\n Action.NAME";
        }

        // Check mnemonics
        int tmpInt = item.getMnemonic();
        SwingUtilities.invokeAndWait(() -> {
            p = changeMnemonicButton.getLocationOnScreen();
            btnWidth = changeMnemonicButton.getWidth();
            btnHeight = changeMnemonicButton.getHeight();
        });

        doMouseMove(p, btnWidth, btnHeight);
        if (tmpInt == item.getMnemonic()) {
            passed = false;
            reason = reason + "\n Action.MNEMONIC_KEY";
        }

        // Check accelerator binding
        tmpResult = item.getAccelerator();
        SwingUtilities.invokeAndWait(() -> {
            p = changeAcceleratorButton.getLocationOnScreen();
            btnWidth = changeAcceleratorButton.getWidth();
            btnHeight = changeAcceleratorButton.getHeight();
        });

        doMouseMove(p, btnWidth, btnHeight);
        if (compareObjects(tmpResult, item.getAccelerator())) {
            passed = false;
            reason = reason + "\n Action.ACCELERATOR_KEY";
        }

        // Check short description (should change ToolTipText)
        tmpResult = item.getToolTipText();
        SwingUtilities.invokeAndWait(() -> {
            p = changeShortDescButton.getLocationOnScreen();
            btnWidth = changeShortDescButton.getWidth();
            btnHeight = changeShortDescButton.getHeight();
        });

        doMouseMove(p, btnWidth, btnHeight);
        if (compareObjects(tmpResult, item.getToolTipText())) {
            passed = false;
            reason = reason + "\n Action.SHORT_DESCRIPTION";
        }
    }

    public static void doMouseMove(Point p, int width, int height) {
        r.mouseMove(p.x + width / 2, p.y + height / 2);
        r.delay(500);
        r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        r.delay(500);
        r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        r.delay(500);
    }
}
