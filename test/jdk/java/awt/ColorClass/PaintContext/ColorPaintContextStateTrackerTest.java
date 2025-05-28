/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;

import sun.awt.image.SurfaceManager;
import sun.java2d.StateTrackable;
import sun.java2d.SurfaceData;

/**
 * @test
 * @bug 8355078
 * @summary Checks that ColorPaintContext surface is STABLE and cacheable
 * @modules java.desktop/sun.awt.image
 *          java.desktop/sun.java2d
 */
public final class ColorPaintContextStateTrackerTest {

    public static void main(String[] args) {
        var context = Color.RED.createContext(null, null, null, null, null);
        var cm = context.getColorModel();
        var raster = (WritableRaster) context.getRaster(0, 0, 1, 1);
        var bi = new BufferedImage(cm, raster, cm.isAlphaPremultiplied(), null);

        SurfaceData sd = SurfaceManager.getManager(bi).getPrimarySurfaceData();
        StateTrackable.State state = sd.getState();
        if (state != StateTrackable.State.STABLE) {
            System.err.println("Actual: " + state);
            System.err.println("Expected: " + StateTrackable.State.STABLE);
            throw new RuntimeException("Wrong state");
        }
    }
}
