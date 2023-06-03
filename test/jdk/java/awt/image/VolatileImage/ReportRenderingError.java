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

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.awt.image.ImageProducer;

/*
 * @test id=default
 * @bug 8272288
 * @key headful
 * @summary Broken rendering should be reported by the contentsLost()
 *
 * @run main/othervm ReportRenderingError
 */

/*
 * @test id=windows
 * @bug 8272288
 * @key headful
 * @summary Broken rendering should be reported by the contentsLost()
 * @requires (os.family == "windows")
 *
 * @run main/othervm -Dsun.java2d.opengl=True ReportRenderingError
 * @run main/othervm -Dsun.java2d.d3d=True    ReportRenderingError
 * @run main/othervm -Dsun.java2d.d3d=false   ReportRenderingError
 */

/*
 * @test id=macos
 * @bug 8272288
 * @key headful
 * @summary Broken rendering should be reported by the contentsLost()
 *
 * @requires (os.family == "mac")
 * @run main/othervm -Dsun.java2d.opengl=True ReportRenderingError
 * @run main/othervm -Dsun.java2d.metal=True  ReportRenderingError
 */

/*
 * @test id=linux
 * @bug 8272288
 * @key headful
 * @summary Broken rendering should be reported by the contentsLost()
 *
 * @requires (os.family == "linux")
 * @run main/othervm -Dsun.java2d.opengl=True   ReportRenderingError
 * @run main/othervm -Dsun.java2d.xrender=True  ReportRenderingError
 * @run main/othervm -Dsun.java2d.xrender=false ReportRenderingError
 */
public final class ReportRenderingError {

    public static void main(String[] args) {
        var gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                                    .getDefaultScreenDevice()
                                    .getDefaultConfiguration();
        var vi = gc.createCompatibleVolatileImage(10, 10);

        Image image = new EmptyImage();
        BufferedImage snapshot;
        int attempt = 0;
        do {
            vi.validate(gc);
            Graphics2D g = vi.createGraphics();
            g.setColor(Color.RED);
            g.drawImage(image, 0, 0, null); // <- can cause InvalidPipeException
            g.fillRect(0, 0, vi.getWidth(), vi.getHeight());
            g.dispose();
            snapshot = vi.getSnapshot();
        } while (vi.contentsLost() && (++attempt <= 10));

        if (vi.contentsLost()) {
            System.out.println("Content is lost, skip the pixel validation");
        } else {
            if (snapshot.getRGB(5, 5) != Color.RED.getRGB()) {
                throw new RuntimeException("Test failed");
            }
        }
    }

    private static final class EmptyImage extends Image {
        @Override
        public int getWidth(ImageObserver observer) {
            return 10;
        }

        @Override
        public int getHeight(ImageObserver observer) {
            return 10;
        }

        @Override
        public ImageProducer getSource() {
            return null;
        }

        @Override
        public Graphics getGraphics() {
            return null;
        }

        @Override
        public Object getProperty(String name, ImageObserver observer) {
            return null;
        }
    }
}
