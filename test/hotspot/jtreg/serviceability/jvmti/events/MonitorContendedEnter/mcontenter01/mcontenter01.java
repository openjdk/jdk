/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.jvmti.DebugeeClass;

/*
 * @test
 *
 * @summary converted from VM Testbase nsk/jvmti/MonitorContendedEnter/mcontenter001.
 * VM Testbase keywords: [quick, jpda, jvmti, noras]
 * VM Testbase readme:
 * DESCRIPTION
 *     The test exercises JVMTI event callback function
 *         MonitorContendedEnter(jni, thread, object).
 *     The test checks if the thread and object parameters of the function
 *     contain expected values for callback when a thread is attempting
 *     to enter a Java language monitor already acquired by another thread.
 * COMMENTS
 *     The test updated to match new JVMTI spec 0.2.90:
 *     - change signature of agentProc function
 *       and save JNIEnv pointer now passed as argument.
 *     1000 ms of sleep added to main thread to reduce probability of bad racing.
 *
 * @library /test/lib
 * @compile --enable-preview -source ${jdk.version} mcontenter01.java
 * @run main/othervm/native --enable-preview -agentlib:mcontenter01 mcontenter01 platform
 * @run main/othervm/native --enable-preview -agentlib:mcontenter01 mcontenter01 virtual
 */


public class mcontenter01 extends DebugeeClass {

    static {
        loadLibrary("mcontenter01");
    }

    public static void main(String args[]) {
        boolean isVirtual = "virtual".equals(args[0]);
        int result = new mcontenter01().runIt(isVirtual);
        if (result != 0) {
            throw new RuntimeException("Unexpected status: " + result);
        }
    }


    int status = DebugeeClass.TEST_PASSED;
    long timeout = 0;

    // run debuggee
    public int runIt(boolean isVirtual) {
        timeout = 60000;
        System.out.println("Timeout = " + timeout + " msc.");

        mcontenter01Task task = new mcontenter01Task();
        Thread.Builder builder;
        if (isVirtual) {
            builder = Thread.ofVirtual();
        } else {
            builder = Thread.ofPlatform();
        }
        Thread thread = builder.name("Debuggee Thread").unstarted(task);
        setExpected(task.endingMonitor, thread);

        synchronized (task.endingMonitor) {

            // run task
            try {
                // start task
                synchronized (task.startingMonitor) {
                    thread.start();
                    task.startingMonitor.wait(timeout);
                }
            } catch (InterruptedException e) {
                throw new Failure(e);
            }

            int totalDelay = 0;
            while (getEventCount() < 1 && totalDelay < timeout) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new Failure(e);
                }
                totalDelay += 100;
            }

            Thread.yield();
            System.out.println("Thread started");
        }

        // wait for task finish
        try {
            thread.join(timeout);
        } catch (InterruptedException e) {
            throw new Failure(e);
        }

        System.out.println("Sync: task finished");
        status = checkStatus(status);

        return status;
    }

    private native int getEventCount();
    private native void setExpected(Object monitor, Object thread);
}



class mcontenter01Task implements Runnable {
    public Object startingMonitor = new Object();
    public Object endingMonitor = new Object();

    public void run() {

        mcontenter01.checkStatus(DebugeeClass.TEST_PASSED);

        // notify about starting
        synchronized (startingMonitor) {
            startingMonitor.notify();
        }

        // wait until main thread release monitor
        synchronized (endingMonitor) {
        }
    }
}
