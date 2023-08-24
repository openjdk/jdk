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
 *            int bytes = UTF8EncodeUtils.encodeDoubleBytes(c);
 *            byte b0 = (byte) (bytes >>> 8);
 *            byte b1 = (byte) (bytes & 0xff);
 *            // handle double bytes
 *        } else if (Character.isSurrogate(c)) {
 *            if (i < s.length() - 1) {
 *                char d = s.charAt(i + 1);
 *                if (Character.isLowSurrogate(d)) {
 *                    int uc = Character.toCodePoint(c, d);
 *                    int bytes = UTF8EncodeUtils.encodeCodePoint(uc);
 *                    byte b0 = (byte) ((bytes >>> 24) & 0xff);
 *                    byte b1 = (byte) ((bytes >>> 16) & 0xff);
 *                    byte b2 = (byte) ((bytes >>> 8) & 0xff);
 *                    byte b3 = (byte) ((bytes) & 0xff);
 *
 *                    // handle four bytes
 *
 *                    i++;
 *                    continue;
 *                }
 *            }
 *            // handle unmappable char
 *        } else {
 *            int bytes = UTF8EncodeUtils.encodeThreeBytes(c);
 *            byte b0 = (byte) ((bytes >>> 16) & 0xff);
 *            byte b1 = (byte) ((bytes >>> 8) & 0xff);
 *            byte b2 = (byte) ((bytes) & 0xff);
 *            // handle three bytes
 *        }
 *    }
 * }
 * @since 22
 */
public class UTF8EncodeUtils {

    public static boolean isSingleByte(char c) {
        return c < 0x80;
    }

    public static boolean isDoubleBytes(char c) {
        return c < 0x800;
    }

    @ForceInline
    public static int encodeDoubleBytes(char c) {
        byte b0 = (byte) (0xc0 | (c >> 6));
        byte b1 = (byte) (0x80 | (c & 0x3f));
        return ((b0 & 0xff) << 8) | b1;
    }

    @ForceInline
    public static int encodeThreeBytes(char c) {
        byte b0 = (byte) (0xe0 | c >> 12);
        byte b1 = (byte) (0x80 | c >> 6 & 0x3f);
        byte b2 = (byte) (0x80 | c & 0x3f);
        return ((b0 & 0xff) << 16) | ((b1 & 0xff) << 8) | b2;
    }

    @ForceInline
    public static int encodeCodePoint(int uc) {
        byte b0 = (byte) (0xf0 | ((uc >> 18)));
        byte b1 = (byte) (0x80 | ((uc >> 12) & 0x3f));
        byte b2 = (byte) (0x80 | ((uc >> 6) & 0x3f));
        byte b3 = (byte) (0x80 | (uc & 0x3f));
        return ((b0 & 0xff) << 24) | ((b1 & 0xff) << 16) | ((b2 & 0xff) << 8) | b3;
    }
}
