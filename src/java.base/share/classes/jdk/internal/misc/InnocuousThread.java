/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.misc;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A thread that is not a member of any user-defined
 * ThreadGroup and supports the ability to erase ThreadLocals.
 */
public final class InnocuousThread extends Thread {
    private static final jdk.internal.misc.Unsafe UNSAFE;
    private static final long THREAD_LOCALS;
    private static final long INHERITABLE_THREAD_LOCALS;
    private static final ThreadGroup INNOCUOUSTHREADGROUP;
    private static final long CONTEXTCLASSLOADER;

    private static final AtomicInteger threadNumber = new AtomicInteger(1);
    private static String newName() {
        return "InnocuousThread-" + threadNumber.getAndIncrement();
    }

    /**
     * Returns a new InnocuousThread with an auto-generated thread name,
     * and its context class loader is set to the system class loader.
     */
    public static Thread newThread(Runnable target) {
        return newThread(newName(), target);
    }

    /**
     * Returns a new InnocuousThread with its context class loader
     * set to the system class loader.
     */
    public static Thread newThread(String name, Runnable target) {
        return newThread(name, target, -1);
    }
    /**
     * Returns a new InnocuousThread with its context class loader
     * set to the system class loader. The thread priority will be
     * set to the given priority.
     */
    public static Thread newThread(String name, Runnable target, int priority) {
        return createThread(name, target, 0L,
                ClassLoader.getSystemClassLoader(), priority);
    }

    /**
     * Returns a new InnocuousThread with an auto-generated thread name.
     * Its context class loader is set to null.
     */
    public static Thread newSystemThread(Runnable target) {
        return newSystemThread(newName(), target);
    }

    /**
     * Returns a new InnocuousThread with null context class loader.
     */
    public static Thread newSystemThread(String name, Runnable target) {
        return newSystemThread(name, target, -1);
    }

    /**
     * Returns a new InnocuousThread with null context class loader.
     * Thread priority is set to the given priority.
     */
    public static Thread newSystemThread(String name, Runnable target, int priority) {
        return createThread(name, target, 0L, null, priority);
    }

    /**
     * Returns a new InnocuousThread with null context class loader.
     * Thread priority is set to the given priority.
     */
    public static Thread newSystemThread(String name, Runnable target,
                                         long stackSize, int priority) {
        return createThread(name, target, stackSize, null, priority);
    }

    private static Thread createThread(String name, Runnable target, long stackSize,
                                       ClassLoader loader, int priority) {
        Thread t = new InnocuousThread(INNOCUOUSTHREADGROUP,
                target, name, stackSize, loader);
        if (priority >= 0) {
            t.setPriority(priority);
        }
        return t;
    }

    private InnocuousThread(ThreadGroup group, Runnable target, String name,
                            long stackSize, ClassLoader tccl) {
        super(group, target, name, stackSize, false);
        UNSAFE.putReferenceRelease(this, CONTEXTCLASSLOADER, tccl);
    }

    @Override
    public void setUncaughtExceptionHandler(UncaughtExceptionHandler x) {
        // silently fail
    }

    @Override
    public void setContextClassLoader(ClassLoader cl) {
        // Allow clearing of the TCCL to remove the reference to the system classloader.
        if (cl == null)
            super.setContextClassLoader(null);
        else
            throw new SecurityException("setContextClassLoader");
    }

    /**
     * Drops all thread locals (and inherited thread locals).
     */
    public final void eraseThreadLocals() {
        UNSAFE.putReference(this, THREAD_LOCALS, null);
        UNSAFE.putReference(this, INHERITABLE_THREAD_LOCALS, null);
    }

    // ensure run method is run only once
    private volatile boolean hasRun;

    @Override
    public void run() {
        if (Thread.currentThread() == this && !hasRun) {
            hasRun = true;
            super.run();
        }
    }

    // Use Unsafe to access Thread group and ThreadGroup parent fields
    static {
        try {
            // Find and use topmost ThreadGroup as parent of new group
            UNSAFE = jdk.internal.misc.Unsafe.getUnsafe();
            Class<?> tk = Thread.class;
            Class<?> gk = ThreadGroup.class;

            THREAD_LOCALS = UNSAFE.objectFieldOffset(tk, "threadLocals");
            INHERITABLE_THREAD_LOCALS = UNSAFE.objectFieldOffset
                    (tk, "inheritableThreadLocals");
            CONTEXTCLASSLOADER = UNSAFE.objectFieldOffset
                (tk, "contextClassLoader");

            long gp = UNSAFE.objectFieldOffset(gk, "parent");
            ThreadGroup group = Thread.currentThread().getThreadGroup();

            while (group != null) {
                ThreadGroup parent = (ThreadGroup)UNSAFE.getReference(group, gp);
                if (parent == null)
                    break;
                group = parent;
            }
            INNOCUOUSTHREADGROUP = new ThreadGroup(group, "InnocuousThreadGroup");
        } catch (Exception e) {
            throw new Error(e);
        }
    }
}
