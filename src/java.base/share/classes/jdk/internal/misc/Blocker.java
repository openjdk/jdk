/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.concurrent.ForkJoinPool;
import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;

/**
 * Defines static methods to mark the beginning and end of a possibly blocking
 * operation. The methods are intended to be used with try-finally as follows:
 * {@snippet lang=java :
 *     long comp = Blocker.begin();
 *     try {
 *         // blocking operation
 *     } finally {
 *         Blocker.end(comp);
 *     }
 * }
 * If invoked from a virtual thread and the underlying carrier thread is a
 * CarrierThread then the code in the block runs as if it were in run in
 * ForkJoinPool.ManagedBlocker. This means the pool can be expanded to support
 * additional parallelism during the blocking operation.
 */
public class Blocker {
    private static final JavaLangAccess JLA;
    static {
        JLA = SharedSecrets.getJavaLangAccess();
        if (JLA == null) {
            throw new InternalError("JavaLangAccess not setup");
        }
    }

    private Blocker() { }

    private static Thread currentCarrierThread() {
        return JLA.currentCarrierThread();
    }

    /**
     * Marks the beginning of possibly blocking operation.
     */
    public static long begin() {
        if (VM.isBooted()
                && currentCarrierThread() instanceof CarrierThread ct && !ct.inBlocking()) {
            ct.beginBlocking();
            long comp = ForkJoinPools.beginCompensatedBlock(ct.getPool());
            assert currentCarrierThread() == ct;
            return comp;
        }

        return 0;
    }

    /**
     * Marks the end an operation that may have blocked.
     */
    public static void end(long post) {
        if (post > 0) {
            assert currentCarrierThread() instanceof CarrierThread ct && ct.inBlocking();
            CarrierThread ct = (CarrierThread) currentCarrierThread();
            ForkJoinPools.endCompensatedBlock(ct.getPool(), post);
            ct.endBlocking();
        }
    }

    /**
     * Defines static methods to invoke non-public ForkJoinPool methods.
     */
    private static class ForkJoinPools {
        private static final Unsafe U = Unsafe.getUnsafe();
        private static final MethodHandle beginCompensatedBlock, endCompensatedBlock;
        static {
            try {
                PrivilegedExceptionAction<MethodHandles.Lookup> pa = () ->
                    MethodHandles.privateLookupIn(ForkJoinPool.class, MethodHandles.lookup());
                @SuppressWarnings("removal")
                MethodHandles.Lookup l = AccessController.doPrivileged(pa);
                MethodType methodType = MethodType.methodType(long.class);
                beginCompensatedBlock = l.findVirtual(ForkJoinPool.class, "beginCompensatedBlock", methodType);
                methodType = MethodType.methodType(void.class, long.class);
                endCompensatedBlock = l.findVirtual(ForkJoinPool.class, "endCompensatedBlock", methodType);

            } catch (Exception e) {
                throw new InternalError(e);
            }
        }
        static long beginCompensatedBlock(ForkJoinPool pool) {
            try {
                return (long) beginCompensatedBlock.invoke(pool);
            } catch (Throwable e) {
                U.throwException(e);
            }
            return 0;
        }
        static void endCompensatedBlock(ForkJoinPool pool, long post) {
            try {
                endCompensatedBlock.invoke(pool, post);
            } catch (Throwable e) {
                U.throwException(e);
            }
        }
    }
}
