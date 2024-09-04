/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/*
 * @test
 * @bug 8338103
 * @key headful
 * @summary Verifies that the OpenGL pipeline does not create artifacts
 * with swing components after window is zoomed to maximum size and then
 * resized back to normal. The test case simulates this operation using
 * a JButton. A file image of the component will be saved before and after
 * the window resize if the test fails. The test passes if both the button
 * images are the same.
 * @run main/othervm -Dsun.java2d.opengl=true -Dsun.java2d.opengl.fbobject=false SwingButtonResizeTestWithOpenGL
 * @run main/othervm -Dsun.java2d.opengl=true -Dsun.java2d.opengl.fbobject=true SwingButtonResizeTestWithOpenGL
 * @run main/othervm -Dsun.java2d.opengl=false SwingButtonResizeTestWithOpenGL
 * @run main/othervm SwingButtonResizeTestWithOpenGL
 */
/*
 * @test
 * @key headful
 * @requires (os.family == "windows")
 * @run main/othervm -Dsun.java2d.d3d=false SwingButtonResizeTestWithOpenGL
 * @run main/othervm -Dsun.java2d.d3d=true SwingButtonResizeTestWithOpenGL
 */
/*
 * @test
 * @key headful
 * @requires (os.family == "linux")
 * @run main/othervm -Dsun.java2d.xrender=false SwingButtonResizeTestWithOpenGL
 * @run main/othervm -Dsun.java2d.xrender=true SwingButtonResizeTestWithOpenGL
 */
/*
 * @test
 * @key headful
 * @requires (os.family == "mac")
 * @run main/othervm -Dsun.java2d.metal=false SwingButtonResizeTestWithOpenGL
 * @run main/othervm -Dsun.java2d.metal=true SwingButtonResizeTestWithOpenGL
 */
public class SwingButtonResizeTestWithOpenGL {
    private static Robot robot;
    private static CountDownLatch focusGainedLatch;
    private JFrame frame;
    private JButton button;

    public SwingButtonResizeTestWithOpenGL() {

        try {
            SwingUtilities.invokeAndWait(() -> createGUI());
        } catch (Exception e) {
            throw new RuntimeException("Problems creating GUI");
        }
    }

