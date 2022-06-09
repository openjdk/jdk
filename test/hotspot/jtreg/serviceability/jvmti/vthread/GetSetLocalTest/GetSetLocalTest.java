/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @summary Verifies JVMTI GetLocalXXX/SetLocalXXX support for virtual threads.
 * @requires vm.continuations
 * @library /test/lib
 * @compile --enable-preview -source ${jdk.version} GetSetLocalTest.java
 * @run main/othervm/native --enable-preview -agentlib:GetSetLocalTest GetSetLocalTest
 */

import java.util.concurrent.*;

public class GetSetLocalTest {
    private static final String agentLib = "GetSetLocalTest";

    static final int MSG_COUNT = 600*1000;
    static final SynchronousQueue<String> QUEUE = new SynchronousQueue<>();
    static native boolean completed();
    static native void enableEvents(Thread thread);
    static native void testSuspendedVirtualThreads(Thread thread);
    static Thread producer;
    static Thread consumer;

    static void producer(String msg) throws InterruptedException {
        Thread tt = Thread.currentThread();
        int ii = 1;
        long ll = 2*(long)ii;
        float ff = ll + 1.2f;
        double dd = ff + 1.3D;

        msg += dd;
        QUEUE.put(msg);
    }

    static final Runnable PRODUCER = () -> {
        try {
            for (int i = 0; i < MSG_COUNT; i++) {
                if (completed()) {
                    consumer.interrupt();
                    break;
                }
                producer("msg: ");
            }
        } catch (InterruptedException e) { }
    };

    static final Runnable CONSUMER = () -> {
        try {
            for (int i = 0; i < MSG_COUNT; i++) {
                String s = QUEUE.take();
            }
        } catch (InterruptedException e) {
            System.err.println("CONSUMER was interrupted!");
        }
    };

    public static void test1() throws Exception {
        producer = Thread.ofVirtual().name("VThread-Producer").start(PRODUCER);
        consumer = Thread.ofVirtual().name("VThread-Consumer").start(CONSUMER);

        testSuspendedVirtualThreads(producer);
        enableEvents(producer);

        producer.join();
        consumer.join();
    }

    void runTest() throws Exception {
        test1();
    }

    public static void main(String[] args) throws Exception {
        try {
            System.loadLibrary(agentLib);
        } catch (UnsatisfiedLinkError ex) {
            System.err.println("Failed to load " + agentLib + " lib");
            System.err.println("java.library.path: " + System.getProperty("java.library.path"));
            throw ex;
        }

        GetSetLocalTest obj = new GetSetLocalTest();
        obj.runTest();
    }
}
