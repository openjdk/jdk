/*
 * Copyright (c) 2007, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6694230 8198613
 * @summary Tests that components overriding getInsets paint correctly
 * @run main/othervm OverriddenInsetsTest
 */

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class OverriddenInsetsTest {
    public static final Insets INSETS1 = new Insets(25,25,0,0);
    public static final Insets INSETS2 = new Insets(100,100,0,0);
    public static final int PANEL_WIDTH = 200;
    public static final int PANEL_HEIGHT = 200;
    static Frame frame;
    static TestPanel p;
    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.getLocalGraphicsEnvironment().
            getDefaultScreenDevice().getDefaultConfiguration().
            getColorModel().getPixelSize() < 16)
        {
            System.out.println("<16 bit mode detected, test passed");
        }
        try {
            EventQueue.invokeAndWait(OverriddenInsetsTest::createAndShowGUI);
            test();
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void test() throws Exception {
        Robot robot = new Robot();
        robot.waitForIdle();
        robot.delay(500);

        Point point = p.getLocationOnScreen();
        BufferedImage bi = robot.createScreenCapture(new Rectangle(point.x,
            point.y, PANEL_WIDTH / 2, PANEL_HEIGHT / 2));
        for (int y = 0; y < bi.getHeight(); y++) {
            for (int x = 0; x < bi.getWidth(); x++) {
                if (bi.getRGB(x, y) != Color.blue.getRGB()) {
                    System.err.printf("Test failed at %d %d c=%x\n",
                        x, y, bi.getRGB(x, y));
                    String name = "OverriddenInsetsTest_res.png";
                    try {
                        ImageIO.write(bi, "png", new File(name));
                        System.out.println("Dumped res to: "+name);
                    } catch (IOException e) {}
                    throw new RuntimeException("Test FAILED.");
                }
            }
        }
    }

    private static void createAndShowGUI() {
        frame = new Frame("OverriddenInsetsTest");
        frame.setSize(260,260);
        frame.setBackground(Color.gray);
        Panel p1 = new Panel() {
            public Insets getInsets() {
                return INSETS1;
            }
        };
        p1.setLayout(null);
        p1.setSize(250, 250);
        p = new TestPanel();
        p.setSize(PANEL_WIDTH, PANEL_HEIGHT);
        p1.add(p);
        p.setLocation(50, 50);
        frame.add(p1);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    static class TestPanel extends Panel {
        @Override
        public Insets getInsets() {
            return INSETS2;
        }

        @Override
        public void paint(Graphics g) {
            g.setColor(Color.red);
            g.drawRect(0,0,getWidth() - 1,getHeight() - 1);
            g.setColor(Color.blue);
            g.fillRect(0,0,getWidth() / 2,getHeight() / 2);
        }
    }
}
