/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, BELLSOFT. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.print;


import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.TexturePaint;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.print.PrinterJob;

/**
 * Proxy class to print with grayscale.
 * Override methods:
 * <ul>
 *     <li>{@link #setColor(Color)}</li>
 *     <li>{@link #setPaint(Paint)}</li>
 * </ul>
 * and change input Colors and Paints to grayscale.
 * It uses {@link ColorConvertOp} to convert income colors and images to grayscale.
 *
 */
public class GrayscaleProxyGraphics2D extends ProxyGraphics2D {

    /**
     * Color converter
     */
    private final ColorConvertOp monochromeConverter =
            new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null);

    /**
     * buffered image is used to convert colors
     */
    private final BufferedImage monochromeImg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);

    /**
     * The new ProxyGraphics2D will forward all graphics
     * calls to 'graphics'.
     *
     * @param graphics
     * @param printerJob
     */
    public GrayscaleProxyGraphics2D(Graphics2D graphics, PrinterJob printerJob) {
        super(graphics, printerJob);
    }

    @Override
    public void setColor(Color c) {
        Color gcolor = getGrayscaleColor(c);
        super.setColor(gcolor);
    }

    @Override
    public void setPaint(Paint paint) {
        if (paint instanceof Color color) {
            super.setPaint(getGrayscaleColor(color));
        } else if (paint instanceof TexturePaint texturePaint) {
            super.setPaint(new TexturePaint(getGrayscaleImage(texturePaint.getImage()), texturePaint.getAnchorRect()));
        } else if (paint instanceof GradientPaint gradientPaint) {
            super.setPaint(new GradientPaint(gradientPaint.getPoint1(),
                    getGrayscaleColor(gradientPaint.getColor1()),
                    gradientPaint.getPoint2(),
                    getGrayscaleColor(gradientPaint.getColor2()),
                    gradientPaint.isCyclic()));
        } else if (paint instanceof LinearGradientPaint linearGradientPaint) {
            Color[] colors = new Color[linearGradientPaint.getColors().length];
            Color[] oldColors = linearGradientPaint.getColors();
            for (int i = 0; i < colors.length; i++) {
                colors[i] = getGrayscaleColor(oldColors[i]);
            }
            super.setPaint(new LinearGradientPaint(linearGradientPaint.getStartPoint(),
                    linearGradientPaint.getEndPoint(),
                    linearGradientPaint.getFractions(),
                    colors,
                    linearGradientPaint.getCycleMethod(),
                    linearGradientPaint.getColorSpace(),
                    linearGradientPaint.getTransform()
            ));
        } else if (paint instanceof RadialGradientPaint radialGradientPaint) {
            Color[] colors = new Color[radialGradientPaint.getColors().length];
            Color[] oldColors = radialGradientPaint.getColors();
            for (int i = 0; i < colors.length; i++) {
                colors[i] = getGrayscaleColor(oldColors[i]);
            }
            super.setPaint(new RadialGradientPaint(radialGradientPaint.getCenterPoint(),
                    radialGradientPaint.getRadius(),
                    radialGradientPaint.getFocusPoint(),
                    radialGradientPaint.getFractions(),
                    colors,
                    radialGradientPaint.getCycleMethod(),
                    radialGradientPaint.getColorSpace(),
                    radialGradientPaint.getTransform()));
        } else {
            super.setPaint(paint);
        }
    }

    /**
     * Returns grayscale variant of the input Color
     * @param color color to transform to grayscale
     * @return grayscale color
     */
    private Color getGrayscaleColor(Color color) {
        monochromeImg.setRGB(0, 0, color.getRGB());
        convertToMonochrome(monochromeImg);
        int[] data = monochromeImg.getData().getPixel(0, 0, new int[3]);
        Color grayColor = new Color(data[0], data[1], data[2]);
        return grayColor;
    }
    /**
     * Converts Image to a grayscale
     * @param img colored image
     * @return grayscale BufferedImage
     */
    private BufferedImage getGrayscaleImage(Image img) {
        BufferedImage grayImage = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
        Graphics grayGraphics = grayImage.getGraphics();
        grayGraphics.drawImage(img, 0, 0, null);
        grayGraphics.dispose();
        convertToMonochrome(grayImage);
        return grayImage;
    }

    /**
     * Convert provided colored BufferdImage to the grayscale image
     * @param img image to be converted
     */
    private void convertToMonochrome(BufferedImage img) {
        monochromeConverter.filter(img, img);
    }
}
