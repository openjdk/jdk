/*
 * Copyright (c) 1999, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4197755
 * @summary Verifies that Arc2D.getBounds() is similar to Arc2D.getBounds2D()
 */

import java.awt.geom.Arc2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

public class Arc2DGetBoundsTest {
    public static void main(String[] args) {
        // Imagine a circle that represents a compass.
        // This arc represents the northern / top quarter.
        Arc2D arc = new Arc2D.Double(0, 0, 1000, 1000, 45, 90, Arc2D.PIE);

        // Create 8 pie slices, and place a dot in the center of each
        List<Point2D> samples = new ArrayList<>();
        for (int segment = 0; segment < 8; segment++) {
            double theta = -(segment + .5) / 8.0 * 2 * Math.PI;
            Point2D p = new Point2D.Double(
                    500 + 100 * Math.cos(theta),
                    500 + 100 * Math.sin(theta)
            );
            samples.add(p);
        }

        // these assertions have never been known to fail:
        assertTrue(!arc.contains(samples.get(0)));
        assertTrue(arc.contains(samples.get(1)));
        assertTrue(arc.contains(samples.get(2)));
        assertTrue(!arc.contains(samples.get(3)));
        assertTrue(!arc.contains(samples.get(4)));
        assertTrue(!arc.contains(samples.get(5)));
        assertTrue(!arc.contains(samples.get(6)));
        assertTrue(!arc.contains(samples.get(7)));

        assertTrue(arc.getBounds2D().contains(samples.get(0)));
        assertTrue(arc.getBounds2D().contains(samples.get(1)));
        assertTrue(arc.getBounds2D().contains(samples.get(2)));
        assertTrue(arc.getBounds2D().contains(samples.get(3)));
        assertTrue(!arc.getBounds2D().contains(samples.get(4)));
        assertTrue(!arc.getBounds2D().contains(samples.get(5)));
        assertTrue(!arc.getBounds2D().contains(samples.get(6)));
        assertTrue(!arc.getBounds2D().contains(samples.get(7)));


        assertTrue(arc.getBounds().contains(samples.get(0)));
        assertTrue(arc.getBounds().contains(samples.get(1)));
        assertTrue(arc.getBounds().contains(samples.get(2)));
        assertTrue(arc.getBounds().contains(samples.get(3)));

        // these are the assertions that failed before resolving 4197755
        assertTrue(!arc.getBounds().contains(samples.get(4)));
        assertTrue(!arc.getBounds().contains(samples.get(5)));
        assertTrue(!arc.getBounds().contains(samples.get(6)));
        assertTrue(!arc.getBounds().contains(samples.get(7)));
    }

    private static void assertTrue(boolean b) {
        if (!b)
            throw new Error();
    }
}
