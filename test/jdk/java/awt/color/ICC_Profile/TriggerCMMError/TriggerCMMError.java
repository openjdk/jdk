/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

import java.awt.color.CMMException;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.io.File;
import java.io.FileInputStream;
import java.util.zip.ZipInputStream;

import static java.awt.image.BufferedImage.TYPE_INT_RGB;

/**
 * @test
 * @bug 8313220
 * @summary Verifies that CMM errors reported properly
 */
public final class TriggerCMMError {

    public static void main(String[] args) throws Exception {
        // "broken.zip" is a copy of "PYCC.pf"
        File file = new File(System.getProperty("test.src", "."), "broken.zip");
        ICC_Profile profile;
        try (FileInputStream fis = new FileInputStream(file);
             ZipInputStream zis = new ZipInputStream(fis))
        {
            zis.getNextEntry();
            profile = ICC_Profile.getInstance(zis);
        }
        var from = new ICC_ColorSpace(profile);
        var to = (ICC_ColorSpace) ColorSpace.getInstance(ColorSpace.CS_CIEXYZ);

        from.getProfile().getData();

        convert(from, to);

        try {
            from.getProfile().getData();
        } catch (CMMException cmm) {
            String message = cmm.getMessage();
            System.err.println("message = " + message);
            if (message.length() > 255) {
                throw new RuntimeException("The message is too long");
            }
            if (!message.matches("LCMS error \\d+:\\s.*")) {
                throw new RuntimeException("The message format is unexpected");
            }
        }
    }

    private static void convert(ColorSpace from, ColorSpace to) {
        BufferedImage src = new BufferedImage(10, 10, TYPE_INT_RGB);
        BufferedImage dst = new BufferedImage(10, 10, TYPE_INT_RGB);
        ColorConvertOp op = new ColorConvertOp(from, to, null);
        op.filter(src, dst);
    }
}
