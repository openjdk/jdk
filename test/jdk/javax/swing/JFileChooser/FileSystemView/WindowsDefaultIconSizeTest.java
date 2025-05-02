/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.filechooser.FileSystemView;
import java.awt.Image;
import java.awt.image.MultiResolutionImage;
import java.io.File;
import java.io.IOException;

/*
 * @test
 * @bug 8282526
 * @summary Default icon is not painted properly
 * @requires (os.family == "windows")
 * @run main WindowsDefaultIconSizeTest
 */

public class WindowsDefaultIconSizeTest {
    public static void main(String[] args) {
        WindowsDefaultIconSizeTest test = new WindowsDefaultIconSizeTest();
        test.test();
    }

    public void test() {
        String filename = "test.not";

        File testFile = new File(filename);
        try {
            if (!testFile.exists()) {
                testFile.createNewFile();
                testFile.deleteOnExit();
            }
            FileSystemView fsv = FileSystemView.getFileSystemView();
            Icon icon = fsv.getSystemIcon(new File(filename));
            if (icon instanceof ImageIcon) {
                Image image = ((ImageIcon) icon).getImage();
                if (image instanceof MultiResolutionImage) {
                    Image variant = ((MultiResolutionImage) image).getResolutionVariant(16, 16);
                    if (variant.getWidth(null) != 16) {
                        throw new RuntimeException("Default file icon has size of " +
                                variant.getWidth(null) + " instead of 16");
                    }
                }
            }
        } catch (IOException ioe) {
            throw new RuntimeException("Unexpected error while creating the test file: " + ioe.getLocalizedMessage());
        }
    }
}
