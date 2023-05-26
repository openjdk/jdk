/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 *
 */

package gc.shenandoah.generational;

import jdk.test.whitebox.WhiteBox;
import java.util.Random;

/*
 * @test TestSimpleGenerational
 * @requires vm.gc.Shenandoah
 * @summary Confirm that card marking and remembered set scanning do not crash.
 * @library /testlibrary /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *      -XX:+IgnoreUnrecognizedVMOptions
 *      -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      -XX:+UnlockExperimentalVMOptions
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
 *      gc.shenandoah.generational.TestSimpleGenerational
 */
public class TestSimpleGenerational {
  private static WhiteBox wb = WhiteBox.getWhiteBox();
  static private final int SeedForRandom = 46;
  // Sequence of random numbers should end with same value
  private static int ExpectedLastRandom = 272454100;


  public static class Node {
    static private final int NeighborCount = 5;
    static private final int IntArraySize = 8;
    static private Random random = new Random(SeedForRandom);

    private int val;
    private Object field_o;

    // Each Node instance holds references to two "private" arrays.
    // One array holds raw seething bits (primitive integers) and the
    // holds references.

    private int[] field_ints;
    private Node [] neighbors;

    public Node(int val) {
      this.val = val;
      this.field_o = new Object();
      this.field_ints = new int[IntArraySize];
      this.field_ints[0] = 0xca;
      this.field_ints[1] = 0xfe;
      this.field_ints[2] = 0xba;
      this.field_ints[3] = 0xbe;
      this.field_ints[4] = 0xba;
      this.field_ints[5] = 0xad;
      this.field_ints[6] = 0xba;
      this.field_ints[7] = 0xbe;

      this.neighbors = new Node[NeighborCount];
    }

    public int value() {
      return val;
    }

    // Copy each neighbor of n into a new node's neighbor array.
    // Then overwrite arbitrarily selected neighbor with newly allocated
    // leaf node.
    public static Node upheaval(Node n) {
      int first_val = random.nextInt();
      if (first_val < 0) first_val = -first_val;
      if (first_val < 0) first_val = 0;
      Node result = new Node(first_val);
      if (n != null) {
        for (int i = 0; i < NeighborCount; i++)
          result.neighbors[i] = n.neighbors[i];
      }
      int second_val = random.nextInt();
      if (second_val < 0) second_val = -second_val;
      if (second_val < 0) second_val = 0;

      int overwrite_index = first_val % NeighborCount;
      result.neighbors[overwrite_index] = new Node(second_val);
      return result;
    }
  }

  public static void main(String args[]) throws Exception {
    Node n = null;

    if (!wb.getBooleanVMFlag("UseShenandoahGC") ||
        !wb.getStringVMFlag("ShenandoahGCMode").equals("generational"))
      throw new IllegalStateException("Command-line options not honored!");

    for (int count = 10000; count > 0; count--) {
      n = Node.upheaval(n);
    }

    if (n.value() != ExpectedLastRandom)
      throw new IllegalStateException("Random number sequence ended badly!");

  }

}

