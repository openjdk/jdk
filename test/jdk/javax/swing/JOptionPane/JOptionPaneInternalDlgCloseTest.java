/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8388824
 * @key headful
 * @summary Verifies that internal option pane title-bar close button closes
 *          the dialog for all installed look and feels
 * @run main JOptionPaneInternalDlgCloseTest
 */

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.AbstractButton;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.plaf.basic.BasicInternalFrameUI;

public class JOptionPaneInternalDlgCloseTest {
    private static final int WAIT_TIME = 5000;
    private static final int CLICK_WAIT_TIME = 1000;

    private static JFrame frame;
    private static JDesktopPane desktopPane;
    private static volatile JInternalFrame dialog;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(100);

        for (UIManager.LookAndFeelInfo laf : UIManager.getInstalledLookAndFeels()) {
            System.out.println("Testing L&F: " + laf.getClassName());
            if (laf.getClassName().contains("Metal") || laf.getClassName().contains("Motif")) continue;
            runTest(robot, laf);
        }
    }

    private static void runTest(Robot robot, UIManager.LookAndFeelInfo laf)
            throws Exception {
        try {
            UIManager.setLookAndFeel(laf.getClassName());
        } catch (UnsupportedLookAndFeelException ignored) {
            return;
        }

        CountDownLatch dialogClosed = new CountDownLatch(1);
        AtomicReference<Throwable> exception = new AtomicReference<>();
        dialog = null;

        try {
            SwingUtilities.invokeAndWait(JOptionPaneInternalDlgCloseTest::createUI);

            SwingUtilities.invokeLater(() -> {
                try {
                    JOptionPane.showInternalMessageDialog(desktopPane, "Dialog1");
                } catch (Throwable t) {
                    exception.set(t);
                } finally {
                    dialogClosed.countDown();
                }
            });

            waitForDialog(laf.getName());

            for (Point p : getCloseClickPoints()) {
                click(robot, p);
                if (dialogClosed.await(CLICK_WAIT_TIME, TimeUnit.MILLISECONDS)) {
                    Throwable t = exception.get();
                    if (t != null) {
                        throw new RuntimeException("Failed for " + laf.getName(), t);
                    }
                    return;
                }
            }

            throw new RuntimeException("Internal option pane was not closed for "
                    + laf.getName());
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                    frame = null;
                }
            });
        }
    }

    private static void createUI() {
        desktopPane = new JDesktopPane();

        frame = new JFrame("Top Frame");
        frame.setLayout(new BorderLayout());
        frame.add(desktopPane);
        frame.setSize(320, 240);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void waitForDialog(String lafName) throws Exception {
        long timeout = System.currentTimeMillis() + WAIT_TIME;
        while (System.currentTimeMillis() < timeout) {
            SwingUtilities.invokeAndWait(() -> {
                JInternalFrame[] frames = desktopPane.getAllFrames();
                dialog = frames.length == 0 ? null : frames[0];
            });

            if (dialog != null && dialog.isShowing()) {
                return;
            }

            Thread.sleep(50);
        }

        throw new RuntimeException("Internal option pane was not shown for "
                + lafName);
    }

    private static List<Point> getCloseClickPoints() throws Exception {
        AtomicReference<List<Point>> pointsRef = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            List<Point> points = new ArrayList<>();
            List<Point> fallbackPoints = new ArrayList<>();

            if (dialog.getUI() instanceof BasicInternalFrameUI ui) {
                collectButtonPoints(ui.getNorthPane(), points, fallbackPoints);
            }

            Insets insets = dialog.getInsets();
            int y = Math.max(8, insets.top / 2);
            addFramePoint(fallbackPoints, insets.left + 20, y);
            addFramePoint(fallbackPoints, insets.left + 30, y);
            addFramePoint(fallbackPoints, dialog.getWidth() - insets.right - 20, y);
            addFramePoint(fallbackPoints, dialog.getWidth() - insets.right - 30, y);

            points.addAll(fallbackPoints);
            pointsRef.set(points);
        });

        return pointsRef.get();
    }

    private static void collectButtonPoints(Component c, List<Point> preferred,
            List<Point> fallback) {
        if (c == null || !c.isShowing()) {
            return;
        }

        if (c instanceof AbstractButton button
                && button.isVisible() && button.isEnabled()) {
            Point p = new Point(button.getWidth() / 2, button.getHeight() / 2);
            SwingUtilities.convertPointToScreen(p, button);
            if (isCloseButton(button)) {
                preferred.add(p);
            } else {
                fallback.add(p);
            }
        }

        if (c instanceof Container container) {
            for (Component child : container.getComponents()) {
                collectButtonPoints(child, preferred, fallback);
            }
        }
    }

    private static boolean isCloseButton(AbstractButton button) {
        return containsClose(button.getActionCommand())
                || containsClose(button.getToolTipText())
                || containsClose(button.getText());
    }

    private static boolean containsClose(String text) {
        return text != null && text.toLowerCase().contains("close");
    }

    private static void addFramePoint(List<Point> points, int x, int y) {
        Point p = new Point(x, y);
        SwingUtilities.convertPointToScreen(p, dialog);
        points.add(p);
    }

    private static void click(Robot robot, Point p) {
        robot.mouseMove(p.x, p.y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
    }
}

