/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/*
 * @test
 * @key headful
 * @bug 4127271
 * @summary Tests that disposing of a Frame with MenuBar removes all traces
 *          of the Frame from the screen.
 */

public class DisposeTest {
    private static Frame backgroundFrame;
    private static Frame testedFrame;

    private static final Rectangle backgroundFrameBounds =
            new Rectangle(100, 100, 200, 200);
    private static final Rectangle testedFrameBounds =
            new Rectangle(150, 150, 100, 100);

    private static Robot robot;

    public static void main(String[] args) throws Exception {
        robot = new Robot();
        try {
            EventQueue.invokeAndWait(DisposeTest::initAndShowGui);
            robot.waitForIdle();
            robot.delay(500);
            EventQueue.invokeAndWait(testedFrame::dispose);
            robot.waitForIdle();
            robot.delay(500);
            test();
        } finally {
            EventQueue.invokeAndWait(() -> {
                backgroundFrame.dispose();
                testedFrame.dispose();
            });
        }
    }

    private static void test() {
        BufferedImage bi = robot.createScreenCapture(backgroundFrameBounds);
        int redPix = Color.RED.getRGB();

        for (int x = 0; x < bi.getWidth(); x++) {
            for (int y = 0; y < bi.getHeight(); y++) {
                if (bi.getRGB(x, y) != redPix) {
                    try {
                        ImageIO.write(bi, "png",
                                new File("failure.png"));
                    } catch (IOException ignored) {}
                    throw new RuntimeException("Test failed");
                }
            }
        }
    }

    private static void initAndShowGui() {
        backgroundFrame = new Frame("DisposeTest background");
        backgroundFrame.setUndecorated(true);
        backgroundFrame.setBackground(Color.RED);
        backgroundFrame.setBounds(backgroundFrameBounds);
        backgroundFrame.setVisible(true);

        testedFrame = new UglyFrame();
    }

    static class UglyFrame extends Frame {
        public UglyFrame() {
            super("DisposeTest");
            MenuBar mb = new MenuBar();
            Menu m = new Menu("menu");
            mb.add(m);
            setMenuBar(mb);
            setBounds(testedFrameBounds);
            setVisible(true);
        }
    }
}

