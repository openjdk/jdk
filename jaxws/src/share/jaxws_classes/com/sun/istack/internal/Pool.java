/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.istack.internal;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.lang.ref.WeakReference;

/**
 * Pool of reusable objects that are indistinguishable from each other,
 * such as JAXB marshallers.
 *
 * @author Kohsuke Kawaguchi
 */
public interface Pool<T> {

    /**
     * Gets a new object from the pool.
     *
     * <p>
     * If no object is available in the pool, this method creates a new one.
     */
    @NotNull T take();

    /**
     * Returns an object back to the pool.
     */
    void recycle(@NotNull T t);

    /**
     * Default implementation that uses {@link ConcurrentLinkedQueue}
     * as the data store.
     *
     * <h2>Note for Implementors</h2>
     * <p>
     * Don't rely on the fact that this class extends from {@link ConcurrentLinkedQueue}.
     */
    public abstract class Impl<T> implements Pool<T> {

        private volatile WeakReference<ConcurrentLinkedQueue<T>> queue;

        /**
         * Gets a new object from the pool.
         *
         * <p>
         * If no object is available in the pool, this method creates a new one.
         *
         * @return
         *      always non-null.
         */
        public final @NotNull T take() {
            T t = getQueue().poll();
            if(t==null) {
                return create();
            }
            return t;
        }

        /**
         * Returns an object back to the pool.
         */
        public final void recycle(T t) {
            getQueue().offer(t);
        }

        private ConcurrentLinkedQueue<T> getQueue() {
            WeakReference<ConcurrentLinkedQueue<T>> q = queue;
            if (q != null) {
                ConcurrentLinkedQueue<T> d = q.get();
                if (d != null) {
                    return d;
                }
            }
            // overwrite the queue
            ConcurrentLinkedQueue<T> d = new ConcurrentLinkedQueue<T>();
            queue = new WeakReference<ConcurrentLinkedQueue<T>>(d);

            return d;
        }

        /**
         * Creates a new instance of object.
         *
         * <p>
         * This method is used when someone wants to
         * {@link #take() take} an object from an empty pool.
         *
         * <p>
         * Also note that multiple threads may call this method
         * concurrently.
         */
        protected abstract @NotNull T create();
    }
}
