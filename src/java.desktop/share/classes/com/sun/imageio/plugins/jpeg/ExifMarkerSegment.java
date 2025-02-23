/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.imageio.plugins.jpeg;

import com.sun.imageio.plugins.tiff.TIFFImageReader;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * An Exif (Exchangeable Image File Format) APP1 (Application-Specific)
 * marker segment. This implementation only supports reading thumbnails
 * and the image creation time.
 */
class ExifMarkerSegment extends MarkerSegment {

    static class ImageFileDirectory implements Cloneable {
        static class Entry implements Cloneable {
            final int tagNumber, dataFormat;
            final long componentCount, fieldValue;

            Entry(ImageInputStream in) throws IOException {
                tagNumber = in.readUnsignedShort();
                dataFormat = in.readUnsignedShort();
                componentCount = in.readUnsignedInt();
                fieldValue = in.readUnsignedInt();
            }

            @Override
            public String toString() {
                return "Entry[ tagNumber: " + tagNumber +
                        ", dataFormat: " + dataFormat +
                        ", componentCount: " + componentCount +
                        ", fieldValue: " + fieldValue + "]";
            }
        }
        static final int[] bytesPerComponent = new int[] {1, 1, 1, 2, 4, 8, 1};

        Map<Integer, Entry> entriesByTag = new LinkedHashMap<>();
        long nextIFD;

        ImageFileDirectory(ImageInputStream in, long pos) throws IOException {
            in.seek(pos);
            int entryCount = in.readUnsignedShort();
            for (int a = 0; a < entryCount; a++) {
                Entry e = new Entry(in);
                entriesByTag.put(e.tagNumber, e);
            }

            // The next 4 bytes SHOULD be the position of the next IFD.

            // However in rare cases: the position of the next IFD header is missing. We can detect
            // this by checking to see if any of the IFD entries we just read appear where the
            // next IFD position *should* be:

            long streamPos = in.getStreamPosition();
            for (Entry e : entriesByTag.values()) {
                int byteLength = e.dataFormat < bytesPerComponent.length ?
                        (int) (e.componentCount * bytesPerComponent[e.dataFormat]) :
                        // this is an unknown data format, so let's just assume its 1 byte
                        1;
                if (byteLength > 4) {
                    long valuePos = e.fieldValue;
                    if (valuePos <= streamPos) {
                        nextIFD = 0;
                        return;
                    }
                }
            }

            nextIFD = in.readUnsignedInt();
        }

        int getTagValueAsInt(int tagID) {
            ImageFileDirectory.Entry e = entriesByTag.get(tagID);
            if (e == null) {
                return NO_VALUE;
            }
            return (int) e.fieldValue;
        }
    }

    private static final int NO_VALUE = -1;

    private static final int TIFF_BIG_ENDIAN = 0x4d4d;
    private static final int TIFF_MAGIC = 42;
    private static final int TIFF_TYPE_SHORT = 3;
    private static final int TAG_IMAGE_WIDTH = 256;
    private static final int TAG_IMAGE_HEIGHT = 257;
    private static final int TAG_DATE_TIME = 306;
    private static final int TAG_JPEG_INTERCHANGE_FORMAT = 513;
    private static final int TAG_JPEG_INTERCHANGE_FORMAT_LENGTH = 514;

    int thumbnailPos = -1;
    int thumbnailLength = -1;
    boolean isThumbnailJPEG;

    int thumbnailWidth = -1;
    int thumbnailHeight = -1;

    final long firstIFDOffset;
    final List<ImageFileDirectory> imageFileDirectories = new LinkedList<>();

