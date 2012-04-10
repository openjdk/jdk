/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.LinkedList;
import java.util.List;

/**
 * <p>This class contains utilities useful for regression testing.
 * <p>When using jtreg you would include this class via something like:
 * <pre>
 *
 * @library ../../regtesthelpers
 * @build Util
 * </pre>
 */

public class Util {
    /**
     * Convert a rectangle from coordinate system of Component c to
     * screen coordinate system.
     *
     * @param r a non-null Rectangle
     * @param c a Component whose coordinate system is used for conversion
     */
    public static void convertRectToScreen(Rectangle r, Component c) {
        Point p = new Point(r.x, r.y);
        SwingUtilities.convertPointToScreen(p, c);
        r.x = p.x;
        r.y = p.y;
    }

    /**
     * Compares two bufferedImages pixel-by-pixel.
     * return true if all pixels in the two areas are identical
     */
    public static boolean compareBufferedImages(BufferedImage bufferedImage0, BufferedImage bufferedImage1) {
        int width = bufferedImage0.getWidth();
        int height = bufferedImage0.getHeight();

        if (width != bufferedImage1.getWidth() || height != bufferedImage1.getHeight()) {
            return false;
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (bufferedImage0.getRGB(x, y) != bufferedImage1.getRGB(x, y)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Fills the heap until OutOfMemoryError occurs. This method is useful for
     * WeakReferences removing.
     */
    public static void generateOOME() {
        List<Object> bigLeak = new LinkedList<Object>();

        boolean oome = false;

        System.out.print("Filling the heap");

        try {
            for(int i = 0; true ; i++) {
                // Now, use up all RAM
                bigLeak.add(new byte[1024 * 1024]);

                System.out.print(".");

                // Give the GC a change at that weakref
                if (i % 10 == 0) {
                    System.gc();
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (OutOfMemoryError e) {
            bigLeak = null;
            oome = true;
        }

        System.out.println("");

        if (!oome) {
            throw new RuntimeException("Problem with test case - never got OOME");
        }

        System.out.println("Got OOME");
    }

    /**
     * Find a sub component by class name.
     * Always run this method on the EDT thread
     */
    public static Component findSubComponent(Component parent, String className) {
        String parentClassName = parent.getClass().getName();

        if (parentClassName.contains(className)) {
            return parent;
        }

        if (parent instanceof Container) {
            for (Component child : ((Container) parent).getComponents()) {
                Component subComponent = findSubComponent(child, className);

                if (subComponent != null) {
                    return subComponent;
                }
            }
        }

        return null;
    }

     /**
     * Hits keys by robot.
     */
    public static void hitKeys(Robot robot, int... keys) {
        for (int i = 0; i < keys.length; i++) {
            robot.keyPress(keys[i]);
        }

        for (int i = keys.length - 1; i >= 0; i--) {
            robot.keyRelease(keys[i]);
        }
    }
}
