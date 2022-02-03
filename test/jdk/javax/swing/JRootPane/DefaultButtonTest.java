/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Robot;
import java.awt.event.KeyEvent;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/*
 * @test
 * @key headful
 * @bug 8280913
 * @summary Check whether the default button is honored when <Enter> key is
 * pressed while the focus is on the frame.
 * @run main DefaultButtonTest
 */
public class DefaultButtonTest {
    volatile boolean buttonPressed = false;
    JFrame frame;

    public static void main(String[] s) throws Exception {
        DefaultButtonTest test = new DefaultButtonTest();
        try {
            test.runTest();
        } finally {
            test.disposeFrame();
        }

    }

    private static void setLookAndFeel(String lafName) {
        try {
            UIManager.setLookAndFeel(lafName);
        } catch (UnsupportedLookAndFeelException ignored) {
            System.out.println("Ignoring Unsupported L&F: " + lafName);
        } catch (ClassNotFoundException | InstantiationException
                | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void disposeFrame() {
        if (frame != null) {
            frame.dispose();
            frame = null;
        }
    }

    private void createUI() {
        frame = new JFrame();
        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout());
        JButton button1 = new JButton("button1");
        button1.addActionListener(e -> buttonPressed = true);
        panel.add(button1);

        JButton button2 = new JButton("button2");
        panel.add(button2);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new FlowLayout());

        frame.add(panel);

        frame.setSize(200, 200);
        frame.setLocationRelativeTo(null);
        frame.getRootPane().setDefaultButton(button1);
        frame.setVisible(true);
    }

    public void runTest() throws Exception {
        Robot robot = new Robot();
        for (UIManager.LookAndFeelInfo laf : UIManager.getInstalledLookAndFeels()) {
            buttonPressed = false;
            String lafName = laf.getClassName();
            System.out.println("Testing L&F: " + lafName);
            SwingUtilities.invokeAndWait(() -> {
                setLookAndFeel(lafName);
                createUI();
                frame.getRootPane().requestFocus();
            });
            robot.waitForIdle();
            robot.keyPress(KeyEvent.VK_ENTER);
            robot.delay(100);
            robot.keyRelease(KeyEvent.VK_ENTER);
            robot.waitForIdle();

            if (buttonPressed) {
                System.out.println("Test Passed for L&F: " + lafName);
            } else {
                throw new RuntimeException("Test Failed, button not pressed for L&F: " + lafName);
            }
            disposeFrame();
        }
    }

}