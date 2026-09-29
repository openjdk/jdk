/*
 * Copyright (c) 2026 IBM Corporation. All rights reserved.
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
 * @bug 8374725
 * @summary C2: assert(x_ctrl == get_late_ctrl_with_anti_dep(x->as_Load(), early_ctrl, x_ctrl)) failed: anti-dependences were already checked
 * @run main/othervm  -XX:CompileCommand=compileonly,*TestSinkingLoadInputOfPhi*::* -XX:-TieredCompilation -Xcomp ${test.main.class}
 * @run main ${test.main.class}
 */

package compiler.loopopts;

public class TestSinkingLoadInputOfPhi {

  static long lFld;
  static int iFld = 55;
  static int iFld2 = 10;
  static void test() {
    int iArr[] = new int[iFld2];

    for (int i13 : iArr)
      switch (iFld) {
      case 69:
      case 46:
      case 65:
        lFld = i13;
      case 55: // taken
        for (int i16 = 1; i16 < 30000; i16++)
          ;
      case 71:
        iArr[iFld2] = 2; // OOB-access
      }
  }

  public static void main(String[] strArr) {
    try {
      test();
      } catch (ArrayIndexOutOfBoundsException e) {
        // expected
      }
  }
}
