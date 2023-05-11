/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 4925447 4887054 8285604
 * @key headful
 * @summary verifies that most basic rendering operations going are successfully
 * clipped against a complex clip shape
 * @run main ClipShapeRendering
*/

/**
 * This test creates a complex clip shape (basically, a rectangular hole
 * cut out of the center of the rendering area) and then cycles through
 * various rendering primitives (image copies, lines, text, and shapes)
 * under various situations (default, scaled transform, and wide lines).
 * After all of that, Robot is used to check whether the clip shape has
 * been disturbed by any of the rendering (no pixels from the rendering
 * operations should have done anything inside the clipped-out area); the
 * test passes or fails based on whether the clip shape is undisturbed.
 *
 * There is a performance-test version of this app which runs all of the
 * tests in loops and times the results.  This can be useful to see, for
 * example, the difference in performance between old and new internal
 * implementations of this clip-shape situation.  To run the performance
 * test, run ClipShapeRendering -perf.
 */

import java.awt.AWTException;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.Stroke;
import java.awt.Toolkit;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DirectColorModel;
import java.awt.image.WritableRaster;


public class ClipShapeRendering extends Frame {
    BufferedImage image;
    BufferedImage imageBM;

    static Image offScreenImage;
    static boolean timeToRun = false;
    static Color imageColor = Color.red;
    static Color fillColor = Color.blue;
    static boolean perfMode = false;
    static boolean showCapture = false;
    static Rectangle clipRect = new Rectangle(100, 100, 100, 100);

    // move away from cursor
    private final static int OFFSET_X = 20;
    private final static int OFFSET_Y = 20;

    public ClipShapeRendering() {
        super("On-screen rendering test frame");
    }

    public void initImages(Color imageColor) {
        int w = getWidth();
        int h = getHeight();

        ColorModel cm = new DirectColorModel(25, 0xff0000, 0xff00, 0xff, 0x1000000);
        WritableRaster wr =
            cm.createCompatibleWritableRaster(w, h);
        imageBM = new BufferedImage(cm, wr,
                                  cm.isAlphaPremultiplied(), null);
        Graphics g2 = imageBM.createGraphics();
        g2.setColor(imageColor);
        g2.fillRect(0, 0, w, h);

        image = new BufferedImage(w, h,
                                  BufferedImage.TYPE_INT_RGB);
        g2 = image.createGraphics();
        g2.setColor(imageColor);
        g2.fillRect(0,0, w, h);
        g2.dispose();

        offScreenImage = createImage(w, h);
    }

    public void paint(Graphics g) {
        synchronized (this) {
            timeToRun = true;
            notifyAll();
        }
    }

    public void runTests() {
        initImages(imageColor);

        // run off-screen test
        System.out.println("Running OFF-SCREEN tests..");
        runTest(offScreenImage.getGraphics());

        // run on-screen test
        System.out.println("Running ON-SCREEN tests..");
        runTest(getGraphics());
    }

    /**
     * Set various parameters on the Graphics object and call the
     * rendering loop with each variation
     */
    public void runTest(Graphics g) {
        int w = getWidth();
        int h = getHeight();
        Area area = new Area( new Rectangle(0,0, w, h));
        area.subtract(new Area(clipRect));
        // Fill completely with background color
        g.setColor(fillColor);
        g.fillRect(0, 0, w, h);

        // Set the clip shape
        g.setClip(area);

        // Now perform various rendering operations
        g.setColor(Color.black);
        Graphics2D g2 = (Graphics2D)g;
        if (perfMode) {
            System.out.println("Default Graphics Results:");
            System.out.println("-------------------------");
        }
        renderingLoop(g2);

        if (perfMode) {
            System.out.println("Scaling Transform Results:");
            System.out.println("-------------------------");
        }
        AffineTransform oldXform = g2.getTransform();
        g2.scale(.9, 1.15);
        renderingLoop(g2);
        g2.setTransform(oldXform);

        if (perfMode) {
            System.out.println("Wide Lines Results:");
            System.out.println("-------------------");
        }
        Stroke oldStroke = g2.getStroke();
        g2.setStroke(new BasicStroke(5.0f));
        renderingLoop(g2);
        g2.setStroke(oldStroke);
    }

