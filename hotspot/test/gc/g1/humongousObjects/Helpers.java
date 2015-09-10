/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package gc.g1.humongousObjects;

import sun.hotspot.WhiteBox;

public class Helpers {

    // In case of 128 byte padding
    private static final int MAX_PADDING_SIZE = 128;

    /**
     * Detects amount of extra bytes required to allocate a byte array.
     * Allocating a byte[n] array takes more then just n bytes in the heap.
     * Extra bytes are required to store object reference and the length.
     * This amount depends on bitness and other factors.
     *
     * @return byte[] memory overhead
     */
    public static int detectByteArrayAllocationOverhead() {

        WhiteBox whiteBox = WhiteBox.getWhiteBox();

        int zeroLengthByteArraySize = (int) whiteBox.getObjectSize(new byte[0]);

        // Since we do not know is there any padding in zeroLengthByteArraySize we cannot just take byte[0] size as overhead
        for (int i = 1; i < MAX_PADDING_SIZE + 1; ++i) {
            int realAllocationSize = (int) whiteBox.getObjectSize(new byte[i]);
            if (realAllocationSize != zeroLengthByteArraySize) {
                // It means we did not have any padding on previous step
                return zeroLengthByteArraySize - (i - 1);
            }
        }
        throw new Error("We cannot find byte[] memory overhead - should not reach here");
    }
}
