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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;
import java.lang.ref.WeakReference;

import jtreg.SkippedException;

/*
 * @test
 * @key headful
 * @bug 8364434
 * @summary Check that garbage-collecting Color before accelerated painting is complete does not cause artifacts.
 * @requires (os.family != "linux")
 * @library /test/lib
 * @run main/othervm -Xms16m -Xmx16m WeakColorTest
 */

public class WeakColorTest {
    public static void main(String[] args) throws Exception {
        BufferedImage bi = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB); // This image is full-black.
        VolatileImage image = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration().createCompatibleVolatileImage(100, 100);
        Graphics2D g = image.createGraphics();

        // Create a new Color - we want it to be collected later.
        g.setColor(new Color(255, 0, 0));
        WeakReference<Color> color = new WeakReference<>(g.getColor());

        g.fillRect(0, 0, 100, 100);

        // Change color to prevent Graphics from keeping our Color alive.
        g.setColor(Color.BLACK);

        // Force Color to be GC'ed.
        final int MAX_ITERATIONS = 1000, ARRAY_SIZE = 1000000;
        WeakReference<Object[]> array = null;
        for (int i = 0;; i++) {
            System.gc();
            if (color.get() == null) {
                System.out.println("Color collected at: " + i);
                break;
            } else if (i >= MAX_ITERATIONS) {
                throw new SkippedException("Color was not collected after " + MAX_ITERATIONS + " iterations");
            }
            Object[] a = new Object[ARRAY_SIZE];
            a[0] = array;
            array = new WeakReference<>(a);
        }

        // Do a blit. If it succeeds, the resulting image will be full-black.
        g.drawImage(bi, 0, 0, null);
        g.dispose();

        // We expect black. If it's red, then the blit must have failed.
        int actualColor = image.getSnapshot().getRGB(50, 50);
        if ((actualColor & 0xFFFFFF) != 0) throw new Error("Wrong color: 0x" + Integer.toHexString(actualColor));
    }
}