    public void renderingLoop(Graphics2D g) {
        int numReps = 1;
        int numTextReps = 1;
        long start, end;
        if (perfMode) {
            numReps = 1000;
            numTextReps = 50;
        }
        int w = getWidth();
        int h = getHeight();

        // Image copies
        robot.waitForIdle();
        start = System.currentTimeMillis();
        for (int i = 0; i < numReps; ++i) {
            g.drawImage(image, 0, 0, null);
            g.drawImage(image, -10, -10, null);
            g.drawImage(image, 50, 50, null);
            g.drawImage(image, 40, 10, null);
        }
        for (int i = 0; i < numReps; ++i) {
            g.drawImage(imageBM, 0, 0, Color.yellow, null);
            g.drawImage(imageBM, -10, -10, Color.yellow, null);
            g.drawImage(imageBM, 50, 50, Color.yellow, null);
            g.drawImage(imageBM, 40, 10, Color.yellow, null);
        }
        robot.waitForIdle();
        end = System.currentTimeMillis();
        if (perfMode) {
            System.out.println("Image Copies   : " + (end - start) + " ms");
        }

        // Image scales
        start = System.currentTimeMillis();
        for (int i = 0; i < numReps; ++i) {
            g.drawImage(image, 0, 0, 500, 500, null);
        }
        robot.waitForIdle();
        end = System.currentTimeMillis();
        if (perfMode) {
            System.out.println("Image scales   : " + (end - start) + " ms");
        }

        // Lines
        start = System.currentTimeMillis();
        for (int i = 0; i < numReps; ++i) {
            g.drawLine(0, 0, w, h);
            g.drawLine(0, h, w, 0);
            g.drawLine(0, h / 2, w, h / 2);
            g.drawLine(w / 2, 0, w / 2, h);
        }
        robot.waitForIdle();
        end = System.currentTimeMillis();
        if (perfMode) {
            System.out.println("drawLine       : " + (end - start) + " ms");
        }

        // Text

        // Non-AA
        start = System.currentTimeMillis();
        for (int i = 0; i < numTextReps; ++i) {
            for (int x = 0; x < w; x += 20) {
                for (int y = 0; y < h; y += 17) {
                    g.drawString("This is a string, this is only a string", x,
                                 y);
                }
            }
        }
        robot.waitForIdle();
        end = System.currentTimeMillis();
        if (perfMode) {
            System.out.println("Text Non-AA    : " + (end - start) + " ms");
        }
        // Anti-Aliased
        Graphics2D g2 = (Graphics2D)g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
        start = System.currentTimeMillis();
        for (int i = 0; i < numTextReps; ++i) {
            for (int x = 0; x < w; x += 20) {
                for (int y = 0; y < h; y += 17) {
                    g.drawString("This is a string, this is only a string", x,
                                 y);
                }
            }
        }
        robot.waitForIdle();
        end = System.currentTimeMillis();
        if (perfMode) {
            System.out.println("Text General AA: " + (end - start) + " ms");
        }
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_OFF);
        // Text AA
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        start = System.currentTimeMillis();
        for (int i = 0; i < numTextReps; ++i) {
            for (int x = 0; x < w; x += 20) {
                for (int y = 0; y < h; y += 17) {
                    g.drawString("This is a string, this is only a string", x,
                                 y);
                }
            }
        }
        robot.waitForIdle();
        end = System.currentTimeMillis();
        if (perfMode) {
            System.out.println("Text textAA    : " + (end - start) + " ms");
        }
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

        // Arcs
        start = System.currentTimeMillis();
        if (numReps > 1) {
            numReps /= 10;
        }
        for (int i = 0; i < numReps; ++i) {
            for (int x = 0; x < w; x += 20) {
                for (int y = 0; y < h; y += 17) {
                    g.drawArc(x, y, 30, 30, 0, 45);
                    g.fillArc(x, y, 25, 25, 0, 45);
                }
            }
        }
        robot.waitForIdle();
        end = System.currentTimeMillis();
        if (perfMode) {
            System.out.println("arcs           : " + (end - start) + " ms");
        }

        // Ovals
        start = System.currentTimeMillis();
        if (numReps > 1) {
            numReps /= 10;
        }
        for (int i = 0; i < numReps; ++i) {
            for (int x = 0; x < w; x += 20) {
                for (int y = 0; y < h; y += 17) {
                    g.drawOval(x, y, 20, 20);
                    g.fillOval(x, y, 15, 15);
                }
            }
        }
        robot.waitForIdle();
        end = System.currentTimeMillis();
        if (perfMode) {
            System.out.println("ovals          : " + (end - start) + " ms");
        }

        // Rects
        start = System.currentTimeMillis();
        for (int i = 0; i < numReps; ++i) {
            for (int x = 0; x < w; x += 20) {
                for (int y = 0; y < h; y += 17) {
                    g.drawRect(x, y, 20, 20);
                    g.fillRect(x, y, 18, 18);
                }
            }
        }
        robot.waitForIdle();
        end = System.currentTimeMillis();
        if (perfMode) {
            System.out.println("rects          : " + (end - start) + " ms");
        }

