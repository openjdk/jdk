/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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

package java.util.zip;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.lang.ref.Cleaner.Cleanable;
import jdk.internal.ref.CleanerFactory;

/**
 * A reference to the native zlib's z_stream structure. It also
 * serves as the "cleaner" to clean up the native resource when
 * the deflater or infalter is ended, closed or cleaned.
 */
class ZStreamRef implements Runnable {

    private LongConsumer end;
    private long address;
    private final Cleanable cleanable;

    private ZStreamRef (Object owner, LongSupplier addr, LongConsumer end) {
        this.cleanable = CleanerFactory.cleaner().register(owner, this);
        this.end = end;
        this.address = addr.getAsLong();
    }

    long address() {
        return address;
    }

    void clean() {
        cleanable.clean();
    }

    public synchronized void run() {
        long addr = address;
        address = 0;
        if (addr != 0) {
            end.accept(addr);
        }
    }

    private ZStreamRef (LongSupplier addr, LongConsumer end) {
        this.cleanable = null;
        this.end = end;
        this.address = addr.getAsLong();
    }

    /*
     * If {@code Inflater/Deflater} has been subclassed and the {@code end} method
     * is overridden, uses {@code finalizer} mechanism for resource cleanup. So
     * {@code end} method can be called when the {@code Inflater/Deflater} is
     * unreachable. This mechanism will be removed when the {@code finalize} method
     * is removed from {@code Inflater/Deflater}.
     */
    static ZStreamRef get(Object owner, LongSupplier addr, LongConsumer end) {
        Class<?> clz = owner.getClass();
        while (clz != Deflater.class && clz != Inflater.class) {
            try {
                clz.getDeclaredMethod("end");
                return new FinalizableZStreamRef(owner, addr, end);
            } catch (NoSuchMethodException nsme) {}
            clz = clz.getSuperclass();
        }
        return new ZStreamRef(owner, addr, end);
    }

    private static class FinalizableZStreamRef extends ZStreamRef {
        final Object owner;

        FinalizableZStreamRef (Object owner, LongSupplier addr, LongConsumer end) {
            super(addr, end);
            this.owner = owner;
        }

        @Override
        void clean() {
            run();
        }

        @Override
        @SuppressWarnings("deprecation")
        protected void finalize() {
            if (owner instanceof Inflater)
                ((Inflater)owner).end();
            else
                ((Deflater)owner).end();
        }
    }
}
