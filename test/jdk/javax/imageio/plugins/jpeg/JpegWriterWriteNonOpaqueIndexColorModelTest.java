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
 * @bug 8351108
 * @summary This test verifies that attempting to write a JPEG using
 *          ImageIO.write(..) fails by returning `false` for
 *          translucent IndexColorModels.
 */

import javax.imageio.ImageIO;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayOutputStream;

public class JpegWriterWriteNonOpaqueIndexColorModelTest {
    public static void main(String[] args) {
        boolean b1 = testJpegWriter(Transparency.OPAQUE, "OPAQUE", true);
        boolean b2 = testJpegWriter(Transparency.BITMASK, "BITMASK", false);
        boolean b3 = testJpegWriter(Transparency.TRANSLUCENT, "TRANSLUCENT", false);
        if (!(b1 && b2 && b3)) {
            throw new Error("Test failed");
        }
    }

    private static boolean testJpegWriter(int imageType, String name, boolean expectedWriteReturnValue) {
        System.out.println();
        System.out.println("TESTING " + name);
        try {
            byte[] gray = new byte[256];
            for (int a = 0; a < gray.length; a++) {
                gray[a] = (byte) a;
            }

            byte[] alpha = new byte[256];
            if (imageType == Transparency.OPAQUE || imageType == Transparency.BITMASK) {
                for (int a = 0; a < alpha.length; a++) {
                    alpha[a] = -1;
                }
                if (imageType == Transparency.BITMASK) {
                    alpha[0] = 0;
                }
            } else if (imageType == Transparency.TRANSLUCENT) {
                for (int a = 0; a < alpha.length; a++) {
                    alpha[a] = (byte) a;
                }
            }

            IndexColorModel indexColorModel = new IndexColorModel(8, 256, gray, gray, gray, alpha);
            System.out.println("colorModel.getTransparency() = " + indexColorModel.getTransparency());
            BufferedImage bi = new BufferedImage(10, 10, BufferedImage.TYPE_BYTE_INDEXED, indexColorModel);
            boolean result = ImageIO.write(bi, "jpg", new ByteArrayOutputStream());
            if (result != expectedWriteReturnValue) {
                throw new Exception("ImageIO.write(..) returned " + result + " but we expected " + expectedWriteReturnValue);
            }
            System.out.println("Tested passed");
            return true;
        } catch (Exception e) {
            System.err.println(name + " test failed");
            e.printStackTrace();
            return false;
        }
    }
}
