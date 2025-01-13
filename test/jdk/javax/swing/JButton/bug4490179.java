/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4490179 8049069
 * @summary Tests that JButton only responds to left mouse clicks.
 * @key headful
 * @run main bug4490179
 */

import java.awt.Point;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.CountDownLatch;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public final class bug4490179
        extends MouseAdapter
        implements ActionListener {
    static JFrame frame;
    static JButton button;

    private static volatile Point buttonCenter;

    private static final CountDownLatch windowGainedFocus = new CountDownLatch(1);

    private static final CountDownLatch mouseButton1Released = new CountDownLatch(1);
    private static final CountDownLatch mouseButton3Released = new CountDownLatch(2);

    private static final CountDownLatch actionPerformed = new CountDownLatch(1);

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(100);

        final bug4490179 eventHandler = new bug4490179();
        try {
            SwingUtilities.invokeAndWait(() -> {
                button = new JButton("Button");
                button.addActionListener(eventHandler);
                button.addMouseListener(eventHandler);

                frame = new JFrame("bug4490179");
                frame.getContentPane().add(button);

                frame.addWindowFocusListener(new WindowAdapter() {
                    @Override
                    public void windowGainedFocus(WindowEvent e) {
                        windowGainedFocus.countDown();
                    }
                });

                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setVisible(true);
            });

            if (!windowGainedFocus.await(1, SECONDS)) {
                throw new RuntimeException("Window didn't gain focus");
            }
            robot.waitForIdle();

            SwingUtilities.invokeAndWait(() -> {
                Point location = button.getLocationOnScreen();
                buttonCenter = new Point(location.x + button.getWidth() / 2,
                                         location.y + button.getHeight() / 2);
            });

            robot.mouseMove(buttonCenter.x, buttonCenter.y);
            System.out.println("Press / Release button 3");
            robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);

            System.out.println("Press button 1");
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            System.out.println("Press button 3");
            robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
            System.out.println("Release button 3");
            robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);

            try {
                if (!mouseButton3Released.await(1, SECONDS)) {
                    throw new RuntimeException("Mouse button 3 isn't released");
                }

                robot.waitForIdle();

                if (actionPerformed.await(100, MILLISECONDS)) {
                    throw new RuntimeException("Action event triggered by releasing button 3");
                }
            } finally {
                System.out.println("Release button 1");
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            }

            if (!mouseButton1Released.await(1, SECONDS)) {
                throw new RuntimeException("Mouse button 1 isn't released");
            }
            if (!actionPerformed.await(100, MILLISECONDS)) {
                throw new RuntimeException("Action event isn't triggered by releasing button 1");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        System.out.println("    actionPerformed");
        actionPerformed.countDown();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            System.out.println("    mouseReleased: button 1");
            mouseButton1Released.countDown();
        } else if (e.getButton() == MouseEvent.BUTTON3) {
            System.out.println("    mouseReleased: button 3");
            mouseButton3Released.countDown();
        }
    }
}