        // GeneralPath rendering
        GeneralPath drawGP = new GeneralPath();
        for (int i = 0; i < 30; ++i) {
            Rectangle rect = new Rectangle(5 * i, 2 * i, 27, 19);
            drawGP.append(rect, false);
        }
        GeneralPath fillGP = new GeneralPath();
        for (int i = 0; i < 30; ++i) {
            Rectangle rect = new Rectangle(5 * i, 100 + 2 * i, 27, 19);
            fillGP.append(rect, false);
        }
        Graphics2D g2d = (Graphics2D)g;
        start = System.currentTimeMillis();
        for (int i = 0; i < numReps; ++i) {
            g2d.draw(drawGP);
            g2d.fill(fillGP);
        }
        robot.waitForIdle();
        end = System.currentTimeMillis();
        if (perfMode) {
            System.out.println("GeneralPath    : " + (end - start) + " ms");
        }

    }

    public static void usage() {
        System.err.println("java ClipShapeRendering [-perf] [-show]");
        System.err.println("  -perf : runs performance benchmark (1000 reps)");
        System.err.println("  -show : shows a frame with captured clip area");
        System.exit(1);
    }

    public static boolean checkResult(BufferedImage clientPixels) {
        int pixels[] = new int[clipRect.width * clipRect.height];
        clientPixels.getRGB(0, 0, clipRect.width,
                            clipRect.height, pixels, 0,
                            clipRect.width);
        int pixelIndex = 0;
        for (int row = 0; row < clipRect.height; ++row) {
            for (int col = 0; col < clipRect.width; ++col) {
                if (!(new Color(pixels[pixelIndex])).equals(fillColor)) {
                    System.err.println("Incorrect color " +
                                       Integer.toHexString(pixels[pixelIndex]) +
                                       " instead of " +
                                       Integer.toHexString(fillColor.getRGB()) +
                                       " in pixel (" + (clipRect.x + col) +
                                       ", " + (clipRect.y + row) + ")");
                    return false;
                }
                pixelIndex++;
            }
        }
        return true;
    }

    static volatile Robot robot;
    public static void main(String args[]) throws Exception {

        for (int i = 0; i < args.length; ++i) {
            if (args[i].equals("-perf")) {
                perfMode = true;
            } else if (args[i].equals("-show")) {
                showCapture = true;
            } else {
                usage();
            }
        }

        try {
            robot = new Robot();
        } catch (AWTException e) {
            throw new RuntimeException("Can't create robot: " + e);
        }

        ClipShapeRendering clipTest = new ClipShapeRendering();
        clipTest.setSize(300, 300);
        clipTest.setLocationRelativeTo(null);
        clipTest.setAlwaysOnTop(true);
        clipTest.setVisible(true);
        try {
            synchronized (clipTest) {
                while (!timeToRun) {
                    clipTest.wait(300);
                }
            }
        } catch (InterruptedException e) {}

        clipTest.runTests();

        // check off-screen rendering;
        BufferedImage offScreenClientPixels =
            (BufferedImage)clipTest.createImage(clipRect.width,
                                                clipRect.height);
        Graphics clientG = offScreenClientPixels.getGraphics();
        clientG.drawImage(offScreenImage,
                          0, 0, clipRect.width, clipRect.height,
                          clipRect.x, clipRect.y,
                          clipRect.x + clipRect.width,
                          clipRect.y + clipRect.height,
                          null);
        if (showCapture) {
            CaptureFrame f =
                new CaptureFrame("OffScreen Image", offScreenImage);
            f.setVisible(true);
        }

        // check onscreen rendering
        Point clientLoc = clipTest.getLocationOnScreen();
        Rectangle r = (Rectangle)clipRect.clone();
        r.translate(clientLoc.x, clientLoc.y);

        // move mouse cursor away from captured region as in some system
        // cursor remain visible in composite captured image
        robot.mouseMove(r.x - OFFSET_X, r.y - OFFSET_Y);
        robot.waitForIdle();
        BufferedImage onScreenClientPixels = robot.createScreenCapture(r);
        try { Thread.sleep(1000); } catch (Exception e) {}
        if (showCapture) {
            CaptureFrame f =
                new CaptureFrame("Onscreen clip area", onScreenClientPixels);
            f.setVisible(true);
        } else {
            clipTest.dispose();
        }


        System.out.print("Checking results for off-screen rendering..");
        boolean offScreenPassed = checkResult(offScreenClientPixels);
        System.out.println("done.");

        System.out.print("Checking results for on-screen rendering..");
        boolean onScreenPassed = checkResult(onScreenClientPixels);
        System.out.println("done.");
        if (!offScreenPassed || !onScreenPassed) {
            javax.imageio.ImageIO.write(offScreenClientPixels, "png", new java.io.File("offscreen.png"));
            javax.imageio.ImageIO.write(onScreenClientPixels, "png", new java.io.File("onscreen.png"));
            throw new RuntimeException("Test failed. off-screen: " +
                                       (offScreenPassed?"passed":"failed") +
                                       "   on-screen: " +
                                       (onScreenPassed?"passed":"failed"));
        }
        System.out.println("Passed");
    }

    static {
        System.setProperty("sun.java2d.pmoffscreen", "true");
    }

}

class CaptureFrame extends Frame {
   static int x = 300, y = 0;
   Image clientPixels;
   public CaptureFrame(String title,  Image clientPixels) {
       super("Capture Frame: " + title);
       this.clientPixels = clientPixels;
       int w = clientPixels.getWidth(null);
       int h = clientPixels.getHeight(null);
       setSize(w, h + 30);
       setLocation(x, y);
       x += w;
       if (x + w > 1024) {
           x = 300;
           y += h;
       }
       add(new Component() {
           public void paint(Graphics g) {
               g.drawImage(CaptureFrame.this.clientPixels, 0, 0, null);
           }
       });
   }
};
