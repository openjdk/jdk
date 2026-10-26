/*
 * Copyright (c) 2026, Datadog, Inc. All rights reserved.
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

package jdk.jfr.jvmti;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import jdk.jfr.Recording;

import jdk.test.lib.Asserts;

/**
 * @test
 * @summary Passing a virtual thread as an explicit thread argument is not
 *          supported and returns UNSUPPORTED_OPERATION. Virtual-thread
 *          sampling is available by calling with thread=NULL from within
 *          the vthread itself; see TestRequestStackTraceFromVirtualThread.
 * @requires vm.hasJFR & vm.continuations & os.family != "windows"
 * @library /test/lib
 * @build jdk.jfr.jvmti.RequestStackTraceHelper
 * @run main/othervm/native -agentlib:RequestStackTraceAgent
 *      jdk.jfr.jvmti.TestRequestStackTraceVirtualThread
 */
public class TestRequestStackTraceVirtualThread {

    private static final String EVENT_NAME = "jdk.StackTraceRequest";

    public static void main(String[] args) throws Exception {
        try (Recording r = new Recording()) {
            r.enable(EVENT_NAME).with("throttle", "100/s");
            r.start();

            CountDownLatch ready = new CountDownLatch(1);
            CountDownLatch finish = new CountDownLatch(1);
            AtomicReference<Throwable> error = new AtomicReference<>();

            Thread vt = Thread.ofVirtual().name("RequestStackTrace-VThread").start(() -> {
                try {
                    ready.countDown();
                    finish.await();
                } catch (Throwable t) {
                    error.set(t);
                }
            });
            ready.await();

            try {
                int rc = RequestStackTraceHelper.requestStackTraceWithThread(vt, 0L);
                Asserts.assertEquals(
                        RequestStackTraceHelper.JVMTI_ERROR_UNSUPPORTED_OPERATION, rc,
                        "expected UNSUPPORTED_OPERATION for virtual thread argument, got "
                                + rc);
            } finally {
                finish.countDown();
                vt.join();
            }

            r.stop();
            if (error.get() != null) throw new RuntimeException(error.get());
        }
    }
}
