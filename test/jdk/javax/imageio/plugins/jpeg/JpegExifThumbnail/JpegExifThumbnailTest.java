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

/**
 * @test
 * @bug 8160327
 * @summary Test verifies thumbnails are read from sample JPEG images
 *          that are encoded using Exif file format.
 * @run     main JpegExifThumbnailTest
 */


import org.w3c.dom.Node;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.event.IIOReadProgressListener;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataFormatImpl;
import javax.imageio.stream.ImageInputStream;

public class JpegExifThumbnailTest {

    private static void assertBoolean(boolean b, String errorMsg) {
        if (!b) {
            throw new Error(errorMsg);
        }
    }

    private static void assertEquals(int expected, int observed) {
        if (expected != observed) {
            throw new Error("expected " + expected + ", but observed " + observed);
        }
    }

    private static void assertEquals(Node node, String attributeName, int expectedAttributeValue) {
        String s = node.getAttributes().getNamedItem(attributeName).getNodeValue();
        if (!Integer.toString(expectedAttributeValue).equals(s))
            throw new Error("expected \"" + attributeName + "\" to be " + expectedAttributeValue + ", but observed " + s);
    }

    static class MyListener implements IIOReadProgressListener {

        boolean thumbnailStarted = false;
        boolean thumbnailCompleted = false;
        float thumbnailPercent = -1;

        @Override
        public void sequenceStarted(ImageReader source, int minIndex) {
            throw new Error("sequenceStarted should not be called");
        }

        @Override
        public void sequenceComplete(ImageReader source) {
            throw new Error("sequenceComplete should not be called");
        }

        @Override
        public void imageStarted(ImageReader source, int imageIndex) {
            throw new Error("imageStarted should not be called");
        }

        @Override
        public void imageProgress(ImageReader source, float percentageDone) {
            throw new Error("imageProgress should not be called");
        }

        @Override
        public void imageComplete(ImageReader source) {
            throw new Error("imageComplete should not be called");
        }

        @Override
        public void thumbnailStarted(ImageReader source, int imageIndex, int thumbnailIndex) {
            if (thumbnailStarted || thumbnailCompleted || thumbnailPercent >= 0)
                throw new Error();
            thumbnailStarted = true;
        }

        @Override
        public void thumbnailProgress(ImageReader source, float percentageDone) {
            if (!thumbnailStarted || thumbnailCompleted || thumbnailPercent >= percentageDone)
                throw new Error();
            thumbnailPercent = percentageDone;
        }

        @Override
        public void thumbnailComplete(ImageReader source) {
            if (!thumbnailStarted || thumbnailCompleted)
                throw new Error();
            thumbnailCompleted = true;
        }

        @Override
        public void readAborted(ImageReader source) {
            throw new Error("readAborted should not be called");
        }
    }

    public static void main (String[] args) throws IOException {
        // all the timestamps were verified in a 3rd party app (such as Preview on Mac)

        // this is little endian, and uses a compression type 0x00000006
        new JpegExifThumbnailTest("SV650.jpg",
                2015,6,24,10,3,55,
                new Dimension(160, 120)).run();

        // This Exif thumbnail is uncompressed (a TIFF). The exif marker uses
        // a compression type of 0x00010000, and it is big endian
        new JpegExifThumbnailTest("uncompressed-exif-thumbnail.jpg",
                2025,2,24,14,22,36,
                new Dimension(80, 60)).run();

        // This includes a JFIF thumbnail and an EXIF thumbnail.
        new JpegExifThumbnailTest("jfif-and-exif.jpg",
                2025,2,21,1,14,51,
                new Dimension(150, 200),
                new Dimension(120, 160)).run();

        // this includes Exif data, but the `compression` tag is 0x60000 instead of 0x6,
        // also it doesn't include any date
        new JpegExifThumbnailTest("plastic-wrap.jpg",
                -1, -1, -1, -1, -1, -1,
                new Dimension(200, 132)).run();

        // here the timestamp doesn't match our parser, so we'll ignore it
        new JpegExifThumbnailTest("bad-timestamp.jpg",
                -1, -1, -1, -1, -1, -1).run();

        // this ImageFileDirectory doesn't terminate like it should. We need to
        // not fail with an EOFException in this case:
        new JpegExifThumbnailTest("unusual-ImageFileDirectory.jpeg",
                2007, 11, 10, 21, 23, 4).run();

        // This file is a replica of SV650.jpg, except the 14th byte was changed
        // so ExifMarkerSegment's constructor throws a "Bad magic number" exception.
        // The expected behavior here is: we do NOT catch an exception, because internally
        // JPEGMetaData just recorded a generic MarkerSegment instead of an ExifMarkerSegment
        new JpegExifThumbnailTest("corrupt-magic-number.jpg",
                -1, -1, -1, -1, -1, -1).run();

        // This file is set up so ImageFileDirectory X points to Y,
        // then Y points back to X. An early draft of the ExifMarkerSegment
        // class would get stuck parsing this loop. (After a few minutes this
        // would probably fail with an OutOfMemoryError as the list of IFDs
        // grew too large.)
        // The expected behavior here is to stop after reading 2 IFDs, and
        // otherwise read the JPEG as usual
        new JpegExifThumbnailTest("malicious-looping-IFD.jpg",
                2025, 2, 16, 15, 48, 34,
                new Dimension(60, 80)).run();
    }

