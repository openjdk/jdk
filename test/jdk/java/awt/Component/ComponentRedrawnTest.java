/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8139581
 * @summary Verify that components are redrawn after
 * removal and addition to a container
 * @run main ComponentRedrawnTest
 */

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Panel;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JButton;

public class ComponentRedrawnTest {

    private static Frame frame;
    private static Panel componentPanel;
    private static Button buttonRemove;
    private static Button buttonAdd;
    private static Button awtButton;

    private static volatile Robot robot;
    private static volatile int x;
    private static volatile int y;
    private static AtomicInteger awtPainted = new AtomicInteger();
    private static AtomicInteger swingPainted = new AtomicInteger();

    public static void main(String args[]) throws Exception {
        try {
            EventQueue.invokeAndWait(() -> createGUI());
            runTest();
            System.out.println("Test Passed");
        } finally {
            EventQueue.invokeAndWait(() -> dispose());
        }
    }

    private static void createGUI() {
        frame = new Frame("ComponentRedrawnTest");
        frame.setSize(350, 300);
        frame.setBackground(Color.red);

        componentPanel = new Panel();
        componentPanel.setLayout(null);
        componentPanel.setBackground(Color.green);

        awtButton = new Button("AWT Button") {
            @Override
            public void paint(Graphics g) {
                super.paint(g);
                awtPainted.incrementAndGet();
            }
        };

        awtButton.setBounds(0, 0, 330, 100);
        componentPanel.add(awtButton);

        JButton swingButton = new JButton("Swing JButton") {
            @Override
            public void paint(Graphics g) {
                super.paint(g);
                swingPainted.incrementAndGet();
            }
        };

        swingButton.setBounds(0, 100, 330, 100);
        componentPanel.add(swingButton);
        frame.add(componentPanel, BorderLayout.CENTER);
        buttonRemove = new Button("remove");
        buttonRemove.addActionListener(ae -> buttonClicked(ae));

        buttonAdd = new Button("add");
        buttonAdd.addActionListener(ae -> buttonClicked(ae));

        Panel controlPanel = new Panel();
        controlPanel.setLayout(new BorderLayout());
        controlPanel.add(buttonRemove, BorderLayout.NORTH);
        controlPanel.add(buttonAdd, BorderLayout.SOUTH);

        frame.add(controlPanel, BorderLayout.SOUTH);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void buttonClicked(ActionEvent ae) {
        if (ae.getSource() == buttonRemove) {
            frame.remove(componentPanel);
        } else if (ae.getSource() == buttonAdd) {
            frame.add(componentPanel);
        }
        frame.invalidate();
        frame.validate();
    }

    private static void runTest() throws Exception {
        EventQueue.invokeAndWait(() -> createGUI());
        robot = new Robot();
        robot.setAutoDelay(500);
        awtPainted.set(0);
        swingPainted.set(0);

        try {
            EventQueue.invokeAndWait(() -> {
                x = awtButton.getLocationOnScreen().x
                    + awtButton.getSize().width / 2;
                y = awtButton.getLocationOnScreen().y
                    + awtButton.getSize().height / 2;
            });
        } catch (Exception e) {
            throw new RuntimeException("Unexpected Exception encountered: " + e);
        }

        robot.mouseMove(x, y);
        robot.waitForIdle();
        robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);

        try {
            EventQueue.invokeAndWait(() -> {
                x = buttonRemove.getLocationOnScreen().x
                    + buttonRemove.getSize().width / 2;
                y = buttonRemove.getLocationOnScreen().y
                    + buttonRemove.getSize().height / 2;
            });
        } catch (Exception e) {
            throw new RuntimeException("Unexpected Exception encountered: " + e);
        }

        robot.mouseMove(x, y);
        robot.waitForIdle();
        robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);

        try {
            EventQueue.invokeAndWait(() -> {
                x = buttonAdd.getLocationOnScreen().x
                    + buttonAdd.getSize().width / 2;
                y = buttonAdd.getLocationOnScreen().y
                    + buttonAdd.getSize().height / 2;
            });

        } catch (Exception e) {
            throw new RuntimeException("Unexpected Exception encountered: " + e);
        }
        robot.mouseMove(x, y);
        robot.waitForIdle();
        robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);

        if (awtPainted.get() == 0) {
            throw new RuntimeException("AWT button is not painted");
        }
        if (swingPainted.get() == 0) {
            throw new RuntimeException("Swing button is not painted");
        }
    }

    private static void dispose() {
        if (frame != null) {
            frame.dispose();
            frame = null;
        }
    }

}
