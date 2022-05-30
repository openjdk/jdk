/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Pass if app exits without error code
 * @bug 8264999
 */

import java.awt.*;
import java.io.*;
import java.awt.geom.*;
import javax.imageio.*;
import java.awt.image.*;

/**
 * This tests redundant line segments. That is: if you draw a line from A to B, and then a line from
 * B to B, then the expected behavior is for the last redundant segment to NOT affect the miter stroke.
 */
public class JoinMiterRedundantLineSegmentsTest {

    public static void main(String[] args) throws Exception {

        boolean failed = false;

        // we'll run several scenarios just to be safe:

        boolean[] booleans = new boolean[] {false, true};
        for (boolean strokeHint_pure : booleans) {
            for (boolean createStrokedShape : booleans) {
                for (boolean closePath : booleans) {

                    BufferedImage expected = null;
                    BufferedImage actual = null;
                    try {
                        expected = createImage(false, strokeHint_pure, createStrokedShape, closePath);
                        actual = createImage(true, strokeHint_pure, createStrokedShape, closePath);
                        assertEquals(expected, actual);
                    } catch (RuntimeException e) {
                        String id = strokeHint_pure+"-"+createStrokedShape+"-"+closePath;
                        if (expected != null) {
                            File file = new File("JoinMiterTest2-" + id + "-expected.png");
                            System.err.println("Failure: "+file.getAbsolutePath());
                            ImageIO.write(expected, "png", file);
                        }
                        if (actual != null) {
                            File file = new File("JoinMiterTest2-" + id + "-actual.png");
                            System.err.println("Failure: "+file.getAbsolutePath());
                            ImageIO.write(actual, "png", file);
                        }
                        e.printStackTrace();
                        failed = true;
                    }
                }
            }
        }

        if (failed)
            System.exit(1);
    }

    /**
     * @param addRedundantPoints add PathIterator segments that result in redundant SEG_LINETO instructions. When true
     *                           this may also include quadratic and cubic segments that should degenerate into lines.
     * @param useHintPure if true we render the stroke using RenderingHints.VALUE_STROKE_PURE
     * @param createStrokedShape if true we use graphics.fill(BasicStroke#createStrokedShape(path)). If false
     *                           we use graphics.draw(path)
     * @param closePath if true we close subpaths (once we make sure they return to their starting location)
     * @return an image we compare against to determine if this test passes or fails.
     * @throws Exception
     */
    private static BufferedImage createImage(boolean addRedundantPoints, boolean useHintPure, boolean createStrokedShape, boolean closePath) throws Exception {
        BufferedImage bi = new BufferedImage(500, 500, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = (Graphics2D) bi.getGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, useHintPure ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);

        g.setBackground(Color.white);
        g.clearRect(0, 0, bi.getWidth(), bi.getHeight());

        AffineTransform at = g.getTransform();

        g.transform(AffineTransform.getTranslateInstance(0, -1400));
        g.transform(AffineTransform.getScaleInstance(10, 10));

        GeneralPath path1 = new GeneralPath();
        path1.moveTo(24.954517, 159);

        path1.lineTo(21.097446, 157.5);
        if (addRedundantPoints)
            path1.lineTo(21.097446, 157.5);

        path1.lineTo(17.61364, 162);
        if (addRedundantPoints) {
            path1.lineTo(17.61364, 162);
            path1.lineTo(17.61364, 162);
        }

        path1.lineTo(13.756569, 163.5);
        if (addRedundantPoints) {
            path1.lineTo(13.756569, 163.5);
            path1.lineTo(13.756569, 163.5);
            path1.lineTo(13.756569, 163.5);
        }

        path1.lineTo(11.890244, 160.5);
        if (addRedundantPoints)
            path1.lineTo(11.890244, 160.5);

        if (closePath) {
            path1.lineTo(24.954517, 159);
            path1.closePath();
        }

        draw(g, Color.red, path1, createStrokedShape);

        // normal cubics don't suffer the same problem; I just wanted visual confirmation so I threw this in:

        Path2D path2 = new Path2D.Double();
        path2.moveTo(17, 150);
        path2.curveTo(17-10, 150-20, 17+10, 150-20, 17, 150);

        if (closePath)
            path2.closePath();

        draw(g, Color.green, path2, createStrokedShape);

        path2.transform(AffineTransform.getRotateInstance(1, 20, 140));

        draw(g, Color.cyan, path2, createStrokedShape);

        // test degenerate cubics

        Path2D path3 = new Path2D.Double();
        path3.moveTo(19, 180);
        if (addRedundantPoints)
            path3.curveTo(19, 180, 19, 180, 19, 180);

        if (closePath)
            path3.closePath();

        draw(g, Color.pink, path3, createStrokedShape);

        // add a cubic that ends by pointing northeast, then see if any redundant
        // segments (lines or higher degenerating segments) change the miter:
        Path2D path4 = new Path2D.Double();
        path4.moveTo(22, 175);
        path4.curveTo(15,175,25,155,30,150);
        if (addRedundantPoints) {
            path4.lineTo(30, 150);
            path4.quadTo(30, 150,30, 150);
            path4.curveTo(30, 150,30, 150,30, 150);
        }

        if (closePath)
            path4.closePath();

        draw(g, Color.blue, path4, createStrokedShape);

        // and test degenerate quadratics

        Path2D path5 = new Path2D.Double();
        path5.moveTo(22, 170);
        if (addRedundantPoints)
            path5.quadTo(22, 170, 22, 170);

        if (closePath)
            path5.closePath();

        draw(g, Color.green, path5, createStrokedShape);

        return bi;
    }

    private static void draw(Graphics2D g, Color color, Shape shape, boolean createStrokedShape) {
        g.setColor(color);
        if (createStrokedShape) {
            g.fill(g.getStroke().createStrokedShape(shape));
        } else {
            g.draw(shape);
        }
    }

    private static void assertEquals(BufferedImage bi1, BufferedImage bi2) {
        int w = bi1.getWidth();
        int h = bi1.getHeight();
        int[] row1 = new int[w];
        int[] row2 = new int[w];
        for (int y = 0; y < h; y++) {
            bi1.getRaster().getDataElements(0,y,w,1,row1);
            bi2.getRaster().getDataElements(0,y,w,1,row2);
            for (int x = 0; x < w; x++) {
                if (row1[x] != row2[x])
                    throw new RuntimeException("failure at ("+x+", "+y+"): 0x"+Integer.toHexString(row1[x])+" != 0x"+Integer.toHexString(row2[x]));
            }
        }
    }
}
