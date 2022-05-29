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

/**
 * Helper class for creating Thread buidlers.
 */
class ThreadBuilders {
    private ThreadBuilders() { }

    /**
     * Returns a builder to create virtual threads that use the given scheduler.
     *
     * Tests using this method need to open java.base/java.lang.
     *
     * @throws UnsupportedOperationException if there is no support for custom schedulers
     */
    static Thread.Builder.OfVirtual virtualThreadBuilder(Executor scheduler) {
        Thread.Builder.OfVirtual builder = Thread.ofVirtual();
        try {
            Class<?> clazz = Class.forName("java.lang.ThreadBuilders$VirtualThreadBuilder");
            Constructor<?> ctor = clazz.getDeclaredConstructor(Executor.class);
            ctor.setAccessible(true);
            return (Thread.Builder.OfVirtual) ctor.newInstance(scheduler);
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
     * Returns a builder to create virtual threads that use the given scheduler
     * or {@code null} if there is no support for custom schedulers.
     *
     * Tests using this method need to open java.base/java.lang.
     */
    static Thread.Builder.OfVirtual virtualThreadBuilderOrNull(Executor scheduler) {
        try {
            return virtualThreadBuilder(scheduler);
        } catch (UnsupportedOperationException e) {
            return null;
        }
    }

}
