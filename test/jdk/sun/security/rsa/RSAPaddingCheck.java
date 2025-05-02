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
/*
 * @test
 * @bug 8302017
 * @summary Ensure that RSAPadding class works as expected after refactoring
 * @modules java.base/sun.security.rsa
 */
import java.util.Arrays;
import sun.security.rsa.RSAPadding;

public class RSAPaddingCheck {

    private static int[] PADDING_TYPES =  {
        RSAPadding.PAD_BLOCKTYPE_1,
        RSAPadding.PAD_BLOCKTYPE_2,
        RSAPadding.PAD_NONE,
        RSAPadding.PAD_OAEP_MGF1,
    };

    public static void main(String[] args) throws Exception {
        int size = 2048 >> 3;
        byte[] testData = "This is some random to-be-padded Data".getBytes();
        for (int type : PADDING_TYPES) {
            byte[] data = (type == RSAPadding.PAD_NONE?
                    Arrays.copyOf(testData, size) : testData);
            System.out.println("Testing PaddingType: " + type);
            RSAPadding padding = RSAPadding.getInstance(type, size);
            byte[] paddedData = padding.pad(data);
            if (paddedData == null) {
                throw new RuntimeException("Unexpected padding op failure!");
            }

            byte[] data2 = padding.unpad(paddedData);
            if (data2 == null) {
                throw new RuntimeException("Unexpected unpadding op failure!");
            }
            if (!Arrays.equals(data, data2)) {
                throw new RuntimeException("diff check failure!");
            }
        }
    }
}
