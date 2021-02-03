/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import static java.awt.event.KeyEvent.VK_TAB;

/*
 * @test
 * @key headful
 * @bug 8216358
 * @summary [macos] The focus is invisible when tab to "Image Radio Buttons" and "Image CheckBoxes"
 * @library ../../regtesthelpers/
 * @library /lib/client/
 * @build Util
 * @build ExtendedRobot
 * @run main ImageCheckboxTest
 */

public class ImageCheckboxTest {
    private static JFrame frame;
    private static JButton testButton;
    private static int locx, locy, frw, frh;

    public static void main(String[] args) throws Exception {
        new ImageCheckboxTest().performTest();
    }

    public void performTest() throws Exception {
        try {
            BufferedImage imageFocus1 = null;
            BufferedImage imageFocus2 = null;
            ExtendedRobot robot = new ExtendedRobot();

            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame("Test frame");
                JPanel panel = new JPanel();
                panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
                testButton = new JButton("Start");
                panel.add(testButton);
                for (int i = 1; i < 6; i++) {
                    JCheckBox cb = new JCheckBox(" Box No. " + i, new MyIcon(Color.GREEN));
                    panel.add(cb);
                }

                frame.setLayout(new BorderLayout());
                frame.add(panel, BorderLayout.CENTER);
                frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                frame.setLocationRelativeTo(null);
                frame.pack();
                frame.setVisible(true);

            });
            robot.setAutoDelay(200);
            robot.delay(1000);
            robot.waitForIdle(1000);

            Rectangle bounds = frame.getBounds();
            Insets insets = frame.getInsets();
            locx = bounds.x + insets.left;
            locy = bounds.y + insets.top;
            frw = bounds.width - insets.left - insets.right;
            frh = bounds.height - insets.top - insets.bottom;

            Point btnLoc = testButton.getLocationOnScreen();
            robot.mouseMove(btnLoc.x + 10, btnLoc.y + 10);
            robot.click();

            robot.keyPress(VK_TAB);
            robot.keyRelease(VK_TAB);

            robot.delay(1000);

            imageFocus1 = robot.createScreenCapture(new Rectangle(locx, locy, frw, frh));

            robot.keyPress(VK_TAB);
            robot.keyRelease(VK_TAB);

            robot.delay(1000);

            imageFocus2 = robot.createScreenCapture(new Rectangle(locx, locy, frw, frh));

            if (Util.compareBufferedImages(imageFocus1, imageFocus2)) {
                ImageIO.write(imageFocus1, "png", new File("imageFocus1.png"));
                ImageIO.write(imageFocus2, "png", new File("imageFocus2.png"));
                throw new Exception("Changing focus is not visualized");
            }
        } finally {
            if (frame != null) {
                frame.dispose();
            }
        }
    }

    class MyIcon implements Icon {
        Color color;
        public MyIcon(Color color) {
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Color old = g.getColor();
            g.setColor(color);
            g.fillArc(x+2, y+2, 12, 12, 0, 360);
            g.setColor(old);
        }

        @Override
        public int getIconWidth() {
            return 18;
        }

        @Override
        public int getIconHeight() {
            return 18;
        }
    }
}
