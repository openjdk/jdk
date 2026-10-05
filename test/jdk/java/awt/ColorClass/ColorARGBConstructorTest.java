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

/**
 * @test
 * @bug 6434110
 * @summary Verify Color(int, boolean) constructor uses ARGB bit layout
 */
public final class ColorARGBConstructorTest {

    public static void main(String[] args) {
        for (int argb : new int[]{0x00000000, 0x01020304, 0xC0903020,
                                  0x40302010, 0xD08040C0, 0x80000000,
                                  0x7FFFFFFF, 0xFFFFFFFF, 0xFF000000,
                                  0x00FF0000, 0x0000FF00, 0x000000FF})
        {
            verify(argb, true);
            verify(argb, false);
        }
    }

    private static void verify(int argb, boolean hasAlpha) {
        var c = new Color(argb, hasAlpha);
        int expRGB = hasAlpha ? argb : (0xFF000000 | argb);
        int expA = hasAlpha ? (argb >>> 24) : 0xFF;
        int expR = (argb >> 16) & 0xFF;
        int expG = (argb >> 8) & 0xFF;
        int expB = argb & 0xFF;
        check("RGB", expRGB, c.getRGB(), argb, hasAlpha);
        check("Alpha", expA, c.getAlpha(), argb, hasAlpha);
        check("Red", expR, c.getRed(), argb, hasAlpha);
        check("Green", expG, c.getGreen(), argb, hasAlpha);
        check("Blue", expB, c.getBlue(), argb, hasAlpha);
    }

    private static void check(String comp, int exp, int got, int argb,
                              boolean hasAlpha)
    {
        if (exp != got) {
            throw new RuntimeException("0x%08X(%b) %s: 0x%08X != 0x%08X"
                    .formatted(argb, hasAlpha, comp, exp, got));
        }
    }
}