    final String filename;
    final Dimension[] thumbnailSizes;
    final int year, month, day, hour, minute, second;

    JpegExifThumbnailTest(String filename,
                          int year, int month, int day,
                          int hour, int minute, int second,
                          Dimension... thumbnailSizes) {
        this.filename = filename;
        this.thumbnailSizes = thumbnailSizes;
        this.year = year;
        this.month = month;
        this.day = day;
        this.hour = hour;
        this.minute = minute;
        this.second = second;
    }

    public void run() throws IOException {
        String sep = System.getProperty("file.separator");
        String dir = System.getProperty("test.src", ".");
        String filePath = dir+sep+filename;
        File file = new File(filePath);

        if (!file.exists())
            throw new IOException("missing test file: " + file.getPath());
        System.out.println("Testing " + file.getPath());

        ImageReader reader = getJPEGImageReader();
        ImageInputStream stream = ImageIO.createImageInputStream(file);
        reader.setInput(stream);

        int thumbnailCount = reader.getNumThumbnails(0);
        if (thumbnailCount != thumbnailSizes.length)
            throw new Error("expected " + thumbnailSizes.length + " thumbnails, but observed " + thumbnailCount);

        for (int a = 0; a < thumbnailSizes.length; a++) {
            MyListener listener = new MyListener();
            reader.addIIOReadProgressListener(listener);
            try {
                int w = reader.getThumbnailWidth(0, a);
                int h = reader.getThumbnailHeight(0, a);
                System.out.println("\tthumbnail: " + w + "x" + h);
                assertEquals(thumbnailSizes[a].width, w);
                assertEquals(thumbnailSizes[a].height, h);

                BufferedImage bi = reader.readThumbnail(0, a);
                assertEquals(thumbnailSizes[a].width, bi.getWidth());
                assertEquals(thumbnailSizes[a].height, bi.getHeight());

                assertBoolean(listener.thumbnailStarted, "the IIOReadProgressListener was not notified that the thumbnail started");
                assertBoolean(listener.thumbnailCompleted, "the IIOReadProgressListener was not notified that the thumbnail completed");
            } finally {
                reader.removeIIOReadProgressListener(listener);
            }
        }

        IIOMetadata metadata = reader.getImageMetadata(0);
        Node root = metadata.getAsTree(IIOMetadataFormatImpl.standardMetadataFormatName);
        Node docNode = findChild(root, "Document");
        Node timeNode = findChild(docNode, "ImageCreationTime");

        if (timeNode == null) {
            // this is OK, it means there's no ImageCreationTime
            assertEquals(-1, year);
            assertEquals(-1, month);
            assertEquals(-1, day);
            assertEquals(-1, hour);
            assertEquals(-1, minute);
            assertEquals(-1, second);
        } else {
            StringBuilder sb = new StringBuilder();
            for (int b = 0; b < timeNode.getAttributes().getLength(); b++) {
                sb.append(timeNode.getAttributes().item(b).getNodeName() + "=" +
                        timeNode.getAttributes().item(b).getNodeValue() + " ");
            }
            System.out.println("\tImageCreationTime: " + sb.toString().trim());

            assertEquals(timeNode, "year", year);
            assertEquals(timeNode, "month", month);
            assertEquals(timeNode, "day", day);
            assertEquals(timeNode, "hour", hour);
            assertEquals(timeNode, "minute", minute);
            assertEquals(timeNode, "second", second);
        }
    }

    private static Node findChild(Node node, String nodeName) {
        if (node == null) {
            return null;
        }
        Node child = node.getFirstChild();
        while (child != null) {
            if (child.getNodeName().equals(nodeName))
                return child;
            child = child.getNextSibling();
        }
        return null;
    }

    private static ImageReader getJPEGImageReader() {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("jpeg");
        ImageReader reader;
        while(readers.hasNext()) {
            reader = readers.next();
            if(reader.canReadRaster()) {
                return reader;
            }
        }
        return null;
    }
}