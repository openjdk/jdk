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
import java.io.File;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.ColorModel;

import javax.imageio.ImageIO;

/*
 * @test
 * @bug 8364583
 * @summary Verify CMYK images work with ColorConvertOp
 */

public class ColorConvertOpCMYK {

    public static void main(String[] args) throws Exception {
        String sep = System.getProperty("file.separator");
        String dir = System.getProperty("test.src", ".");
        String prefix = dir + sep;
        File file = new File(prefix + "black_cmyk.jpg");
        BufferedImage source = ImageIO.read(file);
        ColorModel sourceModel = source.getColorModel();
        ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
        ColorConvertOp convertOp = new ColorConvertOp(cs, null);
        BufferedImage rgb = convertOp.filter(source, null);
    }
}
