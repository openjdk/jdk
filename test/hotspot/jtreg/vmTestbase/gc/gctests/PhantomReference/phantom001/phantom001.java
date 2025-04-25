/*
 * Copyright (c) 2004, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @key stress randomness
 *
 * @summary converted from VM Testbase gc/gctests/PhantomReference/phantom001.
 * VM Testbase keywords: [gc, stress, stressopt, nonconcurrent]
 * VM Testbase readme:
 * DESCRIPTION
 *     The test checks that Garbage Collector correctly works with
 *     PhantomReferences. It also checks that no unexpected exceptions and errors
 *     are thrown or the JVM is not crashed.
 *     The test starts a number of threads. Each thread run tests for some time
 *     or serveral iterations.  See javadoc StressOptions for configuration.
 *     First of all each thread defines what type to check (there are 11 types
 *     totally). As soon as the type is defined, a PhantomRefence is created that
 *     refers to an array of tested type and is registered with in a queue. A
 *     PhantomRefence for NonbranchyTree and Referent calsses does not refer to
 *     arrays, but to instances of the classes.
 *     After that a thread performs next checks for the reference:
 *         1. The reference is in queue after GC is provoked with WB.fullGC()
 *         2. reference.get() returns null.
 *         3. queue.poll() returns the reference that was created.
 *         4. queue.poll() again returns null.
 *         5. If the checked type is class (Referent), then it must be finalized,
 *            since the reference is already enqueued.
 *     The test extends ThreadedGCTest and implements GarbageProducerAware and
 *     MemoryStrategyAware interfaces. The corresponding javadoc documentation
 *     for additional test configuration.
 *
 * @library /vmTestbase
 *          /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI gc.gctests.PhantomReference.phantom001.phantom001
 */

package gc.gctests.PhantomReference.phantom001;

import java.lang.ref.*;
import java.time.LocalTime;

import jdk.test.whitebox.WhiteBox;
import nsk.share.gc.*;
import nsk.share.gc.gp.string.InternedStringProducer;
import nsk.share.gc.gp.string.RandomStringProducer;

public class phantom001 extends ThreadedGCTest {

    private InternedStringProducer internedStringProducer = new InternedStringProducer(new RandomStringProducer(10));
    // Total number of types to test
    final static int TYPES_COUNT = 12;
    // Size of array of each tested type. The constant also specifies the
    // number of nodes in a NonbranchyTree and size of each node
    final static int SIZE = 100;

    protected Runnable createRunnable(int i) {
        return new Test();
    }

    public static void main(String[] args) {
        GC.runTest(new phantom001(), args);
    }

    // The class implements the logic of the testcase
    class Test implements Runnable {

        int iteration;
        private volatile boolean finalized;

        private String addMessageContext(String message) {
            return "T:" + Thread.currentThread().getId() +
                " I:" + iteration +
                " " + LocalTime.now().toString() +
                ": " + message;
        }

        private void info(String message) {
            log.info(addMessageContext(message));
        }

        private void progress(String message) {
            // Uncomment this to get more verbose logging.
            // log.debug(addMessageContext(message));
        }

        private void fail(String message) {
            log.error(addMessageContext("[FAILED] " + message));
            setFailed(true);
        }

        private boolean shouldTerminate() {
            return !getExecutionController().continueExecution();
        }

        public void run() {

            int code = iteration % TYPES_COUNT;
            info("start code " + code);
            ReferenceQueue queue = new ReferenceQueue();
            PhantomReference reference;
            String type;
            // Define a specific type for each thread to test
            switch (code) {
                case 0:
                    reference = new PhantomReference(new byte[SIZE], queue);
                    type = "byte";
                    break;
                case 1:
                    reference = new PhantomReference(new short[SIZE], queue);
                    type = "short";
                    break;
                case 2:
                    reference = new PhantomReference(new int[SIZE], queue);
                    type = "int";
                    break;
                case 3:
                    reference = new PhantomReference(new long[SIZE], queue);
                    type = "long";
                    break;
                case 4:
                    reference = new PhantomReference(new char[SIZE], queue);
                    type = "char";
                    break;
                case 5:
                    reference = new PhantomReference(new boolean[SIZE], queue);
                    type = "boolean";
                    break;
                case 6:
                    reference = new PhantomReference(new double[SIZE], queue);
                    type = "double";
                    break;
                case 7:
                    reference = new PhantomReference(new float[SIZE], queue);
                    type = "float";
                    break;
                case 8:
                    reference = new PhantomReference(new Object[SIZE], queue);
                    type = "Object";
                    break;
                case 9:
                    reference = new PhantomReference(new NonbranchyTree(SIZE, 0.3f, SIZE),
                            queue);
                    type = "NonbranchyTree";
                    break;
                case 10:
                    reference = new PhantomReference(internedStringProducer.create(SIZE), queue);
                    type = "InternedString";
                    break;
                default:
                    reference = new PhantomReference(new Referent(), queue);
                    type = "class";
            }

            // If referent is finalizable, provoke GCs and wait for finalization.
            if (type.equals("class")) {
                progress("Waiting for finalization: " + type);
                WhiteBox.getWhiteBox().fullGC();
                for (int checks = 0; !finalized && !shouldTerminate(); ++checks) {
                    // Wait for up to 10 iterations that the finalizer has been run,
                    // this ought to be sufficient. Full GCs and other threads might
                    // starve out the finalizer thread, requiring more waiting.
                    if (checks > 10) {
                        fail("Waiting for finalization: " + type);
                        return;
                    }
                    // Give some time for finalizer to run.
                    try {
                        Thread.sleep(checks * 100);
                    } catch (InterruptedException e) {}
                }
            }

            // Provoke GCs and wait for reference to be enqueued.
            progress("Waiting for enqueue: " + type);
            Reference polled = queue.poll();
            for (int checks = 0; polled == null && !shouldTerminate(); ++checks) {
                // There are scenarios where one WB.fillGC() isn't enough,
                // but 10 iterations really ought to be sufficient.
                if (checks > 10) {
                    fail("Waiting for enqueue: " + type);
                    return;
                }
                WhiteBox.getWhiteBox().fullGC();
                // Give some time for reference to be enqueued.
                try {
                    polled = queue.remove(checks * 100);
                } catch (InterruptedException e) {}
            }

            if (polled == null && shouldTerminate()) {
                info("Terminated: " + type);
                return;
            }

            // The polled reference must be equal to the one enqueued to
            // the queue
            if (polled != reference) {
                fail("The original reference is not equal to polled reference.");
                return;
            }

            // queue.poll() once again must return null now, since there is
            // only one reference in the queue
            if (queue.poll() != null) {
                fail("There are more than one reference in the queue.");
                return;
            }
            progress("Finished: " + type);
            iteration++;
        }

        class Referent {

            //We need discard this flag to make second and following checks with type.equals("class") useful
            public Referent() {
                finalized = false;
            }

            protected void finalize() {
                finalized = true;
            }
        }
    }

}
