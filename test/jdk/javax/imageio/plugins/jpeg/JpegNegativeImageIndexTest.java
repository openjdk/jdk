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
 * @bug     8364135
 * @summary Test verifies that jpeg image reader getImageTypes() throws
 *          IndexOutOfBoundsException when negative index is passed.
 * @run main JpegNegativeImageIndexTest
 */

import java.io.IOException;
import java.util.Iterator;
import java.util.Objects;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;

public class JpegNegativeImageIndexTest {

    static boolean passed;

    public static void main(String[] args) throws IOException {
        Iterator<ImageReader> readers =
            ImageIO.getImageReadersByFormatName("jpeg");
        if (!readers.hasNext()) {
            throw new RuntimeException("No jpeg image readers found");
        }

        ImageReader ir = readers.next();
        try {
            Iterator<ImageTypeSpecifier> types = ir.getImageTypes(-1);
        } catch (IndexOutOfBoundsException e) {
            if (Objects.equals(e.getMessage(), "imageIndex < 0!")) {
                passed = true;
            }
        }
        if (!passed) {
            throw new RuntimeException("JpegImageReader didn't throw required"
                + " IndexOutOfBoundsException for non-zero image index");
        }
    }
}
