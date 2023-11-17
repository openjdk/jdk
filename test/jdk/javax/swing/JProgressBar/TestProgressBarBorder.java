/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8224261
 * @key headful
 * @library ../regtesthelpers
 * @build Util
 * @summary Verifies JProgressBar border is not painted when border
 *          painting is set to false
 * @run main TestProgressBarBorder
 */

import java.io.File;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import static java.awt.image.BufferedImage.TYPE_INT_RGB;

public class TestProgressBarBorder {
    private static JProgressBar progressBar;
    private static volatile boolean isImgSame;
    private static BufferedImage borderPaintedImg;
    private static BufferedImage borderNotPaintedImg;

    public static void main(String[] args) throws Exception {
        for (UIManager.LookAndFeelInfo laf :
                UIManager.getInstalledLookAndFeels()) {
            if (!laf.getName().contains("Nimbus") && !laf.getName().contains("GTK")) {
                continue;
            }
            System.out.println("Testing LAF: " + laf.getName());
            SwingUtilities.invokeAndWait(() -> {
                setLookAndFeel(laf);
                createAndShowUI();
            });

            borderPaintedImg = paintToImage(progressBar);
            progressBar.setBorderPainted(false);
            borderNotPaintedImg = paintToImage(progressBar);
            isImgSame = Util.compareBufferedImages(borderPaintedImg, borderNotPaintedImg);

            if (isImgSame) {
                ImageIO.write(borderPaintedImg, "png", new File("borderPaintedImg.png"));
                ImageIO.write(borderNotPaintedImg, "png", new File("borderNotPaintedImg.png"));
                throw new RuntimeException("JProgressBar border is painted when border\n" +
                        " painting is set to false");
            }
        }
    }

    private static void setLookAndFeel(UIManager.LookAndFeelInfo laf) {
        try {
            UIManager.setLookAndFeel(laf.getClassName());
        } catch (UnsupportedLookAndFeelException ignored) {
            System.out.println("Unsupported LAF: " + laf.getClassName());
        } catch (ClassNotFoundException | InstantiationException
                 | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static void createAndShowUI() {
        progressBar = new JProgressBar();
        progressBar.setSize(100,50);
        // set initial value
        progressBar.setValue(0);
        progressBar.setBorderPainted(true);
        progressBar.setStringPainted(true);
    }

    private static BufferedImage paintToImage(JComponent content) {
        BufferedImage im = new BufferedImage(content.getWidth(), content.getHeight(),
                TYPE_INT_RGB);
        Graphics g = im.getGraphics();
        content.paint(g);
        g.dispose();
        return im;
    }
}
