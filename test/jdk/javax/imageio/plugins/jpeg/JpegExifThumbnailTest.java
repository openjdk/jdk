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


import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.event.IIOReadProgressListener;
import javax.imageio.stream.ImageInputStream;

public class JpegExifThumbnailTest {
    public static void main (String[] args) throws IOException {
        // this is little endian, and uses a compression type 0x00000006
        new JpegExifThumbnailTest("jdk_8160327-SV650.jpg",
                new Dimension(160, 120)).run();

        // this is big endian, and uses a compression type 0x00010000
        new JpegExifThumbnailTest("jdk_8160327-exif-rgb-thumbnail-sony-d700.jpg",
                new Dimension(80, 60)).run();

        // this includes Exif & JFIF data, but only a JFIF thumbnail
        new JpegExifThumbnailTest("jdk_8160327-jfif-jfif-and-exif-thumbnail-sharpshot-iphone.jpg",
                new Dimension(131, 122)).run();
    }

    boolean listener_thumbnailStarted;
    boolean listener_thumbnailCompleted;
    float listener_thumbnailPercent = - 1;

    final String filename;
    final Dimension[] thumbnailSizes;

    JpegExifThumbnailTest(String filename, Dimension... thumbnailSizes) throws IOException {
        this.filename = filename;
        this.thumbnailSizes = thumbnailSizes;
    }

    public void run() throws IOException {
        String sep = System.getProperty("file.separator");
        String dir = System.getProperty("test.src", ".");
        String filePath = dir+sep+filename;
        File file = new File(filePath);

        System.out.println("Testing " + file.getPath());

        ImageReader reader = getImageReader("JPEG");
        ImageInputStream stream = ImageIO.createImageInputStream(file);
        reader.setInput(stream);
        reader.addIIOReadProgressListener(new IIOReadProgressListener() {
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
                if (listener_thumbnailStarted || listener_thumbnailCompleted || listener_thumbnailPercent >= 0)
                    throw new Error();
                listener_thumbnailStarted = true;
            }

            @Override
            public void thumbnailProgress(ImageReader source, float percentageDone) {
                if (!listener_thumbnailStarted || listener_thumbnailCompleted || listener_thumbnailPercent >= percentageDone)
                    throw new Error();
                listener_thumbnailPercent = percentageDone;
            }

            @Override
            public void thumbnailComplete(ImageReader source) {
                if (!listener_thumbnailStarted || listener_thumbnailCompleted)
                    throw new Error();
                listener_thumbnailCompleted = true;
            }

            @Override
            public void readAborted(ImageReader source) {
                throw new Error("readAborted should not be called");
            }
        });

        int thumbnailCount = reader.getNumThumbnails(0);
        if (thumbnailCount != thumbnailSizes.length)
            throw new Error("expected " + thumbnailSizes.length + " thumbnails, but observed " + thumbnailCount);

        for (int a = 0; a < thumbnailSizes.length; a++) {
            int w = reader.getThumbnailWidth(0, a);
            int h = reader.getThumbnailHeight(0, a);

            if (w != thumbnailSizes[a].width)
                throw new Error("expected " + thumbnailSizes[a].width + " width, but observed " + w);
            if (h != thumbnailSizes[a].height)
                throw new Error("expected " + thumbnailSizes[a].height + " height, but observed " + h);

            BufferedImage bi = reader.readThumbnail(0, a);

            if (bi.getWidth() != thumbnailSizes[a].width)
                throw new Error("expected " + thumbnailSizes[a].width + " width, but observed " + bi.getWidth());
            if (bi.getHeight() != thumbnailSizes[a].height)
                throw new Error("expected " + thumbnailSizes[a].height + " height, but observed " + bi.getHeight());

            if (!listener_thumbnailStarted)
                throw new Error("the IIOReadProgressListener was not notified that the thumbnail started");
            if (!listener_thumbnailCompleted)
                throw new Error("the IIOReadProgressListener was not notified that the thumbnail completed");
        }
    }

    private static ImageReader getImageReader(String fileType) {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(fileType);
        ImageReader reader = null;
        while(readers.hasNext()) {
            reader = (ImageReader) readers.next();
            if(reader.canReadRaster()) {
                return reader;
            }
        }
        return null;
    }
}