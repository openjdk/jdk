/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.awt.image.ImageProducer;
import java.awt.image.MultiResolutionImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * @test
 * @bug 8282526
 * @summary Default icon is not painted properly
 * @run main MultiResolutionImageIcon
 */

public class MultiResolutionImageIcon extends Image implements MultiResolutionImage {
    BufferedImage img;

    public static void main(String[] args) {
        MultiResolutionImageIcon me = new MultiResolutionImageIcon();
        me.test();
    }

    public MultiResolutionImageIcon() {
        final String PATH_TO_FILE=MultiResolutionImageIcon.class.getResource("folder.png").getPath();
        try {
            img = ImageIO.read(new File(PATH_TO_FILE));
        } catch (IOException ioe) {
            throw new RuntimeException("Can't read image file. " + ioe);
        }
    }

    public void test() {
        ImageIcon icon = new ImageIcon();
        icon.setImage(this);

        BufferedImage test = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics g = test.createGraphics();
        icon.paintIcon(null, g, 0, 0);
        g.dispose();

        int transfers = 0;
        boolean last = true;
        for (int i = 0; i < 16; i++) {
            Color pixel = new Color(test.getRGB(12, i), true);
            if (isWhite(pixel) ^ last) {
                transfers++;
                last = !last;
            }
        }
        if (transfers < 8) {
            try {
                ImageIO.write(test, "png", new File("generated.png"));
            } catch (IOException ignore) {}
            throw new RuntimeException("Significant detail is lost in transition");
        }
    }

    public boolean isWhite(Color c) {
        return (c.getRed() + c.getGreen() + c.getBlue()) / 3 > 245;
    }

    @Override
    public Image getResolutionVariant(double destImageWidth, double destImageHeight) {
        return img;
    }

    @Override
    public List<Image> getResolutionVariants() {
        return Arrays.asList(img);
    }


    @Override
    public int getWidth(ImageObserver observer) {
        return 16;
    }

    @Override
    public int getHeight(ImageObserver observer) {
        return 16;
    }

    @Override
    public ImageProducer getSource() {
        return null;
    }

    @Override
    public Graphics getGraphics() {
        return img.getGraphics();
    }

    @Override
    public Object getProperty(String name, ImageObserver observer) {
        return null;
    }
}
