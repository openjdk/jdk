/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @summary  Check that ISO control ranges are valid.
 * @run main TestISOControls
 * @author John O'Conner
 */

public class TestISOControls {


  public static void main(String[] args) {

    int[] test = { -1, 0, 0x0010, 0x001F, 0x0020, 0x007E, 0x007F, 0x0090,
                   0x009F, 0x00A0 };
    boolean[] expectedResult = { false, true, true, true, false, false, true,
                                 true, true, false };

    for (int x=0; x < test.length; ++x) {
      if (Character.isISOControl(test[x]) != expectedResult[x]) {
          System.out.println("Fail: " + test[x]);
          throw new RuntimeException();
      }

    }
    System.out.println("Passed");

  }

}
