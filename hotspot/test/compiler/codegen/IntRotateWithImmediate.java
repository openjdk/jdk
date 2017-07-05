/*
 * Copyright (c) 2015 SAP SE. All rights reserved.
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
 * @bug 8080190
 * @key regression
 * @summary Test that the rotate distance used in the rotate instruction is properly masked with 0x1f
 * @run main/othervm -Xbatch -XX:-UseOnStackReplacement IntRotateWithImmediate
 * @author volker.simonis@gmail.com
 */

public class IntRotateWithImmediate {

  // This is currently the same as Integer.rotateRight()
  static int rotateRight(int i, int distance) {
    // On some architectures (i.e. x86_64 and ppc64) the following computation is
    // matched in the .ad file into a single MachNode which emmits a single rotate
    // machine instruction. It is important that the shift amount is masked to match
    // corresponding immediate width in the native instruction. On x86_64 the rotate
    // left instruction ('rol') encodes an 8-bit immediate while the corresponding
    // 'rotlwi' instruction on Power only encodes a 5-bit immediate.
    return ((i >>> distance) | (i << -distance));
  }

  static int compute(int x) {
    return rotateRight(x, 3);
  }

  public static void main(String args[]) {
    int val = 4096;

    int firstResult = compute(val);

    for (int i = 0; i < 100000; i++) {
      int newResult = compute(val);
      if (firstResult != newResult) {
        throw new InternalError(firstResult + " != " + newResult);
      }
    }
    System.out.println("OK");
  }

}
