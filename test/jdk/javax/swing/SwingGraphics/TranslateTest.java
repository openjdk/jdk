/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4207383
 * @summary This tests, in a round about manner, that SwingGraphics does
 *          not wrongly translate the original graphics when disposed. While
 *          this test seems rather ugly, it was possible to get this to happen
 *          in real world apps. This test is really only valid for 1.1.x.
 * @key headful
 * @run main TranslateTest
 */

import java.io.File;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Image;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.plaf.ComponentUI;
import javax.imageio.ImageIO;

public class TranslateTest {
    static JFrame frame;
    static volatile Point pt;
    static volatile Dimension dim;
    static final int WIDTH = 200;
    static final int HEIGHT = 200;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();

        try {
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame("TranslateTest");

                // paintComponent() triggers create swing graphics which will
                // be invoked on child.
                MyPanel panel = new MyPanel();
                panel.setPreferredSize(new Dimension(WIDTH, HEIGHT));
                frame.getContentPane().add(panel);
                frame.pack();
                frame.setLocationRelativeTo(null);
                panel.test();
                frame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);
            SwingUtilities.invokeAndWait(() -> {
                pt = frame.getLocationOnScreen();
                dim = frame.getSize();
            });
            BufferedImage img = robot.createScreenCapture(
                                    new Rectangle(pt.x + dim.width / 2,
                                                  pt.y + dim.height / 2,
                                                  WIDTH / 2, HEIGHT / 2));
            robot.waitForIdle();
            robot.delay(500);
            Color c = new Color(img.getRGB(img.getWidth() / 2, img.getHeight() / 2));
            if (c.getRed() < 250) {
                ImageIO.write(img, "png", new File("image.png"));
                System.out.println("Color " + c);
                throw new RuntimeException("Translated Color is not red");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }


    static class MyPanel extends JPanel {
        int            state;
        Graphics       realG;
        Image          image;

        public void test() {
            image = createImage(TranslateTest.WIDTH, TranslateTest.HEIGHT);
            Graphics g = image.getGraphics();
            g.setClip(0, 0, TranslateTest.WIDTH, TranslateTest.HEIGHT);
            realG = g;
            state = 1;
            paintComponent(g);
            state = 3;
            paintComponent(g);
            state = 4;
        }


        public void paint(Graphics g) {
            if (state == 0) {
                test();
            }
            super.paint(g);
        }

        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
        }

        public void updateUI() {
            setUI(new ComponentUI() {
                public void paint(Graphics g, JComponent c) {
                    if (state == 1) {
                        // g is the first SwingGraphics, when it is disposed
                        // translateX/translateY will be wrong
                        //System.out.println("FIRST:" + g);
                        g.translate(100, 100);
                        state = 2;
                        paintComponent(realG);
                    }
                    else if (state == 2) {
                        // g is the first SwingGraphics, when it is disposed
                        // translateX/translateY will be wrong
                        g.translate(100, 100);
                        //System.out.println("Second:" + g);
                    }
                    else if (state == 3) {
                        // g should be the same as the first, with the wrong
                        // translate.
                        // otherG should be the second graphics, again with
                        // the wrong translation, disposing the second will
                        // cause g to be translated to -100, -100, which
                        // should not happen.
                        Graphics otherG = g.create(0, 0, 100, 100);
                        //System.out.println("THIRD:" + g);
                        otherG.dispose();
                        g.setColor(Color.red);
                        //System.out.println("LAST: " + g);
                        g.fillRect(100, 100, 100, 100);
                    }
                    else if (state == 4) {
                        g.drawImage(image, 0, 0, null);
                    }
                }
            });
        }
    }
}
