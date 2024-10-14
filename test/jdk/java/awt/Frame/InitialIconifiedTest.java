/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/*
 * @test
 * @key headful
 * @bug 4851435
 * @summary Frame is not shown initially iconified after pack
 */

public class InitialIconifiedTest {

    private static Frame backgroundFrame;
    private static Frame testedFrame;

    private static final Rectangle backgroundFrameBounds =
            new Rectangle(100, 100, 200, 200);
    private static final Rectangle testedFrameBounds =
            new Rectangle(150, 150, 100, 100);

    private static Robot robot;

    private static final StringBuilder FAILURES = new StringBuilder();

    public static void main(String[] args) throws Exception {
        robot = new Robot();
        try {
            EventQueue.invokeAndWait(InitialIconifiedTest::initAndShowBackground);
            robot.waitForIdle();
            robot.delay(500);

            test(false);
            test(true);
        } finally {
            EventQueue.invokeAndWait(() -> {
                backgroundFrame.dispose();
                testedFrame.dispose();
            });
        }

        if (!FAILURES.isEmpty()) {
            throw new RuntimeException(FAILURES.toString());
        }
    }

    private static void test(boolean isUndecorated) throws Exception {
        String prefix = isUndecorated ? "undecorated" : "decorated";

        EventQueue.invokeAndWait(() -> initAndShowTestedFrame(isUndecorated));
        // On macos, we can observe the animation of the window from the initial
        // NORMAL state to the ICONIFIED state,
        // even if the window was created in the ICONIFIED state.
        // The following delay is commented out to capture this animation
        // robot.waitForIdle();
        // robot.delay(500);
        if (!testIfIconified(prefix + "_no_extra_delay")) {
            FAILURES.append("Case %s frame with no extra delay failed\n"
                    .formatted(isUndecorated ? "undecorated" : "decorated"));
        }

        EventQueue.invokeAndWait(() -> initAndShowTestedFrame(isUndecorated));
        robot.waitForIdle();
        robot.delay(500);
        if (!testIfIconified(prefix + "_with_extra_delay")) {
            FAILURES.append("Case %s frame with extra delay failed\n"
                    .formatted(isUndecorated ? "undecorated" : "decorated"));
        }
    }

    private static void initAndShowBackground() {
        backgroundFrame = new Frame("DisposeTest background");
        backgroundFrame.setUndecorated(true);
        backgroundFrame.setBackground(Color.RED);
        backgroundFrame.setBounds(backgroundFrameBounds);
        backgroundFrame.setVisible(true);
    }

    private static void initAndShowTestedFrame(boolean isUndecorated) {
        if (testedFrame != null) {
            testedFrame.dispose();
        }
        testedFrame = new Frame("Should have started ICONIC");
        if (isUndecorated) {
            testedFrame.setUndecorated(true);
        }
        testedFrame.setExtendedState(Frame.ICONIFIED);
        testedFrame.setBounds(testedFrameBounds);
        testedFrame.setVisible(true);
    }

    private static boolean testIfIconified(String prefix) {
        BufferedImage bi = robot.createScreenCapture(backgroundFrameBounds);
        int redPix = Color.RED.getRGB();

        for (int x = 0; x < bi.getWidth(); x++) {
            for (int y = 0; y < bi.getHeight(); y++) {
                if (bi.getRGB(x, y) != redPix) {
                    try {
                        ImageIO.write(bi, "png",
                                new File(prefix + "_failure.png"));
                    } catch (IOException ignored) {}
                    return false;
                }
            }
        }
        return true;
    }
}
