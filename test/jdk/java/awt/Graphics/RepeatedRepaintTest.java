/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;

/*
 * @test
 * @bug 4081126 4129709
 * @summary Test for proper repainting on multiprocessor systems.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual RepeatedRepaintTest
 */
public class RepeatedRepaintTest extends Frame {
    private Font font = null;
    private Image background;

    static String INSTRUCTIONS = """
            The frame next to this window called "AWT Draw Test" has
            some elements drawn on it. Move this window partially outside of the
            screen bounds and then drag it back. Repeat it couple of times.
            Drag the instructions window over the frame partially obscuring it.
            If after number of attempts the frame content stops repainting
            press "Fail", otherwise press "Pass".
            """;

    public RepeatedRepaintTest() {
        setTitle("AWT Draw Test");
        setSize(300, 300);
        background = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics g = background.getGraphics();
        g.setColor(Color.black);
        g.fillRect(0, 0, 300, 300);
        g.dispose();
    }

    public void paint(Graphics g) {
        Dimension dim = this.getSize();
        super.paint(g);
        g.drawImage(background, 0, 0, dim.width, dim.height, null);
        g.setColor(Color.white);
        if (font == null) {
            font = new Font("SansSerif", Font.PLAIN, 24);
        }
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        String message = "Draw Test";
        g.drawString(message, (dim.width / 2) - (metrics.stringWidth(message) / 2),
                (dim.height / 2) + (metrics.getHeight() / 2));

        int counter = 50;
        for (int i = 0; i < 50; i++) {
            counter += 4;
            g.drawOval(counter, 50, i, i);
        }

        counter = 20;
        for (int i = 0; i < 100; i++) {
            counter += 4;
            g.drawOval(counter, 150, i, i);
        }
        g.setColor(Color.black);
        g.drawLine(0, dim.height - 25, dim.width, dim.height - 25);
        g.setColor(Color.gray);
        g.drawLine(0, dim.height - 24, dim.width, dim.height - 24);
        g.setColor(Color.lightGray);
        g.drawLine(0, dim.height - 23, dim.width, dim.height - 23);
        g.fillRect(0, dim.height - 22, dim.width, dim.height);


        g.setXORMode(Color.blue);
        g.fillRect(0, 0, 25, dim.height - 26);
        g.setColor(Color.red);
        g.fillRect(0, 0, 25, dim.height - 26);
        g.setColor(Color.green);
        g.fillRect(0, 0, 25, dim.height - 26);
        g.setPaintMode();

        Image img = createImage(50, 50);
        Graphics imgGraphics = img.getGraphics();
        imgGraphics.setColor(Color.magenta);
        imgGraphics.fillRect(0, 0, 50, 50);
        imgGraphics.setColor(Color.yellow);
        imgGraphics.drawString("offscreen", 0, 20);
        imgGraphics.drawString("image", 0, 30);

        g.drawImage(img, dim.width - 100, dim.height - 100, Color.blue, null);

        g.setXORMode(Color.white);
        drawAt(g, 100, 100, 50, 50);
        drawAt(g, 105, 105, 50, 50);
        drawAt(g, 110, 110, 50, 50);
    }

    public void drawAt(Graphics g, int x, int y, int width, int height) {
        g.setColor(Color.magenta);
        g.fillRect(x, y, width, height);
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        PassFailJFrame.builder()
                .title("Repeated Repaint Test Instructions")
                .instructions(INSTRUCTIONS)
                .testUI(RepeatedRepaintTest::new)
                .build()
                .awaitAndCheck();
    }
}
