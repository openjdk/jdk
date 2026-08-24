/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.event.profiling;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import java.util.Random;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingStream;
import jdk.jfr.consumer.RecordedThread;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.Asserts;

/*
 * @test
 * @comment The purpose of this test is to verify that jdk.ExecutionSample events taken
 *          inside frames classified as needing stack repair are processed correctly,
 *          even though the sp_inc field of the sampled frame is overwritten by the SafepointBlob.
 *          We must be able to resolve samples correctly, even though we cannot reconstruct these frames.
 *          The sender is jdk.jfr.event.profiling.TestNeedStackRepair::start, which is excluded from compilation,
 *          causing it to invoke a specific entry point of the callee, Holder::<init> that, once JIT-compiled,
 *          unpacks fields into the stack extension space. The compiled JIT frame for Holder::<init> is resolved,
 *          not by frame reconstruction, but by directly iterating the scope_decode_offsets of the nmethod.
 *
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @enablePreview
 * @library /test/lib
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints -XX:CompileCommand=exclude,jdk.jfr.event.profiling.TestNeedStackRepair::start jdk.jfr.event.profiling.TestNeedStackRepair
 */
public class TestNeedStackRepair {
    static final AtomicBoolean found = new AtomicBoolean();
    public static int value = 0;

    static value class V0 {
        byte v1;
        byte v2;

        V0(int v1, int v2) {
            this.v1 = (byte) v1;
            this.v2 = (byte) v2;
        }
    }

    static value class V1 {
        V0 v;
        short s;

        V1(V0 v, int s) {
            this.v = v;
            this.s = (short) s;
        }
    }

    static class Holder {
        V1 v1;
        V1 v2;

        Holder(V1 v1, V1 v2) {
            this.v1 = v1;
            this.v2 = v2;
            super();
        }
    }

    static void add(Holder h) {
        value += h.v1.v.v1 + h.v2.s + h.v2.v.v2;
    }

    static void start(Random r) throws Exception {
        V0 v0 = new V0(r.nextInt(), r.nextInt());
        V0 v01 = new V0(r.nextInt(), r.nextInt());
        V1 v1 = new V1(v0, r.nextInt());
        V1 v11 = new V1(v01, r.nextInt());
        Holder h = new Holder(v1, v11);
        add(h);
    }

    static class TestThread extends Thread {
        Random r = new Random();
        public void run() {
            while (true) {
                try {
                    TestNeedStackRepair.start(r);
                } catch (Exception ex) {}
            }
        }
    }

    static long launchTestThread() throws Exception {
        TestThread tt = new TestThread();
        tt.setDaemon(true);
        try {
            tt.start();
        } catch (IllegalStateException ise) {}
        return tt.threadId();
    }

    static RecordedFrame getStackTraceTopFrame(RecordedEvent e) {
        List<RecordedFrame> frames = e.getStackTrace().getFrames();
        return frames.getFirst();
    }

    public static void main(String[] args) throws Exception {
        long threadId = launchTestThread();
        try (RecordingStream rs = new RecordingStream()) {
            rs.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(1));
            rs.onEvent("jdk.ExecutionSample", e -> {
                if (!found.get() && threadId == e.getThread("sampledThread").getId()) {
                    RecordedFrame topFrame = getStackTraceTopFrame(e);
                    RecordedMethod method = topFrame.getMethod();
                    if ("<init>".equals(method.getName())) {
                        if (method.getType().getName().endsWith("Holder")) {
                            if ("JIT compiled".equals(topFrame.getType())) {
                                System.out.println(e);
                                found.set(true);
                                rs.close();
                            }
                        }
                    }
                }
            });
            rs.startAsync();
            rs.awaitTermination();
            Asserts.assertTrue(found.get(), "Did not find the needs stack repair frame");
        }
    }
}
