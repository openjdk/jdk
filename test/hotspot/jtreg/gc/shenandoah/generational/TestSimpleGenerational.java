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
 * @test id=generational
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
    private static WhiteBox WB = WhiteBox.getWhiteBox();
    private static final int RANDOM_SEED = 46;
    // Sequence of random numbers should end with same value
    private static final int EXPECTED_LAST_RANDOM = 136227050;


    public static class Node {
        private static final int NEIGHBOR_COUNT = 5;
        private static final int INT_ARRAY_SIZE = 8;
        private static final Random RANDOM = new Random(RANDOM_SEED);

        private int val;
        private Object objectField;

        // Each Node instance holds references to two "private" arrays.
        // One array holds raw seething bits (primitive integers) and the other
        // holds references.

        private int[] intsField;
        private Node [] neighbors;

        public Node(int val) {
            this.val = val;
            this.objectField = new Object();
            this.intsField = new int[INT_ARRAY_SIZE];
            this.intsField[0] = 0xca;
            this.intsField[1] = 0xfe;
            this.intsField[2] = 0xba;
            this.intsField[3] = 0xbe;
            this.intsField[4] = 0xba;
            this.intsField[5] = 0xad;
            this.intsField[6] = 0xba;
            this.intsField[7] = 0xbe;

            this.neighbors = new Node[NEIGHBOR_COUNT];
        }

        public int value() {
            return val;
        }

        // Copy each neighbor of n into a new node's neighbor array.
        // Then overwrite arbitrarily selected neighbor with newly allocated
        // leaf node.
        public static Node upheaval(Node n) {
            int firstValue = RANDOM.nextInt(Integer.MAX_VALUE);
            Node result = new Node(firstValue);
            if (n != null) {
                for (int i = 0; i < NEIGHBOR_COUNT; i++) {
                    result.neighbors[i] = n.neighbors[i];
                }
            }
            int secondValue = RANDOM.nextInt(Integer.MAX_VALUE);
            int overwriteIndex = firstValue % NEIGHBOR_COUNT;
            result.neighbors[overwriteIndex] = new Node(secondValue);
            return result;
        }
    }

    public static void main(String args[]) throws Exception {
        Node n = null;

        if (!WB.getBooleanVMFlag("UseShenandoahGC") || !WB.getStringVMFlag("ShenandoahGCMode").equals("generational")) {
            throw new IllegalStateException("Command-line options not honored!");
        }

        for (int count = 10000; count > 0; count--) {
            n = Node.upheaval(n);
        }

        System.out.println("Expected Last Random: [" + n.value() + "]");
        if (n.value() != EXPECTED_LAST_RANDOM) {
            throw new IllegalStateException("Random number sequence ended badly!");
        }
    }
}
