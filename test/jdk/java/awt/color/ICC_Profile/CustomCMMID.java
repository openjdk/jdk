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
import java.util.Arrays;

/**
 * @test
 * @bug 8321489
 * @summary tests that the cmm id is not ignored
 */
public final class CustomCMMID {

    private static final byte[] JAVA_ID = {
            (byte) 'j', (byte) 'a', (byte) 'v', (byte) 'a',
    };

    private static final int[] CS = {
            ColorSpace.CS_CIEXYZ, ColorSpace.CS_GRAY, ColorSpace.CS_LINEAR_RGB,
            ColorSpace.CS_PYCC, ColorSpace.CS_sRGB
    };

    public static void main(String[] args) {
        for (int cs : CS) {
            ICC_Profile p = createProfile(cs);
            validate(p);
        }
    }

    private static ICC_Profile createProfile(int type) {
        byte[] data = ICC_Profile.getInstance(type).getData();
        System.arraycopy(JAVA_ID, 0, data, ICC_Profile.icHdrCmmId,
                         JAVA_ID.length);
        return ICC_Profile.getInstance(data);
    }

    private static void validate(ICC_Profile p) {
        byte[] header = p.getData(ICC_Profile.icSigHead);
        byte[] id = new byte[JAVA_ID.length];
        System.arraycopy(header, ICC_Profile.icHdrCmmId, id, 0, JAVA_ID.length);

        if (!java.util.Arrays.equals(id, JAVA_ID)) {
            System.err.println("Expected: " + Arrays.toString(JAVA_ID));
            System.err.println("Actual: " + Arrays.toString(id));
            throw new RuntimeException("Wrong cmm id");
        }
    }
}