    ExifMarkerSegment(MarkerSegment originalSegment) throws IOException {
        super(originalSegment.tag);
        this.length = originalSegment.length;
        this.data = originalSegment.data;

        ByteArrayInputStream in = new ByteArrayInputStream(data, 6, data.length - 6);

        // we aren't actually going to read anything as an image yet, but ImageInputStream
        // has useful helper methods:
        ImageInputStream input = new MemoryCacheImageInputStream(in);
        input.setByteOrder(input.readUnsignedShort() == TIFF_BIG_ENDIAN ?
                ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        if (input.readUnsignedShort() != TIFF_MAGIC) {
            throw new IllegalArgumentException("Bad magic number");
        }

        firstIFDOffset = input.readUnsignedInt();
        ImageFileDirectory ifd1 = null;
        ImageFileDirectory ifd2 = null;
        if (firstIFDOffset != 0) {
            ifd1 = new ImageFileDirectory(input, firstIFDOffset);
            imageFileDirectories.add(ifd1);

            long secondIFDOffset = ifd1.nextIFD;
            if (secondIFDOffset != 0) {
                ifd2 = new ImageFileDirectory(input, secondIFDOffset);
                imageFileDirectories.add(ifd2);
            }
        }

        if (ifd2 != null) {
            // the thumbnail should always be described in the 2nd IFD (if it exists at all)

            thumbnailPos = ifd2.getTagValueAsInt(TAG_JPEG_INTERCHANGE_FORMAT);
            thumbnailLength = ifd2.getTagValueAsInt(TAG_JPEG_INTERCHANGE_FORMAT_LENGTH);
            if (thumbnailPos != NO_VALUE && thumbnailLength != NO_VALUE) {
                // The `compression` tag (259) should also help inform whether we read this
                // image as a JPEG or TIFF. But in reality this is tricky: the docs say
                // the value for a JPEG encoding is 0x0006, but the `jdk_8160327-plastic-wrap.jpg`
                // file shows it can also sometimes be 0x60000. I've also observed it to be
                // undefined, 0x0007, or several variations of 0x????0006. Similarly the same
                // tag should be 0x0001 for TIFFs, but I also observed a case where it as 0x10000.
                isThumbnailJPEG = true;
            } else {
                thumbnailWidth = ifd2.getTagValueAsInt(TAG_IMAGE_WIDTH);
                thumbnailHeight = ifd2.getTagValueAsInt(TAG_IMAGE_HEIGHT);
                thumbnailPos = 0;
                thumbnailLength = data.length - 6;
                isThumbnailJPEG = false;
            }
        }
    }

    LocalDateTime getImageCreationTime() {
        LocalDateTime imageCreationTime = null;

        if (!imageFileDirectories.isEmpty()) {
            ImageFileDirectory ifd = imageFileDirectories.get(0);
            int dateTimeOffset = ifd.getTagValueAsInt(TAG_DATE_TIME);
            if (dateTimeOffset != NO_VALUE) {
                try {
                    String dateTime = new String(data, dateTimeOffset + 6, 19, StandardCharsets.US_ASCII);
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss");
                    imageCreationTime = LocalDateTime.parse(dateTime, formatter);
                } catch(Exception e) {
                    // intentionally empty
                }
            }
        }

        return imageCreationTime;
    }

    @Override
    void print() {
        printTag("Exif APP1");
        for (int a = 0; a < imageFileDirectories.size(); a++) {
            System.out.println("ImageFileDirectory #" + a + ", offset = " + firstIFDOffset);
            int entryCtr = 0;
            for (ImageFileDirectory.Entry entry : imageFileDirectories.get(a).entriesByTag.values()) {
                System.out.println("Entry #" + (entryCtr++) + ": " + entry.toString());
            }
            System.out.println("next directory: " + imageFileDirectories.get(a).nextIFD);
        }
    }

    int getNumThumbnails() {
        return thumbnailPos >= 0 && thumbnailLength > 0 ? 1 : 0;
    }

    int getThumbnailWidth() throws IOException {
        // this should only be called if there is a thumbnail

        if (thumbnailWidth == -1) {
            populateJPEGThumbnailDimensions();
        }
        return thumbnailWidth;
    }

    int getThumbnailHeight() throws IOException {
        // this should only be called if there is a thumbnail

        if (thumbnailHeight == -1) {
            populateJPEGThumbnailDimensions();
        }
        return thumbnailHeight;
    }

    /**
     * Use a JPEGImageReader to identify the size of the thumbnail. This
     * populates the `thumbnailWidth` and `thumbnailHeight` fields.
     */
    private void populateJPEGThumbnailDimensions() throws IOException {
        // this method will never be invoked for TIFF thumbnails, because TIFF
        // thumbnails clearly define their thumbnail size via IFD entries.
        JPEGImageReader reader = new JPEGImageReader(null);
        try {
            reader.setInput(ImageIO.createImageInputStream(new ByteArrayInputStream(
                    data, thumbnailPos + 6, thumbnailLength)));
            thumbnailWidth = reader.getWidth(0);
            thumbnailHeight = reader.getHeight(0);
        } finally {
            reader.dispose();
        }
    }

    BufferedImage getThumbnail(JPEGImageReader callbackReader) throws IOException {
        // this should only be called if there is a thumbnail

        callbackReader.thumbnailStarted(0);
        ImageReader thumbReader;
        int imageIndex = 0;
        if (isThumbnailJPEG) {
            thumbReader = new JPEGImageReader(null);
            imageIndex = 0;
        } else {
            thumbReader = new TIFFImageReader(null);
            imageIndex = 1;
        }
        try {
            InputStream byteIn = new ByteArrayInputStream(data, thumbnailPos + 6, thumbnailLength);
            ImageInputStream input = new MemoryCacheImageInputStream(byteIn);
            thumbReader.setInput(input);
            thumbReader.addIIOReadProgressListener(new JFIFMarkerSegment.JFIFThumbJPEG.ThumbnailReadListener(callbackReader));
            BufferedImage thumbnailImage = thumbReader.read(imageIndex);
            thumbnailWidth = thumbnailImage.getWidth();
            thumbnailHeight = thumbnailImage.getHeight();
            callbackReader.thumbnailComplete();
            return thumbnailImage;
        } finally {
            thumbReader.dispose();
        }
    }
}
