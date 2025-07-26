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
 * @run main ImageIconTest
 */

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.util.Random;
import java.awt.Image;
import java.awt.Toolkit;
import javax.swing.ImageIcon;

public class ImageIconTest {

    static enum ArgType { FILE, URL, BYTEARRAY, IMAGE };
    static enum ArgVal { NULL, INVALIDDATA };

    public static void main(String[] args) throws Exception {

        String imgName = "invalid.gif";
        byte[] invalidData = new byte[100];
        new Random().nextBytes(invalidData);
        try (FileOutputStream fos = new FileOutputStream(imgName)) {
            fos.write(invalidData);
        }
        String fileName = (new File(System.getProperty("test.src", "."), imgName)).getName();

        for (ArgType a : ArgType.values()) {
            for (final ArgVal v : ArgVal.values()) {
                System.out.println("Testing for ArgType " + a + " for case " + v);
                boolean expected = true;
                boolean passed = false;
                try {
                    switch (a) {
                       case FILE :
                           expected = false;
                           String s = (v == ArgVal.NULL) ? null : fileName;
                           new ImageIcon(s);
                           passed = true; // no exception expected for this case
                           break;
                       case URL :
                           if (v == ArgVal.NULL) {
                               new ImageIcon((URL)null);
                           } else if (v == ArgVal.INVALIDDATA) {
                               expected = false;
                               new ImageIcon("file://" + imgName, "gif");
                               passed = true; // no exception expected for this case
                           }
                           break;
                       case BYTEARRAY :
                           if (v == ArgVal.NULL) {
                               byte[] bytes = null;
                               new ImageIcon(bytes);
                           } else if (v == ArgVal.INVALIDDATA) {
                               expected = false;
                               new ImageIcon(new byte[0], "gif");
                               passed = true; // no exception expected for this case
                           }
                           break;
                       case IMAGE :
                           if (v == ArgVal.NULL) {
                               new ImageIcon((Image)null);
                           } else if (v == ArgVal.INVALIDDATA) {
                               expected = false;
                               new ImageIcon((Image)Toolkit.getDefaultToolkit().createImage(fileName), "gif");
                               passed = true; // no exception expected for this case
                           }
                           break;
                    }
                } catch (NullPointerException e) {
                    if (expected) {
                        passed = true;
                    }
                }
                if (expected && !passed) {
                   System.err.println("Did not receive expected exception for : " + a);
                   throw new RuntimeException("Test failed");
                }
                if (!expected && !passed) {
                   System.err.println("Received unexpected exception for : " + a);
                   throw new RuntimeException("Test failed");
                }
            }
        }

        // test setter
        try {
            ImageIcon ii = new ImageIcon();
            ii.setImage((Image)null);
            throw new RuntimeException("No NPE");
        } catch (NullPointerException e) {
            // expected
        }
    }
}
