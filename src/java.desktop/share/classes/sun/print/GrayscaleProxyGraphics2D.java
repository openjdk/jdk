/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, BELLSOFT. All rights reserved.
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
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.awt.image.RenderedImage;
import java.awt.print.PrinterJob;

/**
 * Proxy class to print with grayscale.
 * Convert Colors, Paints and Images to the grayscale.
 *
 */
public class GrayscaleProxyGraphics2D extends ProxyGraphics2D {

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
    public void setBackground(Color color) {
        Color gcolor = getGrayscaleColor(color);
        super.setBackground(gcolor);
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
        } else if (paint == null) {
            super.setPaint(paint);
        } else {
            throw new IllegalArgumentException("Unsupported Paint");
        }
    }

    @Override
    public void drawRenderedImage(RenderedImage img, AffineTransform xform) {
        BufferedImage grayImage = new BufferedImage(img.getWidth() + img.getTileWidth(),
                img.getHeight() + img.getTileHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2 = grayImage.createGraphics();
        g2.drawRenderedImage(img, new AffineTransform());
        g2.dispose();
        super.drawRenderedImage(getGrayscaleImage(grayImage), xform);
    }

    @Override
    public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2,
                             Color bgcolor, ImageObserver observer) {
        return super.drawImage(getGrayscaleImage(img), dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, bgcolor, observer);
    }

    @Override
    public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2,
                             ImageObserver observer) {
        return super.drawImage(getGrayscaleImage(img), dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, observer);
    }

    @Override
    public boolean drawImage(Image img, int x, int y, int width, int height, Color bgcolor, ImageObserver observer) {
        return super.drawImage(getGrayscaleImage(img), x, y, width, height, bgcolor, observer);
    }

    @Override
    public boolean drawImage(Image img, int x, int y, Color bgcolor, ImageObserver observer) {
        return super.drawImage(getGrayscaleImage(img), x, y, bgcolor, observer);
    }

    @Override
    public boolean drawImage(Image img, int x, int y, int width, int height, ImageObserver observer) {
        return super.drawImage(getGrayscaleImage(img), x, y, width, height, observer);
    }

    @Override
    public boolean drawImage(Image img, int x, int y, ImageObserver observer) {
        return super.drawImage(getGrayscaleImage(img), x, y, observer);
    }

    @Override
    public void drawImage(BufferedImage img, BufferedImageOp op, int x, int y) {
        super.drawImage(getGrayscaleImage(img), op, x, y);
    }

    @Override
    public boolean drawImage(Image img, AffineTransform xform, ImageObserver obs) {
        return super.drawImage(getGrayscaleImage(img), xform, obs);
    }

    /**
     * Returns grayscale variant of the input Color
     * @param color color to transform to grayscale
     * @return grayscale color
     */
    private Color getGrayscaleColor(Color color) {
        if (color == null) {
            return null;
        }
        float[] gcolor = color.getComponents(ColorSpace.getInstance(ColorSpace.CS_GRAY), null);
        return switch (gcolor.length) {
            case 1 -> new Color(gcolor[0], gcolor[0], gcolor[0]);
            case 2 -> new Color(gcolor[0], gcolor[0], gcolor[0], gcolor[1]);
            default -> throw new IllegalArgumentException("Unknown grayscale color. " +
                    "Expected 1 or 2 components, received " + gcolor.length + " components.");
        };
    }

    /**
     * Converts Image to a grayscale
     * @param img colored image
     * @return grayscale BufferedImage
     */
    private BufferedImage getGrayscaleImage(Image img) {
        if (img == null) {
            return null;
        }
        BufferedImage grayImage = new BufferedImage(img.getWidth(null), img.getHeight(null),
                BufferedImage.TYPE_BYTE_GRAY);
        Graphics grayGraphics = grayImage.getGraphics();
        grayGraphics.drawImage(img, 0, 0, null);
        grayGraphics.dispose();
        return grayImage;
    }

}
