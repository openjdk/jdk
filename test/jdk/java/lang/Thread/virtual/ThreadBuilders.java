/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Helper class for creating Thread buidlers.
 *
 * Tests using this class need to open java.base/java.lang.
 */
class ThreadBuilders {
    private ThreadBuilders() { }

    private static final Constructor<?> VTBUILDER_CTOR;
    static {
        try {
            Class<?> clazz = Class.forName("java.lang.ThreadBuilders$VirtualThreadBuilder");
            Constructor<?> ctor = clazz.getDeclaredConstructor(Executor.class);
            ctor.setAccessible(true);
            VTBUILDER_CTOR = ctor;
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }

    /**
     * Returns a builder to create virtual threads that use the given scheduler.
     * @throws UnsupportedOperationException if custom schedulers are not supported
     */
    static Thread.Builder.OfVirtual virtualThreadBuilder(Executor scheduler) {
        try {
            return (Thread.Builder.OfVirtual) VTBUILDER_CTOR.newInstance(scheduler);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Return true if custom schedulers are supported.
     */
    static boolean supportsCustomScheduler() {
        try (var pool = Executors.newCachedThreadPool()) {
            try {
                virtualThreadBuilder(pool);
                return true;
            } catch (UnsupportedOperationException e) {
                return false;
            }
        }
    }
}
