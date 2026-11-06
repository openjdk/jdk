/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.File;
import java.io.IOException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOInvalidTreeException;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

import org.w3c.dom.NodeList;

/**
 * Utility class to create animated GIF images with the specified number
 * of frames and loop count. The frames are generated as the image is
 * written into the file.
 */
public final class GIFWriter implements AutoCloseable {
    private static final int WIDTH = 100;
    private static final int HEIGHT = 100;
    private static final int STROKE = 4;

    /**
     * The image that stores the current frame.
     */
    private final BufferedImage image;

    private final ImageWriter writer;
    private final ImageOutputStream outputStream;

    /** Number of frames. */
    private final int frameCount;
    /** Number of animation loops. */
    private final int loopCount;

    private int x;
    private int y;

    /**
     * Creates a {@code GIFWriter} object which allows creating
     * different types of GIF images, including animated.
     * @param fileName file name to write the image
     * @param frameCount number of frames to write
     * @param loopCount number of animation loops
     */
    public GIFWriter(final String fileName,
                     final int frameCount,
                     final int loopCount) throws IOException {
        if (frameCount <= 0) {
            throw new IllegalArgumentException("frameCount must be positive");
        }
        this.frameCount = frameCount;

        if (loopCount < 0) {
            throw new IllegalArgumentException("loopCount cannot be negative");
        }
        this.loopCount = loopCount;

        this.image = new BufferedImage(WIDTH, HEIGHT,
                                       BufferedImage.TYPE_BYTE_INDEXED,
                                       createIndexColorModel());
        createNextFrame();


        writer = ImageIO.getImageWritersByFormatName("GIF").next();
        outputStream = ImageIO.createImageOutputStream(new File(fileName));
        writer.setOutput(outputStream);
    }

    /**
     * Write the frames into the image stream.
     * @throws IOException if an error occurs during writing.
     */
    public void writeFrames() throws IOException {
        final ImageWriteParam writeParam = writer.getDefaultWriteParam();
        final ImageTypeSpecifier type =
                ImageTypeSpecifier.createFromRenderedImage(image);

        writer.prepareWriteSequence(
                writer.getDefaultStreamMetadata(writeParam));

        IIOMetadata meta = writer.getDefaultImageMetadata(type, writeParam);
        if (loopCount != 1) {
            setLoopCount(meta, loopCount);
        }
        writer.writeToSequence(new IIOImage(image, null, meta),
                               writeParam);

        for (int i = 1; i < frameCount; i++) {
            createNextFrame();

            meta = writer.getDefaultImageMetadata(type, writeParam);
            writer.writeToSequence(new IIOImage(image, null, meta),
                                   writeParam);
        }

        writer.endWriteSequence();
    }

    /**
     * Renders a frame into the image and
     * adjusts the {@code x } and {@code y} coordinates.
     */
    private void createNextFrame() {
        Graphics g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        g.setColor(Color.RED);
        g.fillRect(x, 0, STROKE, HEIGHT);

        g.setColor(Color.BLUE);
        g.fillRect(STROKE, y, WIDTH, STROKE);

        g.setColor(Color.GREEN);
        g.fillRect(x + WIDTH / 2, y + HEIGHT / 2,
                   STROKE, STROKE);

        x += STROKE;
        y += STROKE;

        x %= WIDTH;
        y %= HEIGHT;

        g.dispose();
    }

    private static void setLoopCount(IIOMetadata meta, int loopCount)
            throws IIOInvalidTreeException {
        String formatName = meta.getNativeMetadataFormatName();

        IIOMetadataNode loopNode = new IIOMetadataNode("ApplicationExtension");
        loopNode.setAttribute("applicationID", "NETSCAPE");
        loopNode.setAttribute("authenticationCode", "2.0");
        loopNode.setUserObject(new byte[] {
                0x01,
                (byte) (loopCount & 0xff),
                (byte) ((loopCount >> 8) & 0xff)
        });

        IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(formatName);

        IIOMetadataNode exts = getMetaNode(root, "ApplicationExtensions");
        exts.appendChild(loopNode);

        meta.setFromTree(formatName, root);
    }

    private static IIOMetadataNode getMetaNode(IIOMetadataNode root,
                                               String name) {
        NodeList nodes = root.getElementsByTagName(name);
        if (nodes.getLength() > 0) {
            return (IIOMetadataNode) nodes.item(0);
        }

        IIOMetadataNode node = new IIOMetadataNode(name);
        root.appendChild(node);
        return node;
    }

    /**
     * {@inheritDoc}
     * @throws Exception {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        outputStream.close();
        writer.dispose();
    }

    /**
     * Creates an {@code IndexColorModel} that contains five colors:
     * black, red, green, blue, and white.
     *
     * @return the {@code IndexColorModel} with the five colors
     */
    private static IndexColorModel createIndexColorModel() {
        byte[] r = {(byte)0x00, (byte)0xFF, (byte)0x00, (byte)0x00, (byte)0xFF};
        byte[] g = {(byte)0x00, (byte)0x00, (byte)0xFF, (byte)0x00, (byte)0xFF};
        byte[] b = {(byte)0x00, (byte)0x00, (byte)0x00, (byte)0xFF, (byte)0xFF};

        return new IndexColorModel(8, r.length,
                                   r, g, b);
    }

}
