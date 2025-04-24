/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

import jtreg.SkippedException;

/*
 * @test
 * @key headful
 * @bug 6251941
 * @summary Undecorated frames should be minimizable.
 * @library /test/lib
 * @build jtreg.SkippedException
 * @run main MinimizeUndecoratedTest
 */

public class MinimizeUndecoratedTest {
    private static final int SIZE = 300;
    private static final CountDownLatch isMinimized = new CountDownLatch(1);

    private static Frame frame;
    private static Robot robot;

    private static volatile Point frameLoc;

    public static void main(String[] args) throws Exception {
        if (!Toolkit.getDefaultToolkit()
                    .isFrameStateSupported(Frame.ICONIFIED)) {
            throw new SkippedException("Test is not applicable as"
                    + " the Window manager does not support MINIMIZATION");
        }

        try {
            robot = new Robot();
            EventQueue.invokeAndWait(MinimizeUndecoratedTest::createUI);
            robot.waitForIdle();
            robot.delay(1000);

            EventQueue.invokeAndWait(() -> frameLoc = frame.getLocationOnScreen());

            Color beforeColor = robot.getPixelColor(frameLoc.x + SIZE / 2,
                                                    frameLoc.y + SIZE / 2);

            EventQueue.invokeAndWait(() -> frame.setExtendedState(Frame.ICONIFIED));
            robot.waitForIdle();
            robot.delay(500);

            if (!isMinimized.await(8, TimeUnit.SECONDS)) {
                throw new RuntimeException("Window iconified event not received.");
            }

            EventQueue.invokeAndWait(() -> System.out.println("Frame state: "
                                                              + frame.getExtendedState()));
            Color afterColor = robot.getPixelColor(frameLoc.x + SIZE / 2,
                                                   frameLoc.y + SIZE / 2);

            if (beforeColor.equals(afterColor)) {
                saveScreenCapture();
                throw new RuntimeException("Color before & after minimization : "
                                           + beforeColor + " vs " + afterColor + "\n"
                                           + "Test Failed !! Frame not minimized.");
            }
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.setExtendedState(Frame.NORMAL);
                    frame.dispose();
                }
            });
        }
    }

    private static void createUI() {
        frame = new Frame("Test Minimization of Frame");
        frame.setSize(SIZE, SIZE);
        frame.setBackground(Color.GREEN);
        frame.setResizable(true);
        frame.setUndecorated(true);
        frame.addWindowStateListener(new WindowAdapter() {
            @Override
            public void windowStateChanged(WindowEvent e) {
                if (e.getNewState() == Frame.ICONIFIED) {
                    System.out.println("Window iconified event received.");
                    isMinimized.countDown();
                }
            }
        });

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void saveScreenCapture() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        BufferedImage image = robot.createScreenCapture(new Rectangle(new Point(),
                                                                      screenSize));
        try {
            ImageIO.write(image, "png", new File("MinimizedFrame.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
