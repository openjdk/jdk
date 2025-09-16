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

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ImageConsumer;
import java.awt.image.IndexColorModel;
import java.net.URL;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This compares the last frame of ImageIO's rendering of a gif with the
 * ToolkitImage's rendering.
 * <p>
 * This is intended to serve as a helper class for more specific test cases.
 */
public class GifComparison {

    /**
     * This inspects the last frame of a gif and throws an Error / Exception
     * if ImageIO and ToolkitImage produce different BufferedImage renderings.
     *
     * @param srcURL the URL of the image to inspect
     *
     * @return the last frame encoded as a TYPE_INT_ARGB image.
     *         <p>
     *         Unit tests may further inspect this image to make sure certain
     *         conditions are met.
     */
    public static BufferedImage run(URL srcURL) throws Throwable {
        System.out.println("Comparing ImageIO vs ToolkitImage rendering of " +
                srcURL);
        ImageIOModel ioModel = new ImageIOModel(srcURL);
        AWTModel awtModel = new AWTModel(srcURL);

        BufferedImage lastImage = null;

        int a = ioModel.frames.size() - 1;
        BufferedImage ioImg = ioModel.getFrame(a);
        BufferedImage awtImage = awtModel.getFrame(a);

        lastImage = awtImage;

        if (!(ioImg.getWidth() == awtImage.getWidth() &&
                ioImg.getHeight() == awtImage.getHeight()))
            throw new Error("These images are not the same size: " +
                    ioImg.getWidth() + "x" + ioImg.getHeight() + " vs " +
                    awtImage.getWidth() + "x" + awtImage.getHeight());

        for (int y = 0; y < ioImg.getHeight(); y++) {
            for (int x = 0; x < ioImg.getWidth(); x++) {
                int argb1 = ioImg.getRGB(x, y);
                int argb2 = awtImage.getRGB(x, y);

                int alpha1 = (argb1 & 0xff000000) >> 24;
                int alpha2 = (argb2 & 0xff000000) >> 24;
                if (alpha1 == 0 && alpha2 == 0) {
                    continue;
                } else if (alpha1 == 0 || alpha2 == 0) {
                    throw new Error("pixels at (" + x + ", " + y +
                            ") have different opacities: " +
                            Integer.toUnsignedString(argb1, 16) + " vs " +
                            Integer.toUnsignedString(argb2, 16));
                }
                int rgb1 = argb1 & 0xffffff;
                int rgb2 = argb2 & 0xffffff;
                if (rgb1 != rgb2) {
                    throw new Error("pixels at (" + x + ", " + y +
                            ") have different opaque RGB values: " +
                            Integer.toUnsignedString(rgb1, 16) + " vs " +
                            Integer.toUnsignedString(rgb2, 16));
                }
            }
        }
        System.out.println("Passed");
        return lastImage;
    }
}

/**
 * This identifies frames of a GIF image using ImageIO classes.
 */
class ImageIOModel {

    record Frame(int x, int y, int w, int h, String disposalMethod,
                 int transparentColorIndex) {}

    private final URL url;
    private int width, height;

    final List<Frame> frames = new LinkedList<>();
    private Color backgroundColor;

    ImageIOModel(URL url) throws Exception {
        this.url = url;

        ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
        try {
            initialize(reader);
        } finally {
            reader.dispose();
        }
    }

