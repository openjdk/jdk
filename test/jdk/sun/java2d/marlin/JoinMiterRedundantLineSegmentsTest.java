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
import java.util.List;
import java.util.ArrayList;
import java.awt.geom.*;
import javax.imageio.*;
import java.awt.image.*;

/**
 * This tests redundant line segments. That is: if you draw a line from A to B, and then a line from
 * B to B, then the expected behavior is for the last redundant segment to NOT affect the miter stroke.
 */
public class JoinMiterRedundantLineSegmentsTest {

    public static void main(String[] args) throws Exception {
        System.out.println("This test defines a series of shapes with optional shape data. The optional data (which is enclosed in brackets) should not make a difference in how the shape is rendered. This test renders the shape data with and without the bracketed segments and tests to see if those renderings are identical.");

        List<Test> tests = createTests();
        int sampleCtr = 1;
        boolean[] booleans = new boolean[] {false, true};
        boolean failed = false;
        String header = null;
        for (Test test : tests) {
            header = null;

            for (Object strokeHint : new Object[] { RenderingHints.VALUE_STROKE_PURE, RenderingHints.VALUE_STROKE_NORMALIZE } ) {
                for (boolean createStrokedShape : booleans) {
                    for (boolean closePath : booleans) {
                        try {
                            test.run(strokeHint, createStrokedShape, closePath);
                        } catch(TestException e) {
                            failed = true;

                            if (header == null) {
                                System.out.println();

                                header = "#############################\n";
                                header += "## " + test.name + "\n";
                                header += "## " + test.description + "\n";
                                header += "## " + test.shapeString + "\n";
                                header += "#############################";
                                System.out.println(header);
                            }

                            System.out.println();
                            System.out.println("# sample index = " + (sampleCtr));
                            System.out.println("strokeHint = " + strokeHint);
                            System.out.println("createStrokedShape = " + createStrokedShape);
                            System.out.println("closePath = " + closePath);
                            System.out.println("FAILED");
                            e.printStackTrace(System.out);
                            BufferedImage bi = e.getImage();
                            File file = new File("failure-"+sampleCtr+".png");
                            ImageIO.write(bi, "png", file);
                        }

                        sampleCtr++;
                    }
                }
            }
        }

        if (failed)
            System.exit(1);
    }

    private static List<Test> createTests() {
        List<Test> tests = new ArrayList<>();

        tests.add(new Test("Redundant diagonal line endpoint",
                "m 0 0 l 10 10 [l 10 10]",
                "This creates a diagonal line with a redundant endpoint; this is the core problem demonstrated in JDK-8264999."));

        tests.add(new Test("jdk-8264999",
                "m 24.954517 159 l 21.097446 157.5 [l 21.097446 157.5] l 17.61364 162 [l 17.61364 162] l 13.756569 163.5 [l 13.756569 163.5] l 11.890244 160.5",
                "This is the original shape reported in https://bugs.openjdk.org/browse/JDK-8264999"));

        tests.add(new Test("2x and 3x redundant lines",
                "m 24.954517 159 l 21.097446 157.5 [l 21.097446 157.5 l 21.097446 157.5] l 17.61364 162 [l 17.61364 162 l 17.61364 162 l 17.61364 162] l 13.756569 163.5 [l 13.756569 163.5 l 13.756569 163.5 l 13.756569 163.5] l 11.890244 160.5",
                "This is a derivative of JDK-8264999 that includes two or three redundant lines (instead of just one)."));

        tests.add(new Test("cubic curve with redundant line",
                "m 17 100 c 7 130 27 130 17 100 [l 17 100]",
                "This creates a simple cubic curve (a teardrop shape) with one redundant line at the end."));

        tests.add(new Test("degenerate cubic curve",
                "m 19 180 l 20 181 [c 20 181 20 181 20 181]",
                "This creates a degenerate cubic curve after the last end point."));

        tests.add(new Test("degenerate quadratic curve",
                "m 19 180 l 20 181 [q 20 181 20 181]",
                "This creates a degenerate quadratic curve after the last end point."));

        // This test reaches the line in Stroker.java where we detect a change of (+0, +0)
        // and manually change the dx:
        // dx = 1.0d;

//        tests.add(new Test("Redundant lineTo after moveTo",
//                "m 0 0 [l 0 0] l 10 10",
//                "This creates a diagonal line that may include a redundant lineTo after the moveTo"));

        // This test does NOT reach the same "dx = 1.0d" line. I'm not sure what the expected behavior here is.

//        tests.add(new Test("lineTo after close",
//                "m 0 0 z [l 10 10]",
//                "This tests a lineTo after a close (but without a second moveTo)"));

        // The following 2 tests fail because the mitered stroke covers different ares.
        // They might (?) be working as expected, and I just don't understand the expected behavior?

//        tests.add(new Test("Diagonal line, optional lineTo back",
//                "m 0 0 l 20 20 [l 0 0]",
//                "This creates a diagonal line and optionally returns to the starting point."));
//
//        tests.add(new Test("Diagonal line, optional close",
//                "m 0 0 l 20 20 l 0 0 [z]",
//                "This creates a diagonal line, returns to the starting point, and optionally closes the path."));

        // We've decided the following commented-out tests are invalid. The current interpretation is:
        // "a moveTo statement without any additional information should NOT result in rendering anything"

//        tests.add(new Test("empty line",
//                "m 19 180 [l 19 180]",
//                "This creates an empty shape with a lineTo the starting point."));
//
//        tests.add(new Test("empty degenerate cubic curve",
//                "m 19 180 [c 19 180 19 180 19 180]",
//                "This creates an empty degenerate cubic curve that is effectively a line to the starting point."));
//
//        tests.add(new Test("empty degenerate quadratic curve",
//                "m 19 180 [q 19 180 19 180]",
//                "This creates an empty degenerate quadratic curve that is effectively a line to the starting point."));
//
//        tests.add(new Test("moveTo then close",
//                "m 19 180 [z]",
//                "This moves to a starting position and then optionally closes the path."));

        return tests;
    }
}

