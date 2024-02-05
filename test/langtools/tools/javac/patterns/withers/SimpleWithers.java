/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @compile SimpleWithers.java
 * @run main SimpleWithers
 */
public class SimpleWithers {
    public static void main(String... args) {
        R r = new R(1, 2, 3);
        r = r with {
            val1 = -1;
            val3 = -3;
        };
        if (r.val1() != (-1) ||
            r.val2() != 2 ||
            r.val3() != (-3)) {
            throw new AssertionError("Incorrect value: " + r);
        }
        R r2 = r;
        boolean match = switch (r2) {
            case R(var i1, var i2, var i3) when r2 with {
                val1 = -1;
                val3 = -3;
            }.val1() == -1 -> true;
            default -> false;
        };
        if (!match) {
            throw new AssertionError("Did not match.");
        }
    }
    record R(int val1, int val2, int val3) {}
}