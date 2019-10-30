/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.jfr.event.oldobject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.internal.test.WhiteBox;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @key jfr
 * @requires vm.hasJFR
 * @requires vm.gc == "null"
 * @library /test/lib /test/jdk
 * @modules jdk.jfr/jdk.jfr.internal.test
 * @run main/othervm -XX:TLABSize=2k jdk.jfr.event.oldobject.TestLargeRootSet
 */
public class TestLargeRootSet {

    private static final int THREAD_COUNT = 50;
    private static final Random RANDOM = new Random(4711);
    public static Vector<StackObject[]> temporaries = new Vector<>(OldObjects.MIN_SIZE);

    private static class RootThread extends Thread {
        private final CyclicBarrier barrier;
        private int maxDepth = OldObjects.MIN_SIZE / THREAD_COUNT;

        RootThread(CyclicBarrier cb) {
            this.barrier = cb;
        }

        public void run() {
            buildRootObjects();
        }

        private void buildRootObjects() {
            if (maxDepth-- > 0) {
                // Allocate array to trigger sampling code path for interpreter
                // / c1
                StackObject[] stackObject = new StackObject[RANDOM.nextInt(7)];
                temporaries.add(stackObject); // make sure object escapes
                buildRootObjects();
            } else {
                temporaries.clear();
                try {
                    barrier.await(); // wait for gc
                    barrier.await(); // wait for recording to be stopped
                } catch (InterruptedException e) {
                    System.err.println("Thread was unexpected interrupted: " + e.getMessage());
                } catch (BrokenBarrierException e) {
                    System.err.println("Unexpected barrier exception: " + e.getMessage());
                }
                return;
            }
        }
    }

    private static class StackObject {
    }

    public static void main(String[] args) throws Exception {
        WhiteBox.setWriteAllObjectSamples(true);
        int attempt = 1;
        while (true) {
            System.out.println();
            System.out.println();
            System.out.println("ATTEMPT: " + attempt);
            System.out.println("====================================");
            List<RootThread> threads = new ArrayList<>();
            try (Recording r = new Recording()) {
                r.enable(EventNames.OldObjectSample).withStackTrace().with("cutoff", "infinity");
                r.start();
                CyclicBarrier cb = new CyclicBarrier(THREAD_COUNT + 1);
                for (int i = 0; i < THREAD_COUNT; i++) {
                    RootThread t = new RootThread(cb);
                    t.start();
                    if (i % 10 == 0) {
                        // Give threads some breathing room before starting next
                        // batch
                        Thread.sleep(100);
                    }
                    threads.add(t);
                }
                cb.await();
                System.gc();
                r.stop();
                cb.await();
                List<RecordedEvent> events = Events.fromRecording(r);
                Events.hasEvents(events);
                int sample = 0;
                for (RecordedEvent e : events) {
                    RecordedObject ro = e.getValue("object");
                    RecordedClass rc = ro.getValue("type");
                    System.out.println("Sample: " + sample);
                    System.out.println(" - allocationTime: " + e.getInstant("allocationTime"));
                    System.out.println(" - type: " + rc.getName());
                    RecordedObject root = e.getValue("root");
                    if (root != null) {
                        System.out.println(" - root:");
                        System.out.println("   - description: " + root.getValue("description"));
                        System.out.println("   - system: " + root.getValue("system"));
                        System.out.println("   - type: " + root.getValue("type"));
                    } else {
                        System.out.println(" - root: N/A");
                    }
                    RecordedStackTrace stack = e.getStackTrace();
                    if (stack != null) {
                        System.out.println(" - stack:");
                        int frameCount = 0;
                        for (RecordedFrame frame : stack.getFrames()) {
                            RecordedMethod m = frame.getMethod();
                            System.out.println("      " + m.getType().getName() + "." + m.getName() + "(...)");
                            frameCount++;
                            if (frameCount == 10) {
                                break;
                            }
                        }
                    } else {
                        System.out.println(" - stack: N/A");
                    }
                    System.out.println();
                    if (rc.getName().equals(StackObject[].class.getName())) {
                        return; // ok
                    }
                    sample++;
                }
            }
            attempt++;
        }
    }

}
