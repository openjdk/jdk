/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @bug 4677084
  @summary Tests that the PageIncrement (BlockIncrement) and
           LineIncrement (UnitIncrement) cannot be < 1
  @key headful
*/

import java.awt.Scrollbar;

public class PageIncrementTest {
    static Scrollbar sb;

    public static void main(String[] args) {
        sb = new Scrollbar();
        sb.setBlockIncrement(0);
        sb.setUnitIncrement(0);

        if (sb.getBlockIncrement() < 1) {
            String msg = "Failed: getBlockIncrement() == " + sb.getBlockIncrement();
            System.out.println(msg);
            throw new RuntimeException(msg);
        }
        if (sb.getUnitIncrement() < 1) {
            String msg = "Failed: getLineIncrement() == " + sb.getUnitIncrement();
            System.out.println(msg);
            throw new RuntimeException(msg);
        }

        sb.setBlockIncrement(-1);
        sb.setUnitIncrement(-1);

        if (sb.getBlockIncrement() < 1) {
            String msg = "Failed: getBlockIncrement() == " + sb.getBlockIncrement();
            System.out.println(msg);
            throw new RuntimeException(msg);
        }
        if (sb.getUnitIncrement() < 1) {
            String msg = "Failed: getLineIncrement() == " + sb.getUnitIncrement();
            System.out.println(msg);
            throw new RuntimeException(msg);
        }

        sb.setBlockIncrement(2);
        sb.setUnitIncrement(2);

        if (sb.getBlockIncrement() != 2) {
            String msg = "Failed: getBlockIncrement() == " + sb.getBlockIncrement();
            System.out.println(msg);
            throw new RuntimeException(msg);
        }
        if (sb.getUnitIncrement() != 2) {
            String msg = "Failed: getLineIncrement() == " + sb.getUnitIncrement();
            System.out.println(msg);
            throw new RuntimeException(msg);
        }
        System.out.println("Test Pass!!");
    }
}
