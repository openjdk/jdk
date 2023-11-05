/*
 * Copyright (c) 2022, Arm Limited. All rights reserved.
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
 * @bug 8295407
 * @summary Superword should remove unsupported cmove packs from candidate packset
 * @library /test/lib /
 * @run main TestUnsupportedConditionalMove
 */

import jdk.test.lib.Asserts;

public class TestUnsupportedConditionalMove {
    public static int LENGTH = 3000;
    public static float[] fa = new float[LENGTH];
    public static float[] fb = new float[LENGTH];
    public static boolean[] mask = new boolean[LENGTH];

    public static void test() {
        for (int i = 0; i < fa.length; i++) {
            fb[i] = mask[i]? fa[i]: -fa[i];
        }
    }

    public static void main(String[] k) {
        for (int i= 0; i < LENGTH; i++) {
            mask[i] = (i % 3 == 0);
            fa[i] = i + 1.0f;
        }

        for (int i = 0; i < 10_000; i++) {
            test();
        }

        for (int i = 0; i < LENGTH; i++) {
            if (i % 3 == 0) {
                Asserts.assertEquals(fb[i], i + 1.0f);
            } else {
                Asserts.assertEquals(fb[i], - (i + 1.0f));
            }
        }
    }

}
