/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8262085
 * @summary Tests tooltip for not throwing IllegalArgumentException on fast switching between frames.
 * @run main FastTooltipSwitchIAE
 */

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.WindowConstants;
import javax.swing.plaf.metal.MetalLookAndFeel;
import java.awt.AWTException;
import java.awt.Color;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Window;

public class FastTooltipSwitchIAE {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(new MetalLookAndFeel());
            } catch (UnsupportedLookAndFeelException e) {
                throw new RuntimeException(e);
            }
        });

        FastTooltipSwitchIAE fastTooltipSwitchIAE = new FastTooltipSwitchIAE();
        fastTooltipSwitchIAE.doTest();
    }

    Robot robot = new Robot();
    JFrame frame;
    JDialog dialog;

    public FastTooltipSwitchIAE() throws AWTException {
        SwingUtilities.invokeLater(() -> {
            frame = new JFrame("Frame");
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            frame.setSize(250, 250);
            frame.setLocation(100, 100);

            frame.add(createLabel("Frame label", Color.RED, "frame tooltip"));
            frame.setVisible(true);

            dialog = new JDialog(frame, "Dialog");
            dialog.add(createLabel("Dialog label", Color.YELLOW, "dialog tooltip"));
            dialog.pack();
            dialog.setLocation(350, 100);
            dialog.setVisible(true);
        });
    }

    private Point getCenter(Window window) {
        Rectangle bounds = window.getBounds();
        Insets insets = window.getInsets();
        int width = bounds.width - insets.right - insets.left;
        int height = bounds.height - insets.top - insets.bottom;

        return new Point(
                bounds.x + insets.left + width / 2,
                bounds.y + insets.top + height / 2
        );
    }

    private volatile Throwable unexpectedThrowable = null;

    private void doTest() throws InterruptedException {
        robot.waitForIdle();

        Point frameCenter = getCenter(frame);
        Point dialogCenter = getCenter(dialog);


        robot.mouseMove(frameCenter.x, frameCenter.y);

        // waiting for tooltip to show up
        Thread.sleep(3000);

        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            // Let's catch all exceptions, not only IllegalArgumentException
            unexpectedThrowable = e;
            e.printStackTrace();
        });

        boolean moveToDialog = true;

        int timeoutMs = 40_000;
        long endTime = System.currentTimeMillis() + timeoutMs;

        while (
                unexpectedThrowable == null
                        && System.currentTimeMillis() <= endTime
        ) {
            if (moveToDialog) {
                robot.mouseMove(dialogCenter.x, dialogCenter.y);
            } else {
                robot.mouseMove(frameCenter.x, frameCenter.y);
            }
            robot.waitForIdle();
            moveToDialog = !moveToDialog;
        }
        frame.dispose();
        if (unexpectedThrowable == null) {
            System.out.println("Test passed, no exception thrown in " + timeoutMs + "ms");
        } else {
            throw new RuntimeException("Test failed due to exception thrown:", unexpectedThrowable);
        }
    }

    private static JLabel createLabel(
            final String labelText,
            final Color bgColor,
            final String tooltipContent
    ) {
        final JLabel label = new JLabel(labelText);
        label.setOpaque(true);
        label.setBackground(bgColor);
        label.setToolTipText("<html><h1>" + tooltipContent + "</h1></html>");
        return label;
    }
}
