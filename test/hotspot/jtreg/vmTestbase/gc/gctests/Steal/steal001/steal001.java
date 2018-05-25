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
 * @summary converted from VM Testbase gc/gctests/Steal/steal001.
 * VM Testbase keywords: [gc, stress, stressopt, nonconcurrent]
 * VM Testbase readme:
 * DESCRIPTION
 *     The test checks that Garbage Collector correctly uses stealing technique:
 *     no unexpected exceptions and errors are thrown; the JVM is not crashed.
 *     Actually, the test is intended for Parallel Collector.
 *     The test starts just one thread and fills the memory with NonbranyTrees
 *     (the number of nodes of the tree and its size are based on
 *     Runtime.maxMemory() value) until OutOfMemoryError is thrown. All references
 *     to the trees are saved in a java.util.Vector. Then the test removes a
 *     number of trees from the vector, this number is equal to number of
 *     processors (returned by nsk.share.gc.Algorithms.availableProcessors()).
 *     Algorithms.eatMemory(int) is invoked after that to provoke GC to clean the
 *     memory. Then procedure is repeated.
 *
 * @library /vmTestbase
 *          /test/lib
 * @run driver jdk.test.lib.FileInstaller . .
 * @run main/othervm -XX:-UseGCOverheadLimit gc.gctests.Steal.steal001.steal001
 */

package gc.gctests.Steal.steal001;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import nsk.share.gc.*;
import nsk.share.gc.gp.GarbageUtils;
import nsk.share.test.ExecutionController;

public class steal001 extends ThreadedGCTest {
    // Preload ThreadLocalRandom class to avoid class initialization failure
    // due to OOM error in static class initializer
    final static public ThreadLocalRandom threadLocalRandom = ThreadLocalRandom.current();

    // Internal number of iterations to remove and create new elements
    // for the vector
    final static int INTERNAL_ITERATIONS = 10;
    // Approximate number of trees to fill the heap with
    final static int TREES = 50;
    // Number of nodes for each tree
    final static int NODES = 500;

    private class Eater implements Runnable {

        private int nodeSize;
        private List<NonbranchyTree> list;
        int processors = Runtime.getRuntime().availableProcessors();
        ExecutionController stresser;

        public Eater(int nodeSize) {
            list = new ArrayList<>();
            this.nodeSize = nodeSize;
        }

        @Override
        public void run() {
            if (stresser == null) {
                stresser = getExecutionController();
            }
            int counter = NODES;
            while (stresser.continueExecution()) {
                fillHeap(counter);
                removeElements();
                counter = (counter == 1) ? 1 : counter - 1;
            }
        }

        // Fill the memory with trees of defined size until OutOfMemoryError
        private void fillHeap(int n) {
            try {
                while (stresser.continueExecution()) {
                    // Passing in the ExecutionController to make sure we
                    // stop allocating nodes when time is up.
                    list.add(new NonbranchyTree(n, 0.3f, nodeSize, stresser));
                }
            } catch (OutOfMemoryError e) {
            }
        }

        // Remove a number of elements (equal to number of processors) from the
        // vector and provoke GC to clean the heap
        private void removeElements() {
            if (list.size() <= 0) {
                return;
            }
            list.remove(0);
            GarbageUtils.eatMemory(stresser);
        }
    }

    @Override
    protected Runnable createRunnable(int i) {
        // Perform calculations specific to the test
        double treeSize = Runtime.getRuntime().maxMemory() / TREES;
        int nodeSize = (int) (treeSize / NODES - NonbranchyTree.MIN_NODE_SIZE);
        nodeSize = Math.max(1, nodeSize);
        return new Eater(nodeSize);
    }

    public static void main(String args[]) {
        // just to preload GarbageUtils and avoid exception
        // in removeElements()
        GarbageUtils.getGarbageProducers();
        GC.runTest(new steal001(), args);
    }
}
