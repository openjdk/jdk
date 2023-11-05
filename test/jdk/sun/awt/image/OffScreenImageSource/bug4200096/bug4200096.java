/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ImageConsumer;
import java.io.PrintStream;
import java.lang.AutoCloseable;
import java.lang.AutoCloseable;
import java.util.Hashtable;

/**
 * @test
 * @bug 4200096
 * @summary if an ImageConsumer detaches from OffScreenImageSource mid-production: we shouldn't print a NPE to System.err
 * @author Jeremy Wood
 */

/**
 * This detaches an ImageConsumer at different points and checks to see if a
 * NullPointerException is thrown by the OffScreenImageSource as a result.
 */
public class bug4200096 {

    /**
     * This hijacks System.err so we can detect if a NullPointerException is printed to it.
     * <p>
     * When this AutoCloseable is closed: we throw an exception based on whether we observed
     * a NPE vs whether a NPE is expected or not.
     * </p>
     *
     * @param expectPrintedNPE this is consulted when {@link java.lang.AutoCloseable#close()} is called,
     *                         and if the expectation doesn't match what happened then a RuntimeException is thrown.
     * @return an AutoCloseable that restores System.err and tests whether a NPE was observed or not.
     */
    public static AutoCloseable setupTest(boolean expectPrintedNPE) {
        NullPointerException[] wrapper = new NullPointerException[] { null };
        PrintStream origErr = System.err;
        System.setErr(new PrintStream(origErr) {
            @Override
            public void println(Object x) {
                super.println(x);
                if (x instanceof NullPointerException e)
                    wrapper[0] = e;
            }
        });
        return new AutoCloseable() {
            @Override
            public void close() {
                System.setErr(origErr);
                if (expectPrintedNPE) {
                    if (wrapper[0] == null)
                        throw new RuntimeException("This test expected a NullPointerException to be printed to System.err, but that didn't happen.");
                } else {
                    if (wrapper[0] != null)
                        throw new RuntimeException("This test expected no NullPointerExceptions to be printed to System.err, but (at least) one was.)");
                }
            }
        };
    }

    /**
     * This enumerates the different notifications that OffScreenImageSource issues.
     * <p>
     * There is no SET_HINTS because (as of this writing) OSIS doesn't call setHints(int).
     * </p>
     */
    public enum TestCase {
        SET_DIMENSIONS, SET_PROPERTIES, SET_COLOR_MODEL, SET_PIXELS, IMAGE_COMPLETE
    }

    public static void main(String[] args) throws Exception {
        try (AutoCloseable setup = setupTest(false)) {
            for (TestCase testCase : TestCase.values()) {
                BufferedImage bufferedImage = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
                if (!"sun.awt.image.OffScreenImageSource".equals(
                        bufferedImage.getSource().getClass().getName())) {
                    throw new IllegalStateException("If BufferedImage#getSource() is not an OffScreenImageSource: that isn't necessarily a problem, but it invalidates the usefulness of this test.");
                }

                ImageConsumer consumer = new ImageConsumer() {

                    private void run(TestCase methodInvocation) {
                        if (!bufferedImage.getSource().isConsumer(this))
                            throw new IllegalStateException();
                        if (testCase == methodInvocation)
                            bufferedImage.getSource().removeConsumer(this);
                    }

                    @Override
                    public void setDimensions(int width, int height) {
                        run(TestCase.SET_DIMENSIONS);
                    }

                    @Override
                    public void setProperties(Hashtable<?, ?> props) {
                        run(TestCase.SET_PROPERTIES);
                    }

                    @Override
                    public void setColorModel(ColorModel model) {
                        run(TestCase.SET_COLOR_MODEL);
                    }

                    @Override
                    public void setHints(int hintflags) {
                        // intentionally empty.
                    }

                    @Override
                    public void setPixels(int x, int y, int w, int h, ColorModel model, byte[] pixels, int off, int scansize) {
                        throw new UnsupportedOperationException("this test should use int[] pixels");
                    }

                    @Override
                    public void setPixels(int x, int y, int w, int h, ColorModel model, int[] pixels, int off, int scansize) {
                        if (y == 5) {
                            run(TestCase.SET_PIXELS);
                        }
                    }

                    @Override
                    public void imageComplete(int status) {
                        run(TestCase.IMAGE_COMPLETE);
                    }
                };

                bufferedImage.getSource().startProduction(consumer);

                if (bufferedImage.getSource().isConsumer(consumer)) {
                    // this confirms our calls to .removeConsumer above were being invoked as expected
                    throw new IllegalStateException("This test is not executing as expected.");
                }
            }
        }
    }
}