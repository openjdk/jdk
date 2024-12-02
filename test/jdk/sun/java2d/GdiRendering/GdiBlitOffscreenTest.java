/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4725045
 * @key headful
 * @summary verifies that there are no artifacts due to using
 * GDI for copies to the back buffer (GDI should only be used
 * for copies to the screen)
 * @run main GdiBlitOffscreenTest
*/

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Point;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class GdiBlitOffscreenTest {

    static volatile JFrame f;
    static final int imageW = 100, imageH = 100, FW = 500, FH = 500;
    static volatile BufferedImage greenImage;

    public static void main(String[] args) throws Exception {

        // First, create an image.
        greenImage = new BufferedImage(imageW, imageH,
                                       BufferedImage.TYPE_INT_RGB);
        Graphics redG = greenImage.getGraphics();
        redG.setColor(Color.green);
        redG.fillRect(0, 0, imageW, imageH);
        redG.setColor(Color.white);
        redG.drawString("Passed!", 30, 80);

        Robot robot = new Robot();
        try {
            SwingUtilities.invokeAndWait(GdiBlitOffscreenTest::createUI);
            robot.delay(1000);
            robot.waitForIdle();
            Point p = f.getLocationOnScreen();
            Color c = robot.getPixelColor(p.x+FW/2, p.y+FH/2);
            if (!c.equals(Color.green)) {
                throw new RuntimeException("Color is " + c);
            }
        } finally {
            if (f != null) {
                SwingUtilities.invokeAndWait(f::dispose);
            }
        }
    }

    private static void createUI() {
        f = new JFrame("GdiBlitOffscreenTest");
        f.setSize(FW, FH);
        f.setVisible(true);

        // copy the image to the window.
        Graphics g = f.getGraphics();
        g.drawImage(greenImage, 0, 0, null);

        // Now, get on with the rest of the test
        JComponent app = new GdiBlitOffscreenTestComponent(imageW, imageH, greenImage);
        app.setSize(500, 500);
        f.getContentPane().add(app);
        f.validate();
        f.repaint();
    }
}

class GdiBlitOffscreenTestComponent extends JComponent {

    int imageW, imageH;
    Image theImage;

    public GdiBlitOffscreenTestComponent(int imageW, int imageH,
                                         Image theImage)
    {
        this.theImage = theImage;
        this.imageW = imageW;
        this.imageH = imageH;
    }

    public void paintComponent(Graphics g) {
        int imageX = (getWidth() - imageW) / 2;
        int imageY = (getHeight() - imageH) / 2;
        g.setColor(Color.blue);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(Color.red);
        g.fillRect(imageX, imageY, imageW, imageH);
        g.setColor(Color.white);
        g.drawString("Failed!", imageX + 30, imageY + 80);
        g.drawImage(theImage, imageX, imageY, null);
    }

}
