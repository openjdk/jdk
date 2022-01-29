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

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;

/*
 * @test
 * @key headful
 * @bug 8280913
 * @summary Check whether the default button is honoured when <Enter> key is
 * pressed when the focus is on the frame.
 * @run main DefaultButtonTest
 */
public class DefaultButtonTest {
    static JFrame frame = new JFrame();
    static volatile boolean buttonPressed = false;
    boolean testFailed = false;
    JButton button1;
    Robot robot;
    private JPanel panel;
    private JButton button2;

    public static void main(String[] s) throws Exception {
        DefaultButtonTest test = new DefaultButtonTest();
        try {
            test.runTest();
        } finally {
            if (frame != null) {
                frame.dispose();
                frame = null;
            }
        }

    }

    private static void setLookAndFeel(UIManager.LookAndFeelInfo laf) {
        try {
            UIManager.setLookAndFeel(laf.getClassName());
        } catch (UnsupportedLookAndFeelException ignored) {
            System.out.println("Unsupported L&F: " + laf.getClassName());
        } catch (ClassNotFoundException | InstantiationException
                | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void createUI() {
        panel = new JPanel();
        panel.setLayout(new FlowLayout());
        button1 = new JButton("butt1");
        button1.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                synchronized (this) {
                    buttonPressed = true;
                    notifyAll();
                }
            }
        });
        panel.add(button1);

        button2 = new JButton("butt2");
        panel.add(button2);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new FlowLayout());

        frame.add(panel);

        frame.setSize(200, 300);
        frame.setLocationRelativeTo(null);
        frame.getRootPane().setDefaultButton(button1);
        frame.setVisible(true);
    }

    public void runTest() throws InvocationTargetException, InterruptedException {
        try {
            robot = new Robot();
        } catch (Exception e) {
            System.err.print("Error creating robot");
            e.printStackTrace();
            System.exit(1);
        }
        for (UIManager.LookAndFeelInfo laf : UIManager.getInstalledLookAndFeels()) {
            buttonPressed = false;
            System.out.println("Testing L&F: " + laf);
            try {
                SwingUtilities.invokeAndWait(new Runnable() {
                    public void run() {
                        setLookAndFeel(laf);
                        createUI();
                        frame.getRootPane().requestFocus();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
            robot.waitForIdle();
            robot.keyPress(KeyEvent.VK_ENTER);
            robot.delay(100);
            robot.keyRelease(KeyEvent.VK_ENTER);
            robot.waitForIdle();

            if (buttonPressed) {
                System.out.println("Test Passed for laf " + laf);
            } else {
                testFailed = true;
                System.out.println("Test Failed, button not pressed for laf " + laf);
            }
        }
        if (testFailed) {
            throw new RuntimeException("Test Failed, button not pressed in one or more LAFs");
        } else {
            System.out.println("Test Passed for all supported LAFs ");
        }
    }

}

