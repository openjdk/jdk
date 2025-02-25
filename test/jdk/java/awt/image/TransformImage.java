/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4090743
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Make sure that there is no garbage drawn on the rotated image
 * @run main/manual TransformImage
 */

import java.net.URL;
import java.net.MalformedURLException;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.Image;
import java.awt.image.ImageObserver;
import java.awt.MediaTracker;
import java.awt.Toolkit;

public class TransformImage extends Canvas  {
    static Image image;

    private static final String INSTRUCTIONS = """
            The rotated image should be drawn without garbage.""";

    public static void main(String[] argv) throws Exception {
        PassFailJFrame.builder()
                .title("TransformImage Instructions")
                .instructions(INSTRUCTIONS)
                .rows(5)
                .columns(35)
                .testUI(TransformImage::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static Frame createTestUI() {
        Frame f = new Frame();
        String dir = System.getProperty("test.src");
        String sep = System.getProperty("file.separator");
        if (dir == null) {
            dir = ".";
        }
        image = Toolkit.getDefaultToolkit().getImage(dir+sep+"duke.gif");
        f.add(new TransformImage());

        f.pack();
        return f;
    }

    public Dimension getPreferredSize() {
        return new Dimension (256, 256);
    }

    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    public void paint(Graphics g) {
        int w, h;
        java.awt.Graphics2D g2d = (Graphics2D) g;
        AffineTransform at = new AffineTransform();

        MediaTracker mt = new MediaTracker(this);
        mt.addImage(image, 0);
        try {
            mt.waitForAll();
        } catch (InterruptedException e) {
            System.err.println("can't track");
            return;
        }
        w = image.getWidth(this);
        h = image.getHeight(this);
        g2d.drawImage(image, 0, 0, this);
        g2d.drawRect(0, 0, w, h);

        double rad = .5;
        at.rotate(-rad);
        g2d.setTransform(at);
        g2d.drawImage(image, 0, 100, this);
        g2d.drawRect(0, 100, w, h);
    }
}
