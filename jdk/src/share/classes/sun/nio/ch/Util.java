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
    private static final int TEMP_BUF_POOL_SIZE = 8;

    // Per-thread cache of temporary direct buffers
    private static ThreadLocal<BufferCache> bufferCache =
        new ThreadLocal<BufferCache>()
    {
        @Override
        protected BufferCache initialValue() {
            return new BufferCache();
        }
    };

    /**
     * A simple cache of direct buffers.
     */
    private static class BufferCache {
        // the array of buffers
        private ByteBuffer[] buffers;

        // the number of buffers in the cache
        private int count;

        // the index of the first valid buffer (undefined if count == 0)
        private int start;

        private int next(int i) {
            return (i + 1) % TEMP_BUF_POOL_SIZE;
        }

        BufferCache() {
            buffers = new ByteBuffer[TEMP_BUF_POOL_SIZE];
        }

        /**
         * Removes and returns a buffer from the cache of at least the given
         * size (or null if no suitable buffer is found).
         */
        ByteBuffer get(int size) {
            if (count == 0)
                return null;  // cache is empty

            ByteBuffer[] buffers = this.buffers;

            // search for suitable buffer (often the first buffer will do)
            ByteBuffer buf = buffers[start];
            if (buf.capacity() < size) {
                buf = null;
                int i = start;
                while ((i = next(i)) != start) {
                    ByteBuffer bb = buffers[i];
                    if (bb == null)
                        break;
                    if (bb.capacity() >= size) {
                        buf = bb;
                        break;
                    }
                }
                if (buf == null)
                    return null;
                // move first element to here to avoid re-packing
                buffers[i] = buffers[start];
            }

            // remove first element
            buffers[start] = null;
            start = next(start);
            count--;

            // prepare the buffer and return it
            buf.rewind();
            buf.limit(size);
            return buf;
        }

        boolean offerFirst(ByteBuffer buf) {
            if (count >= TEMP_BUF_POOL_SIZE) {
                return false;
            } else {
                start = (start + TEMP_BUF_POOL_SIZE - 1) % TEMP_BUF_POOL_SIZE;
                buffers[start] = buf;
                count++;
                return true;
            }
        }

        boolean offerLast(ByteBuffer buf) {
            if (count >= TEMP_BUF_POOL_SIZE) {
                return false;
            } else {
                int next = (start + count) % TEMP_BUF_POOL_SIZE;
                buffers[next] = buf;
                count++;
                return true;
            }
        }

        boolean isEmpty() {
            return count == 0;
        }

        ByteBuffer removeFirst() {
            assert count > 0;
            ByteBuffer buf = buffers[start];
            buffers[start] = null;
            start = next(start);
            count--;
            return buf;
        }
    }

    /**
     * Returns a temporary buffer of at least the given size
     */
    static ByteBuffer getTemporaryDirectBuffer(int size) {
        BufferCache cache = bufferCache.get();
        ByteBuffer buf = cache.get(size);
        if (buf != null) {
            return buf;
        } else {
            // No suitable buffer in the cache so we need to allocate a new
            // one. To avoid the cache growing then we remove the first
            // buffer from the cache and free it.
            if (!cache.isEmpty()) {
                buf = cache.removeFirst();
                free(buf);
            }
            return ByteBuffer.allocateDirect(size);
        }
    }

    /**
     * Releases a temporary buffer by returning to the cache or freeing it.
     */
    static void releaseTemporaryDirectBuffer(ByteBuffer buf) {
        offerFirstTemporaryDirectBuffer(buf);
    }

    /**
     * Releases a temporary buffer by returning to the cache or freeing it. If
     * returning to the cache then insert it at the start so that it is
     * likely to be returned by a subsequent call to getTemporaryDirectBuffer.
     */
    static void offerFirstTemporaryDirectBuffer(ByteBuffer buf) {
        assert buf != null;
        BufferCache cache = bufferCache.get();
        if (!cache.offerFirst(buf)) {
            // cache is full
            free(buf);
        }
    }

    /**
     * Releases a temporary buffer by returning to the cache or freeing it. If
     * returning to the cache then insert it at the end. This makes it
     * suitable for scatter/gather operations where the buffers are returned to
     * cache in same order that they were obtained.
     */
    static void offerLastTemporaryDirectBuffer(ByteBuffer buf) {
        assert buf != null;
        BufferCache cache = bufferCache.get();
        if (!cache.offerLast(buf)) {
            // cache is full
            free(buf);
        }
    }

    /**
     * Frees the memory for the given direct buffer
     */
    private static void free(ByteBuffer buf) {
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
