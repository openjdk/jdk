/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4827074
 * @summary ImageIcon serialization does not preload restored images
 * @run main bug4827074
 */

import javax.swing.ImageIcon;
import java.awt.Image;
import java.awt.Panel;
import java.awt.image.MemoryImageSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class bug4827074 extends Panel {

    static ImageIcon testIcon = null;
    private volatile static boolean passed = false;

    public void init() {
        testIcon = new TestImageIcon();
        ImageIcon icon = saveAndLoad(testIcon);

        if (!passed) {
            throw new RuntimeException("Image was not loaded properly");
        }
    }

    synchronized static void setPassed(boolean p) {
        passed = p;
    }

    static ImageIcon saveAndLoad(ImageIcon ii) {
        ImageIcon _ii = null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(baos);
            out.writeObject(ii);
            out.flush();
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            ObjectInputStream in = new ObjectInputStream(bais);
            _ii = (ImageIcon)in.readObject();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return _ii;
    }

    class TestImageIcon extends ImageIcon {
        public TestImageIcon() {
            super();
            setImage(buildImage());
        }

        private Image buildImage() {
            int w = 32, h = 32;
            float halfW = w / 2 , halfH = h / 2;
            int col = 0xff0000;
            int[] pixels = new int[w * h];
            for(int y = 0; y < h; y++) {
                for(int x = 0; x < w; x++) {
                    float cx = 1f - (float)x / halfW;
                    float cy = 1f - (float)y / halfH;
                    double ray = Math.sqrt(cx * cx + cy * cy);
                    pixels[y * w + x] = ray < 1 ? col | (255 - (int)(ray * 255)) << 24:0;
                }
            }
            MemoryImageSource mis = new MemoryImageSource(w, h, pixels, 0, w);
            Image image = createImage(mis);
            return image;
        }

        protected void loadImage(Image image) {
            super.loadImage(image);
            if (testIcon != null && image != testIcon.getImage()) {
                setPassed(true);
            }
        }
    }

    public static void main(String[] args) {
        bug4827074 bug = new bug4827074();
        bug.init();
    }
}
