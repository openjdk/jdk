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

/*
 * @test
 * @key headful
 * @bug 4659800
 * @summary Check whether typing <Space> key generates
 * ActionEvent on focused Button or not.
 * @run main bug4659800
 */
public class bug4659800 {
    private static JFrame frame = null;
    private static volatile boolean buttonPressed = false;
    private boolean testFailed = false;
    private JButton button1;
    private Robot robot;
    private JButton dummyButton;

    public static void main(String[] s) {
        bug4659800 test = new bug4659800();
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
        frame = new JFrame();
        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout());
        button1 = new JButton("butt1");
        buttonPressed = false;
        button1.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                synchronized (this) {
                    System.out.println("ActionPerformed on Button1 : ");
                    buttonPressed = true;
                    notifyAll();
                }
            }
        });

        panel.add(button1);

        dummyButton = new JButton("butt2");
        panel.add(dummyButton);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new FlowLayout());

        frame.add(panel);

        frame.setSize(200, 200);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

    }

    public void runTest() {
        try {
            robot = new Robot();
        } catch (Exception e) {
            System.err.print("Error creating robot");
            e.printStackTrace();
            System.exit(1);
        }
        for (UIManager.LookAndFeelInfo laf : UIManager.getInstalledLookAndFeels()) {
            System.out.println("Testing L&F: " + laf);

            try {
                SwingUtilities.invokeAndWait(() -> {
                    setLookAndFeel(laf);
                    createUI();
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
            // Clicks on dummy button to activate the parent window.
            dummyButton.doClick();
            button1.requestFocusInWindow();
            Point p = button1.getLocationOnScreen();
            robot.mouseMove(p.x + button1.getWidth() / 2, p.y + button1.getHeight() / 2);
            robot.waitForIdle();
            robot.keyPress(KeyEvent.VK_SPACE);
            robot.delay(100);
            robot.keyRelease(KeyEvent.VK_SPACE);
            robot.delay(100);
            robot.waitForIdle();

            if (buttonPressed) {
                System.out.println("Test Passed for laf " + laf);
            } else {
                testFailed = true;
                System.out.println("Test Failed, button not pressed for laf " + laf + " buttonPressed = " + buttonPressed);
            }
            frame.dispose();
            frame = null;
        }
        if (testFailed) {
            throw new RuntimeException("Test Failed, button not pressed in one or more LAFs");
        } else {
            System.out.println("Test Passed for all supported LAFs ");
        }
    }

}
