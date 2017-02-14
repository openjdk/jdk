/*
 * Copyright 2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* @test
 * @bug 8170552
 * @summary verify enabling text layout for complex text on macOS
 * @requires os.family == "mac"
 */

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

public class DiacriticsDrawingTest {
    private static final Font FONT = new Font("Menlo", Font.PLAIN, 12);
    private static final int IMAGE_WIDTH = 20;
    private static final int IMAGE_HEIGHT = 20;
    private static final int TEXT_X = 5;
    private static final int TEXT_Y = 15;

    public static void main(String[] args) {
        BufferedImage composed = drawString("\u00e1"); // latin small letter a with acute
        BufferedImage decomposed = drawString("a\u0301"); // same letter in decomposed form

        if (!imagesAreEqual(composed, decomposed)) {
            throw new RuntimeException("Text rendering is supposed to be the same");
        }
    }

    private static BufferedImage drawString(String text) {
        BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics g = image.createGraphics();
        g.setColor(Color.white);
        g.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
        g.setColor(Color.black);
        g.setFont(FONT);
        g.drawString(text, TEXT_X, TEXT_Y);
        g.dispose();
        return image;
    }

    private static boolean imagesAreEqual(BufferedImage i1, BufferedImage i2) {
        if (i1.getWidth() != i2.getWidth() || i1.getHeight() != i2.getHeight()) return false;
        for (int i = 0; i < i1.getWidth(); i++) {
            for (int j = 0; j < i1.getHeight(); j++) {
                if (i1.getRGB(i, j) != i2.getRGB(i, j)) {
                    return false;
                }
            }
        }
        return true;
    }
}
