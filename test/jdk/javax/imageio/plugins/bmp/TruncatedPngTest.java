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

/*
 * @test
 * @bug 8262297
 * @summary Checks that ImageIO.write(..., ..., File) truncates the file
 */

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import javax.imageio.IIOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

public class TruncatedPngTest {

    public static void main(String[] args) {
        String fileName = "0.png";
        String separator = System.getProperty("file.separator");
        String dirPath = System.getProperty("test.src", ".");
        String filePath = dirPath + separator + fileName;

        boolean passed = false;
        File inputFile = new File(filePath);
        try (InputStream is = new FileInputStream(inputFile)) {
            BufferedImage image = ImageIO.read(is);
            ImageIO.write(image, "bmp", new File("0.bmp"));
        } catch (IIOException e) {
            System.out.println("Test PASSED.");
            passed = true;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Unexpected exception!");
        }

        if (!passed) {
            throw new RuntimeException("IIOException was not caught!");
        }
    }
}
