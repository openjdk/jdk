/*
 * Copyright (c) 2025, Red Hat, Inc. All rights reserved.
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
 * @bug 8341976
 * @summary C2: use_mem_state != load->find_exact_control(load->in(0)) assert failure
 * @run main/othervm -XX:-BackgroundCompilation TestSunkLoadAntiDependency
 * @run main TestSunkLoadAntiDependency
 */

public class TestSunkLoadAntiDependency {
    private static volatile  int volatileField;

    public static void main(String[] args) {
        int[] array = new int[100];
        for (int i = 0; i < 20_000; i++) {
            test1(array, 2);
            inlined1(array, 100, 0, 100, array);
        }
    }

    private static int test1(int[] src, int length) {
        length = Integer.max(1, length);
        int[] dst = new int[2];
        int stop;
        for (stop = 0; stop < 10; stop++) {
            for (int i = 0; i < 10; i++) {
            }
        }
        int start;
        for (start = 0; start < 9; start++) {
            for (int i = 0; i < 10; i++) {
            }
        }
        inlined1(src, length, start, stop, dst);
        return dst[0] + dst[1];
    }

    private static void inlined1(int[] src, int length, int start, int stop, int[] dst) {
        for (int i = start; i < stop; i++) {
            volatileField = 42;
            System.arraycopy(src, 0, dst, 0, length);
        }
    }
}
