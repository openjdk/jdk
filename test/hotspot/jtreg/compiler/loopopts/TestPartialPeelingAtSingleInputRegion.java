/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8321278
 * @summary C2: Partial peeling fails with assert "last_peel <- first_not_peeled"
 * @run main/othervm -XX:CompileCommand=quiet -XX:CompileCommand=compileonly,TestPartialPeelingAtSingleInputRegion::test
 *                   -XX:-TieredCompilation -Xbatch -XX:PerMethodTrapLimit=0 TestPartialPeelingAtSingleInputRegion
 *
 */

public class TestPartialPeelingAtSingleInputRegion {

    static void test() {
        for (int i = 100; i > 10; --i) {
            for (int j = i; j < 10; ++j) {
                switch (j) {
                case 1:
                    if (j != 0) {
                        return;
                    }
                }
             }
        }
    }

    public static void main(String[] args) {
        for (int i = 0; i < 10_000; ++i) {
            test();
        }
    }
}
