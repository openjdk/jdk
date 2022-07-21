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
 * @summary Checks that complex emoji are rendered with proper shaping.
 */

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.stream.Stream;

public class ComplexEmoji {
    private static final int IMG_WIDTH = 60;
    private static final int IMG_HEIGHT = 20;

    private static final String[] EMOJI = {
            "\ud83d\udd25", // Fire
            "\u2764\ufe0f", // Heart + color variation selector
            "\ud83e\udd18\ud83c\udffb", // Horns sign - white hand
            "\ud83d\udc41\ufe0f\u200d\ud83d\udde8\ufe0f", // Eye in speech bubble - ZWJ sequence
            "\uD83C\uDDE6\uD83C\uDDF6", // Antarctica flag
            "\ud83c\udff4\udb40\udc67\udb40\udc62\udb40\udc65\udb40\udc6e\udb40\udc67\udb40\udc7f", // England flag - tag sequence
    };

    public static void main(String[] args) {
        requireFont("Apple Color Emoji", "Segoe UI Emoji", "Noto Color Emoji");

        // Platform-specific tricks
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            EMOJI[4] = EMOJI[5] = null; // Flags and tags are not supported on Windows
        }

        BufferedImage img = new BufferedImage(IMG_WIDTH, IMG_HEIGHT, BufferedImage.TYPE_INT_RGB);
        String errors = "";
        for (int i = 0; i < EMOJI.length; i++) {
            String emoji = EMOJI[i];
            if (emoji == null) continue;
            drawEmoji(img, emoji);
            String error = checkEmoji(img);
            if (error != null) {
                errors += "\n#" + i + ": " + error;
                try {
                    ImageIO.write(img, "PNG", new File("ComplexEmoji" + i + ".png"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        if (!errors.isEmpty()) throw new RuntimeException(errors);
    }

    private static void drawEmoji(Image img, String emoji) {
        Graphics g = img.getGraphics();
        g.setColor(Color.white);
        g.fillRect(0, 0, IMG_WIDTH, IMG_HEIGHT);
        g.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
        // Try to trick shaper by prepending "A" letter
        // White on white will not be visible anyway
        g.drawString("A" + emoji, 2, 15);
        g.dispose();
    }

    private static String checkEmoji(BufferedImage img) {
        Point min = new Point(Integer.MAX_VALUE, Integer.MAX_VALUE);
        Point max = new Point(Integer.MIN_VALUE, Integer.MIN_VALUE);
        for (int x = 0; x < IMG_WIDTH; x++) {
            for (int y = 0; y < IMG_HEIGHT; y++) {
                int rgb = img.getRGB(x, y);
                if (rgb != -1) {
                    if (x < min.x) min.x = x;
                    if (y < min.y) min.y = y;
                    if (x > max.x) max.x = x;
                    if (y > max.y) max.y = y;
                }
            }
        }
        if (min.x >= max.x || min.y >= max.y) {
            return "Empty image";
        }
        int width = max.x - min.x + 1;
        int height = max.y - min.y + 1;
        double ratio = (double) width / (double) height;
        if (ratio > 1.5) {
            return "Too wide image, is there few glyphs instead of one?";
        }
        return null;
    }

    private static void requireFont(String macOS, String windows, String linux) {
        String os = System.getProperty("os.name").toLowerCase();
        String font;
        if (os.contains("mac")) font = macOS;
        else if (os.contains("windows")) font = windows;
        else if (os.contains("linux")) font = linux;
        else return;
        String[] fs = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        if (Stream.of(fs).noneMatch(s -> s.equals(font))) {
            throw new Error("Required font not found: " + font);
        }
    }
}
