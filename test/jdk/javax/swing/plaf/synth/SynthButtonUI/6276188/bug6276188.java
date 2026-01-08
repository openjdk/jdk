/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
/**
 * @test
 * @key headful
 * @bug 6276188
 * @library ../../../../regtesthelpers
 * @build Util
 * @summary Tests PRESSED and MOUSE_OVER and FOCUSED state for buttons with Synth.
 * @run main/othervm -Dsun.java2d.uiScale=1 bug6276188
 */

import java.io.File;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.plaf.synth.SynthLookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.util.concurrent.CountDownLatch;

public class bug6276188 {

    private static JButton button;
    private static JFrame testFrame;

     // move away from cursor
    private final static int OFFSET_X = 20;
    private final static int OFFSET_Y = 20;

    public static void main(String[] args) throws Throwable {
        Robot robot = new Robot();
        try {
            robot.setAutoDelay(100);

            SynthLookAndFeel lookAndFeel = new SynthLookAndFeel();
            lookAndFeel.load(bug6276188.class.getResourceAsStream("bug6276188.xml"), bug6276188.class);
            UIManager.setLookAndFeel(lookAndFeel);
            CountDownLatch latch = new CountDownLatch(1);

            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    testFrame = new JFrame();
                    testFrame.setLayout(new BorderLayout());
                    testFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                    testFrame.add(BorderLayout.CENTER, button = new JButton());
                    button.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mousePressed(MouseEvent e) {
                            System.out.println("Mouse pressed");
                            latch.countDown();
                        }
                    });

                    testFrame.setSize(new Dimension(320, 200));
                    testFrame.setLocationRelativeTo(null);
                    testFrame.setVisible(true);
                }
            });

            robot.waitForIdle();
            robot.delay(1000);

            Point p = Util.getCenterPoint(button);
            System.out.println("Button center point: " + p);

            robot.mouseMove(p.x , p.y);
            robot.waitForIdle();
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            latch.await();
            robot.delay(1000);

            Color color = robot.getPixelColor(p.x - OFFSET_X, p.y - OFFSET_Y);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            boolean red = color.getRed() > 0 && color.getGreen() == 0 && color.getBlue() == 0;
            if (!red) {
                System.err.println("Red: " + color.getRed() + "; Green: " + color.getGreen() + "; Blue: " + color.getBlue());
                Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                Rectangle screen = new Rectangle(0, 0, (int) screenSize.getWidth(), (int) screenSize.getHeight());
                BufferedImage img = robot.createScreenCapture(screen);
                ImageIO.write(img, "png", new File("image.png"));
                throw new RuntimeException("Synth ButtonUI does not handle PRESSED & MOUSE_OVER state");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (testFrame != null) {
                    testFrame.dispose();
                }
            });
        }
    }
}
