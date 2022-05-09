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

package nsk.jvmti.GetThreadInfo;

import java.io.PrintStream;
import java.util.concurrent.ThreadFactory;

public class thrinfo001 {

    final static int JCK_STATUS_BASE = 95;

    static {
        try {
            System.loadLibrary("thrinfo001");
        } catch (UnsatisfiedLinkError ule) {
            System.err.println("Could not load thrinfo001 library");
            System.err.println("java.library.path:"
                + System.getProperty("java.library.path"));
            throw ule;
        }
    }

    native static void checkInfo(Thread thr, ThreadGroup thr_group, int ind);
    native static int getRes();

    public static void main(String args[]) {
        args = nsk.share.jvmti.JVMTITest.commonInit(args);

        // produce JCK-like exit status.
        System.exit(run(args, System.out) + JCK_STATUS_BASE);
    }

    public static int run(String argv[], PrintStream ref) {
        Thread currThr = Thread.currentThread();
        checkInfo(currThr, currThr.getThreadGroup(), 0);

        ThreadGroup tg = new ThreadGroup("tg1");
        thrinfo001a t_a = new thrinfo001a(tg, "thread1");
        t_a.setPriority(Thread.MIN_PRIORITY + 2);
        t_a.setDaemon(true);
        checkInfo(t_a, tg, 1);
        t_a.start();
        try {
            t_a.join();
        } catch (InterruptedException e) {}
        checkInfo(t_a, t_a.getThreadGroup(), 1);

        thrinfo001b t_b = new thrinfo001b();
        t_b.setPriority(Thread.MIN_PRIORITY);
        t_b.setDaemon(true);
        checkInfo(t_b, t_b.getThreadGroup(), 2);
        t_b.start();
        try {
            t_b.join();
        } catch (InterruptedException e) {}
        checkInfo(t_b, t_b.getThreadGroup(), 2);


        Thread t_c = virtualThreadFactory().newThread(new thrinfo001c());
        t_c.setName("vthread");

        checkInfo(t_c, t_c.getThreadGroup(), 3);
        t_c.start();
        try {
            t_c.join();
        } catch (InterruptedException e) {}
        checkInfo(t_c, t_c.getThreadGroup(), 3);

        return getRes();
    }

    private static ThreadFactory virtualThreadFactory() {
        try {
            Object builder = Thread.class.getMethod("ofVirtual").invoke(null);
            Class<?> clazz = Class.forName("java.lang.Thread$Builder");
            java.lang.reflect.Method factory = clazz.getMethod("factory");
            return (ThreadFactory) factory.invoke(builder);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

class thrinfo001a extends Thread {
    thrinfo001a(ThreadGroup tg, String name) {
        super(tg, name);
    }

    public void run() {
        Thread currThr = Thread.currentThread();
        thrinfo001.checkInfo(currThr, currThr.getThreadGroup(), 1);
    }
}

class thrinfo001b extends Thread {
    public void run() {
        Thread currThr = Thread.currentThread();
        thrinfo001.checkInfo(currThr, currThr.getThreadGroup(), 2);
    }
}

class thrinfo001c implements Runnable {
    public void run() {
        Thread currThr = Thread.currentThread();
        thrinfo001.checkInfo(currThr, currThr.getThreadGroup(), 3);
    }
}
