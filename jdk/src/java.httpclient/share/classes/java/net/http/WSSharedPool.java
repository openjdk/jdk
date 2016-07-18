/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General  License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General  License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General  License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.net.http;

import java.nio.Buffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static java.lang.System.Logger.Level.TRACE;
import static java.net.http.WSShared.duplicate;
import static java.net.http.WSUtils.logger;
import static java.util.Objects.requireNonNull;

final class WSSharedPool<T extends Buffer> implements Supplier<WSShared<T>> {

    private final Supplier<T> factory;
    private final BlockingQueue<T> queue;

    WSSharedPool(Supplier<T> factory, int maxPoolSize) {
        this.factory = requireNonNull(factory);
        this.queue = new LinkedBlockingQueue<>(maxPoolSize);
    }

    @Override
    public Pooled get() {
        T b = queue.poll();
        if (b == null) {
            logger.log(TRACE, "Pool {0} contains no free buffers", this);
            b = requireNonNull(factory.get());
        }
        Pooled buf = new Pooled(new AtomicInteger(1), b, duplicate(b));
        logger.log(TRACE, "Pool {0} created new buffer {1}", this, buf);
        return buf;
    }

    private void put(Pooled b) {
        assert b.disposed.get() && b.refCount.get() == 0
                : WSUtils.dump(b.disposed, b.refCount, b);
        b.shared.clear();
        boolean accepted = queue.offer(b.getShared());
        if (logger.isLoggable(TRACE)) {
            if (accepted) {
                logger.log(TRACE, "Pool {0} accepted {1}", this, b);
            } else {
                logger.log(TRACE, "Pool {0} discarded {1}", this, b);
            }
        }
    }

    @Override
    public String toString() {
        return super.toString() + "[queue.size=" + queue.size() + "]";
    }

    private final class Pooled extends WSShared<T> {

        private final AtomicInteger refCount;
        private final T shared;

        private Pooled(AtomicInteger refCount, T shared, T region) {
            super(region);
            this.refCount = refCount;
            this.shared = shared;
        }

        private T getShared() {
            return shared;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Pooled share(final int pos, final int limit) {
            synchronized (this) {
                T buffer = buffer();
                checkRegion(pos, limit, buffer);
                final int oldPos = buffer.position();
                final int oldLimit = buffer.limit();
                select(pos, limit, buffer);
                T slice = WSShared.slice(buffer);
                select(oldPos, oldLimit, buffer);
                referenceAndGetCount();
                Pooled buf = new Pooled(refCount, shared, slice);
                logger.log(TRACE, "Shared {0} from {1}", buf, this);
                return buf;
            }
        }

        @Override
        public void dispose() {
            logger.log(TRACE, "Disposed {0}", this);
            super.dispose();
            if (dereferenceAndGetCount() == 0) {
                WSSharedPool.this.put(this);
            }
        }

        private int referenceAndGetCount() {
            return refCount.updateAndGet(n -> {
                if (n != Integer.MAX_VALUE) {
                    return n + 1;
                } else {
                    throw new IllegalArgumentException
                            ("Too many references: " + this);
                }
            });
        }

        private int dereferenceAndGetCount() {
            return refCount.updateAndGet(n -> {
                if (n > 0) {
                    return n - 1;
                } else {
                    throw new InternalError();
                }
            });
        }

        @Override
        public String toString() {
            return WSUtils.toStringSimple(this) + "[" + WSUtils.toString(buffer)
                    + "[refCount=" + refCount + ", disposed=" + disposed + "]]";
        }
    }
}
