/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.util;

import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.api.pipe.TubeCloner;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.lang.ref.WeakReference;

/**
 * General-purpose object pool.
 *
 * <p>
 * In many parts of the runtime, we need to pool instances of objects that
 * are expensive to create (such as JAXB objects, StAX parsers, {@link Tube} instances.)
 *
 * <p>
 * This class provides a default implementation of such a pool.
 *
 * TODO: improve the implementation
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class Pool<T> {

    // volatile since multiple threads may access queue reference
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
    public final T take() {
        T t = getQueue().poll();
        if(t==null)
            return create();
        return t;
    }

    private ConcurrentLinkedQueue<T> getQueue() {
        WeakReference<ConcurrentLinkedQueue<T>> q = queue;
        if (q != null) {
            ConcurrentLinkedQueue<T> d = q.get();
            if (d != null)
                return d;
        }

        // overwrite the queue
        ConcurrentLinkedQueue<T> d = new ConcurrentLinkedQueue<T>();
        queue = new WeakReference<ConcurrentLinkedQueue<T>>(d);

        return d;
    }

    /**
     * Returns an object back to the pool.
     */
    public final void recycle(T t) {
        getQueue().offer(t);
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
    protected abstract T create();


    /**
     * JAXB {@link javax.xml.bind.Marshaller} pool.
     */
    public static final class Marshaller extends Pool<javax.xml.bind.Marshaller> {
        private final JAXBContext context;

        public Marshaller(JAXBContext context) {
            this.context = context;
        }

        @Override
        protected javax.xml.bind.Marshaller create() {
            try {
                return context.createMarshaller();
            } catch (JAXBException e) {
                // impossible
                throw new AssertionError(e);
            }
        }
    }

    /**
     * JAXB {@link javax.xml.bind.Marshaller} pool.
     */
    public static final class Unmarshaller extends Pool<javax.xml.bind.Unmarshaller> {
        private final JAXBContext context;

        public Unmarshaller(JAXBContext context) {
            this.context = context;
        }

        @Override
        protected javax.xml.bind.Unmarshaller create() {
            try {
                return context.createUnmarshaller();
            } catch (JAXBException e) {
                // impossible
                throw new AssertionError(e);
            }
        }
    }

    /**
     * {@link Tube} pool.
     */
    public static final class TubePool extends Pool<Tube> {
        private final Tube master;

        public TubePool(Tube master) {
            this.master = master;
            recycle(master);    // we'll use master as a part of the pool, too.
        }

        @Override
        protected Tube create() {
            return TubeCloner.clone(master);
        }

        /**
         *
         * @return master tubeline from pool
         * @deprecated Expected to be used in rare cases where access to master
         * tubeline is required and safe, such as Stub.close()."
         */
        @Deprecated()
        public final Tube takeMaster() {
            return master;
        }

    }
}
