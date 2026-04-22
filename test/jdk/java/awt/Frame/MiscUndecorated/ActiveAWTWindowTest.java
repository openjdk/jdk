/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @key headful
 * @summary To check proper WINDOW_EVENTS are triggered when Frame gains
 * or loses the focus
 * @run main ActiveAWTWindowTest
 */

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.swing.JButton;
import javax.swing.JComponent;

public class ActiveAWTWindowTest {

    private static Frame frame, frame2;
    private static Button button, button2;
    private static TextField textField, textField2;

    private static CountDownLatch windowActivatedLatch = new CountDownLatch(1);
    private static CountDownLatch windowDeactivatedLatch = new CountDownLatch(1);
    private static CountDownLatch windowFocusGainedLatch = new CountDownLatch(1);

    public static void main(String[] args) throws Exception {
        EventQueue.invokeAndWait(() -> {
            initializeGUI();
        });
        doTest();
        EventQueue.invokeAndWait(() -> {
            if (frame != null) {
                frame.dispose();
            }
            if (frame2 != null) {
                frame2.dispose();
            }
        });
    }

    private static void initializeGUI() {
        frame = new Frame();
        frame.setLayout(new FlowLayout());
        frame.setLocation(5, 20);
        frame.setSize(200, 200);
        frame.setUndecorated(true);

        frame.addWindowFocusListener(new WindowFocusListener() {
            @Override
            public void windowGainedFocus(WindowEvent event) {
                System.out.println("Frame Focus gained");
                windowFocusGainedLatch.countDown();
            }

            @Override
            public void windowLostFocus(WindowEvent event) {
                System.out.println("Frame Focus lost");
            }
        });

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
                System.out.println("Undecorated Frame is activated");
                windowActivatedLatch.countDown();
            }

            @Override
            public void windowDeactivated(WindowEvent e) {
                System.out.println("Undecorated Frame got Deactivated");
                windowDeactivatedLatch.countDown();
            }
        });

        textField = new TextField("TextField");
        button = new Button("Click me");
        button.addActionListener(e -> textField.setText("Focus gained"));

        frame.setBackground(Color.green);
        frame.add(button);
        frame.add(textField);
        frame.setVisible(true);

        frame2 = new Frame();
        frame2.setLayout(new FlowLayout());
        frame2.setLocation(5, 250);
        frame2.setSize(200, 200);
        frame2.setBackground(Color.green);

        button2 = new Button("Click me");
        textField2 = new TextField("TextField");
        button2.addActionListener(e -> textField2.setText("Got the focus"));

        frame2.add(button2, BorderLayout.SOUTH);
        frame2.add(textField2, BorderLayout.NORTH);
        frame2.setVisible(true);
    }

    private static void doTest() throws AWTException, InterruptedException  {
        Robot robot = new Robot();
        robot.setAutoDelay(150);
        robot.setAutoWaitForIdle(true);
        if (!windowFocusGainedLatch.await(1000, TimeUnit.MILLISECONDS)) {
            throw new RuntimeException("Frame did not gain focus");
        }
        clickButtonCenter(robot, button);

        if (!windowActivatedLatch.await(1000, TimeUnit.MILLISECONDS)) {
            throw new RuntimeException("Frame was not activated");
        }
        clickButtonCenter(robot, button2);

        if (!windowDeactivatedLatch.await(2000, TimeUnit.MILLISECONDS)) {
            throw new RuntimeException("Frame was not deactivated");
        }
        if (frame.hasFocus()) {
            throw new RuntimeException("Frame did not lose focus");
        }
    }

    private static void clickButtonCenter(Robot robot, Component button) {
        Point location = button.getLocationOnScreen();
        Dimension size = button.getSize();
        int x = location.x + size.width / 2;
        int y = location.y + size.height / 2;
        robot.mouseMove(x, y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }
}

