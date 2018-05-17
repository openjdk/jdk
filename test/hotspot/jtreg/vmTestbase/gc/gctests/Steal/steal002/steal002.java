/*
 * Copyright (c) 2003, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @key stress gc
 *
 * @summary converted from VM Testbase gc/gctests/Steal/steal002.
 * VM Testbase keywords: [gc, stress, stressopt, nonconcurrent]
 * VM Testbase readme:
 * DESCRIPTION
 *     The test checks that Garbage Collector correctly uses stealing technique:
 *     no unexpected exceptions and errors are thrown; the JVM is not crashed.
 *     Actually, the test is intended for Parallel Collector.
 *     The test starts just one thread, then creates a small NonbranyTree and a
 *     huge one. Both trees are to fill about 80% of the memory. Then the test
 *     drops references to both trees and invoke Algorithms.eatMemory(int) to
 *     provoke GC to clean the memory. the GC should correctly remove both
 *     objects. If the GC is Parallel, there are more than one GC threads, so one
 *     will try to "steal" some job from others.
 *
 * @library /vmTestbase
 *          /test/lib
 * @run driver jdk.test.lib.FileInstaller . .
 * @run main/othervm -XX:-UseGCOverheadLimit gc.gctests.Steal.steal002.steal002
 */

package gc.gctests.Steal.steal002;

import nsk.share.gc.*;
import nsk.share.test.ExecutionController;
import nsk.share.test.Stresser;

public class steal002 extends GCTestBase {

    ExecutionController stresser;
    // Number of nodes for the small tree
    final static int SMALL_NODES = 10;
    // Size (in bytes) for a node of the small tree
    final static int SMALL_NODE_SIZE = 1;
    // Number of nodes for the huge tree
    final static int HUGE_NODES = 500;
    // Part of the heap to fill with both trees
    final static double PART_OF_HEAP = 0.8;
    final int hugeNodeSize;
    public static NonbranchyTree smallTree;
    public static NonbranchyTree hugeTree;

    @Override
    public void run() {
        if (stresser == null) {
            stresser = new Stresser(runParams.getStressOptions());
            stresser.start(runParams.getIterations());
        }
        while (stresser.continueExecution()) {
            // Create a small tree and a huge one. Then drop references
            // to both of them.
            smallTree = new NonbranchyTree(SMALL_NODES, 0.3f, SMALL_NODE_SIZE);
            hugeTree = new NonbranchyTree(HUGE_NODES, 0.3f, hugeNodeSize);

            // Drop references to both trees and provoke GC to clean
            // the memory
            hugeTree = null;
            smallTree = null;

            // Provoke GC to clean the memory
            Algorithms.eatMemory(stresser);
        }
    }

    public steal002() {
        hugeNodeSize = Math.max(1, (int) (PART_OF_HEAP * Runtime.getRuntime().maxMemory() / HUGE_NODES
                - NonbranchyTree.MIN_NODE_SIZE));
    }

    public static void main(String args[]) {
        GC.runTest(new steal002(), args);
    }
}
