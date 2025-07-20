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

import java.awt.Container;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.BorderLayout;
import javax.swing.JPopupMenu;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class TestPopupInvoker {
    static JPopupMenu jpm;
    static JFrame frame;
    static JLabel label;
    static volatile Point pt;
    static volatile Rectangle size;

    public static class MyThread implements Runnable {
        static Container box;
        static Component invo;

        public MyThread(Container cont, Component comp) {
            this.invo = comp;
            this.box = cont;
        }

        public void run() {
            System.out.println("Starting 3 second countdown...");
            try{
                Thread.currentThread().sleep(3000);
            } catch (Exception e) {}
            System.out.println("Removing popup invoker from the container.");
            box.remove(invo);
            box.repaint();
            if (jpm.isVisible()) {
                throw new RuntimeException("poup is visible after component is removed");
            }
        }
    }

    private static void createUI() {
        frame = new JFrame("My frame");
        final Container pane = frame.getContentPane();
        pane.setLayout(new BorderLayout());
        label = new JLabel("Click here to invoke popup");
        label.addMouseListener(new MouseAdapter() {
            public void mouseReleased(MouseEvent e) {
                if (jpm == null) {
                    jpm = new JPopupMenu("Popup");
                    jpm.add("One");
                    jpm.add("Two");
                    jpm.add("Three");
                }
                jpm.show(label, 0, 0);
            }

            public void mousePressed(MouseEvent e) {
                Thread thr = new Thread(new MyThread(pane, label));
                thr.start();
            }
        });
        pane.add(label, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String args[]) throws Exception {
        try {
            Robot robot = new Robot();
            SwingUtilities.invokeAndWait(() -> createUI());
            robot.waitForIdle();
            robot.delay(1000);
            SwingUtilities.invokeAndWait(() -> {
                pt = label.getLocationOnScreen();
                size = label.getBounds();
            });
            robot.mouseMove(pt.x + size.width / 2, pt.y + size.height / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            robot.delay(1000);
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}
