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

package com.sun.corba.se.impl.transport;

import sun.misc.Unsafe;
import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * A thread that has it's thread locals, and inheritable thread
 * locals erased on construction.
 */
public class ManagedLocalsThread extends Thread {
    private static final Unsafe UNSAFE;
    private static final long THREAD_LOCALS;
    private static final long INHERITABLE_THREAD_LOCALS;

    public ManagedLocalsThread () {
        super();
    }

    public ManagedLocalsThread(String  name) {
        super(name);
        eraseThreadLocals();
    }
    public ManagedLocalsThread(Runnable target) {
        super(target);
        eraseThreadLocals();
    }

    public ManagedLocalsThread(Runnable target, String name) {
        super(target, name);
        eraseThreadLocals();
    }

    public ManagedLocalsThread(ThreadGroup group, Runnable target, String name) {
        super(group, target, name);
        eraseThreadLocals();
    }

    public ManagedLocalsThread(ThreadGroup group, String name) {
        super(group, name);
        eraseThreadLocals();
    }

    /**
     * Drops all thread locals (and inherited thread locals).
     */
    public final void eraseThreadLocals() {
                UNSAFE.putObject(this, THREAD_LOCALS, null);
                UNSAFE.putObject(this, INHERITABLE_THREAD_LOCALS, null);
    }

    private static Unsafe getUnsafe() {
        PrivilegedAction<Unsafe> pa = () -> {
            Class<?> unsafeClass = sun.misc.Unsafe.class;
            try {
                Field f = unsafeClass.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                return (Unsafe) f.get(null);
            } catch (Exception e) {
                throw new Error(e);
            }
        };
        return AccessController.doPrivileged(pa);
    }

    private static long getThreadFieldOffset(String fieldName) {
        PrivilegedAction<Long> pa = () -> {
            Class<?> t = Thread.class;
            long fieldOffset;
            try {
                fieldOffset = UNSAFE.objectFieldOffset(t
                        .getDeclaredField("inheritableThreadLocals"));
            } catch (Exception e) {
                throw new Error(e);
            }
            return fieldOffset;
        };
        return AccessController.doPrivileged(pa);
    }

    static {
        UNSAFE = getUnsafe();
        try {
            THREAD_LOCALS = getThreadFieldOffset("threadLocals");
            INHERITABLE_THREAD_LOCALS = getThreadFieldOffset("inheritableThreadLocals");
        } catch (Exception e) {
            throw new Error(e);
        }
    }
}
