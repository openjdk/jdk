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
 * @bug 8263583
 * @summary Checks that emoji characters are rendered
 * @requires (os.family == "mac")
 */

import java.awt.*;
import java.awt.image.BufferedImage;

public class MacEmoji {
    private static final int IMG_WIDTH = 20;
    private static final int IMG_HEIGHT = 20;

    public static void main(String[] args) {
        BufferedImage img = new BufferedImage(IMG_WIDTH, IMG_HEIGHT,
                                              BufferedImage.TYPE_INT_RGB);

        Graphics2D g = img.createGraphics();
        g.setColor(Color.white);
        g.fillRect(0, 0, IMG_WIDTH, IMG_HEIGHT);
        g.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
        g.drawString("\uD83D\uDE00" /* U+1F600 'GRINNING FACE' */, 2, 15);
        g.dispose();

        boolean rendered = false;
        for (int x = 0; x < IMG_WIDTH; x++) {
            for (int y = 0; y < IMG_HEIGHT; y++) {
                if (img.getRGB(x, y) != 0xFFFFFFFF) {
                    rendered = true;
                    break;
                }
            }
        }
        if (!rendered) {
            throw new RuntimeException("Emoji character wasn't rendered");
        }
    }
}
