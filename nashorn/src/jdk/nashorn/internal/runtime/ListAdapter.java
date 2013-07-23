/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.runtime;

import java.util.AbstractList;
import java.util.Deque;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.RandomAccess;
import jdk.nashorn.internal.runtime.linker.Bootstrap;
import jdk.nashorn.internal.runtime.linker.InvokeByName;

/**
 * An adapter that can wrap any ECMAScript Array-like object (that adheres to the array rules for the property
 * {@code length} and having conforming {@code push}, {@code pop}, {@code shift}, {@code unshift}, and {@code splice}
 * methods) and expose it as both a Java list and double-ended queue. While script arrays aren't necessarily efficient
 * as dequeues, it's still slightly more efficient to be able to translate dequeue operations into pushes, pops, shifts,
 * and unshifts, than to blindly translate all list's add/remove operations into splices. Also, it is conceivable that a
 * custom script object that implements an Array-like API can have a background data representation that is optimized
 * for dequeue-like access. Note that with ECMAScript arrays, {@code push} and {@code pop} operate at the end of the
 * array, while in Java {@code Deque} they operate on the front of the queue and as such the Java dequeue
 * {@link #push(Object)} and {@link #pop()} operations will translate to {@code unshift} and {@code shift} script
 * operations respectively, while {@link #addLast(Object)} and {@link #removeLast()} will translate to {@code push} and
 * {@code pop}.
 */
public class ListAdapter extends AbstractList<Object> implements RandomAccess, Deque<Object> {
    // These add to the back and front of the list
    private static final InvokeByName PUSH    = new InvokeByName("push",    ScriptObject.class, void.class, Object.class);
    private static final InvokeByName UNSHIFT = new InvokeByName("unshift", ScriptObject.class, void.class, Object.class);

    // These remove from the back and front of the list
    private static final InvokeByName POP   = new InvokeByName("pop",   ScriptObject.class, Object.class);
    private static final InvokeByName SHIFT = new InvokeByName("shift", ScriptObject.class, Object.class);

    // These insert and remove in the middle of the list
    private static final InvokeByName SPLICE_ADD    = new InvokeByName("splice", ScriptObject.class, void.class, int.class, int.class, Object.class);
    private static final InvokeByName SPLICE_REMOVE = new InvokeByName("splice", ScriptObject.class, void.class, int.class, int.class);

    private final ScriptObject obj;

    /**
     * Creates a new list wrapper for the specified script object.
     * @param obj script the object to wrap
     */
    public ListAdapter(ScriptObject obj) {
        this.obj = obj;
    }

    @Override
    public int size() {
        return JSType.toInt32(obj.getLength());
    }

    @Override
    public Object get(int index) {
        checkRange(index);
        return obj.get(index);
    }

    @Override
    public Object set(int index, Object element) {
        checkRange(index);
        final Object prevValue = get(index);
        obj.set(index, element, false);
        return prevValue;
    }

    private void checkRange(int index) {
        if(index < 0 || index >= size()) {
            throw invalidIndex(index);
        }
    }

    @Override
    public void push(Object e) {
        addFirst(e);
    }

    @Override
    public boolean add(Object e) {
        addLast(e);
        return true;
    }

