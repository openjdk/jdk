/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.org.apache.xml.internal.utils;

/**
 * This is a combination of ThreadControllerWrapper's inner class SafeThread
 * that was introduced as a fix for CR 6607339
 * and sun.misc.ManagedLocalsThread, a thread that has it's thread locals, and
 * inheritable thread locals erased on construction. Except the run method,
 * it is identical to sun.misc.ManagedLocalsThread.
 */
public class SafeThread extends Thread {

    private static final jdk.internal.misc.Unsafe UNSAFE;
    private static final long THREAD_LOCALS;
    private static final long INHERITABLE_THREAD_LOCALS;

    private volatile boolean ran = false;

    public SafeThread(Runnable target) {
        super(target);
        eraseThreadLocals();
    }

    public SafeThread(Runnable target, String name) {
        super(target, name);
        eraseThreadLocals();
    }

    public SafeThread(ThreadGroup group, Runnable target, String name) {
        super(group, target, name);
        eraseThreadLocals();
    }

    public final void run() {
        if (Thread.currentThread() != this) {
            throw new IllegalStateException("The run() method in a"
                    + " SafeThread cannot be called from another thread.");
        }
        synchronized (this) {
            if (!ran) {
                ran = true;
            } else {
                throw new IllegalStateException("The run() method in a"
                        + " SafeThread cannot be called more than once.");
            }
        }
        super.run();
    }

    /**
     * Drops all thread locals (and inherited thread locals).
     */
    public final void eraseThreadLocals() {
        UNSAFE.putObject(this, THREAD_LOCALS, null);
        UNSAFE.putObject(this, INHERITABLE_THREAD_LOCALS, null);
    }

    static {
        UNSAFE = jdk.internal.misc.Unsafe.getUnsafe();
        Class<?> t = Thread.class;
        try {
            THREAD_LOCALS = UNSAFE.objectFieldOffset(t.getDeclaredField("threadLocals"));
            INHERITABLE_THREAD_LOCALS = UNSAFE.objectFieldOffset(t.getDeclaredField("inheritableThreadLocals"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

}
