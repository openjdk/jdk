/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     8266435
 * @summary Test verifies that WBMPImageReader doesnt truncate
 *          the stream and reads it fully
 * @run     main WBMPStreamTruncateTest
 */

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStreamImpl;

public class WBMPStreamTruncateTest
{
    static final int LIMIT = 100;
    static final int width = 100;
    static final int height = 100;
    public static void main(String[] args) throws IOException
    {
        String sep = System.getProperty("file.separator");
        String dir = System.getProperty("test.src", ".");
        String filePath = dir+sep;
        BufferedImage srcImage = new
                BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g = (Graphics2D) srcImage.getGraphics();
        g.setBackground(Color.WHITE);
        g.fillRect(0, 0, srcImage.getWidth(), srcImage.getHeight());
        g.dispose();
        // create WBMP image
        File imageFile = File.
                createTempFile("test", ".wbmp", new File(filePath));
        imageFile.deleteOnExit();
        ImageIO.write(srcImage, "wbmp", imageFile);
        BufferedImage testImg =
                ImageIO.read(new LimitedImageInputStream(imageFile, LIMIT));
        for (int x = 0; x < testImg.getWidth(); ++x)
        {
            for (int y = 0; y < testImg.getHeight(); ++y)
            {
                int i1 = testImg.getRGB(x, y);
                int i2 = srcImage.getRGB(x, y);
                if (i1 != i2)
                {
                    throw new RuntimeException("Stream is decoded only until "
                    + "the limit specified");
                }
            }
        }
    }

    static class LimitedImageInputStream extends ImageInputStreamImpl
    {
        private final RandomAccessFile raf;
        private final int limit;

        public LimitedImageInputStream(File file, int limit)
                throws FileNotFoundException
        {
            raf = new RandomAccessFile(file, "r");
            this.limit = limit;
        }

        @Override
        public int read() throws IOException
        {
            return raf.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException
        {
            return raf.read(b, off, Math.min(limit, len));
        }

        @Override
        public void close() throws IOException
        {
            super.close();
            raf.close();
        }

        @Override
        public void seek(long pos) throws IOException
        {
            super.seek(pos);
            raf.seek(pos);
        }
    }
}
