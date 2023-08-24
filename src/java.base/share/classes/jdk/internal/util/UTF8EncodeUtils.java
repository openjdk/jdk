/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * Utility methods for encoding characters into UTF-8 byte sequences,
 *
 * <p>For example to writing a fast UTF-8 encoding loop:
 * {@snippet lang = java:
 *    for (int i = 0; i < s.length(); i++) {
 *        char c = s.charAt(i);
 *        if (UTF8EncodeUtils.isSingleByte(c)) {
 *            // handle single byte
 *        } else if (UTF8EncodeUtils.isDoubleBytes(c)) {
 *            byte[] bytes = UTF8EncodeUtils.encodeDoubleBytes(c);
 *            // handle double bytes
 *        } else if (Character.isSurrogate(c)) {
 *            if (i < s.length() - 1) {
 *                char d = s.charAt(i + 1);
 *                if (Character.isLowSurrogate(d)) {
 *                    int uc = Character.toCodePoint(c, d);
 *                    byte[] bytes = UTF8EncodeUtils.encodeCodePoint(uc);
 *
 *                    // handle four bytes
 *
 *                    i++;
 *                    continue;
 *                }
 *            }
 *            // handle unmappable char
 *        } else {
 *            byte[] bytes = UTF8EncodeUtils.encodeThreeBytes(c);
 *            // handle three bytes
 *        }
 *    }
 * }
 * @since 22
 */
public final class UTF8EncodeUtils {

    private UTF8EncodeUtils() {
    }

    @ForceInline
    public static boolean isSingleByte(char c) {
        return c < 0x80;
    }

    @ForceInline
    public static boolean isDoubleBytes(char c) {
        return c < 0x800;
    }

    @ForceInline
    public static byte[] encodeDoubleBytes(char c) {
        byte b0 = (byte) (0xc0 | (c >> 6));
        byte b1 = (byte) (0x80 | (c & 0x3f));
        return new byte[]{b0, b1};
    }

    @ForceInline
    public static byte[] encodeThreeBytes(char c) {
        byte b0 = (byte) (0xe0 | (c >> 12));
        byte b1 = (byte) (0x80 | ((c >> 6) & 0x3f));
        byte b2 = (byte) (0x80 | (c & 0x3f));
        return new byte[]{b0, b1, b2};
    }

    @ForceInline
    public static byte[] encodeCodePoint(int uc) {
        byte b0 = (byte) (0xf0 | ((uc >> 18)));
        byte b1 = (byte) (0x80 | ((uc >> 12) & 0x3f));
        byte b2 = (byte) (0x80 | ((uc >> 6) & 0x3f));
        byte b3 = (byte) (0x80 | (uc & 0x3f));
        return new byte[]{b0, b1, b2, b3};
    }
}
