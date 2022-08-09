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
 * bug 8280842
 * @summary Access violation in ciTypeFlow::profiled_count
 * @run main/othervm -XX:-BackgroundCompilation TestSharedHeadExceptionBackedges
 */

public class TestSharedHeadExceptionBackedges {
    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test();
        }
    }

    static class MyException extends Exception {
    }

    private static void test() {
        int i = 0;
        while (i < 10) {
            try {
                int j = 0;
                i++;
                if (i % 2 == 0) {
                    throw new MyException();
                }
                do {
                    j++;
                } while (j < 100);

                throw new MyException();

            } catch (MyException me) {
            }
        }
    }
}
