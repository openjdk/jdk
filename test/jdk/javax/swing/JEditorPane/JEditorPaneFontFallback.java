/*
 * Copyright 2022 JetBrains s.r.o.
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
 * @test
 * @bug 8185261
 * @summary Tests that font fallback works reliably in JEditorPane
 */

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

public class JEditorPaneFontFallback {
    public static final char CHINESE_CHAR = '\u4e2d';

    public static void main(String[] args) throws Exception {
        String fontFamily = findSuitableFont();
        if (fontFamily == null) {
            System.out.println("No suitable fonts, test cannot be performed");
            return;
        }
        System.out.println("Fount font: " + fontFamily);
        BufferedImage img1 = renderJEditorPaneInSubprocess(fontFamily, false);
        BufferedImage img2 = renderJEditorPaneInSubprocess(fontFamily, true);
        if (!imagesAreEqual(img1, img2)) {
            throw new RuntimeException("Unexpected rendering in JEditorPane");
        }
    }

    private static String findSuitableFont() {
        String[] familyNames = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        for (String familyName : familyNames) {
            if (!familyName.contains("'") && !familyName.contains("<") && !familyName.contains("&")) {
                Font font = new Font(familyName, Font.PLAIN, 1);
                if (!font.canDisplay(CHINESE_CHAR)) return familyName;
            }
        }
        return null;
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

    private static BufferedImage renderJEditorPaneInSubprocess(String fontFamilyName, boolean afterFontInfoCaching)
            throws Exception {
        String tmpFileName = "image.png";
        int exitCode = Runtime.getRuntime().exec(new String[]{
                System.getProperty("java.home") + File.separator + "bin" + File.separator + "java",
                "-cp",
                System.getProperty("test.classes", "."),
                JEditorPaneRenderer.class.getName(),
                fontFamilyName,
                Boolean.toString(afterFontInfoCaching),
                tmpFileName
        }).waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Sub-process exited abnormally: " + exitCode);
        }
        return ImageIO.read(new File(tmpFileName));
    }
}

class JEditorPaneRenderer {
    private static final int FONT_SIZE = 12;
    private static final int WIDTH = 20;
    private static final int HEIGHT = 20;

    public static void main(String[] args) throws Exception {
        String fontFamily = args[0];
        JEditorPane pane = new JEditorPane("text/html",
                "<html><head><style>body {font-family:'" + fontFamily + "'; font-size:" + FONT_SIZE +
                "pt;}</style></head><body>" + JEditorPaneFontFallback.CHINESE_CHAR + "</body></html>");
        pane.setSize(WIDTH, HEIGHT);
        if (Boolean.parseBoolean(args[1])) pane.getFontMetrics(new Font(fontFamily, Font.PLAIN, FONT_SIZE));
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        pane.paint(g);
        g.dispose();
        ImageIO.write(img, "png", new File(args[2]));
    }
}
