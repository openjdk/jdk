/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4323121
 * @summary Tests whether any button that extends JButton always
            returns true for isArmed()
 * @key headful
 * @run main bug4323121
 */

import java.awt.Graphics;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class bug4323121 {

    static JFrame frame;
    static testButton button;
    static volatile Point pt;
    static volatile int buttonW;
    static volatile int buttonH;
    static volatile boolean failed = false;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(100);
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame("bug4323121");
                button = new testButton("gotcha");
                frame.getContentPane().add(button);
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);
            SwingUtilities.invokeAndWait(() -> {
                pt = button.getLocationOnScreen();
                buttonW = button.getSize().width;
                buttonH = button.getSize().height;
            });
            robot.mouseMove(pt.x + buttonW / 2, pt.y + buttonH / 2);
            robot.waitForIdle();
            if (failed) {
                throw new RuntimeException("Any created button returns " +
                                    "true for isArmed()");
            }
        } finally {
                SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    static class testButton extends JButton implements MouseMotionListener, MouseListener {
        public testButton(String label) {
            super(label);
            addMouseMotionListener(this);
            addMouseListener(this);
        }

        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
        }

        protected void paintBorder(Graphics g) {
        }

        public void mousePressed(MouseEvent e) {
        }

        public void mouseDragged(MouseEvent e) {
        }

        public void mouseMoved(MouseEvent e) {
        }

        public void mouseReleased(MouseEvent e) {
        }

        public void mouseEntered(MouseEvent e) {
            if (getModel().isArmed()) {
                failed = true;
            }
        }

        public void mouseExited(MouseEvent e) {
        }

        public void mouseClicked(MouseEvent e) {
        }
    }
}
