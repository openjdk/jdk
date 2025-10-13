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

/*
 * @test
 * @bug 5043343
 * @summary Verify that FIIS and FIOS constructors set streamPos to RAF position
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @run main FileImageStreamPos
 * @key randomness
 */

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Random;
import javax.imageio.ImageIO;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.FileImageOutputStream;
import jdk.test.lib.RandomFactory;

public class FileImageStreamPos {
    // Maximum size of random prefix
    private static final int BOUND = 1024;

    // Maximum image dimensions
    public static final int SIZE_MIN = 512;
    public static final int SIZE_MAX = 1024;

    public static void main(String[] args) throws IOException {

        // Create source image with random content
        Random random = RandomFactory.getRandom();
        final int w = random.nextInt(SIZE_MIN, SIZE_MAX);
        final int h = random.nextInt(SIZE_MIN, SIZE_MAX);
        BufferedImage srcImage =
            new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
        int[] rgbArray = random.ints(w*h).toArray();
        srcImage.setRGB(0, 0, w, h, rgbArray, 0, w);

        // Write some random bytes followed by the image
        File dstFile = File.createTempFile("before", "after", new File("."));
        dstFile.deleteOnExit();
        final int offset = random.nextInt(BOUND);
        System.out.println("offset: " + offset);
        try(RandomAccessFile raf = new RandomAccessFile(dstFile, "rw");) {
            byte[] b = new byte[offset];
            random.nextBytes(b);
            raf.write(b);
            FileImageOutputStream fios = new FileImageOutputStream(raf);
            if (fios.getStreamPosition() != offset)
                throw new RuntimeException(fios.getStreamPosition() + " != " +
                                           offset);
            ImageIO.write(srcImage, "PNG", fios);
        }

        // Read the image just written and compare with the original
        try(RandomAccessFile raf = new RandomAccessFile(dstFile, "r")) {
            // Read the image from after then random prefix
            raf.skipBytes(offset);
            FileImageInputStream fiis = new FileImageInputStream(raf);
            if (fiis.getStreamPosition() != offset)
                throw new RuntimeException(fiis.getStreamPosition() + " != " +
                                           offset);
            BufferedImage dstImage = ImageIO.read(fiis);

            // Compare the image dimensions
            if (dstImage.getWidth() != w)
                throw new RuntimeException(dstImage.getWidth() + " != " + w);
            if (dstImage.getHeight() != h)
                throw new RuntimeException(dstImage.getHeight() + " != " + h);

            // Compare RGB pixels
            if (!Arrays.equals(srcImage.getRGB(0, 0, w, h, null, 0, w),
                               dstImage.getRGB(0, 0, w, h, null, 0, w)))
                throw new RuntimeException("Pixels are not equal");
        }
    }
}