    private void createGUI() {
        frame = new JFrame("SwingButtonResizeTestWithOpenGL");
        button = new JButton("Button A");
        frame.setLocation(200, 200);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        button.setPreferredSize(new Dimension(300, 300));
        button.setFocusPainted(false);
        button.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent fe) {
                focusGainedLatch.countDown();
            }
        });
        frame.getContentPane().setLayout(new FlowLayout());
        frame.getContentPane().add(button);
        frame.pack();
        frame.setVisible(true);
        frame.toFront();
    }

    public static void main(String[] args) throws Exception {
        focusGainedLatch = new CountDownLatch(1);
        SwingButtonResizeTestWithOpenGL test =
                new SwingButtonResizeTestWithOpenGL();
        test.runTest();
    }

    public void runTest() throws Exception {
        BufferedImage bimage1;
        BufferedImage bimage2;

        try {
            robot = new Robot();
            robot.waitForIdle();
            robot.delay(1000);
            if (focusGainedLatch.await(3, TimeUnit.SECONDS)) {
                System.out.println("Button focus gained...");
            } else {
                System.out.println("Button focus not gained...");
                throw new RuntimeException(
                        "Can't gain focus on button even after waiting " +
                        "too long..");
            }

            System.out.println("Getting initial button image..image1");
            bimage1 = getButtonImage();

            // some platforms may not support maximize frame
            if (frame.getToolkit().isFrameStateSupported(
                    JFrame.MAXIMIZED_BOTH)) {
                // maximize frame from normal size
                frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
                System.out.println("Frame is maximized");
                robot.waitForIdle();
                robot.delay(100);

                if (frame.getToolkit().isFrameStateSupported(JFrame.NORMAL)) {
                    System.out.println("Frame is back to normal");
                    // resize from maximum size to normal
                    frame.setExtendedState(JFrame.NORMAL);
                    robot.waitForIdle();
                    robot.delay(100);
                    // capture image of JButton after resize
                    System.out.println(
                            "Getting image of JButton after resize..image2");
                    bimage2 = getButtonImage();

                    // compare button images from before and after frame resize
                    DiffImage di = new DiffImage(bimage1.getWidth(),
                                                 bimage1.getHeight());
                    System.out.println(
                            "Taking the diff of two images, image1 and image2");
                    if (!di.compare(bimage1, bimage2)) {
                        throw new RuntimeException(
                                "Button renderings are different after window "
                                + "resize, num of Diff Pixels="
                                + di.getNumDiffPixels());
                    } else {
                        System.out.println("Test passed...");
                    }

                } else {
                    System.out.println(
                            "Test skipped: JFrame.NORMAL resize is " +
                            "not supported");
                }

            } else {
                System.out.println(
                        "Test skipped: JFrame.MAXIMIZED_BOTH resize is " +
                        "not supported");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> disposeFrame());
        }
    }

    // Capture button rendering as a BufferedImage
    private BufferedImage getButtonImage() {
        try {
            robot.waitForIdle();
            robot.delay(500);

            AtomicReference<Point> buttonLocRef = new AtomicReference<>();
            SwingUtilities.invokeAndWait(
                    () -> buttonLocRef.set(button.getLocationOnScreen()));
            Point buttonLoc = buttonLocRef.get();
            System.out.println("Button loc: " + buttonLoc);
            return robot.createScreenCapture(
                    new Rectangle(buttonLoc.x, buttonLoc.y, button.getWidth(),
                                  button.getHeight()));
        } catch (Exception e) {
            throw new RuntimeException(
                    "Problems capturing button image from Robot", e);
        }
    }

    private void disposeFrame() {
        if(frame != null) {
            frame.dispose();
        }
    }

    // Save BufferedImage to PNG file
    private void saveButtonImage(BufferedImage image, File file) {
        if (image != null) {
            try {
                System.out.println(
                        "Saving button image to " + file.getAbsolutePath());
                ImageIO.write(image, "PNG", file);
            } catch (Exception e) {
                throw new RuntimeException("Could not write image file");
            }
        } else {
            throw new RuntimeException("BufferedImage was set to null");
        }
    }

    private class DiffImage extends BufferedImage {

        public boolean diff = false;
        public int nDiff = -1;

        Color bgColor;

        int threshold = 0;

        public DiffImage(int w, int h) {
            super(w, h, BufferedImage.TYPE_INT_ARGB);
            bgColor = Color.LIGHT_GRAY;
        }

        public int getNumDiffPixels() {
            return nDiff;
        }

        public boolean compare(BufferedImage img1, BufferedImage img2)
                throws IOException {

            int minx1 = img1.getMinX();
            int minx2 = img2.getMinX();
            int miny1 = img1.getMinY();
            int miny2 = img2.getMinY();

            int w1 = img1.getWidth();
            int w2 = img2.getWidth();
            int h1 = img1.getHeight();
            int h2 = img2.getHeight();

            if ((minx1 != minx2) || (miny1 != miny2) || (w1 != w2)
                || (h1 != h2)) {
                // image sizes are different
                throw new RuntimeException(
                        "img1: <" + minx1 + "," + miny1 + "," + w1 + "x" + h1
                        + ">" + " img2: " + minx2 + "," + miny2 + "," + w2 + "x"
                        + h2 + ">" + " are different sizes");
            }
            // Get the actual data behind the images
            Raster ras1 = img1.getData();
            Raster ras2 = img2.getData();

            ColorModel cm1 = img1.getColorModel();
            ColorModel cm2 = img2.getColorModel();

            int r1, r2;  // red
            int g1, g2;  // green
            int b1, b2;  // blue

            Object o1 = null;
            Object o2 = null;
            nDiff = 0;
            for (int x = minx1; x < (minx1 + w1); x++) {
                for (int y = miny1; y < (miny1 + h1); y++) {
                    // Causes rasters to allocate data
                    o1 = ras1.getDataElements(x, y, o1);
                    // and we reuse the data on every loop
                    o2 = ras2.getDataElements(x, y, o2);

                    r1 = cm1.getRed(o1);
                    r2 = cm2.getRed(o2);
                    g1 = cm1.getGreen(o1);
                    g2 = cm2.getGreen(o2);
                    b1 = cm1.getBlue(o1);
                    b2 = cm2.getBlue(o2);

                    int redAbs = Math.abs(r1 - r2);
                    int greenAbs = Math.abs(g1 - g2);
                    int blueAbs = Math.abs(b1 - b2);
                    if ((redAbs > threshold)
                        || (greenAbs > threshold)
                        || (blueAbs > threshold)) {
                        // pixel is different
                        setDiffPixel(x, y, redAbs, greenAbs, blueAbs);
                        nDiff++;
                    } else {
                        setSamePixel(x, y);
                    }

                }
            }
            if (nDiff != 0) {
                ImageIO.write(this, "png",
                              new File("diffImage.png"));
                saveButtonImage(img1, new File("image1.png"));
                saveButtonImage(img2, new File("image2.png"));
            }
            return nDiff == 0;
        }

        void setDiffPixel(int x, int y, int r, int g, int b) {
            diff = true;
            setPixelValue(x, y, 255, r, g, b);
        }

        void setSamePixel(int x, int y) {
            if (bgColor != null) {
                setPixelValue(x, y, 255, bgColor.getRed(),
                              bgColor.getGreen(),
                              bgColor.getBlue());
            } else {
                setPixelValue(x, y, 255, Color.black.getRed(),
                              Color.black.getGreen(), Color.black.getBlue());
            }
        }

        void setPixelValue(int x, int y, int a, int r, int g, int b) {
            // setRGB uses BufferedImage.TYPE_INT_ARGB format
            int pixel =
                    ((a & 0xff) << 24) + ((r & 0xff) << 16) + ((g & 0xff) << 8)
                    + ((b & 0xff));
            setRGB(x, y, pixel);
        }

    }

}


