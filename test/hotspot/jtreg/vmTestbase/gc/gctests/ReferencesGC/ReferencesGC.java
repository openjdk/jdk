/*
 * Copyright (c) 2002, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @summary converted from VM Testbase gc/gctests/ReferencesGC.
 * VM Testbase keywords: [gc, stress, stressopt, nonconcurrent, quick]
 *
 * @library /vmTestbase
 *          /test/lib
 * @run driver jdk.test.lib.FileInstaller . .
 * @run main/othervm
 *      -XX:-UseGCOverheadLimit
 *      gc.gctests.ReferencesGC.ReferencesGC
 *      -range 200
 *      -ratio 0.9
 *      -t 1
 */

package gc.gctests.ReferencesGC;

import java.lang.ref.*;
import nsk.share.TestFailure;
import nsk.share.gc.Algorithms;
import nsk.share.gc.GC;
import nsk.share.gc.ThreadedGCTest;
import nsk.share.gc.gp.GarbageProducer;
import nsk.share.gc.gp.GarbageUtils;
import nsk.share.test.ExecutionController;

public class ReferencesGC extends ThreadedGCTest {

    static int RANGE = 256;
    static float RATIO = (float) 1.0;

    public static void main(String[] args) {
        parseArgs(args);
        GC.runTest(new ReferencesGC(), args);
    }

    public static void parseArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].compareTo("-range") == 0) {
                RANGE = new Integer(args[++i]).intValue();
            } else if (args[i].compareTo("-ratio") == 0) {
                RATIO = new Float(args[++i]).floatValue();
            }
        }
    }

    private class Worker implements Runnable {

        static final int WEAK = 0;
        static final int SOFT = 1;
        static final int PHANTOM = 2;
        private ExecutionController stresser;
        int finalizationMaxTime = 1000 * 60 * runParams.getNumberOfThreads();
        int[] alive = new int[3];
        int[] enqued = new int[3];
        CircularLinkedList holder[] = new CircularLinkedList[RANGE];
        WeakReference wr[] = new WeakReference[RANGE];
        SoftReference sr[] = new SoftReference[RANGE];
        PhantomReference phr[] = new PhantomReference[RANGE];
        ReferenceQueue refq = new ReferenceQueue();
        GarbageProducer gp = GarbageUtils.getArrayProducers().get(0);
        int iter = 0;

        @Override
        public void run() {
            if (stresser == null) {
                stresser = getExecutionController();
            }

            while (stresser.continueExecution()) {
                int totalQ = 0;
                try {
                    refq = new ReferenceQueue();
                    alive = new int[3];
                    enqued = new int[3];
                    for (int j = 0; j < RANGE; j++) {
                        holder[j] = new CircularLinkedList();
                        holder[j].addNelements(300);
                        wr[j] = new WeakReference(holder[j], refq);
                        sr[j] = new SoftReference(holder[j], refq);
                        phr[j] = new PhantomReference(holder[j], refq);
                    }
                } catch (OutOfMemoryError oome) {
                    // we should just skip the test
                    // the other thread could eat all memory
                    continue;
                }

                for (int i = 0; i < RANGE; i++) {
                    if (wr[i].isEnqueued()) {
                        ++totalQ;
                    }
                    if (sr[i].isEnqueued()) {
                        ++totalQ;
                    }
                    if (phr[i].isEnqueued()) {
                        ++totalQ;
                    }
                }
                if (totalQ != 0) {
                    throw new TestFailure("There are " + totalQ + " references in the queue instead 0 before null-assigment.");
                }

                for (int i = 0; i < (int) (RANGE * RATIO); i++) {
                    holder[i] = null;
                }

                Algorithms.eatMemory(stresser);
                if (!stresser.continueExecution()) {
                    break;
                }
                // At this point OOME was thrown and accordingly to spec
                // all weak refs should be processed

                alive = new int[3];
                enqued = new int[3];
                for (int i = 0; i < RANGE; i++) {
                    if (wr[i].get() != null) {
                        ++alive[WEAK];
                    }
                    if (wr[i].isEnqueued()) {
                        ++enqued[WEAK];
                    }
                    if (sr[i].get() != null) {
                        ++alive[SOFT];
                    }
                    if (sr[i].isEnqueued()) {
                        ++enqued[SOFT];
                    }
                    if (phr[i].isEnqueued()) {
                        ++enqued[PHANTOM];
                    }
                }

                long waitTime = System.currentTimeMillis() + finalizationMaxTime;
                while (totalQ < (RANGE * RATIO * 3 * 0.9) && (System.currentTimeMillis() < waitTime)) {
                    alive = new int[3];
                    enqued = new int[3];
                    for (int i = 0; i < RANGE; i++) {
                        if (wr[i].get() != null) {
                            ++alive[WEAK];
                        }
                        if (wr[i].isEnqueued()) {
                            ++enqued[WEAK];
                        }
                        if (sr[i].get() != null) {
                            ++alive[SOFT];
                        }
                        if (sr[i].isEnqueued()) {
                            ++enqued[SOFT];
                        }
                        if (phr[i].isEnqueued()) {
                            ++enqued[PHANTOM];
                        }
                    }
                    totalQ = (enqued[WEAK] + enqued[SOFT] + enqued[PHANTOM]);
                    if (totalQ < (int) (3 * RANGE * RATIO * 0.9)) {
                        log.debug("After null-assignment to " + (int) (RANGE * RATIO) +
                                //" elements from " + lower + " to " + (upper - 1) +
                                " and provoking gc found:\n\t" +
                                enqued[WEAK] + " weak\n\t" +
                                enqued[SOFT] + " soft\n\t" +
                                enqued[PHANTOM] + " phantom " +
                                " queuened refs and \n\t" +
                                alive[WEAK] + " weak\n\t" +
                                alive[SOFT] + " soft\n\t" +
                                "alive refs.");
                        try {
                            log.debug("sleeping to give gc one more chance ......");
                            Thread.sleep(1000);
                        } catch (InterruptedException ie) {
                        }
                    }
                }
                log.debug("iteration.... " + iter++);
                if (totalQ < (int) (3 * RANGE * RATIO * 0.9) || totalQ > (int) (3 * RANGE * RATIO)) {
                    throw new TestFailure("Test failed");
                }
            }
        }
    }

    @Override
    protected Runnable createRunnable(int i) {
        return new Worker();
    }
}
