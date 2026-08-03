/*
 * Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;

/*
 *
 *
 * Tests that the use of shared memory pixmaps isn't broken:
 * create a VolatileImage, fill it with red color, copy it to the screen
 * make sure the pixels on the screen are red.
 *
 * Note that we force the use of shared memory pixmaps in the shell script.
 */

public class SharedMemoryPixmapsTest {
    static final int IMAGE_SIZE = 200;
    static volatile boolean show = false;
    static volatile Frame testFrame;
    static volatile TestComponent testComponent;

    static void createUI() {
        testFrame = new Frame("SharedMemoryPixmapsTest");
        testComponent = new TestComponent();
        testFrame.add(testComponent);
        testFrame.setUndecorated(true);
        testFrame.setResizable(false);
        testFrame.pack();
        testFrame.setLocationRelativeTo(null);
        testFrame.setVisible(true);
        testFrame.toFront();
    }

    public static void main(String[] args) throws Exception {
        for (String s : args) {
            if ("-show".equals(s)) {
                show = true;
            } else {
                System.err.println("Usage: SharedMemoryPixmapsTest [-show]");
            }
        }
        EventQueue.invokeAndWait(SharedMemoryPixmapsTest::createUI);
        if (testRendering()) {
            System.err.println("Test Passed");
        } else {
            System.err.println("Test Failed");
        }
        if (!show && testFrame != null) {
            EventQueue.invokeAndWait(testFrame::dispose);
        }
    }

    static boolean testRendering() throws Exception {
        Robot r = new Robot();
        r.waitForIdle();
        r.delay(2000);
        Point p = testComponent.getLocationOnScreen();
        BufferedImage b =
            r.createScreenCapture(new Rectangle(p, testComponent.getPreferredSize()));
        for (int y = 20; y < b.getHeight() - 40; y++) {
            for (int x = 20; x < b.getWidth() - 40; x++) {
                if (b.getRGB(x, y) != Color.red.getRGB()) {
                    System.err.println("Incorrect pixel at "
                        + x + "x" + y + " : " +
                        Integer.toHexString(b.getRGB(x, y)));
                    if (show) {
                        return false;
                    }
                    System.err.println("Test Failed");
                    System.exit(1);
                }
            }
        }
        return true;
    }

    static class TestComponent extends Component {
        VolatileImage vi = null;

        void initVI() {
            int res;
            if (vi == null) {
                res = VolatileImage.IMAGE_INCOMPATIBLE;
            } else {
                res = vi.validate(getGraphicsConfiguration());
            }
            if (res == VolatileImage.IMAGE_INCOMPATIBLE) {
                if (vi != null) vi.flush();
                vi = createVolatileImage(IMAGE_SIZE, IMAGE_SIZE);
                vi.validate(getGraphicsConfiguration());
                res = VolatileImage.IMAGE_RESTORED;
            }
            if (res == VolatileImage.IMAGE_RESTORED) {
                Graphics vig = vi.getGraphics();
                vig.setColor(Color.red);
                vig.fillRect(0, 0, vi.getWidth(), vi.getHeight());
                vig.dispose();
            }
        }

        @Override
        public synchronized void paint(Graphics g) {
            do {
                g.setColor(Color.green);
                g.fillRect(0, 0, getWidth(), getHeight());

                vi = null;
                initVI();
                g.drawImage(vi, 0, 0, null);
            } while (vi.contentsLost());
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(IMAGE_SIZE, IMAGE_SIZE);
        }
    }
}
