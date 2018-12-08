/*
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
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
 */


package org.graalvm.compiler.core.test.deopt;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

import org.junit.Assert;
import org.junit.Test;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;

public final class MonitorDeoptTest extends GraalCompilerTest {

    private enum State {
        INITIAL,
        ALLOWING_SAFEPOINT,
        RUNNING_GRAAL,
        INVALIDATED,
        RUNNING_INTERPRETER,
        TERMINATED
    }

    static final long TIMEOUT = 5000;

    private static class Monitor {
        private volatile State state = State.INITIAL;

        public synchronized void setState(State newState) {
            state = newState;
            notifyAll();
        }

        public synchronized boolean tryUpdateState(State oldState, State newState) {
            if (state == oldState) {
                state = newState;
                notifyAll();
                return true;
            } else {
                return false;
            }
        }

        public synchronized void waitState(State targetState) throws InterruptedException {
            long startTime = System.currentTimeMillis();
            while (state != targetState && System.currentTimeMillis() - startTime < TIMEOUT) {
                wait(100);
            }
            if (state != targetState) {
                throw new IllegalStateException("Timed out waiting for " + targetState);
            }
        }

        /**
         * Change the current state to {@link State#ALLOWING_SAFEPOINT} and do a short wait to allow
         * a safepoint to happen. Then restore the state to the original value.
         *
         * @param expectedState
         * @throws InterruptedException
         */
        public synchronized void safepoint(State expectedState) throws InterruptedException {
            if (state == expectedState) {
                state = State.ALLOWING_SAFEPOINT;
                wait(1);
                if (state != State.ALLOWING_SAFEPOINT) {
                    throw new InternalError("Other threads can not update the state from ALLOWING_SAFEPOINT: " + state);
                }
                state = expectedState;
                notifyAll();
            }
        }

        public synchronized State getState() {
            return state;
        }

        public synchronized void invalidate(InstalledCode code) throws InterruptedException {
            // wait for the main thread to start
            waitState(State.RUNNING_GRAAL);

            state = State.INVALIDATED;
            code.invalidate();
        }
    }

    public static boolean test(Monitor monitor) throws InterruptedException {
        // initially, we're running as Graal compiled code
        monitor.setState(State.RUNNING_GRAAL);

        boolean timedOut = true;
        long startTime = System.currentTimeMillis();
        long safepointCheckTime = startTime;
        while (System.currentTimeMillis() - startTime < TIMEOUT) {
            // wait for the compiled code to be invalidated
            if (monitor.tryUpdateState(State.INVALIDATED, State.RUNNING_INTERPRETER)) {
                timedOut = false;
                break;
            }
            if (System.currentTimeMillis() - safepointCheckTime > 200) {
                /*
                 * It's possible for a safepoint to be triggered by external code before
                 * invalidation is ready. Allow a safepoint to occur if required but don't allow
                 * invalidation to proceed.
                 */
                monitor.safepoint(State.RUNNING_GRAAL);
                safepointCheckTime = System.currentTimeMillis();
            }
        }
        if (timedOut) {
            throw new InternalError("Timed out while waiting for code to be invalidated: " + monitor.getState());
        }

        for (int i = 0; i < 500; i++) {
            // wait for the control thread to send the TERMINATED signal
            if (monitor.getState() == State.TERMINATED) {
                return true;
            }

            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
            }
        }

        // we're running for more than 5 s in the interpreter
        // probably the control thread is deadlocked
        return false;
    }

    private static LoopBeginNode findFirstLoop(StructuredGraph graph) {
        FixedNode node = graph.start();
        for (;;) {
            if (node instanceof LoopBeginNode) {
                return (LoopBeginNode) node;
            } else if (node instanceof FixedWithNextNode) {
                node = ((FixedWithNextNode) node).next();
            } else if (node instanceof AbstractEndNode) {
                node = ((AbstractEndNode) node).merge();
            } else {
                Assert.fail(String.format("unexpected node %s in graph of test method", node));
            }
        }
    }

    /**
     * Remove the safepoint from the first loop in the test method, so only the safepoints on
     * MonitorEnter and MonitorExit remain in the loop. That way, we can make sure it deopts inside
     * the MonitorEnter by invalidating the code while holding the lock.
     */
    private static void removeLoopSafepoint(StructuredGraph graph) {
        LoopBeginNode loopBegin = findFirstLoop(graph);
        loopBegin.disableSafepoint();
    }

    @Test
    public void run0() throws Throwable {
        ResolvedJavaMethod javaMethod = getResolvedJavaMethod("test");

        StructuredGraph graph = parseEager(javaMethod, AllowAssumptions.YES);
        removeLoopSafepoint(graph);

        CompilationResult compilationResult = compile(javaMethod, graph);
        final InstalledCode installedCode = getBackend().createDefaultInstalledCode(graph.getDebug(), javaMethod, compilationResult);

        final Monitor monitor = new Monitor();

        Thread controlThread = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    // Wait for thread to reach RUNNING_GRAAL and then invalidate the code
                    monitor.invalidate(installedCode);

                    // wait for the main thread to continue running in the interpreter
                    monitor.waitState(State.RUNNING_INTERPRETER);

                    // terminate the main thread
                    monitor.setState(State.TERMINATED);
                } catch (InterruptedException e) {
                }
            }
        });

        controlThread.start();

        boolean result = test(monitor);
        Assert.assertTrue(result);
    }
}
