/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.ImageWriteParam;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * This constructs sample gif files used to test different combinations
 * of gif frame disposal methods and transparent pixel indices.
 */
public class GifBuilder {

    /**
     * Different disposal methods for gif frames. These names exactly
     * correspond to the String identifier ImageIO uses.
     */
    public enum Disposal {none, doNotDispose, restoreToBackgroundColor,
        restoreToPrevious};


    /**
     * @param disposal the frame disposal method
     * @param isFirstTableIndexTransparent if true then the transparent pixel
     *                                     is set to 0. If false then the
     *                                     transparent pixel is set to the
     *                                     last index.
     */
    public record FrameDescription(Disposal disposal, boolean
            isFirstTableIndexTransparent) {}

    /**
     * This creates a sample gif image based on a series of FrameDescriptions,
     * and the calls {@link GifComparison#run(URL)}
     */
    public static void test(FrameDescription... frameDescriptions)
            throws Throwable {
        File file = createTestFile(frameDescriptions);
        try {
            GifComparison.run(file.toURI().toURL());
        } finally {
            file.delete();
        }
    }

    private static File createTestFile(FrameDescription... frameDescriptions)
            throws IOException {
        Color[] colors = new Color[] {
                Color.red,
                Color.yellow,
                Color.green,
                Color.cyan
        };
        File file = File.createTempFile("GifBuilder", ".gif");
        ImageOutputStream ios = ImageIO.createImageOutputStream(file);
        ImageWriter gifWriter = ImageIO.getImageWritersByFormatName("GIF").
                next();
        gifWriter.setOutput(ios);
        ImageWriteParam wparam = gifWriter.getDefaultWriteParam();
        IIOMetadata streamMetadata = gifWriter.
                getDefaultStreamMetadata(wparam);
        gifWriter.prepareWriteSequence(streamMetadata);

        IndexColorModel icm = createIndexColorModel(colors, colors.length - 1);

        ImageTypeSpecifier s = ImageTypeSpecifier.createFromBufferedImageType(
                BufferedImage.TYPE_BYTE_INDEXED);
        IIOMetadata metadata = gifWriter.getDefaultImageMetadata(s, wparam);
        String metaFormatName = metadata.getNativeMetadataFormatName();

        for (FrameDescription frameDescription : frameDescriptions) {

            // prepare the image:
            int width = 100 + 50 * (icm.getMapSize() - 2);
            BufferedImage bi = new BufferedImage(width, 100,
                    BufferedImage.TYPE_BYTE_INDEXED, icm);
            Graphics2D g = bi.createGraphics();
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, 0, bi.getWidth(), bi.getHeight());
            g.setComposite(AlphaComposite.SrcOver);
            int x = 0;
            for (int a = 0; a < icm.getMapSize() - 1; a++) {
                if (a != icm.getTransparentPixel()) {
                    Color color = new Color(icm.getRGB(a));
                    g.setColor(color);
                    g.fillOval(x, 0, 100, 100);
                    x += 50;
                }
            }
            g.dispose();

            // wrap attributes for gifWriter:
            int transparentPixel = frameDescription.isFirstTableIndexTransparent
                    ? 0 : icm.getMapSize() - 1;
            IIOMetadata frameMetadata = gifWriter.getDefaultImageMetadata(
                    ImageTypeSpecifier.createFromRenderedImage(bi), wparam);
            IIOMetadataNode root = new IIOMetadataNode(metaFormatName);
            IIOMetadataNode gce = new IIOMetadataNode(
                    "GraphicControlExtension");
            gce.setAttribute("disposalMethod",
                    frameDescription.disposal.name());
            gce.setAttribute("userInputFlag", "FALSE");
            gce.setAttribute("transparentColorFlag", "TRUE");
            gce.setAttribute("delayTime", "0");
            gce.setAttribute("transparentColorIndex",
                    Integer.toString(transparentPixel));
            root.appendChild(gce);
            frameMetadata.mergeTree(metaFormatName, root);
            IIOImage img = new IIOImage(bi,  null, frameMetadata);
            gifWriter.writeToSequence(img, wparam);
        }
        gifWriter.endWriteSequence();
        ios.flush();
        ios.close();

        return file;
    }

    private static IndexColorModel createIndexColorModel(Color[] colors,
                                                 int transparentIndex) {
        byte[] r = new byte[colors.length];
        byte[] g = new byte[colors.length];
        byte[] b = new byte[colors.length];
        for (int a = 0; a < colors.length; a++) {
            r[a] = (byte) colors[a].getRed();
            g[a] = (byte) colors[a].getGreen();
            b[a] = (byte) colors[a].getBlue();
        }
        int bits = (int)(Math.log(colors.length) / Math.log(2) + .5);
        return new IndexColorModel(bits, colors.length, r, g, b,
                transparentIndex);
    }
}
