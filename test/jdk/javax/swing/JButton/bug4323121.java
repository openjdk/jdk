/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Point;
import java.awt.Robot;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.CountDownLatch;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import static java.util.concurrent.TimeUnit.SECONDS;

public final class bug4323121 {

    static JFrame frame;
    static JButton button;

    static volatile Point buttonCenter;

    private static final CountDownLatch windowGainedFocus = new CountDownLatch(1);

    private static final CountDownLatch mouseEntered = new CountDownLatch(1);

    // Usage of this flag is thread-safe because of using the mouseEntered latch
    private static boolean modelArmed;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(100);

        try {
            SwingUtilities.invokeAndWait(() -> {
                button = new TestButton("gotcha");
                button.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        if (button.getModel().isArmed()) {
                            modelArmed = true;
                        }
                        mouseEntered.countDown();
                    }
                });

                frame = new JFrame("bug4323121");
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

            if (!mouseEntered.await(1, SECONDS)) {
                throw new RuntimeException("Mouse entered event wasn't received");
            }
            if (modelArmed) {
                throw new RuntimeException("getModel().isArmed() returns true "
                                           + "for a subclass of JButton");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static final class TestButton extends JButton {
        public TestButton(String label) {
            super(label);
        }
    }

}
