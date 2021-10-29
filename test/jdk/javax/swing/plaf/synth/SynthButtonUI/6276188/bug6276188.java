/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @test 1.4 08/08/05
 * @key headful
 * @bug 6276188
 * @library ../../../../regtesthelpers
 * @build Util
 * @author Romain Guy
 * @summary Tests PRESSED and MOUSE_OVER and FOCUSED state for buttons with Synth.
 * @run main/othervm -Dsun.java2d.uiScale=1 bug6276188
 */
import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.plaf.synth.*;

public class bug6276188 {

    private static JButton button;
    private static Point p;
    private static JFrame testFrame;

     // move away from cursor
    private final static int OFFSET_X = -20;
    private final static int OFFSET_Y = -20;

    public static void main(String[] args) throws Throwable {
        try {
            Robot robot = new Robot();
            robot.setAutoDelay(100);

            SynthLookAndFeel lookAndFeel = new SynthLookAndFeel();
            lookAndFeel.load(bug6276188.class.getResourceAsStream("bug6276188.xml"), bug6276188.class);
            UIManager.setLookAndFeel(lookAndFeel);

            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    testFrame = new JFrame();
                    testFrame.setLayout(new BorderLayout());
                    testFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                    testFrame.add(BorderLayout.CENTER, button = new JButton());

                    testFrame.setSize(new Dimension(320, 200));
                    testFrame.setLocationRelativeTo(null);
                    testFrame.setVisible(true);
                }
            });

            robot.waitForIdle();
            robot.delay(1000);

            p = Util.getCenterPoint(button);
            System.out.println("Button center point: " + p);

            robot.mouseMove(p.x , p.y);
            robot.waitForIdle();
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();

            Color color = robot.getPixelColor(p.x - OFFSET_X, p.y - OFFSET_y);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            boolean red = color.getRed() > 0 && color.getGreen() == 0 && color.getBlue() == 0;
            if (!red) {
                System.err.println("Red: " + color.getRed() + "; Green: " + color.getGreen() + "; Blue: " + color.getBlue());
                Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                Rectangle screen = new Rectangle(0, 0, (int) screenSize.getWidth(), (int) screenSize.getHeight());
                BufferedImage img = robot.createScreenCapture(screen);
                javax.imageio.ImageIO.write(img, "png", new java.io.File("image.png"));
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
