/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
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
 * @bug 8286451
 * @summary C2: assert(nb == 1) failed: only when the head is not shared
 * @run main/othervm -XX:-BackgroundCompilation TestSharedLoopHead
 */

public class TestSharedLoopHead {
    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test();
        }
    }

    private static void test() {
        int j = 0;
        int i = 0;
        do {
            do {
                i++;
            } while (i < 2);
            do {
                i++;
            } while (i < 2);
            j++;
            i = 0;
        } while (j < 2);
    }
}
