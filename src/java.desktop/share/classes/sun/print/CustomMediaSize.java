/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, BELLSOFT. All rights reserved.
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

package sun.print;

import javax.print.attribute.standard.MediaSize;
import javax.print.attribute.standard.MediaSizeName;
import java.io.Serial;

/**
 * Class allows to use medium with a landscape orientation.
 * The orientation is specified relative to the media feed direction
 * as described in the "PostScript Printer Description
 * File Format Specification".
 * Width - the width of the page perpendicular to the direction
 * of media feed.
 * Height - the height of the page parallel to the direction of
 * media feed.
 * It stores orientation of the medium and use this information
 * to provide the physical width and height of the medium.
 */
class CustomMediaSize extends MediaSize {

    /**
     * Serial
     */
    @Serial
    private static final long serialVersionUID = -1967958664615414771L;

    public static final int PORTRAIT  = 0;
    public static final int LANDSCAPE  = 1;
    private final int orientation;

    /**
     * Ctor
     * @param x width
     * @param y height
     * @param units units
     * @param orientation orientation
     */
    private CustomMediaSize(float x, float y, int units, int orientation) {
        super(x, y, units);
        this.orientation = orientation;
    }

    private CustomMediaSize(int x, int y, int units, int orientation) {
        super(x, y, units);
        this.orientation = orientation;
    }

    private CustomMediaSize(float x, float y, int units, MediaSizeName media, int orientation) {
        super(x, y, units, media);
        this.orientation = orientation;
    }

    private CustomMediaSize(int x, int y, int units, MediaSizeName media, int orientation) {
        super(x, y, units, media);
        this.orientation = orientation;
    }

    /**
     * Returns this media's width in the given units as a floating-point value.
     * It takes into account the physical orientation.
     * @param units unit conversion factor, e.g. {@link #INCH INCH} or
     *         {@link #MM MM}
     * @return the physical width of the medium
     */
    @Override
    public float getX(int units) {
        return orientation == PORTRAIT ?
                super.getX(units) : super.getY(units);
    }

    /**
     * Returns this media's height in the given units as a floating-point value.
     * It takes into account the physical orientation.
     * @param units unit conversion factor, e.g. {@link #INCH INCH} or
     *         {@link #MM MM}
     * @return the physical hight of the medium
     */
    @Override
    public float getY(int units) {
        return orientation == PORTRAIT ?
                super.getY(units) : super.getX(units);
    }

    @Override
    protected int getXMicrometers() {
        return orientation == PORTRAIT ?
                super.getXMicrometers() : super.getYMicrometers();
    }

    @Override
    protected int getYMicrometers() {
        return orientation == PORTRAIT ?
                super.getYMicrometers() : super.getXMicrometers();
    }

    /**
     * Creates new instance of CustomMediaSize from the given floating-point
     * values.
     *
     * @param x physical width of the media
     * @param y physical height of the media
     * @param units unit conversion factor, e.g. {@link #INCH INCH} or
     *         {@link #MM MM}
     * @return CustomMediaSize with provided dimension
     */
    public static CustomMediaSize create(float x, float y, int units) {
        CustomMediaSize cms;
        if (x > y) {
            cms = new CustomMediaSize(y, x, units, LANDSCAPE);
        } else {
            cms = new CustomMediaSize(x, y, units, PORTRAIT);
        }
        return cms;
    }

    /**
     * Creates new instance of CustomMediaSize from the given integer
     * values.
     *
     * @param x physical width of the media
     * @param y physical height of the media
     * @param units unit conversion factor, e.g. {@link #INCH INCH} or
     *         {@link #MM MM}
     * @return CustomMediaSize with provided dimension
     */
    public static CustomMediaSize create(int x, int y, int units) {
        CustomMediaSize cms;
        if (x > y) {
            cms = new CustomMediaSize(y, x, units, LANDSCAPE);
        } else {
            cms = new CustomMediaSize(x, y, units, PORTRAIT);
        }
        return cms;
    }

    /**
     * Creates new instance of CustomMediaSize from the given floating-point
     * values.
     *
     * @param x physical width of the media
     * @param y physical height of the media
     * @param units unit conversion factor, e.g. {@link #INCH INCH} or
     *         {@link #MM MM}
     * @param media a media name to associate with this {@code MediaSize}
     * @return CustomMediaSize
     */
    public static CustomMediaSize create(float x, float y, int units, MediaSizeName media) {
        CustomMediaSize cms;
        if (x > y) {
            cms = new CustomMediaSize(y, x, units, media, LANDSCAPE);
        } else {
            cms = new CustomMediaSize(x, y, units, media, PORTRAIT);
        }
        return cms;
    }

    /**
     * Creates new instance of CustomMediaSize from the given integer
     * values.
     *
     * @param x physical width of the media
     * @param y physical height of the media
     * @param units unit conversion factor, e.g. {@link #INCH INCH} or
     *         {@link #MM MM}
     * @param media a media name to associate with this {@code MediaSize}
     * @return CustomMediaSize
     */
    public static CustomMediaSize create(int x, int y, int units, MediaSizeName media) {
        CustomMediaSize cms;
        if (x > y) {
            cms = new CustomMediaSize(y, x, units, media, LANDSCAPE);
        } else {
            cms = new CustomMediaSize(x, y, units, media, PORTRAIT);
        }
        return cms;
    }
}
