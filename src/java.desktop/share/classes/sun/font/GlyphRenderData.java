/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

package sun.font;

import jdk.internal.misc.Unsafe;

import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.image.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data for rendering any number of glyphs bypassing glyph cache.
 */
public class GlyphRenderData {

    public GeneralPath outline;
    public List<ColorLayer> colorLayers;
    public List<Bitmap> bitmaps;

    public GlyphRenderData() {}
    public GlyphRenderData(GlyphRenderData i) {
        if (i.outline != null) {
            outline = (GeneralPath) i.outline.clone();
        }
        if (i.colorLayers != null) {
            colorLayers = new ArrayList<>(i.colorLayers.size());
            for (ColorLayer l : i.colorLayers) {
                colorLayers.add(new ColorLayer(l.color, (GeneralPath) l.outline.clone()));
            }
        }
        if (i.bitmaps != null) {
            bitmaps = new ArrayList<>(i.bitmaps.size());
            for (Bitmap b : i.bitmaps) {
                bitmaps.add(new Bitmap(new AffineTransform(b.transform), b.image));
            }
        }
    }

    /**
     * @param i must not be used afterwards
     */
    public void merge(GlyphRenderData i) {
        if (i.outline != null) {
            if (outline == null) {
                outline = i.outline;
            } else {
                outline.append(i.outline.getPathIterator(null), false);
            }
        }
        if (i.colorLayers != null) {
            if (colorLayers == null) {
                colorLayers = i.colorLayers;
            } else {
                colorLayers.addAll(i.colorLayers);
            }
        }
        if (i.bitmaps != null) {
            if (bitmaps == null) {
                bitmaps = i.bitmaps;
            } else {
                bitmaps.addAll(i.bitmaps);
            }
        }
    }

    public void transform(AffineTransform transform) {
        if (outline != null) {
            outline.transform(transform);
        }
        if (colorLayers != null) {
            for (ColorLayer layer : colorLayers) {
                layer.outline.transform(transform);
            }
        }
        if (bitmaps != null) {
            for (Bitmap bitmap : bitmaps) {
                bitmap.transform.preConcatenate(transform);
            }
        }
    }

    public void draw(Graphics2D g) {
        if (outline != null) {
            g.fill(outline);
        }
        if (colorLayers != null) {
            Color color = g.getColor();
            for (ColorLayer layer : colorLayers) {
                g.setColor(layer.color == null ? color : layer.color);
                g.fill(layer.outline);
            }
            g.setColor(color);
        }
        if (bitmaps != null) {
            for (Bitmap bitmap : bitmaps) {
                g.drawImage(bitmap.image, bitmap.transform, null);
            }
        }
    }

    public record ColorLayer(Color color, GeneralPath outline) {}

    public record Bitmap(AffineTransform transform, Image image) {}

    // These methods exist for convenience and are called from native

    private void setColorLayersList(int capacity) {
        colorLayers = new ArrayList<>(capacity);
    }

    private void addColorLayers(GeneralPath outline) {
        colorLayers.add(new ColorLayer(null, outline));
    }

    private void addColorLayers(int r, int g, int b, int a, GeneralPath outline) {
        colorLayers.add(new ColorLayer(new Color(r, g, b, a), outline));
    }

    private static DirectColorModel colorModel(boolean premultiplied, int bits, int r, int g, int b, int a) {
        if (Unsafe.getUnsafe().isBigEndian()) {
            r = Integer.reverse(r) >>> (32 - bits);
            g = Integer.reverse(g) >>> (32 - bits);
            b = Integer.reverse(b) >>> (32 - bits);
            a = Integer.reverse(a) >>> (32 - bits);
        }
        return new DirectColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB),
                bits, r, g, b, a, premultiplied, DataBuffer.TYPE_INT);
    }
    private static final DirectColorModel[] BITMAP_COLOR_MODELS = {
            colorModel(false, 32, // macOS RGBA
                    0x000000ff,
                    0x0000ff00,
                    0x00ff0000,
                    0xff000000),
            colorModel(false, 32, // macOS ARGB
                    0x0000ff00,
                    0x00ff0000,
                    0xff000000,
                    0x000000ff),
            colorModel(true, 32, // Freetype BGRA
                    0x00ff0000,
                    0x0000ff00,
                    0x000000ff,
                    0xff000000)
    };
    private void addBitmap(double m00, double m10,
                           double m01, double m11,
                           double m02, double m12,
                           int width, int height, int pitch,
                           int colorModel, int[] data) {
        if (bitmaps == null) {
            bitmaps = new ArrayList<>();
        }
        DirectColorModel color = BITMAP_COLOR_MODELS[colorModel];
        DataBuffer buffer = new DataBufferInt(data, data.length);
        WritableRaster raster = Raster.createPackedRaster(buffer, width, height, pitch, color.getMasks(), null);
        BufferedImage image = new BufferedImage(color, raster, color.isAlphaPremultiplied(), null);
        bitmaps.add(new Bitmap(new AffineTransform(m00, m10, m01, m11, m02, m12), image));
    }
}
