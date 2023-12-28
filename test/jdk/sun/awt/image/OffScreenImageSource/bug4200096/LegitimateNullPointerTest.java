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

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ImageConsumer;
import java.util.Hashtable;

/**
 * @test
 * @bug 4200096
 * @summary This makes sure NPE's that come from the ImageConsumer are still printed to System.err
 * @author Jeremy Wood
 */

/**
 * This test makes sure that if an ImageConsumer throws a NullPointerException: that NPE
 * should be printed to System.err.
 * <p>
 * Most of the discussion around JDK-4200096 focuses on how we handle NPE's that come from
 * OffScreenImageSource itself, but this test checks how we handle NPE's that come from
 * the ImageConsumer that's listening to an OffScreenImageSource.
 * </p>
 * <p>
 * This test is enforcing a legacy behavior.
 * </p>
 */
 public class LegitimateNullPointerTest {
    public static void main(String[] args) throws Exception {
        try (AutoCloseable setup = bug4200096.setupTest(true)) {
            BufferedImage bufferedImage = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
            bufferedImage.getSource().addConsumer(new ImageConsumer() {

                @Override
                public void setDimensions(int width, int height) {
                    throw new NullPointerException();
                }

                @Override
                public void setProperties(Hashtable<?, ?> props) {
                    // intentionally empty
                }

                @Override
                public void setColorModel(ColorModel model) {
                    // intentionally empty
                }

                @Override
                public void setHints(int hintflags) {
                    // intentionally empty
                }

                @Override
                public void setPixels(int x, int y, int w, int h, ColorModel model, byte[] pixels, int off, int scansize) {
                    // intentionally empty
                }

                @Override
                public void setPixels(int x, int y, int w, int h, ColorModel model, int[] pixels, int off, int scansize) {
                    // intentionally empty
                }

                @Override
                public void imageComplete(int status) {
                    // intentionally empty
                }
            });
        }
    }
}