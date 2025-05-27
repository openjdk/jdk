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

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;

/*
 * @test
 * @bug 8345538
 * @summary Tests mouseMove clamping to screen bounds when set to move offscreen
 * @requires (os.family == "mac")
 * @key headful
 * @run main MouseMoveOffScreen
 */

public class MouseMoveOffScreen {
    private static final Point STARTING_LOC = new Point(200, 200);
    private static final Point OFF_SCREEN_LOC = new Point(20000, 200);
    private static Rectangle[] r;

    public static void main(String[] args) throws Exception {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] gs = ge.getScreenDevices();
        r = new Rectangle[gs.length];

        for (int i = 0; i < gs.length; i++) {
            r[i] = gs[i].getDefaultConfiguration().getBounds();
            System.out.println("Screen: "+ gs[i].getIDstring() + "  Bounds: " + r[i]);
        }

        Point offsc = validateOffScreen(OFF_SCREEN_LOC);
        Robot robot = new Robot();
        robot.mouseMove(STARTING_LOC.x, STARTING_LOC.y);
        robot.delay(500);
        robot.mouseMove(offsc.x, offsc.y);
        robot.delay(500);

        Point currLoc = MouseInfo.getPointerInfo().getLocation();

        if (currLoc == null) {
            throw new RuntimeException("Test Failed, getLocation returned null.");
        }

        System.out.println("Current mouse location: " + currLoc);
        if (currLoc.equals(OFF_SCREEN_LOC)) {
            throw new RuntimeException("Test Failed, robot moved mouse off screen.");
        }
    }

    private static Point validateOffScreen(Point p) {
        for (Rectangle rect : r) {
            if (rect.contains(p)) {
                return validateOffScreen(new Point(p.x * 2, p.y));
            }
        }
        return p;
    }
}
