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
 * @bug 8159055
 * @summary Verifies ImageIcon.setImage handles null parameter
 * @run main ImageIconNullImageTest
 */

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;
import java.awt.image.BufferedImage;
import java.awt.Image;
import java.awt.Toolkit;
import javax.swing.ImageIcon;

public class ImageIconNullImageTest {
    static File file;

    public static void main(String[] args) throws Exception {
        testImageIconFileDesc(false);
        testImageIconFile(false);
        testImageIconURLDesc(false);
        testImageIconURL(false);
        testImageIconImageDesc(false);
        testImageIconNull();
        testImageIconByteDesc(false);
        testImageIconByte(false);
        testSetImageNull();
        testInvalid();
    }

    private static void testInvalid() throws Exception {
        try {
            String imgName = "invalid.gif";
            byte[] invalidData = new byte[100];
            new Random().nextBytes(invalidData);
            try (FileOutputStream fos = new FileOutputStream(imgName)) {
                fos.write(invalidData);
            }
            file = new File(System.getProperty("test.src", "."), imgName);
            testImageIconFileDesc(true);
            testImageIconFile(true);
            testImageIconURLDesc(true);
            testImageIconURL(true);
            testImageIconImageDesc(true);
            testImageIconByteDesc(true);
            testImageIconByte(true);
        } finally {
            file.deleteOnExit();
        }
    }

    private static void testImageIconFileDesc(boolean invalid) {
        // Passing null image shouldn't cause NPE
        if (!invalid) {
            try {
                new ImageIcon((String)null, "gif");
            } catch (NullPointerException e) {
                System.out.println("null ImageIcon(FileName,Desc) throws NPE");
            }
        } else {
            try {
                ImageIcon icon = new ImageIcon(file.getName(), "gif");
                System.out.println(icon.getImageLoadStatus());
            } catch (NullPointerException e) {
                System.out.println("invalid ImageIcon(FileName,Desc) throws NPE");
            }
        }
    }

    private static void testImageIconFile(boolean invalid) {
        // Passing null image shouldn't cause NPE
        if (!invalid) {
            try {
                new ImageIcon((String)null);
            } catch (NullPointerException e) {
                System.out.println("null ImageIcon(FileName) throws NPE");
            }
        } else {
            try {
                ImageIcon icon = new ImageIcon(file.getName());
                System.out.println(icon.getImageLoadStatus());
            } catch (NullPointerException e) {
                System.out.println("invalid ImageIcon(FileName) throws NPE");
            }
        }
    }

    private static void testImageIconURLDesc(boolean invalid) {
        // Passing null image shouldn't cause NPE
        if (!invalid) {
            try {
                new ImageIcon((java.net.URL)null, "gif");
            } catch (NullPointerException e) {
                System.out.println("null ImageIcon(URL, Desc) throws NPE");
            }
        } else {
            try {
                ImageIcon icon = new ImageIcon("file://invalid.gif", "gif");
                System.out.println(icon.getImageLoadStatus());
            } catch (NullPointerException e) {
                System.out.println("invalid ImageIcon(URL, Desc) throws NPE");
            }
        }
    }

    private static void testImageIconURL(boolean invalid) {
        // Passing null image shouldn't cause NPE
        if (!invalid) {
            try {
                new ImageIcon((java.net.URL)null);
            } catch (NullPointerException e) {
                System.out.println("null ImageIcon(URL) throws NPE");
            }
        } else {
            try {
                ImageIcon icon = new ImageIcon("file://invalid.gif");
                System.out.println(icon.getImageLoadStatus());
            } catch (NullPointerException e) {
                System.out.println("invalid ImageIcon(URL) throws NPE");
            }
        }
    }

    private static void testImageIconImageDesc(boolean invalid) {
        // Passing null image shouldn't cause NPE
        if (!invalid) {
            try {
                new ImageIcon((Image) null, "gif");
            } catch (NullPointerException e) {
                System.out.println("null ImageIcon(Image, Desc) throws NPE");
            }
        } else {
            try {
                ImageIcon icon = new ImageIcon((Image)
                        Toolkit.getDefaultToolkit().createImage(file.getName()), "gif");
                System.out.println(icon.getImageLoadStatus());
            } catch (NullPointerException e) {
                System.out.println("invalid ImageIcon(Image, Desc) throws NPE");
            }
        }
    }

    private static void testImageIconNull() {
        // Passing null image shouldn't cause NPE
        try {
            new ImageIcon((Image) null);
        } catch (NullPointerException e) {
            System.out.println("null ImageIcon(image) throws NPE");
        }
    }

    private static void testImageIconByteDesc(boolean invalid) {
        // Passing null image shouldn't cause NPE
        if (!invalid) {
            byte[] imageData = null;
            try {
                new ImageIcon(imageData, "gif");
            } catch (NullPointerException e) {
                System.out.println("null ImageIcon(byte[], desc) throws NPE");
            }
        } else {
            try {
               ImageIcon icon = new ImageIcon(new byte[0], "gif");
                System.out.println(icon.getImageLoadStatus());
            } catch (NullPointerException e) {
                System.out.println("invalid ImageIcon(byte[], desc) throws NPE");
            }
        }
    }

    private static void testImageIconByte(boolean invalid) {
        // Passing null image shouldn't cause NPE
        if (!invalid) {
            byte[] imageData = null;
            try {
                new ImageIcon(imageData);
            } catch (NullPointerException e) {
                System.out.println("null ImageIcon(byte[]) throws NPE");
            }
        } else {
            try {
                ImageIcon icon = new ImageIcon(new byte[0]);
                System.out.println(icon.getImageLoadStatus());
            } catch (NullPointerException e) {
                System.out.println("invalid ImageIcon(byte[], desc) throws NPE");
            }
        }
    }

    private static void testSetImageNull() {
        ImageIcon icon = new ImageIcon();

        // Passing null image shouldn't cause NPE
        icon.setImage(null);
    }
}
