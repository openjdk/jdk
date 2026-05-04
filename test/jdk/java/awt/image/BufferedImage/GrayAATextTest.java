/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4309915
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Check that Antialiased text drawn on a BYTE_GRAY image
 *              resolves the color correctly
 * @run main/manual GrayAATextTest
 */

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Panel;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public class GrayAATextTest extends Panel {

    public static final int WIDTH = 600;
    public static final int HEIGHT = 200;

    private static final String INSTRUCTIONS = """
        All of the strings in a given column should be drawn
        in the same color.  If the bug is present, then the
        Antialiased strings will all be of a fixed color that
        is not the same as the other strings in their column.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("GrayAATextTest Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int)INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(GrayAATextTest::createTestUI)
                .build()
                .awaitAndCheck();
    }

    public void paint(Graphics g) {
        BufferedImage bi = new BufferedImage(WIDTH, HEIGHT,
                                             BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2d = bi.createGraphics();
        g2d.setFont(new Font("Helvetica", Font.PLAIN, 24));
        g2d.setColor(Color.white);
        g2d.fillRect(0, 0, WIDTH / 2, HEIGHT);
        drawText(g2d, Color.black, "Black", 25);
        drawText(g2d, Color.lightGray, "Light Gray", 175);
        g2d.setColor(Color.black);
        g2d.fillRect(WIDTH / 2, 0, WIDTH / 2, HEIGHT);
        drawText(g2d, Color.white, "White", 325);
        drawText(g2d, Color.lightGray, "Light Gray", 475);
        g2d.dispose();
        g.drawImage(bi, 0, 0, null);
    }

    public void drawText(Graphics2D g2d, Color c, String colorname, int x) {
        g2d.setColor(c);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                             RenderingHints.VALUE_ANTIALIAS_OFF);
        g2d.drawString(colorname, x, 50);
        g2d.drawString("Aliased", x, 100);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                             RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.drawString("Antialiased", x, 150);
    }

    public Dimension getPreferredSize() {
        return new Dimension(WIDTH, HEIGHT);
    }

    private static Frame createTestUI() {
        Frame f = new Frame("GrayAATextTest Frame");
        f.add(new GrayAATextTest());
        f.setSize(WIDTH, HEIGHT);
        return f;
    }
}
