/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @key headful
 * @bug      8176795 8275843
 * @summary  Test verifies that we get proper color when we draw translucent
 *           color over an opaque color using X Render extension in Linux.
 * @run      main/othervm XRenderTranslucentColorDrawTest
 * @run      main/othervm -Dsun.java2d.xrender=true XRenderTranslucentColorDrawTest
 */

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;

public class XRenderTranslucentColorDrawTest {

    public static void main(String[] args) {
        var env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        for (GraphicsDevice screen : env.getScreenDevices()) {
            for (GraphicsConfiguration gc : screen.getConfigurations()) {
                test(gc, Transparency.OPAQUE);
                test(gc, Transparency.BITMASK);
                test(gc, Transparency.TRANSLUCENT);
            }
        }
    }

    private static void test(GraphicsConfiguration gc, int transparency) {
        int width = 10;
        int height = 10;
        VolatileImage image = gc.createCompatibleVolatileImage(width, height,
                                                               transparency);
        Graphics2D g = image.createGraphics();
        // draw opaque black color
        g.setColor(new Color(0xff000000, true));
        g.fillRect(0, 0, width, height);
        // draw translucent white color over opaque black color
        g.setColor(new Color(0x80ffffff, true));
        g.fillRect(0, 0, width, height);
        g.dispose();
        // Get snapshot of VolatileImage to pick color and verify the same
        BufferedImage snapshot = image.getSnapshot();
        int argb = snapshot.getRGB(width / 2, height / 2);
        // we expect the resultant rgb hex value to be ff808080
        String actual = Integer.toHexString(argb);
        if (!(actual.equals("ff808080"))) {
            throw new RuntimeException("Drawing translucent color is not " +
                                       "giving expected results: " + actual);
        }
    }
}