/*
 * Copyright (c) 2000, 2009, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.ch;

import java.lang.ref.SoftReference;
import java.lang.reflect.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.*;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;
import sun.misc.Unsafe;
import sun.misc.Cleaner;
import sun.security.action.GetPropertyAction;


class Util {


    // -- Caches --

    // The number of temp buffers in our pool
    private static final int TEMP_BUF_POOL_SIZE = 3;

    // Per-thread soft cache of the last temporary direct buffer
    private static ThreadLocal<SoftReference<ByteBuffer>>[] bufferPool;

    @SuppressWarnings("unchecked")
    static ThreadLocal<SoftReference<ByteBuffer>>[] createThreadLocalBufferPool() {
        return new ThreadLocal[TEMP_BUF_POOL_SIZE];
    }

    static {
        bufferPool = createThreadLocalBufferPool();
        for (int i=0; i<TEMP_BUF_POOL_SIZE; i++)
            bufferPool[i] = new ThreadLocal<SoftReference<ByteBuffer>>();
    }

    static ByteBuffer getTemporaryDirectBuffer(int size) {
        ByteBuffer buf = null;
        // Grab a buffer if available
        for (int i=0; i<TEMP_BUF_POOL_SIZE; i++) {
            SoftReference<ByteBuffer> ref = bufferPool[i].get();
            if ((ref != null) && ((buf = ref.get()) != null) &&
                (buf.capacity() >= size)) {
                buf.rewind();
                buf.limit(size);
                bufferPool[i].set(null);
                return buf;
            }
        }

        // Make a new one
        return ByteBuffer.allocateDirect(size);
    }

    static void releaseTemporaryDirectBuffer(ByteBuffer buf) {
        if (buf == null)
            return;
        // Put it in an empty slot if such exists
        for (int i=0; i<TEMP_BUF_POOL_SIZE; i++) {
            SoftReference<ByteBuffer> ref = bufferPool[i].get();
            if ((ref == null) || (ref.get() == null)) {
                bufferPool[i].set(new SoftReference<ByteBuffer>(buf));
                return;
            }
        }
        // Otherwise replace a smaller one in the cache if such exists
        for (int i=0; i<TEMP_BUF_POOL_SIZE; i++) {
            SoftReference<ByteBuffer> ref = bufferPool[i].get();
            ByteBuffer inCacheBuf = ref.get();
            if ((inCacheBuf == null) || (buf.capacity() > inCacheBuf.capacity())) {
                bufferPool[i].set(new SoftReference<ByteBuffer>(buf));
                return;
            }
        }

        // release memory
       ((DirectBuffer)buf).cleaner().clean();
    }

    private static class SelectorWrapper {
        private Selector sel;
        private SelectorWrapper (Selector sel) {
            this.sel = sel;
            Cleaner.create(this, new Closer(sel));
        }
        private static class Closer implements Runnable {
            private Selector sel;
            private Closer (Selector sel) {
                this.sel = sel;
            }
            public void run () {
                try {
                    sel.close();
                } catch (Throwable th) {
                    throw new Error(th);
                }
            }
        }
        public Selector get() { return sel;}
    }

    // Per-thread cached selector
    private static ThreadLocal<SoftReference<SelectorWrapper>> localSelector
        = new ThreadLocal<SoftReference<SelectorWrapper>>();
    // Hold a reference to the selWrapper object to prevent it from
    // being cleaned when the temporary selector wrapped is on lease.
    private static ThreadLocal<SelectorWrapper> localSelectorWrapper
        = new ThreadLocal<SelectorWrapper>();

    // When finished, invoker must ensure that selector is empty
    // by cancelling any related keys and explicitly releasing
    // the selector by invoking releaseTemporarySelector()
    static Selector getTemporarySelector(SelectableChannel sc)
        throws IOException
    {
        SoftReference<SelectorWrapper> ref = localSelector.get();
        SelectorWrapper selWrapper = null;
        Selector sel = null;
        if (ref == null
            || ((selWrapper = ref.get()) == null)
            || ((sel = selWrapper.get()) == null)
            || (sel.provider() != sc.provider())) {
            sel = sc.provider().openSelector();
            selWrapper = new SelectorWrapper(sel);
            localSelector.set(new SoftReference<SelectorWrapper>(selWrapper));
        }
        localSelectorWrapper.set(selWrapper);
        return sel;
    }

    static void releaseTemporarySelector(Selector sel)
        throws IOException
    {
        // Selector should be empty
        sel.selectNow();                // Flush cancelled keys
        assert sel.keys().isEmpty() : "Temporary selector not empty";
        localSelectorWrapper.set(null);
    }


    // -- Random stuff --

    static ByteBuffer[] subsequence(ByteBuffer[] bs, int offset, int length) {
        if ((offset == 0) && (length == bs.length))
            return bs;
        int n = length;
        ByteBuffer[] bs2 = new ByteBuffer[n];
        for (int i = 0; i < n; i++)
            bs2[i] = bs[offset + i];
        return bs2;
    }

    static <E> Set<E> ungrowableSet(final Set<E> s) {
        return new Set<E>() {

                public int size()                 { return s.size(); }
                public boolean isEmpty()          { return s.isEmpty(); }
                public boolean contains(Object o) { return s.contains(o); }
                public Object[] toArray()         { return s.toArray(); }
                public <T> T[] toArray(T[] a)     { return s.toArray(a); }
                public String toString()          { return s.toString(); }
                public Iterator<E> iterator()     { return s.iterator(); }
                public boolean equals(Object o)   { return s.equals(o); }
                public int hashCode()             { return s.hashCode(); }
                public void clear()               { s.clear(); }
                public boolean remove(Object o)   { return s.remove(o); }

                public boolean containsAll(Collection<?> coll) {
                    return s.containsAll(coll);
                }
                public boolean removeAll(Collection<?> coll) {
                    return s.removeAll(coll);
                }
                public boolean retainAll(Collection<?> coll) {
                    return s.retainAll(coll);
                }

                public boolean add(E o){
                    throw new UnsupportedOperationException();
                }
                public boolean addAll(Collection<? extends E> coll) {
                    throw new UnsupportedOperationException();
                }

        };
    }


    // -- Unsafe access --

    private static Unsafe unsafe = Unsafe.getUnsafe();

    private static byte _get(long a) {
        return unsafe.getByte(a);
    }

    private static void _put(long a, byte b) {
        unsafe.putByte(a, b);
    }

    static void erase(ByteBuffer bb) {
        unsafe.setMemory(((DirectBuffer)bb).address(), bb.capacity(), (byte)0);
    }

    static Unsafe unsafe() {
        return unsafe;
    }

    private static int pageSize = -1;

    static int pageSize() {
        if (pageSize == -1)
            pageSize = unsafe().pageSize();
        return pageSize;
    }

    private static volatile Constructor directByteBufferConstructor = null;

    private static void initDBBConstructor() {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
                public Void run() {
                    try {
                        Class<?> cl = Class.forName("java.nio.DirectByteBuffer");
                        Constructor ctor = cl.getDeclaredConstructor(
                            new Class[] { int.class,
                                          long.class,
                                          Runnable.class });
                        ctor.setAccessible(true);
                        directByteBufferConstructor = ctor;
                    } catch (ClassNotFoundException x) {
                        throw new InternalError();
                    } catch (NoSuchMethodException x) {
                        throw new InternalError();
                    } catch (IllegalArgumentException x) {
                        throw new InternalError();
                    } catch (ClassCastException x) {
                        throw new InternalError();
                    }
                    return null;
                }});
    }

    static MappedByteBuffer newMappedByteBuffer(int size, long addr,
                                                Runnable unmapper)
    {
        MappedByteBuffer dbb;
        if (directByteBufferConstructor == null)
            initDBBConstructor();
        try {
            dbb = (MappedByteBuffer)directByteBufferConstructor.newInstance(
              new Object[] { new Integer(size),
                             new Long(addr),
                             unmapper });
        } catch (InstantiationException e) {
            throw new InternalError();
        } catch (IllegalAccessException e) {
            throw new InternalError();
        } catch (InvocationTargetException e) {
            throw new InternalError();
        }
        return dbb;
    }

    private static volatile Constructor directByteBufferRConstructor = null;

    private static void initDBBRConstructor() {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
                public Void run() {
                    try {
                        Class<?> cl = Class.forName("java.nio.DirectByteBufferR");
                        Constructor ctor = cl.getDeclaredConstructor(
                            new Class[] { int.class,
                                          long.class,
                                          Runnable.class });
                        ctor.setAccessible(true);
                        directByteBufferRConstructor = ctor;
                    } catch (ClassNotFoundException x) {
                        throw new InternalError();
                    } catch (NoSuchMethodException x) {
                        throw new InternalError();
                    } catch (IllegalArgumentException x) {
                        throw new InternalError();
                    } catch (ClassCastException x) {
                        throw new InternalError();
                    }
                    return null;
                }});
    }

    static MappedByteBuffer newMappedByteBufferR(int size, long addr,
                                                 Runnable unmapper)
    {
        MappedByteBuffer dbb;
        if (directByteBufferRConstructor == null)
            initDBBRConstructor();
        try {
            dbb = (MappedByteBuffer)directByteBufferRConstructor.newInstance(
              new Object[] { new Integer(size),
                             new Long(addr),
                             unmapper });
        } catch (InstantiationException e) {
            throw new InternalError();
        } catch (IllegalAccessException e) {
            throw new InternalError();
        } catch (InvocationTargetException e) {
            throw new InternalError();
        }
        return dbb;
    }


    // -- Bug compatibility --

    private static volatile String bugLevel = null;

    static boolean atBugLevel(String bl) {              // package-private
        if (bugLevel == null) {
            if (!sun.misc.VM.isBooted())
                return false;
            String value = AccessController.doPrivileged(
                new GetPropertyAction("sun.nio.ch.bugLevel"));
            bugLevel = (value != null) ? value : "";
        }
        return bugLevel.equals(bl);
    }



    // -- Initialization --

    private static boolean loaded = false;

    static void load() {
        synchronized (Util.class) {
            if (loaded)
                return;
            loaded = true;
            java.security.AccessController
                .doPrivileged(new sun.security.action.LoadLibraryAction("net"));
            java.security.AccessController
                .doPrivileged(new sun.security.action.LoadLibraryAction("nio"));
            // IOUtil must be initialized; Its native methods are called from
            // other places in native nio code so they must be set up.
            IOUtil.initIDs();
        }
    }

}
