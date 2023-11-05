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

/**
 * @test
 * @bug 8282577
 * @summary Verify setting data for a tag doesn't invalidate the profile.
 */

import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;

public final class SetTagDataValidation {

    public static void main(String[] args) throws Exception {

        ICC_Profile srgb = ICC_Profile.getInstance(ColorSpace.CS_sRGB);
        // Create a new profile, using the srgb data but private to us.
        ICC_Profile icc = ICC_Profile.getInstance(srgb.getData());

        // Get data for some tag, which one isn't important so long as it exists
        int tag = ICC_Profile.icSigBlueColorantTag;
        byte[] tagData = icc.getData(tag);
        if (tagData == null) {
            throw new RuntimeException("No data for tag");
        }
        // Set the data to be the SAME data which ought to be a harmless no-op
        icc.setData(tag, tagData);

        // Perform a color conversion - from rgb to rgb but it doesn't matter
        // we just need to verify the op is applied and results are sane.

        ColorSpace cs = new ICC_ColorSpace(icc);
        float[] in = new float[3];
        in[0] = 0.4f;
        in[1] = 0.5f;
        in[2] = 0.6f;

        // the toRGB op previously threw an exception - or crashed
        float[] out = cs.toRGB(in);
        // If we get this far let's validate the results.
        if (out == null || out.length !=3) {
            throw new RuntimeException("out array invalid");
        }
        for (int i=0;i<out.length;i++) {
           System.out.println(out[i]);
        }
        for (int i=0;i<out.length;i++) {
           if ((Math.abs(in[i]-out[i]) > 0.01)) {
               throw new RuntimeException("Inaccurate no-op conversion");
           }
        }
    }
}
