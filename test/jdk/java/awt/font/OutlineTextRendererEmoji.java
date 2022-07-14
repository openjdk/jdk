/*
 * Copyright 2021 JetBrains s.r.o.
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
 * @summary Checks that emoji rendered via glyph cache and bypassing it look similar.
 */

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

public class OutlineTextRendererEmoji {
    private static final int IMG_WIDTH = 84;
    private static final int IMG_HEIGHT = 84;
    private static final int EMOJI_X = 7;
    private static final int EMOJI_Y = 70;
    private static final int FONT_SIZE = 70;
    private static final String EMOJI = "\ud83d\udd25"; // Fire

    private static final int CHECK_RADIUS = 10; // In pixels
    private static final double CHECK_TOLERANCE = 15; // Euclidean distance between colors

    public static void main(String[] args) throws Exception {
        BufferedImage small = new BufferedImage(IMG_WIDTH, IMG_HEIGHT, BufferedImage.TYPE_INT_RGB);
        BufferedImage big = new BufferedImage(IMG_WIDTH*2, IMG_HEIGHT*2, BufferedImage.TYPE_INT_RGB);
        drawEmoji(small, EMOJI_X, EMOJI_Y, FONT_SIZE);
        drawEmoji(big, EMOJI_X*2, EMOJI_Y*2, FONT_SIZE*2);
        checkEmoji(small, big);
    }

    private static void drawEmoji(Image img, int x, int y, int size) {
        Graphics g = img.getGraphics();
        g.setColor(Color.white);
        g.fillRect(0, 0, img.getWidth(null), img.getHeight(null));
        g.setFont(new Font(Font.DIALOG, Font.PLAIN, size));
        g.drawString(EMOJI, x, y);
        g.dispose();
    }

    private static double distance(int rgb1, int rgb2) {
        double b = (rgb1 & 0xff) - (rgb2 & 0xff);
        double g = ((rgb1 >>> 8) & 0xff) - ((rgb2 >>> 8) & 0xff);
        double r = ((rgb1 >>> 16) & 0xff) - ((rgb2 >>> 16) & 0xff);
        double a = ((rgb1 >>> 24) & 0xff) - ((rgb2 >>> 24) & 0xff);
        return Math.sqrt(b*b + g*g + r*r + a*a);
    }

    private static int sampleRectAvg(BufferedImage img, int x, int y, int width, int height) {
        int xTo = x + width, yTo = y + height;
        if (x < 0) x = 0;
        if (y < 0) y = 0;
        if (xTo > img.getWidth()) xTo = img.getWidth();
        if (yTo > img.getHeight()) yTo = img.getHeight();

        int b = 0, g = 0, r = 0, a = 0;
        for (int i = x; i < xTo; i++) {
            for (int j = y; j < yTo; j++) {
                int c = img.getRGB(i, j);
                b += c & 0xff;
                g += (c >>> 8) & 0xff;
                r += (c >>> 16) & 0xff;
                a += (c >>> 24) & 0xff;
            }
        }
        int count = (xTo - x) * (yTo - y);
        b /= count;
        g /= count;
        r /= count;
        a /= count;
        return b | (g << 8) | (r << 16) | (a << 24);
    }

    private static void checkEmoji(BufferedImage small, BufferedImage big) throws Exception {
        boolean empty = true, match = true;
        for (int x = 0; x < small.getWidth(); x++) {
            for (int y = 0; y < small.getHeight(); y++) {
                int s = sampleRectAvg(small, x-CHECK_RADIUS, y-CHECK_RADIUS, 1+2*CHECK_RADIUS, 1+2*CHECK_RADIUS);
                int b = sampleRectAvg(big, (x-CHECK_RADIUS)*2, (y-CHECK_RADIUS)*2, 1+4*CHECK_RADIUS, 1+4*CHECK_RADIUS);
                if (s != -1 || b != -1) {
                    empty = false;
                    if (distance(s, b) > CHECK_TOLERANCE) match = false;
                }
            }
        }
        if (empty) {
            throw new Exception("Empty image");
        } if (!match) {
            ImageIO.write(small, "PNG", new File("OutlineTextRendererEmoji-small.png"));
            ImageIO.write(big, "PNG", new File("OutlineTextRendererEmoji-big.png"));
            throw new Exception("Images mismatch");
        }
    }
}
