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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * @test
 * @bug 8367384
 * @summary Checks ICC_Profile serialization for standard profiles
 */
public final class StandardProfilesRoundTrip {

    private static final ICC_Profile[] PROFILES = {
            ICC_Profile.getInstance(ColorSpace.CS_sRGB),
            ICC_Profile.getInstance(ColorSpace.CS_LINEAR_RGB),
            ICC_Profile.getInstance(ColorSpace.CS_CIEXYZ),
            ICC_Profile.getInstance(ColorSpace.CS_PYCC),
            ICC_Profile.getInstance(ColorSpace.CS_GRAY)
    };

    public static void main(String[] args) throws Exception {
        // Test profiles one by one
        for (ICC_Profile profile : PROFILES) {
            test(profile);
        }
        // Test all profiles at once
        test(PROFILES);
    }

    private static void test(ICC_Profile... profiles) throws Exception {
        byte[] data;
        try (var bos = new ByteArrayOutputStream();
             var oos = new ObjectOutputStream(bos))
        {
            for (ICC_Profile p : profiles) {
                oos.writeObject(p);
            }
            data = bos.toByteArray();
        }
        try (var ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
            for (ICC_Profile p : profiles) {
                if (p != ois.readObject()) {
                    throw new RuntimeException("Wrong deserialized object");
                }
            }
        }
    }
}