class TestException extends Exception {
    BufferedImage bi;

    public TestException(Throwable t, BufferedImage bi) {
        super(t);
        this.bi = bi;
    }

    public BufferedImage getImage() {
        return bi;
    }
}

class Test {
    Path2D path_expected, path_actual;
    String name, description, shapeString;

    /**
     * @param name a short name of this test
     * @param shape shape data, including optional phrases in brackets. The shape should render the same
     *              whether the data in brackets is included or not.
     * @param description a sentence describing this test
     */
    public Test(String name, String shape, String description) {
        // make sure the test contains optional path data. Because if it doesn't: this test
        // is meaningless because nothing will change.
        if (!shape.contains("["))
            throw new IllegalArgumentException("The shape must contain optional path data.");

        this.shapeString = shape;
        this.name = name;
        this.description = description;
        path_expected = parse(shape, false);
        path_actual = parse(shape, true);
    }

    @Override
    public String toString() {
        return name;
    }

    private String stripBracketPhrases(String str) {
        StringBuffer sb = new StringBuffer();
        int ctr = 0;
        for (int a = 0; a < str.length(); a++) {
            char ch = str.charAt(a);
            if (ch == '[') {
                ctr++;
            } else if (ch == ']') {
                ctr--;
            } else if (ctr == 0) {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private Path2D.Double parse(String str, boolean includeBrackets) {
        if (includeBrackets) {
            str = str.replace('[', ' ');
            str = str.replace(']', ' ');
        } else {
            str = stripBracketPhrases(str);
        }
        Path2D.Double path = new Path2D.Double();
        String[] terms = str.split(" ");
        int a = 0;
        while (a < terms.length) {
            if ("m".equals(terms[a])) {
                path.moveTo(Double.parseDouble(terms[a + 1]), Double.parseDouble(terms[a + 2]));
                a += 3;
            } else if ("l".equals(terms[a])) {
                path.lineTo( Double.parseDouble(terms[a+1]), Double.parseDouble(terms[a+2]) );
                a += 3;
            } else if ("q".equals(terms[a])) {
                path.quadTo( Double.parseDouble(terms[a+1]), Double.parseDouble(terms[a+2]),
                        Double.parseDouble(terms[a+3]), Double.parseDouble(terms[a+4]) );
                a += 5;
            } else if ("c".equals(terms[a])) {
                path.curveTo( Double.parseDouble(terms[a+1]), Double.parseDouble(terms[a+2]),
                        Double.parseDouble(terms[a+3]), Double.parseDouble(terms[a+4]),
                        Double.parseDouble(terms[a+5]), Double.parseDouble(terms[a+6]) );
                a += 7;
            } else if ("z".equals(terms[a])) {
                path.closePath();
                a += 1;
            } else if(terms[a].trim().isEmpty()) {
                a += 1;
            } else {
                throw new RuntimeException("\""+terms[a]+"\" in \""+str+"\"");
            }
        }
        return path;
    }

    public void run(Object strokeRenderingHint, boolean createStrokedShape, boolean closePath) throws Exception {
        BufferedImage bi_expected = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB);
        BufferedImage bi_actual = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB);

        paint(path_expected, bi_expected, Color.black, strokeRenderingHint, createStrokedShape, closePath);
        paint(path_actual, bi_actual, Color.black, strokeRenderingHint, createStrokedShape, closePath);

        try {
            assertEquals(bi_expected, bi_actual);
        } catch(Exception e) {
            BufferedImage composite = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB);
            paint(path_expected, composite, Color.blue, strokeRenderingHint, createStrokedShape, closePath);
            paint(path_actual, composite, new Color(255,0,0,100), strokeRenderingHint, createStrokedShape, closePath);
            throw new TestException(e, composite);
        }
    }

    /**
     * Throw an exception if two images are not equal.
     */
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

    /**
     * Create a transform that maps from one rectangle to another.
     */
    private AffineTransform createTransform(Rectangle2D oldRect,Rectangle2D newRect) {
        double scaleX = newRect.getWidth() / oldRect.getWidth();
        double scaleY = newRect.getHeight() / oldRect.getHeight();

        double translateX = -oldRect.getX() * scaleX + newRect.getX();
        double translateY = -oldRect.getY() * scaleY + newRect.getY();
        return new AffineTransform(scaleX, 0, 0, scaleY, translateX, translateY);
    }

    /**
     * Paint a path to an image.
     */
    private void paint(Path2D path, BufferedImage dst, Color color, Object strokeRenderingHint,
                       boolean createStrokedShape, boolean closePath) {
        Rectangle2D pathBounds = path.getBounds2D();
        pathBounds.setFrame(pathBounds.getX() - 10,
                pathBounds.getY() - 10,
                pathBounds.getWidth() + 20,
                pathBounds.getHeight() + 20);
        Rectangle imageBounds = new Rectangle(0, 0, dst.getWidth(), dst.getHeight());

        Path2D p = new Path2D.Double();
        p.append(path, false);
        if (closePath)
            p.closePath();

        Graphics2D g = dst.createGraphics();
        g.transform(createTransform(pathBounds, imageBounds));
        g.setColor(color);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, strokeRenderingHint);
        Stroke stroke = new BasicStroke(3);
        if (createStrokedShape) {
            g.fill( stroke.createStrokedShape(p) );
        } else {
            g.setStroke(stroke);
            g.draw(p);
        }
        g.dispose();
    }
}
