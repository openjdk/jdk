/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4213160
 * @summary Should generate a black image
 */

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.awt.geom.AffineTransform;

public class MultiOp {

    public static void main(String[] argv) {

       int width = 256;
       int height = 256;

       int pixelBits = 2; // 1, 2, 4, or 8
       // 1 and 8 make the code throw ImagingOpException, 2 and 4
       // make the code SEGV on Sol.

       byte[] lut1Arr = new byte[] {0, (byte)255 };
       byte[] lut2Arr = new byte[] {0, (byte)85, (byte)170, (byte)255};
       byte[] lut4Arr = new byte[] {0, (byte)17, (byte)34, (byte)51,
                                  (byte)68, (byte)85,(byte) 102, (byte)119,
                                  (byte)136, (byte)153, (byte)170, (byte)187,
                                  (byte)204, (byte)221, (byte)238, (byte)255};
       byte[] lut8Arr = new byte[256];
       for (int i = 0; i < 256; i++) {
           lut8Arr[i] = (byte)i;
       }

       // Create the binary image
       int bytesPerRow = width * pixelBits / 8;
       byte[] imageData = new byte[height * bytesPerRow];
       ColorModel cm = null;

       switch (pixelBits) {
       case 1:
           cm = new IndexColorModel(pixelBits, lut1Arr.length,
                                    lut1Arr, lut1Arr, lut1Arr);
           break;
       case 2:
           cm = new IndexColorModel(pixelBits, lut2Arr.length,
                                    lut2Arr, lut2Arr, lut2Arr);
           break;
       case 4:
           cm = new IndexColorModel(pixelBits, lut4Arr.length,
                                    lut4Arr, lut4Arr, lut4Arr);
           break;
       case 8:
           cm = new IndexColorModel(pixelBits, lut8Arr.length,
                                    lut8Arr, lut8Arr, lut8Arr);
           break;
       default:
           {new Exception("Invalid # of bit per pixel").printStackTrace();}
       }

       DataBuffer db = new DataBufferByte(imageData, imageData.length);
       WritableRaster r = Raster.createPackedRaster(db, width, height,
                                                    pixelBits, null);
       BufferedImage srcImage = new BufferedImage(cm, r, false, null);

       BufferedImage destImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
       Graphics2D g = destImage.createGraphics();
       AffineTransform af = AffineTransform.getScaleInstance(.5, .5);
       // This draw image is the problem
       g.drawImage(srcImage, af, null);
       int blackPixel = Color.black.getRGB();
       for (int x = 0; x < width; x++) {
           for (int y = 0; y < height; y++) {
              if (destImage.getRGB(x, y) != blackPixel) {
                  throw new RuntimeException("Not black");
              }
          }
      }
   }
}
