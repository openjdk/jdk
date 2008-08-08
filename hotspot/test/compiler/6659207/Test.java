/*
 * Copyright 1997-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

/*
 * @test
 * @bug 6659207
 * @summary access violation in CompilerThread0
 */

public class Test {
    static int[] array = new int[12];

    static int index(int i) {
        if (i == 0) return 0;
        for (int n = 0; n < array.length; n++)
            if (i < array[n]) return n;
        return -1;
    }

    static int test(int i) {
        int result = 0;
        i = index(i);
        if (i >= 0)
            if (array[i] != 0)
                result++;

        if (i != -1)
            array[i]++;

        return result;
    }

    public static void main(String[] args) {
        int total = 0;
        for (int i = 0; i < 100000; i++) {
            total += test(10);
        }
        System.out.println(total);
    }
}
