/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.hotspot;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Iterator;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This class manages the set of metadata roots that must be scanned during garbage collection.
 * Because of class redefinition Method* and ConstantPool* can be freed if they don't appear to be
 * in use so they must be tracked when there are live references to them from Java.
 *
 * The general theory of operation is that all {@link MetaspaceWrapperObject}s are created by
 * calling into the VM which calls back out to actually create the wrapper instance. During the call
 * the VM keeps the metadata reference alive through the use of metadata handles. Once the call
 * completes the wrapper object is registered here and will be scanned during metadata scanning. The
 * weakness of the reference to the wrapper object allows them to be reclaimed when they are no
 * longer used.
 *
 */
class HotSpotJVMCIMetaAccessContext {

    /**
     * The set of currently live contexts used for tracking of live metadata. Examined from the VM
     * during garbage collection.
     */
    private static WeakReference<?>[] allContexts = new WeakReference<?>[0];

    /**
     * This is a chunked list of metadata roots. It can be read from VM native code so it's been
     * marked volatile to ensure the order of updates are respected.
     */
    private volatile Object[] metadataRoots;

    private ChunkedList<WeakReference<MetaspaceWrapperObject>> list = new ChunkedList<>();

    /**
     * The number of weak references freed since the last time the list was shrunk.
     */
    private int freed;

    /**
     * The {@link ReferenceQueue} tracking the weak references created by this context.
     */
    private final ReferenceQueue<MetaspaceWrapperObject> queue = new ReferenceQueue<>();

    static synchronized void add(HotSpotJVMCIMetaAccessContext context) {
        for (int i = 0; i < allContexts.length; i++) {
            if (allContexts[i] == null || allContexts[i].get() == null) {
                allContexts[i] = new WeakReference<>(context);
                return;
            }
        }
        int index = allContexts.length;
        allContexts = Arrays.copyOf(allContexts, index + 2);
        allContexts[index] = new WeakReference<>(context);
    }

    HotSpotJVMCIMetaAccessContext() {
        add(this);
    }

    /**
     * Periodically trim the list of tracked metadata. A new list is created to replace the old to
     * avoid concurrent scanning issues.
     */
    private void clean() {
        Reference<?> ref = queue.poll();
        if (ref == null) {
            return;
        }
        while (ref != null) {
            freed++;
            ref = queue.poll();
        }
        if (freed > list.size() / 2) {
            ChunkedList<WeakReference<MetaspaceWrapperObject>> newList = new ChunkedList<>();
            for (WeakReference<MetaspaceWrapperObject> element : list) {
                /*
                 * The referent could become null anywhere in here but it doesn't matter. It will
                 * get cleaned up next time.
                 */
                if (element != null && element.get() != null) {
                    newList.add(element);
                }
            }
            list = newList;
            metadataRoots = list.getHead();
            freed = 0;
        }
    }

    /**
     * Add a {@link MetaspaceWrapperObject} to tracked by the GC. It's assumed that the caller is
     * responsible for keeping the reference alive for the duration of the call. Once registration
     * is complete then the VM will ensure it's kept alive.
     *
     * @param metaspaceObject
     */

    public synchronized void add(MetaspaceWrapperObject metaspaceObject) {
        clean();
        list.add(new WeakReference<>(metaspaceObject, queue));
        if (list.getHead() != metadataRoots) {
            /*
             * The list enlarged so update the head.
             */
            metadataRoots = list.getHead();
        }
        assert isRegistered(metaspaceObject);
    }

    protected ResolvedJavaType createClass(Class<?> javaClass) {
        if (javaClass.isPrimitive()) {
            JavaKind kind = JavaKind.fromJavaClass(javaClass);
            return new HotSpotResolvedPrimitiveType(kind);
        } else {
            return new HotSpotResolvedObjectTypeImpl(javaClass, this);
        }
    }

    private final ClassValue<WeakReference<ResolvedJavaType>> resolvedJavaType = new ClassValue<>() {
        @Override
        protected WeakReference<ResolvedJavaType> computeValue(Class<?> type) {
            return new WeakReference<>(createClass(type));
        }
    };

    /**
     * Gets the JVMCI mirror for a {@link Class} object.
     *
     * @return the {@link ResolvedJavaType} corresponding to {@code javaClass}
     */
    public ResolvedJavaType fromClass(Class<?> javaClass) {
        ResolvedJavaType javaType = null;
        while (javaType == null) {
            WeakReference<ResolvedJavaType> type = resolvedJavaType.get(javaClass);
            javaType = type.get();
            if (javaType == null) {
                /*
                 * If the referent has become null, clear out the current value and let computeValue
                 * above create a new value. Reload the value in a loop because in theory the
                 * WeakReference referent can be reclaimed at any point.
                 */
                resolvedJavaType.remove(javaClass);
            }
        }
        return javaType;
    }

    /**
     * A very simple append only chunked list implementation.
     */
    static class ChunkedList<T> implements Iterable<T> {
        private static final int CHUNK_SIZE = 32;

        private static final int NEXT_CHUNK_INDEX = CHUNK_SIZE - 1;

        private Object[] head;
        private int index;
        private int size;

        ChunkedList() {
            head = new Object[CHUNK_SIZE];
            index = 0;
        }

        void add(T element) {
            if (index == NEXT_CHUNK_INDEX) {
                Object[] newHead = new Object[CHUNK_SIZE];
                newHead[index] = head;
                head = newHead;
                index = 0;
            }
            head[index++] = element;
            size++;
        }

        Object[] getHead() {
            return head;
        }

        @Override
        public Iterator<T> iterator() {
            return new ChunkIterator<>();
        }

        int size() {
            return size;
        }

        class ChunkIterator<V> implements Iterator<V> {

            ChunkIterator() {
                currentChunk = head;
                currentIndex = -1;
                next = findNext();
            }

            Object[] currentChunk;
            int currentIndex;
            V next;

            @SuppressWarnings("unchecked")
            V findNext() {
                V result;
                do {
                    currentIndex++;
                    if (currentIndex == NEXT_CHUNK_INDEX) {
                        currentChunk = (Object[]) currentChunk[currentIndex];
                        currentIndex = 0;
                        if (currentChunk == null) {
                            return null;
                        }
                    }
                    result = (V) currentChunk[currentIndex];
                } while (result == null);
                return result;
            }

            @Override
            public boolean hasNext() {
                return next != null;
            }

            @Override
            public V next() {
                V result = next;
                next = findNext();
                return result;
            }

        }

    }

    synchronized boolean isRegistered(MetaspaceWrapperObject wrapper) {
        for (WeakReference<MetaspaceWrapperObject> m : list) {
            if (m != null && m.get() == wrapper) {
                return true;
            }
        }
        return false;
    }
}