    @Override
    public void addFirst(Object e) {
        try {
            final Object fn = UNSHIFT.getGetter().invokeExact(obj);
            checkFunction(fn, UNSHIFT);
            UNSHIFT.getInvoker().invokeExact(fn, obj, e);
        } catch(RuntimeException | Error ex) {
            throw ex;
        } catch(Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @Override
    public void addLast(Object e) {
        try {
            final Object fn = PUSH.getGetter().invokeExact(obj);
            checkFunction(fn, PUSH);
            PUSH.getInvoker().invokeExact(fn, obj, e);
        } catch(RuntimeException | Error ex) {
            throw ex;
        } catch(Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @Override
    public void add(int index, Object e) {
        try {
            if(index < 0) {
                throw invalidIndex(index);
            } else if(index == 0) {
                addFirst(e);
            } else {
                final int size = size();
                if(index < size) {
                    final Object fn = SPLICE_ADD.getGetter().invokeExact(obj);
                    checkFunction(fn, SPLICE_ADD);
                    SPLICE_ADD.getInvoker().invokeExact(fn, obj, index, 0, e);
                } else if(index == size) {
                    addLast(e);
                } else {
                    throw invalidIndex(index);
                }
            }
        } catch(RuntimeException | Error ex) {
            throw ex;
        } catch(Throwable t) {
            throw new RuntimeException(t);
        }
    }
    private static void checkFunction(Object fn, InvokeByName invoke) {
        if(!(Bootstrap.isCallable(fn))) {
            throw new UnsupportedOperationException("The script object doesn't have a function named " + invoke.getName());
        }
    }

    private static IndexOutOfBoundsException invalidIndex(int index) {
        return new IndexOutOfBoundsException(String.valueOf(index));
    }

    @Override
    public boolean offer(Object e) {
        return offerLast(e);
    }

    @Override
    public boolean offerFirst(Object e) {
        addFirst(e);
        return true;
    }

    @Override
    public boolean offerLast(Object e) {
        addLast(e);
        return true;
    }

    @Override
    public Object pop() {
        return removeFirst();
    }

    @Override
    public Object remove() {
        return removeFirst();
    }

    @Override
    public Object removeFirst() {
        checkNonEmpty();
        return invokeShift();
    }

    @Override
    public Object removeLast() {
        checkNonEmpty();
        return invokePop();
    }

    private void checkNonEmpty() {
        if(isEmpty()) {
            throw new NoSuchElementException();
        }
    }

    @Override
    public Object remove(int index) {
        if(index < 0) {
            throw invalidIndex(index);
        } else if (index == 0) {
            return invokeShift();
        } else {
            final int maxIndex = size() - 1;
            if(index < maxIndex) {
                final Object prevValue = get(index);
                invokeSpliceRemove(index, 1);
                return prevValue;
            } else if(index == maxIndex) {
                return invokePop();
            } else {
                throw invalidIndex(index);
            }
        }
    }

    private Object invokeShift() {
        try {
            final Object fn = SHIFT.getGetter().invokeExact(obj);
            checkFunction(fn, SHIFT);
            return SHIFT.getInvoker().invokeExact(fn, obj);
        } catch(RuntimeException | Error ex) {
            throw ex;
        } catch(Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private Object invokePop() {
        try {
            final Object fn = POP.getGetter().invokeExact(obj);
            checkFunction(fn, POP);
            return POP.getInvoker().invokeExact(fn, obj);
        } catch(RuntimeException | Error ex) {
            throw ex;
        } catch(Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @Override
    protected void removeRange(int fromIndex, int toIndex) {
        invokeSpliceRemove(fromIndex, toIndex - fromIndex);
    }

    private void invokeSpliceRemove(int fromIndex, int count) {
        try {
            final Object fn = SPLICE_REMOVE.getGetter().invokeExact(obj);
            checkFunction(fn, SPLICE_REMOVE);
            SPLICE_REMOVE.getInvoker().invokeExact(fn, obj, fromIndex, count);
        } catch(RuntimeException | Error ex) {
            throw ex;
        } catch(Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @Override
    public Object poll() {
        return pollFirst();
    }

    @Override
    public Object pollFirst() {
        return isEmpty() ? null : invokeShift();
    }

    @Override
    public Object pollLast() {
        return isEmpty() ? null : invokePop();
    }

    @Override
    public Object peek() {
        return peekFirst();
    }

    @Override
    public Object peekFirst() {
        return isEmpty() ? null : get(0);
    }

    @Override
    public Object peekLast() {
        return isEmpty() ? null : get(size() - 1);
    }

    @Override
    public Object element() {
        return getFirst();
    }

    @Override
    public Object getFirst() {
        checkNonEmpty();
        return get(0);
    }

    @Override
    public Object getLast() {
        checkNonEmpty();
        return get(size() - 1);
    }

    @Override
    public Iterator<Object> descendingIterator() {
        final ListIterator<Object> it = listIterator(size());
        return new Iterator<Object>() {
            @Override
            public boolean hasNext() {
                return it.hasPrevious();
            }

            @Override
            public Object next() {
                return it.previous();
            }

            @Override
            public void remove() {
                it.remove();
            }
        };
    }

    @Override
    public boolean removeFirstOccurrence(Object o) {
        return removeOccurrence(o, iterator());
    }

    @Override
    public boolean removeLastOccurrence(Object o) {
        return removeOccurrence(o, descendingIterator());
    }

    private static boolean removeOccurrence(Object o, Iterator<Object> it) {
        while(it.hasNext()) {
            final Object e = it.next();
            if(o == null ? e == null : o.equals(e)) {
                it.remove();
                return true;
            }
        }
        return false;
    }
}
