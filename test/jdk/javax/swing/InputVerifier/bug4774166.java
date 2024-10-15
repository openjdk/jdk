/*
 * Copyright (c) 2004, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.FlowLayout;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;
import javax.swing.InputVerifier;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

import static java.awt.event.InputEvent.BUTTON1_DOWN_MASK;

/*
 * @test
 * @bug 4774166
 * @key headful
 * @summary InputVerifier should be called after a window loses and then regains focus
 * @library /javax/swing/regtesthelpers
 * @build JRobot
 * @run main bug4774166
 */

class TestPanel extends JPanel {
    JTextField tf1 = null;
    JTextField tf2 = null;
    volatile boolean verifierCalled;

    public void init() {
        tf1 = new JTextField(10);
        tf2 = new JTextField(10);

        InputVerifier verifier = new InputVerifier() {
            public boolean verify(JComponent input) {
                verifierCalled = true;
                return false;
            }
        };

        setLayout(new FlowLayout());
        tf1.setInputVerifier(verifier);
        add(tf1);
        add(tf2);
        validate();
    }
}

public class bug4774166 {
    private static JRobot robot = JRobot.getRobot();

    JFrame testframe;
    JFrame testwindowframe;
    JFrame customframe;
    JWindow testwindow;
    JDialog testdialog;
    JTextField frametf1, frametf2, windowtf1, windowtf2, dialogtf1, dialogtf2;
    TestPanel testpanel;

    volatile boolean isFocused;
    volatile boolean verifierCalled;

    public void setupGUI() {
        testframe = new JFrame("Test 4774166");
        testframe.setLayout(new FlowLayout());
        testframe.setBounds(100, 100, 200, 100);

        testwindowframe = new JFrame("Owner of JWindow");
        testwindowframe.setBounds(0, 0, 0, 0);

        testwindow = new JWindow(testwindowframe);
        testwindow.setBounds(175, 325, 200, 100);
        testwindow.setLayout(new FlowLayout());

        testdialog = new JDialog((JFrame)null, "Test dialog");
        testdialog.setBounds(420, 100, 200, 100);
        testdialog.setLayout(new FlowLayout());

        InputVerifier verifier = new InputVerifier() {
            public boolean verify(JComponent input) {
                verifierCalled = true;
                return false;
            }
        };

        frametf1 = new JTextField(10);
        frametf2 = new JTextField(10);
        frametf1.setInputVerifier(verifier);
        testframe.add(frametf1);
        testframe.add(frametf2);
        testframe.setVisible(true);

        windowtf1 = new JTextField(10);
        windowtf2 = new JTextField(10);
        windowtf1.setInputVerifier(verifier);
        testwindow.add(windowtf1);
        testwindow.add(windowtf2);
        testwindowframe.setVisible(true);
        testwindow.setVisible(true);

        dialogtf1 = new JTextField(10);
        dialogtf2 = new JTextField(10);
        dialogtf1.setInputVerifier(verifier);
        testdialog.add(dialogtf1);
        testdialog.add(dialogtf2);
        testdialog.setVisible(true);

        customframe = new JFrame("Frame with custom panel");
        customframe.setLayout(new FlowLayout());
        testpanel = new TestPanel();
        testpanel.init();

        customframe.add(testpanel);
        customframe.setBounds(420, 250, 200, 100);
        customframe.pack();
        customframe.setVisible(true);
    }

    public void performTest() throws InterruptedException, InvocationTargetException {
        robot.setAutoDelay(100);
        robot.delay(2000);

        robot.clickMouseOn(frametf1, BUTTON1_DOWN_MASK);
        robot.hitKey(KeyEvent.VK_A);
        robot.hitKey(KeyEvent.VK_B);
        robot.hitKey(KeyEvent.VK_C);
        robot.hitKey(KeyEvent.VK_D);
        robot.hitKey(KeyEvent.VK_E);

        robot.clickMouseOn(windowtf1, BUTTON1_DOWN_MASK);
        robot.hitKey(KeyEvent.VK_F);
        robot.hitKey(KeyEvent.VK_G);
        robot.hitKey(KeyEvent.VK_H);
        robot.hitKey(KeyEvent.VK_I);
        robot.hitKey(KeyEvent.VK_J);

        robot.clickMouseOn(dialogtf1, BUTTON1_DOWN_MASK);
        robot.hitKey(KeyEvent.VK_K);
        robot.hitKey(KeyEvent.VK_L);
        robot.hitKey(KeyEvent.VK_M);
        robot.hitKey(KeyEvent.VK_N);
        robot.hitKey(KeyEvent.VK_O);

        robot.clickMouseOn(testpanel.tf1, BUTTON1_DOWN_MASK);
        robot.hitKey(KeyEvent.VK_P);
        robot.hitKey(KeyEvent.VK_Q);
        robot.hitKey(KeyEvent.VK_R);
        robot.hitKey(KeyEvent.VK_S);
        robot.hitKey(KeyEvent.VK_T);

        verifierCalled = false;
        robot.clickMouseOn(frametf2, BUTTON1_DOWN_MASK);
        robot.delay(2000);
        SwingUtilities.invokeAndWait(() -> {
            isFocused = frametf1.isFocusOwner();
        });
        if (!isFocused) {
            throw new RuntimeException("Focus error. Test failed!");
        }
        if (!verifierCalled) {
            throw new RuntimeException("Verifier was not called upon regaining focus");
        }

        verifierCalled = false;
        robot.clickMouseOn(windowtf2, BUTTON1_DOWN_MASK);
        robot.delay(2000);
        SwingUtilities.invokeAndWait(() -> {
            isFocused = windowtf1.isFocusOwner();
        });
        if (!isFocused) {
            throw new RuntimeException("Focus error. Test failed!");
        }
        if (!verifierCalled) {
            throw new RuntimeException("Verifier was not called upon regaining focus");
        }

        testpanel.verifierCalled = false;
        robot.clickMouseOn(testpanel.tf2, BUTTON1_DOWN_MASK);
        robot.delay(2000);
        SwingUtilities.invokeAndWait(() -> {
            isFocused = testpanel.tf1.isFocusOwner();
        });
        if (!isFocused) {
            throw new RuntimeException("Focus error. Test failed!");
        }
        if (!testpanel.verifierCalled) {
            throw new RuntimeException("Verifier was not called upon regaining focus");
        }

        verifierCalled = false;
        robot.clickMouseOn(dialogtf2, BUTTON1_DOWN_MASK);
        robot.delay(2000);
        SwingUtilities.invokeAndWait(() -> {
            isFocused = dialogtf1.isFocusOwner();
        });
        if (!isFocused) {
            throw new RuntimeException("Focus error. Test failed!");
        }
        if (!verifierCalled) {
            throw new RuntimeException("Verifier was not called upon regaining focus");
        }
    }

    public void cleanupGUI() {
        if (testframe != null) {
            testframe.dispose();
        }
        if (testwindowframe != null) {
            testwindowframe.dispose();
        }
        if (testwindow != null) {
            testwindow.dispose();
        }
        if (customframe != null) {
            customframe.dispose();
        }
        if (testdialog != null) {
            testdialog.dispose();
        }
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        bug4774166 b = new bug4774166();
        SwingUtilities.invokeAndWait(b::setupGUI);
        try {
            b.performTest();
        } finally {
            SwingUtilities.invokeAndWait(b::cleanupGUI);
        }
    }
}