    private void initialize(ImageReader reader) throws Exception {
        reader.setInput(ImageIO.createImageInputStream(url.openStream()));
        IIOMetadata metadata = reader.getStreamMetadata();
        IIOMetadataNode globalRoot = (IIOMetadataNode) metadata.getAsTree(
                metadata.getNativeMetadataFormatName());

        NodeList globalScreenDescriptor = globalRoot.getElementsByTagName(
                "LogicalScreenDescriptor");
        if (globalScreenDescriptor.getLength() > 0) {
            IIOMetadataNode screenDescriptor = (IIOMetadataNode)
                    globalScreenDescriptor.item(0);

            if (screenDescriptor != null) {
                width = Integer.parseInt(
                        screenDescriptor.getAttribute("logicalScreenWidth"));
                height = Integer.parseInt(
                        screenDescriptor.getAttribute("logicalScreenHeight"));
            }
        }

        NodeList globalColorTable = globalRoot.getElementsByTagName(
                "GlobalColorTable");
        if (globalColorTable.getLength() > 0) {
            IIOMetadataNode colorTable = (IIOMetadataNode)
                    globalColorTable.item(0);

            if (colorTable != null) {
                String bgIndex = colorTable.getAttribute(
                        "backgroundColorIndex");
                IIOMetadataNode colorEntry = (IIOMetadataNode)
                        colorTable.getFirstChild();
                while (colorEntry != null) {
                    if (colorEntry.getAttribute("index").equals(bgIndex)) {
                        int red = Integer.parseInt(colorEntry.getAttribute(
                                "red"));
                        int green = Integer.parseInt(colorEntry.getAttribute(
                                "green"));
                        int blue = Integer.parseInt(colorEntry.getAttribute(
                                "blue"));

                        backgroundColor = new Color(red, green, blue);
                        break;
                    }

                    colorEntry = (IIOMetadataNode) colorEntry.getNextSibling();
                }
            }
        }

        int frameCount = reader.getNumImages(true);

        for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
            IIOMetadataNode root = (IIOMetadataNode) reader.
                    getImageMetadata(frameIndex).
                    getAsTree("javax_imageio_gif_image_1.0");
            IIOMetadataNode gce = (IIOMetadataNode) root.
                    getElementsByTagName("GraphicControlExtension").item(0);
            NodeList children = root.getChildNodes();
            int transparentColorIndex = -1;
            if ("TRUE".equalsIgnoreCase(gce.getAttribute(
                    "transparentColorFlag"))) {
                transparentColorIndex = Integer.parseInt(gce.getAttribute(
                        "transparentColorIndex"));
            }

            String disposalMethodStr = gce.getAttribute("disposalMethod");

            int frameX = 0;
            int frameY = 0;
            int frameWidth = width;
            int frameHeight = height;

            for (int nodeIndex = 0; nodeIndex < children.getLength();
                 nodeIndex++) {
                Node nodeItem = children.item(nodeIndex);

                if (nodeItem.getNodeName().equals("ImageDescriptor")) {
                    NamedNodeMap map = nodeItem.getAttributes();

                    frameX = Integer.parseInt(map.getNamedItem(
                            "imageLeftPosition").getNodeValue());
                    frameY = Integer.parseInt(map.getNamedItem(
                            "imageTopPosition").getNodeValue());
                    frameWidth = Integer.parseInt(map.getNamedItem(
                            "imageWidth").getNodeValue());
                    frameHeight = Integer.parseInt(map.getNamedItem(
                            "imageHeight").getNodeValue());
                    width = Math.max(width, frameX + frameWidth);
                    height = Math.max(height, frameY + frameHeight);
                }
            }

            frames.add(new Frame(frameX, frameY, frameWidth, frameHeight,
                    disposalMethodStr, transparentColorIndex));
        }
    }

    public BufferedImage getFrame(int frameIndex) throws Exception {
        ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
        reader.setInput(ImageIO.createImageInputStream(url.openStream()));
        try {
            BufferedImage image = new BufferedImage(width, height,
                    BufferedImage.TYPE_INT_ARGB);
            BufferedImage previousImage = null;

            for (int a = 0; a <= frameIndex; a++) {
                Frame f = frames.get(a);
                if (Objects.equals(f.disposalMethod, "restoreToPrevious")) {
                    if (previousImage == null) {
                        previousImage = new BufferedImage(image.getWidth(),
                                image.getHeight(),
                                BufferedImage.TYPE_INT_ARGB);
                    }
                }

                if (previousImage != null) {
                    Graphics2D g = previousImage.createGraphics();
                    g.setComposite(AlphaComposite.Clear);
                    g.fillRect(0, 0, image.getWidth(), image.getHeight());
                    g.setComposite(AlphaComposite.SrcOver);
                    g.drawImage(image, 0, 0, null);
                    g.dispose();
                }

                BufferedImage frame = reader.read(a);
                Graphics2D g = image.createGraphics();
                g.drawImage(frame, f.x, f.y, null);
                g.dispose();
                IndexColorModel icm = (IndexColorModel) frame.getColorModel();

                if (a != frameIndex) {
                    switch (f.disposalMethod) {
                        case "restoreToBackgroundColor" -> {
                            g = image.createGraphics();
                            int t = icm.getTransparentPixel();
                            if (t >= 0 && t == f.transparentColorIndex) {
                                g.setComposite(AlphaComposite.Clear);
                                g.fillRect(f.x, f.y, f.w, f.h);
                            } else {
                                g.setColor(backgroundColor);
                                g.fillRect(f.x, f.y, f.w, f.h);
                            }
                            g.dispose();
                        }
                        case "restoreToPrevious" -> {
                            g = image.createGraphics();
                            g.setComposite(AlphaComposite.Clear);
                            g.fillRect(f.x, f.y, f.w, f.h);
                            g.setComposite(AlphaComposite.SrcOver);
                            g.drawImage(previousImage, f.x, f.y,
                                    f.x + f.w, f.y + f.h,
                                    f.x, f.y,
                                    f.x + f.w, f.y + f.h, null);
                            g.dispose();
                        }
                    }
                }
            }

            return image;
        } finally {
            reader.dispose();
        }
    }
}


