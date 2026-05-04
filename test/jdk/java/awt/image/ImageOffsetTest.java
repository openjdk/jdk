/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4259548
 * @summary tests that MemoryImageSource correctly handles images with offsets
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ImageOffsetTest
 */

import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Panel;
import java.awt.Toolkit;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.MemoryImageSource;

public class ImageOffsetTest {

    static int height = 100;
    static int width  = 100;
    static int levels = 3;
    static IndexColorModel cm;
    static Image image;
    static boolean first = true;

    static byte[] db = new byte[height * width * levels] ;

    private static final String INSTRUCTIONS = """
         If on the appeared 'Test frame' all color squares are of one color
         test failed, otherwise it's passed.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                      .title("ImageOffsetTest")
                      .instructions(INSTRUCTIONS)
                      .rows((int) INSTRUCTIONS.lines().count() + 2)
                      .columns(35)
                      .testUI(ImageOffsetTest::createUI)
                      .build()
                      .awaitAndCheck();
    }

    private static Frame createUI() {
        Frame frame = new Frame("ImageOffset Frame");
        frame.add(new Panel() {
            public void paint(Graphics g) {
                for ( int i=0 ; i<3 ; i++ ) {
                    g.drawImage(
                        generateBuggyImage(i * width * height), 10 + i * 110, 10, null);
                }
            }
        });
        frame.setSize(400, 200);
        frame.setLocation(300, 200);
        createColorModel();
        int l = 0;
        for (int k = 0; k < levels; k++) {
            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {
                    if( k == 0) {
                        db[l] = (byte)(70 & 0xff) ;
                    }
                    if (k == 1) {
                        db[l] = (byte)(150 & 0xff) ;
                    }
                    if (k == 2) {
                        db[l] = (byte)(230 & 0xff) ;
                    }
                    l++ ;
                }
            }
        }
        return frame;
    }

    private static void createColorModel() {
        byte[] red = new byte[256];
        byte[] green = new byte[256];
        byte[] blue = new byte[256];

        for (int i = 0; i < 256; i++) {
            red[i]   = (byte)(i & 0xff);
            //green[i] = (byte)( i & 0xff ) ;
            blue[i]  = (byte)( i & 0xff ) ;
            //commented out green so I could get purple
        }

        cm = new IndexColorModel( 8, 256, red, green, blue ) ;
    }

    private static Image generateBuggyImage(int offset) {
        // Initialize the database, Three slices, different shades of grey
        // Here the image is created using the offset,
        return Toolkit.getDefaultToolkit().createImage(
                new MemoryImageSource(width, height, (ColorModel)cm,
                                      db, offset, width));
    }
}
