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

import java.awt.AlphaComposite;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBufferInt;
import java.awt.image.DirectColorModel;
import java.awt.image.WritableRaster;
import java.util.Arrays;

/**
 * @test
 * @bug 8275843
 * @key headful
 * @summary No exception or errors should occur.
 */
public final class DrawCustomColorModel {

    public static void main(String[] args) {
        var ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        for (GraphicsDevice gd : ge.getScreenDevices()) {
            GraphicsConfiguration[] gcs = gd.getConfigurations();
            for (GraphicsConfiguration gc : gcs) {
                test(gc);
            }
        }
    }

    private static void test(GraphicsConfiguration gc) {
        Frame frame = new Frame(gc);
        frame.setUndecorated(true);
        frame.pack();
        frame.setSize(15, 15);
        ColorModel cm = new DirectColorModel(32,
                                             0xff000000, // Red
                                             0x00ff0000, // Green
                                             0x0000ff00, // Blue
                                             0x000000FF  // Alpha
        );
        WritableRaster wr = cm.createCompatibleWritableRaster(16, 16);
        DataBufferInt buff = (DataBufferInt) wr.getDataBuffer();
        int[] data = buff.getData();
        Arrays.fill(data, -1); // more chance to reproduce
        Image image =  new BufferedImage(cm, wr, false, null);

        Graphics2D graphics = (Graphics2D) frame.getGraphics();
        graphics.setComposite(AlphaComposite.Src);
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        frame.dispose();
    }
}
