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


/*
 * @test
 *
 * @summary converted from VM Testbase nsk/jvmti/GetThreadInfo/thrinfo001.
 * VM Testbase keywords: [quick, jpda, jvmti, noras]
 * VM Testbase readme:
 * DESCRIPTION
 *     The test exercise JVMTI function GetThreadInfo.
 *     The test cases include:
 *     - user-defined and default thread name
 *     - main thread
 *     - user-defined and default thread group
 *     - norm, min and min+2 priorities
 *     - daemon and non-daemon threads
 * COMMENTS
 *     Fixed according to the 4387521 bug.
 *     Fixed according to the 4480280 bug.
 *     Ported from JVMDI.
 *
 * @requires vm.continuations
 * @library /test/lib
 * @compile --enable-preview -source ${jdk.version} thrinfo01.java
 * @run main/othervm/native --enable-preview -agentlib:thrinfo01 thrinfo01
 */


public class thrinfo01 {

    static {
        System.loadLibrary("thrinfo01");
    }

    native static boolean checkInfo0(Thread thread, ThreadGroup threadGroup, int ind);
    static void checkInfo(Thread thread, ThreadGroup threadGroup, int ind) {
        if(!checkInfo0(thread, threadGroup, ind)) {
            throw new RuntimeException(("Error in checkInfo. See log."));
        }
    }

    public static void main(String args[]) {
        Thread.currentThread().setName("main");
        Thread currentThread = Thread.currentThread();
        checkInfo(currentThread, currentThread.getThreadGroup(), 0);

        ThreadGroup tg = new ThreadGroup("tg1");
        ThreadInfo01a threadInfo01a = new ThreadInfo01a(tg, "thread1");
        threadInfo01a.setPriority(Thread.MIN_PRIORITY + 2);
        threadInfo01a.setDaemon(true);
        checkInfo(threadInfo01a, tg, 1);
        threadInfo01a.start();
        try {
            threadInfo01a.join();
        } catch (InterruptedException e) {}
        checkInfo(threadInfo01a, threadInfo01a.getThreadGroup(), 1);

        ThreadInfo01b threadInfo01b = new ThreadInfo01b();
        threadInfo01b.setPriority(Thread.MIN_PRIORITY);
        threadInfo01b.setDaemon(true);
        checkInfo(threadInfo01b, threadInfo01b.getThreadGroup(), 2);
        threadInfo01b.start();
        try {
            threadInfo01b.join();
        } catch (InterruptedException e) {}
        checkInfo(threadInfo01b, threadInfo01b.getThreadGroup(), 2);

        Thread threadInfo01c = Thread.ofVirtual().name("vthread").unstarted(new ThreadInfo01c());
        checkInfo(threadInfo01c, threadInfo01c.getThreadGroup(), 3);
        threadInfo01c.start();
        try {
            threadInfo01c.join();
        } catch (InterruptedException e) {}
        checkInfo(threadInfo01c, threadInfo01c.getThreadGroup(), 3);
    }
}

class ThreadInfo01a extends Thread {
    ThreadInfo01a(ThreadGroup tg, String name) {
        super(tg, name);
    }

    public void run() {
        Thread currentThread = Thread.currentThread();
        thrinfo01.checkInfo(currentThread, currentThread.getThreadGroup(), 1);
    }
}

class ThreadInfo01b extends Thread {
    public void run() {
        Thread currentThread = Thread.currentThread();
        thrinfo01.checkInfo(currentThread, currentThread.getThreadGroup(), 2);
    }
}

class ThreadInfo01c implements Runnable {
    public void run() {
        Thread currentThread = Thread.currentThread();
        thrinfo01.checkInfo(currentThread, currentThread.getThreadGroup(), 3);
    }
}
