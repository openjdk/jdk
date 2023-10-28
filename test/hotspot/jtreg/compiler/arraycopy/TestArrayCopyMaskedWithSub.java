/*
 * Copyright (c) 2023, Arm Limited. All rights reserved.
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
 * @bug 8305524
 * @run main/othervm -Xbatch compiler.arraycopy.TestArrayCopyMaskedWithSub
 */

package compiler.arraycopy;

public class TestArrayCopyMaskedWithSub {
    private static char[] src = {'A', 'A', 'A', 'A', 'A'};
    private static char[] dst = {'B', 'B', 'B', 'B', 'B'};

    private static void copy(int nlen) {
      System.arraycopy(src, 0, dst, 0, -nlen);
    }

    public static void main(String[] args) {
      for (int i = 0; i < 25000; i++) {
        copy(0);
      }
      copy(-5);
      for (char c : dst) {
        if (c != 'A') {
          throw new RuntimeException("Wrong value!");
        }
      }
      System.out.println("PASS");
    }
}
