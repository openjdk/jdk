/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6318027
 * @key headful
 * @summary  Verifies BasicScrollBarUI disables timer when enclosing frame is disabled
 * @run main DisableFrameFromScrollBar
 */

import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.InputEvent;

import javax.swing.JFrame;
import javax.swing.JScrollBar;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class DisableFrameFromScrollBar {

    private static JFrame frame;
    private static JScrollBar bar;
    private static int oldValue;
    private static volatile boolean doCheck;
    private static volatile boolean isAdjusting;

    private static void setLookAndFeel(UIManager.LookAndFeelInfo laf) {
        try {
            UIManager.setLookAndFeel(laf.getClassName());
        } catch (UnsupportedLookAndFeelException ignored) {
            System.out.println("Unsupported LAF: " + laf.getClassName());
        } catch (ClassNotFoundException | InstantiationException
                 | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static void createUI() {
        frame = new JFrame(DisableFrameFromScrollBar.class.getName());
        bar = new JScrollBar();
        bar.getModel().addChangeListener(new DisableChangeListener(frame));
        frame.getContentPane().setLayout(new FlowLayout());
        frame.add(bar);

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(150, 150);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] args) throws Exception {
        for (UIManager.LookAndFeelInfo laf : UIManager.getInstalledLookAndFeels()) {
            System.out.println("Testing LAF : " + laf.getClassName());
            Robot robot = new Robot();
            robot.setAutoDelay(100);
            try {
                SwingUtilities.invokeAndWait(() -> {
                    setLookAndFeel(laf);
                    createUI();
                });

                robot.waitForIdle();
                robot.delay(1000);
                Point point = getClickPoint();
                robot.mouseMove(point.x, point.y);
                robot.waitForIdle();
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

                SwingUtilities.invokeAndWait(() -> {
                    oldValue = bar.getValue();
                    bar.addAdjustmentListener(new AdjustmentListener() {
                        public void adjustmentValueChanged(AdjustmentEvent e) {
                            int curValue = e.getValue();
                            int extent = bar.getMaximum() - bar.getVisibleAmount();
                            if (curValue < extent && curValue != oldValue) {
                                oldValue = curValue;
                                isAdjusting = true;
                            } else {
                                doCheck = true;
                                isAdjusting = false;
                            }
                        }
                    });
                });
                do {
                    Thread.sleep(200);
                } while (isAdjusting && !doCheck);
                if (bar.getValue() == (bar.getMaximum() - bar.getVisibleAmount())) {
                    throw new RuntimeException("ScrollBar didn't disable timer");
                }
            } finally {
                SwingUtilities.invokeAndWait(() -> {
                    if (frame != null) {
                       frame.dispose();
                    }
               });
            }
        }
    }

    private static Point getClickPoint() throws Exception {
        final Point[] result = new Point[1];

        SwingUtilities.invokeAndWait(new Runnable() {

            @Override
            public void run() {
                Point p = bar.getLocationOnScreen();
                Rectangle rect = bar.getBounds();
                result[0] = new Point((int) (p.x + rect.width / 2),
                        (int) (p.y + rect.height - 10));
            }
        });

        return result[0];

    }

    public static class DisableChangeListener implements ChangeListener {
        private final JFrame m_frame;
        private boolean m_done;

        public DisableChangeListener(JFrame p_frame) {
            m_frame = p_frame;
        }

        public void stateChanged(ChangeEvent p_e) {
            if (!m_done) {
                m_frame.setEnabled(false);
                Thread t = new Thread(new Enabler(m_frame));
                t.start();
                m_done = true;
            }
        }
    }

    public static class Enabler implements Runnable {
        private JFrame m_frame;

        Enabler(JFrame p_frame) {
            m_frame = p_frame;
        }

        public void run() {
            try {
                Thread.sleep(1000);
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
            m_frame.setEnabled(true);
        }
    }
}

