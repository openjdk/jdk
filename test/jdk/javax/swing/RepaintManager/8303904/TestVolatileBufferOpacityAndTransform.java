/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
   @bug 8303904
   @summary when "swing.volatileImageBufferEnabled" is "false" windows repaint
            as opaque and as if on a 100% resolution monitor
*/

import javax.swing.JFrame;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Transparency;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.concurrent.CountDownLatch;

public class TestVolatileBufferOpacityAndTransform {

    public static void main(String[] args) throws Exception {
        System.setProperty("swing.volatileImageBufferEnabled", "false");
        TestVolatileBufferOpacityAndTransform test =
                new TestVolatileBufferOpacityAndTransform();
        test.run();
    }

    int testFailureCtr = 0;
    CountDownLatch latch = new CountDownLatch(1);

    public TestVolatileBufferOpacityAndTransform() {
        SwingUtilities.invokeLater(() -> {
            try {
                GraphicsDevice[] allDevices = GraphicsEnvironment.
                        getLocalGraphicsEnvironment().getScreenDevices();
                for (GraphicsDevice gd : allDevices) {
                    String id = gd.getIDstring() + "; " +
                            gd.getDefaultConfiguration().getDefaultTransform();
                    System.out.println("Testing GraphicsDevice = " + id);
                    for (boolean transparentBackground :
                            new boolean[] {true, false}) {
                        System.out.println("\tTesting transparentBackground = "
                                + transparentBackground);
                        JFrame f = new JFrame(gd.getDefaultConfiguration());
                        f.setUndecorated(true);
                        if (transparentBackground) {
                            f.setBackground(new Color(0, 0, 0, 0));
                        }
                        f.pack();
                        f.setLocationRelativeTo(null);
                        f.setVisible(true);

                        testOpacity(f, transparentBackground ?
                                Transparency.TRANSLUCENT :
                                Transparency.OPAQUE);
                        testTransform(f);
                    }
                }
            } finally {
                latch.countDown();
            }
        });
    }

    public void run() throws Exception {
        latch.await();
        if (testFailureCtr > 0)
            throw new Error("Test failed");
        System.out.println("Test passed");
    }

    /**
     * Make sure Component.createImage() returns an image that can be
     * Transparency.OPAQUE or Transparency.TRANSLUCENT
     */
    private void testOpacity(Component c, int expectedTransparencyValue) {
        BufferedImage image = (BufferedImage) c.createImage(1, 1);
        assertEquals( image.getTransparency(), expectedTransparencyValue);
    }

    private void assertEquals(int expectedValue, int actualValue) {
        if (expectedValue != actualValue) {
            testFailureCtr++;
            System.err.println("failed assertion; expected = " +
                    expectedValue + ", actual = " + actualValue);
            Thread.dumpStack();
        }
    }

    private void assertEquals(double expectedValue, double actualValue) {
        if (expectedValue != actualValue) {
            testFailureCtr++;
            System.err.println("failed assertion; expected = " +
                    expectedValue + ", actual = " + actualValue);
            Thread.dumpStack();
        }
    }

    /**
     * Inspector the image we use to repaint a Component. Make sure its
     * AffineTransform resembles the GraphicsConfiguration's transform.
     * (That is: on a 200% resolution monitor we should be painting
     * at 200%.)
     */
    private void testTransform(Component c) {
        // we put the MultiResolutionImage logic in RepaintManager:
        Image i = RepaintManager.currentManager(c).
                getOffscreenBuffer(c, 10, 10);

        // we considered putting the MultiResolutionImage logic in
        // Component.createImage(), so this could resemble:
//        i = dialog.createImage(10, 10);

        Graphics2D g = (Graphics2D) i.getGraphics();
        AffineTransform tx1 = g.getTransform();
        AffineTransform tx2 = c.getGraphicsConfiguration().
                getDefaultTransform();
        assertEquals(tx2.getScaleX(), tx1.getScaleX());
        assertEquals(tx2.getScaleY(), tx1.getScaleY());
        assertEquals(tx2.getShearX(), tx1.getShearX());
        assertEquals(tx2.getShearY(), tx1.getShearY());
    }
}