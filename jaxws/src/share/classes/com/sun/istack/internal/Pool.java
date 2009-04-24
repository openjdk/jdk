/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package com.sun.istack.internal;

import java.util.concurrent.ConcurrentLinkedQueue;

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
    public abstract class Impl<T> extends ConcurrentLinkedQueue<T> implements Pool<T> {
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
            T t = super.poll();
            if(t==null)
                return create();
            return t;
        }

        /**
         * Returns an object back to the pool.
         */
        public final void recycle(T t) {
            super.offer(t);
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
