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

import java.lang.invoke.MethodHandle;
import java.util.AbstractList;
import java.util.Deque;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.concurrent.Callable;
import jdk.nashorn.api.scripting.JSObject;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.internal.objects.Global;
import jdk.nashorn.internal.runtime.linker.Bootstrap;

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
    // Invoker creator for methods that add to the start or end of the list: PUSH and UNSHIFT. Takes fn, this, and value, returns void.
    private static final Callable<MethodHandle> ADD_INVOKER_CREATOR = invokerCreator(void.class, Object.class, JSObject.class, Object.class);

    // PUSH adds to the start of the list
    private static final Object PUSH = new Object();
    // UNSHIFT adds to the end of the list
    private static final Object UNSHIFT = new Object();

    // Invoker creator for methods that remove from the tail or head of the list: POP and SHIFT. Takes fn, this, returns Object.
    private static final Callable<MethodHandle> REMOVE_INVOKER_CREATOR = invokerCreator(Object.class, Object.class, JSObject.class);

    // POP removes from the start of the list
    private static final Object POP = new Object();
    // SHIFT removes from the end of the list
    private static final Object SHIFT = new Object();

    // SPLICE can be used to add a value in the middle of the list.
    private static final Object SPLICE_ADD = new Object();
    private static final Callable<MethodHandle> SPLICE_ADD_INVOKER_CREATOR = invokerCreator(void.class, Object.class, JSObject.class, int.class, int.class, Object.class);

    // SPLICE can also be used to remove values from the middle of the list.
    private static final Object SPLICE_REMOVE = new Object();
    private static final Callable<MethodHandle> SPLICE_REMOVE_INVOKER_CREATOR = invokerCreator(void.class, Object.class, JSObject.class, int.class, int.class);

    /** wrapped object */
    final JSObject obj;
    private final Global global;

    // allow subclasses only in this package
    ListAdapter(final JSObject obj, final Global global) {
        if (global == null) {
            throw new IllegalStateException(ECMAErrors.getMessage("list.adapter.null.global"));
        }

        this.obj = obj;
        this.global = global;
    }

    /**
     * Factory to create a ListAdapter for a given script object.
     *
     * @param obj script object to wrap as a ListAdapter
     * @return A ListAdapter wrapper object
     */
    public static ListAdapter create(final Object obj) {
        final Global global = Context.getGlobal();
        return new ListAdapter(getJSObject(obj, global), global);
    }

    private static JSObject getJSObject(final Object obj, final Global global) {
        if (obj instanceof ScriptObject) {
            return (JSObject)ScriptObjectMirror.wrap(obj, global);
        } else if (obj instanceof JSObject) {
            return (JSObject)obj;
        }
        throw new IllegalArgumentException("ScriptObject or JSObject expected");
    }

    @Override
    public final Object get(final int index) {
        checkRange(index);
        return getAt(index);
    }

    private Object getAt(final int index) {
        return obj.getSlot(index);
    }

    @Override
    public Object set(final int index, final Object element) {
        checkRange(index);
        final Object prevValue = getAt(index);
        obj.setSlot(index, element);
        return prevValue;
    }

    private void checkRange(final int index) {
        if(index < 0 || index >= size()) {
            throw invalidIndex(index);
        }
    }

    @Override
    public int size() {
        return JSType.toInt32(obj.getMember("length"));
    }

    @Override
    public final void push(final Object e) {
        addFirst(e);
    }

    @Override
    public final boolean add(final Object e) {
        addLast(e);
        return true;
    }

    @Override
    public final void addFirst(final Object e) {
        try {
            getDynamicInvoker(UNSHIFT, ADD_INVOKER_CREATOR).invokeExact(getFunction("unshift"), obj, e);
        } catch(RuntimeException | Error ex) {
            throw ex;
        } catch(final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @Override
    public final void addLast(final Object e) {
        try {
            getDynamicInvoker(PUSH, ADD_INVOKER_CREATOR).invokeExact(getFunction("push"), obj, e);
        } catch(RuntimeException | Error ex) {
            throw ex;
        } catch(final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @Override
    public final void add(final int index, final Object e) {
        try {
            if(index < 0) {
                throw invalidIndex(index);
            } else if(index == 0) {
                addFirst(e);
            } else {
                final int size = size();
                if(index < size) {
                    getDynamicInvoker(SPLICE_ADD, SPLICE_ADD_INVOKER_CREATOR).invokeExact(obj.getMember("splice"), obj, index, 0, e);
                } else if(index == size) {
                    addLast(e);
                } else {
                    throw invalidIndex(index);
                }
            }
        } catch(final RuntimeException | Error ex) {
            throw ex;
        } catch(final Throwable t) {
            throw new RuntimeException(t);
        }
    }
    private Object getFunction(final String name) {
        final Object fn = obj.getMember(name);
        if(!(Bootstrap.isCallable(fn))) {
            throw new UnsupportedOperationException("The script object doesn't have a function named " + name);
        }
        return fn;
    }

    private static IndexOutOfBoundsException invalidIndex(final int index) {
        return new IndexOutOfBoundsException(String.valueOf(index));
    }

    @Override
    public final boolean offer(final Object e) {
        return offerLast(e);
    }

    @Override
    public final boolean offerFirst(final Object e) {
        addFirst(e);
        return true;
    }

    @Override
    public final boolean offerLast(final Object e) {
        addLast(e);
        return true;
    }

    @Override
    public final Object pop() {
        return removeFirst();
    }

    @Override
    public final Object remove() {
        return removeFirst();
    }

    @Override
    public final Object removeFirst() {
        checkNonEmpty();
        return invokeShift();
    }

    @Override
    public final Object removeLast() {
        checkNonEmpty();
        return invokePop();
    }

    private void checkNonEmpty() {
        if(isEmpty()) {
            throw new NoSuchElementException();
        }
    }

    @Override
    public final Object remove(final int index) {
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
            return getDynamicInvoker(SHIFT, REMOVE_INVOKER_CREATOR).invokeExact(getFunction("shift"), obj);
        } catch(RuntimeException | Error ex) {
            throw ex;
        } catch(final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private Object invokePop() {
        try {
            return getDynamicInvoker(POP, REMOVE_INVOKER_CREATOR).invokeExact(getFunction("pop"), obj);
        } catch(RuntimeException | Error ex) {
            throw ex;
        } catch(final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @Override
    protected final void removeRange(final int fromIndex, final int toIndex) {
        invokeSpliceRemove(fromIndex, toIndex - fromIndex);
    }

    private void invokeSpliceRemove(final int fromIndex, final int count) {
        try {
            getDynamicInvoker(SPLICE_REMOVE, SPLICE_REMOVE_INVOKER_CREATOR).invokeExact(getFunction("splice"), obj, fromIndex, count);
        } catch(RuntimeException | Error ex) {
            throw ex;
        } catch(final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @Override
    public final Object poll() {
        return pollFirst();
    }

    @Override
    public final Object pollFirst() {
        return isEmpty() ? null : invokeShift();
    }

    @Override
    public final Object pollLast() {
        return isEmpty() ? null : invokePop();
    }

    @Override
    public final Object peek() {
        return peekFirst();
    }

    @Override
    public final Object peekFirst() {
        return isEmpty() ? null : get(0);
    }

    @Override
    public final Object peekLast() {
        return isEmpty() ? null : get(size() - 1);
    }

    @Override
    public final Object element() {
        return getFirst();
    }

    @Override
    public final Object getFirst() {
        checkNonEmpty();
        return get(0);
    }

    @Override
    public final Object getLast() {
        checkNonEmpty();
        return get(size() - 1);
    }

    @Override
    public final Iterator<Object> descendingIterator() {
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
    public final boolean removeFirstOccurrence(final Object o) {
        return removeOccurrence(o, iterator());
    }

    @Override
    public final boolean removeLastOccurrence(final Object o) {
        return removeOccurrence(o, descendingIterator());
    }

    private static boolean removeOccurrence(final Object o, final Iterator<Object> it) {
        while(it.hasNext()) {
            if(Objects.equals(o, it.next())) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    private static Callable<MethodHandle> invokerCreator(final Class<?> rtype, final Class<?>... ptypes) {
        return new Callable<MethodHandle>() {
            @Override
            public MethodHandle call() {
                return Bootstrap.createDynamicCallInvoker(rtype, ptypes);
            }
        };
    }

    private MethodHandle getDynamicInvoker(final Object key, final Callable<MethodHandle> creator) {
        return global.getDynamicInvoker(key, creator);
    }
}
