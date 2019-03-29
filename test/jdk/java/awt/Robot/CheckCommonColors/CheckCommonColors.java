/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Color;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.util.List;

/**
 * @test
 * @key headful
 * @bug 8215105
 * @summary tests that Robot can capture the common colors without artifacts
 */
public final class CheckCommonColors {

    private static final Frame frame = new Frame();
    private static Robot robot;

    public static void main(final String[] args) throws Exception {
        robot = new Robot();
        try {
            test();
        } finally {
            frame.dispose();
        }
    }

    private static void test() {
        frame.setSize(400, 400);
        frame.setLocationRelativeTo(null);
        frame.setUndecorated(true);
        for (final Color color : List.of(Color.WHITE, Color.LIGHT_GRAY,
                                         Color.GRAY, Color.DARK_GRAY,
                                         Color.BLACK, Color.RED, Color.PINK,
                                         Color.ORANGE, Color.YELLOW,
                                         Color.GREEN, Color.MAGENTA, Color.CYAN,
                                         Color.BLUE)) {
            frame.dispose();
            frame.setBackground(color);
            frame.setVisible(true);
            checkPixels(color);
        }
    }

    private static void checkPixels(final Color color) {
        int attempt = 0;
        while (true) {
            Point p = frame.getLocationOnScreen();
            Color pixel = robot.getPixelColor(p.x + frame.getWidth() / 2,
                                              p.y + frame.getHeight() / 2);
            if (color.equals(pixel)) {
                return;
            }
            if (attempt > 10) {
                System.err.println("Expected: " + color);
                System.err.println("Actual: " + pixel);
                throw new RuntimeException("Too many attempts: " + attempt);
            }
            // skip Robot.waitForIdle to speedup the common case, but also take
            // care about slow systems
            robot.delay((int) Math.pow(2.2, attempt++));
        }
    }
}
