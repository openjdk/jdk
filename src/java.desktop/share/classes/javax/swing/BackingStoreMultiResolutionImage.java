/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package javax.swing;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.AffineTransform;
import java.awt.image.AbstractMultiResolutionImage;
import java.awt.image.ImageObserver;
import java.util.Arrays;
import java.util.Collections;

class BackingStoreMultiResolutionImage
        extends AbstractMultiResolutionImage {

    private final int width;
    private final int height;
    private final int scaledWidth;
    private final int scaledHeight;
    private final Image rvImage;

    BackingStoreMultiResolutionImage(int width, int height,
                                     int scaledWidth, int scaledHeight,
                                     Image rvImage) {
        this.width = width;
        this.height = height;
        this.scaledWidth = scaledWidth;
        this.scaledHeight = scaledHeight;
        this.rvImage = rvImage;
    }

    int getScaledWidth() {
        return scaledWidth;
    }

    int getScaledHeight() {
        return scaledHeight;
    }

    @Override
    public int getWidth(ImageObserver observer) {
        return width;
    }

    @Override
    public int getHeight(ImageObserver observer) {
        return height;
    }

    @Override
    protected Image getBaseImage() {
        return rvImage;
    }

    @Override
    public Graphics getGraphics() {
        Graphics graphics = rvImage.getGraphics();
        if (graphics instanceof Graphics2D) {
            double sx = (double) scaledWidth / width;
            double sy = (double) scaledHeight / height;
            ((Graphics2D) graphics).scale(sx, sy);
        }
        return graphics;
    }

    @Override
    public Image getResolutionVariant(double w, double h) {
        return rvImage;
    }

    @Override
    public java.util.List<Image> getResolutionVariants() {
        return Collections.unmodifiableList(Arrays.asList(rvImage));
    }
}
