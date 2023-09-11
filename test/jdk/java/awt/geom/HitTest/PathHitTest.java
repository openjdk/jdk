/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Polygon;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;

/*
 * @test
 * @bug 4210936 4214524
 * @summary Tests the results of the hit test methods on 3 different
 *          Shape objects - Polygon, Area, and GeneralPath.  Both an
 *          automatic test for constraint compliance and a manual
 *          test for correctness are included in this one class.
 * @run main PathHitTest
 */

public class PathHitTest {
    public static final int BOXSIZE = 5;
    public static final int BOXCENTER = 2;
    public static final int TESTSIZE = 400;

    public static Shape[] testShapes = new Shape[5];
    public static String[] testNames = {
            "Polygon",
            "EvenOdd GeneralPath",
            "NonZero GeneralPath",
            "Area from EO GeneralPath",
            "Area from NZ GeneralPath",
    };

    static {
        GeneralPath gpeo = new GeneralPath(GeneralPath.WIND_EVEN_ODD);
        Ellipse2D ell = new Ellipse2D.Float();
        Point2D center = new Point2D.Float();
        AffineTransform at = new AffineTransform();
        for (int i = 0; i < 360; i += 30) {
            center.setLocation(100, 0);
            at.setToTranslation(200, 200);
            at.rotate(i * Math.PI / 180);
            at.transform(center, center);
            ell.setFrame(center.getX()-50, center.getY()-50, 100, 100);
            gpeo.append(ell, false);
        }
        GeneralPath side = new GeneralPath();
        side.moveTo(0, 0);
        side.lineTo(15, 10);
        side.lineTo(30, 0);
        side.lineTo(45, -10);
        side.lineTo(60, 0);
        append4sides(gpeo, side, 20, 20);
        side.reset();
        side.moveTo(0, 0);
        side.quadTo(15, 10, 30, 0);
        side.quadTo(45, -10, 60, 0);
        append4sides(gpeo, side, 320, 20);
        side.reset();
        side.moveTo(0, 0);
        side.curveTo(15, 10, 45, -10, 60, 0);
        append4sides(gpeo, side, 20, 320);

        GeneralPath gpnz = new GeneralPath(GeneralPath.WIND_NON_ZERO);
        gpnz.append(gpeo, false);
        Polygon p = new Polygon();
        p.addPoint( 50,  50);
        p.addPoint( 60, 350);
        p.addPoint(250, 340);
        p.addPoint(260, 150);
        p.addPoint(140, 140);
        p.addPoint(150, 260);
        p.addPoint(340, 250);
        p.addPoint(350,  60);
        testShapes[0] = p;
        testShapes[1] = gpeo;
        testShapes[2] = gpnz;
        testShapes[3] = new Area(gpeo);
        testShapes[3].getPathIterator(null);
        testShapes[4] = new Area(gpnz);
        testShapes[4].getPathIterator(null);
    }

    private static void append4sides(GeneralPath path, GeneralPath side,
                                     double xoff, double yoff) {
        AffineTransform at = new AffineTransform();
        at.setToTranslation(xoff, yoff);
        for (int i = 0; i < 4; i++) {
            path.append(side.getPathIterator(at), i != 0);
            at.rotate(Math.toRadians(90), 30, 30);
        }
    }

    public static void main(String[] argv) {
        int totalerrs = 0;
        for (int i = 0; i < testShapes.length; i++) {
            totalerrs += testshape(testShapes[i], testNames[i]);
        }
        if (totalerrs != 0) {
            throw new RuntimeException(totalerrs+
                    " constraint conditions violated!");
        }
    }

    public static int testshape(Shape s, String name) {
        int numerrs = 0;
        long start = System.currentTimeMillis();
        for (int y = 0; y < TESTSIZE; y += BOXSIZE) {
            for (int x = 0; x < TESTSIZE; x += BOXSIZE) {
                boolean rectintersects = s.intersects(x, y, BOXSIZE, BOXSIZE);
                boolean rectcontains = s.contains(x, y, BOXSIZE, BOXSIZE);
                boolean pointcontains = s.contains(x+BOXCENTER, y+BOXCENTER);
                if (rectcontains && !rectintersects) {
                    System.err.println("rect is contained "+
                            "but does not intersect!");
                    numerrs++;
                }
                if (rectcontains && !pointcontains) {
                    System.err.println("rect is contained "+
                            "but center is not contained!");
                    numerrs++;
                }
                if (pointcontains && !rectintersects) {
                    System.err.println("center is contained "+
                            "but rect does not intersect!");
                    numerrs++;
                }
            }
        }
        long end = System.currentTimeMillis();
        System.out.println(name+" completed in "+
                (end-start)+"ms with "+
                numerrs+" errors");
        return numerrs;
    }
}