/**
 * This identifies frames of a GIF image using ToolkitImage / ImageProducer.
 */
class AWTModel {
    private final URL url;

    AWTModel(URL url) {
        this.url = url;
    }

    public BufferedImage getFrame(int frameIndex) {
        // Unfortunately the AWT gif decoder calls Thread.sleep if the frame
        // delay is non-zero. So this method may take a long time to render a
        // frame simply because the decoder is calling Thread.sleep constantly.

        Image image = Toolkit.getDefaultToolkit().createImage(url);

        AtomicReference<BufferedImage> returnValue = new AtomicReference<>();
        Semaphore semaphore = new Semaphore(1);
        semaphore.acquireUninterruptibly();
        image.getSource().startProduction(new ImageConsumer() {
            BufferedImage bi;
            int frameCtr = 0;

            @Override
            public void setDimensions(int width, int height) {
                bi = new BufferedImage(width, height,
                        BufferedImage.TYPE_INT_ARGB);
            }

            @Override
            public void setProperties(Hashtable<?, ?> props) {}

            @Override
            public void setColorModel(ColorModel model) {}

            @Override
            public void setHints(int hintflags) {}

            @Override
            public void setPixels(int x, int y, int w, int h,
                                  ColorModel model, byte[] pixels, int off,
                                  int scansize) {
                try {
                    final int yMax = y + h;
                    final int xMax = x + w;

                    IndexColorModel icm = (IndexColorModel) model;
                    int[] colorModelRGBs = new int[icm.getMapSize()];
                    icm.getRGBs(colorModelRGBs);
                    int[] argbRow = new int[bi.getWidth()];

                    for (int y_ = y; y_ < yMax; y_++) {
                        int i = y_ * scansize + off;
                        for (int x_ = x; x_ < xMax; x_++, i++) {
                            int pixel = pixels[i] & 0xff;
                            argbRow[x_ - x] = colorModelRGBs[pixel];
                        }
                        bi.getRaster().setDataElements(x, y_, w, 1, argbRow);
                    }
                } catch (RuntimeException e) {
                    // we don't expect this to happen, but if something goes
                    // wrong nobody else will print our stacktrace for us:
                    e.printStackTrace();
                    throw e;
                }
            }

            @Override
            public void setPixels(int x, int y, int w, int h,
                                  ColorModel model, int[] pixels, int off,
                                  int scansize) {}

            @Override
            public void imageComplete(int status) {
                try {
                    frameCtr++;
                    if (frameCtr == frameIndex + 1) {
                        returnValue.set(bi);
                        semaphore.release();
                        // if we don't detach this consumer the producer will
                        // loop forever
                        image.getSource().removeConsumer(this);
                        image.flush();
                    }
                } catch(Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
        });

        semaphore.acquireUninterruptibly();

        return returnValue.get();
    }
}
