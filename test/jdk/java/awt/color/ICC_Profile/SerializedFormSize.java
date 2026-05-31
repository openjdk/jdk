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

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;

/**
 * @test
 * @bug 8369032
 * @summary Checks the size of the serialized ICC_Profile for standard and
 *          non-standard profiles.
 */
public final class SerializedFormSize {

    private static final ICC_Profile[] PROFILES = {
            ICC_Profile.getInstance(ColorSpace.CS_sRGB),
            ICC_Profile.getInstance(ColorSpace.CS_LINEAR_RGB),
            ICC_Profile.getInstance(ColorSpace.CS_CIEXYZ),
            ICC_Profile.getInstance(ColorSpace.CS_PYCC),
            ICC_Profile.getInstance(ColorSpace.CS_GRAY)
    };

    public static void main(String[] args) throws Exception {
        for (ICC_Profile profile : PROFILES) {
            byte[] data = profile.getData();
            int dataSize = data.length;
            int min = 3; // At least version, name and data fields
            int max = 200; // Small enough to confirm no data saved

            // Standard profile: should serialize to a small size, no data
            test(profile, min, max);
            // Non-standard profile: includes full data, but only once
            test(ICC_Profile.getInstance(data), dataSize, dataSize + max);
        }
    }

    private static void test(ICC_Profile p, int min, int max) throws Exception {
        try (var bos = new ByteArrayOutputStream();
             var oos = new ObjectOutputStream(bos))
        {
            oos.writeObject(p);
            int size = bos.size();
            if (size < min || size > max) {
                System.err.println("Expected: >= " + min + " and <= " + max);
                System.err.println("Actual: " + size);
                throw new RuntimeException("Wrong size");
            }
        }
    }
}
