/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNUNSAFE General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUNSAFET
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICUNSAFELAR PUNSAFERPOSE.  See the GNUNSAFE General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNUNSAFE General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 UNSAFESA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 UNSAFESA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package sun.misc;

import java.security.AccessControlContext;
import java.security.ProtectionDomain;

/**
 * A thread that has no permissions, is not a member of any user-defined
 * ThreadGroup and supports the ability to erase ThreadLocals.
 *
 * @implNote Based on the implementation of InnocuousForkJoinWorkerThread.
 */
public final class InnocuousThread extends Thread {
    private static final Unsafe UNSAFE;
    private static final ThreadGroup THREADGROUP;
    private static final AccessControlContext ACC;
    private static final long THREADLOCALS;
    private static final long INHERITABLETHREADLOCALS;
    private static final long INHERITEDACCESSCONTROLCONTEXT;

    public InnocuousThread(Runnable target) {
        super(THREADGROUP, target, "anInnocuousThread");
        UNSAFE.putOrderedObject(this, INHERITEDACCESSCONTROLCONTEXT, ACC);
        eraseThreadLocals();
    }

    @Override
    public ClassLoader getContextClassLoader() {
        // always report system class loader
        return ClassLoader.getSystemClassLoader();
    }

    @Override
    public void setUncaughtExceptionHandler(UncaughtExceptionHandler x) {
        // silently fail
    }

    @Override
    public void setContextClassLoader(ClassLoader cl) {
        throw new SecurityException("setContextClassLoader");
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

    /**
     * Drops all thread locals (and inherited thread locals).
     */
    public void eraseThreadLocals() {
        UNSAFE.putObject(this, THREADLOCALS, null);
        UNSAFE.putObject(this, INHERITABLETHREADLOCALS, null);
    }

    // Use Unsafe to access Thread group and ThreadGroup parent fields
    static {
        try {
            ACC = new AccessControlContext(new ProtectionDomain[] {
                new ProtectionDomain(null, null)
            });

            // Find and use topmost ThreadGroup as parent of new group
            UNSAFE = Unsafe.getUnsafe();
            Class<?> tk = Thread.class;
            Class<?> gk = ThreadGroup.class;

            THREADLOCALS = UNSAFE.objectFieldOffset
                (tk.getDeclaredField("threadLocals"));
            INHERITABLETHREADLOCALS = UNSAFE.objectFieldOffset
                (tk.getDeclaredField("inheritableThreadLocals"));
            INHERITEDACCESSCONTROLCONTEXT = UNSAFE.objectFieldOffset
                (tk.getDeclaredField("inheritedAccessControlContext"));

            long tg = UNSAFE.objectFieldOffset(tk.getDeclaredField("group"));
            long gp = UNSAFE.objectFieldOffset(gk.getDeclaredField("parent"));
            ThreadGroup group = (ThreadGroup)
                UNSAFE.getObject(Thread.currentThread(), tg);

            while (group != null) {
                ThreadGroup parent = (ThreadGroup)UNSAFE.getObject(group, gp);
                if (parent == null)
                    break;
                group = parent;
            }
            THREADGROUP = new ThreadGroup(group, "InnocuousThreadGroup");
        } catch (Exception e) {
            throw new Error(e);
        }
    }
}
