/*
 * Copyright (c) 2024, Alibaba Group Holding Limited. All Rights Reserved.
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

package jdk.internal.util;

import jdk.internal.vm.annotation.ForceInline;

/**
 * Helper to JDK UTF putChar and Calculate length
 *
 * @since 24
 */
public abstract class ModifiedUtf {
    // Maximum number of bytes allowed for a Modified UTF-8 encoded string
    // in a ClassFile constant pool entry (CONSTANT_Utf8_info).
    public static final int CONSTANT_POOL_UTF8_MAX_BYTES = 65535;

    private ModifiedUtf() {
    }

    @ForceInline
    public static int putChar(byte[] buf, int offset, char c) {
        if (c != 0 && c < 0x80) {
            buf[offset++] = (byte) c;
        } else if (c >= 0x800) {
            buf[offset    ] = (byte) (0xE0 | c >> 12 & 0x0F);
            buf[offset + 1] = (byte) (0x80 | c >> 6  & 0x3F);
            buf[offset + 2] = (byte) (0x80 | c       & 0x3F);
            offset += 3;
        } else {
            buf[offset    ] = (byte) (0xC0 | c >> 6 & 0x1F);
            buf[offset + 1] = (byte) (0x80 | c      & 0x3F);
            offset += 2;
        }
        return offset;
    }

    /**
     * Calculate the utf length of a string
     * @param str input string
     * @param countNonZeroAscii the number of non-zero ascii characters in the prefix calculated by JLA.countNonZeroAscii(str)
     */
    @ForceInline
    public static int utfLen(String str, int countNonZeroAscii) {
        int utflen = str.length();
        for (int i = utflen - 1; i >= countNonZeroAscii; i--) {
            int c = str.charAt(i);
            if (c >= 0x80 || c == 0)
                utflen += (c >= 0x800) ? 2 : 1;
        }
        return utflen;
    }

    /**
     * Checks whether the Modified UTF-8 encoded length of the given string
     * fits within the ClassFile constant pool limit (u2 length = 65535 bytes).
     * @param str the string to check
     */
    @ForceInline
    public static boolean isValidLengthInConstantPool(String str) {
        // Quick approximation: each char can be at most 3 bytes in Modified UTF-8.
        // If the string is short enough, it definitely fits.
        int strLen = str.length();
        if (strLen <= CONSTANT_POOL_UTF8_MAX_BYTES / 3) {
            return true;
        }
        if (strLen > CONSTANT_POOL_UTF8_MAX_BYTES) {
            return false;
        }
        // Check exact Modified UTF-8 length.
        // The check strLen > CONSTANT_POOL_UTF8_MAX_BYTES above ensures that utfLen can't overflow here.
        int utfLen = utfLen(str, 0);
        return utfLen <= CONSTANT_POOL_UTF8_MAX_BYTES;
    }
}
