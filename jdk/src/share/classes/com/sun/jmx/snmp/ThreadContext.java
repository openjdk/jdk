/*
 * Copyright (c) 2000, 2007, Oracle and/or its affiliates. All rights reserved.
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


package com.sun.jmx.snmp;

import java.util.Stack;
import java.util.EmptyStackException;

/**
 * <p><b>Warning: The interface of this class is subject to change.
 * Use at your own risk.</b></p>
 *
 * <p>This class associates a context with each thread that
 * references it.  The context is a set of mappings between Strings
 * and Objects.  It is managed as a stack, typically with code like
 * this:</p>
 *
 * <pre>
 * ThreadContext oldContext = ThreadContext.push(myKey, myObject);
 * // plus possibly further calls to ThreadContext.push...
 * try {
 *      doSomeOperation();
 * } finally {
 *      ThreadContext.restore(oldContext);
 * }
 * </pre>
 *
 * <p>The <code>try</code>...<code>finally</code> block ensures that
 * the <code>restore</code> is done even if
 * <code>doSomeOperation</code> terminates abnormally (with an
 * exception).</p>
 *
 * <p>A thread can consult its own context using
 * <code>ThreadContext.get(myKey)</code>.  The result is the
 * value that was most recently pushed with the given key.</p>
 *
 * <p>A thread cannot read or modify the context of another thread.</p>
 *
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 */
public class ThreadContext implements Cloneable {

    /* The context of a thread is stored as a linked list.  At the
       head of the list is the value returned by localContext.get().
       At the tail of the list is a sentinel ThreadContext value with
       "previous" and "key" both null.  There is a different sentinel
       object for each thread.

       Because a null key indicates the sentinel, we reject attempts to
       push context entries with a null key.

       The reason for using a sentinel rather than just terminating
       the list with a null reference is to protect against incorrect
       or even malicious code.  If you have a reference to the
       sentinel value, you can erase the context stack.  Only the
       caller of the first "push" that put something on the stack can
       get such a reference, so if that caller does not give this
       reference away, no one else can erase the stack.

       If the restore method took a null reference to mean an empty
       stack, anyone could erase the stack, since anyone can make a
       null reference.

       When the stack is empty, we discard the sentinel object and
       have localContext.get() return null.  Then we recreate the
       sentinel object on the first subsequent push.

       ThreadContext objects are immutable.  As a consequence, you can
       give a ThreadContext object to setInitialContext that is no
       longer current.  But the interface says this can be rejected,
       in case we remove immutability later.  */

    /* We have to comment out "final" here because of a bug in the JDK1.1
       compiler.  Uncomment it when we discard 1.1 compatibility.  */
    private /*final*/ ThreadContext previous;
    private /*final*/ String key;
    private /*final*/ Object value;

    private ThreadContext(ThreadContext previous, String key, Object value) {
        this.previous = previous;
        this.key = key;
        this.value = value;
    }

    /**
     * <p>Get the Object that was most recently pushed with the given key.</p>
     *
     * @param key the key of interest.
     *
     * @return the last Object that was pushed (using
     * <code>push</code>) with that key and not subsequently canceled
     * by a <code>restore</code>; or null if there is no such object.
     * A null return value may also indicate that the last Object
     * pushed was the value <code>null</code>.  Use the
     * <code>contains</code> method to distinguish this case from the
     * case where there is no Object.
     *
     * @exception IllegalArgumentException if <code>key</code> is null.
     */
    public static Object get(String key) throws IllegalArgumentException {
        ThreadContext context = contextContaining(key);
        if (context == null)
            return null;
        else
            return context.value;
    }

    /**
     * <p>Check whether a value with the given key exists in the stack.
     * This means that the <code>push</code> method was called with
     * this key and it was not cancelled by a subsequent
     * <code>restore</code>.  This method is useful when the
     * <code>get</code> method returns null, to distinguish between
     * the case where the key exists in the stack but is associated
     * with a null value, and the case where the key does not exist in
     * the stack.</p>
     *
     * @return true if the key exists in the stack.
     *
     * @exception IllegalArgumentException if <code>key</code> is null.
     */
    public static boolean contains(String key)
            throws IllegalArgumentException {
        return (contextContaining(key) != null);
    }

    /**
     * <p>Find the ThreadContext in the stack that contains the given key,
     * or return null if there is none.</p>
     *
     * @exception IllegalArgumentException if <code>key</code> is null.
     */
    private static ThreadContext contextContaining(String key)
            throws IllegalArgumentException {
        if (key == null)
            throw new IllegalArgumentException("null key");
        for (ThreadContext context = getContext();
             context != null;
             context = context.previous) {
            if (key.equals(context.key))
                return context;
            /* Note that "context.key" may be null if "context" is the
               sentinel, so don't write "if (context.key.equals(key))"!  */
        }
        return null;
    }

//  /**
//   * Change the value that was most recently associated with the given key
//   * in a <code>push</code> operation not cancelled by a subsequent
//   * <code>restore</code>.  If there is no such association, nothing happens
//   * and the return value is null.
//   *
//   * @param key the key of interest.
//   * @param value the new value to associate with that key.
//   *
//   * @return the value that was previously associated with the key, or null
//   * if the key does not exist in the stack.
//   *
//   * @exception IllegalArgumentException if <code>key</code> is null.
//   */
//  public static Object set(String key, Object value)
//          throws IllegalArgumentException {
//      ThreadContext context = contextContaining(key);
//      if (context == null)
//          return null;
//      Object old = context.value;
//      context.value = value;
//      return old;
//  }

    /**
     * <p>Push an object on the context stack with the given key.
     * This operation can subsequently be undone by calling
     * <code>restore</code> with the ThreadContext value returned
     * here.</p>
     *
     * @param key the key that will be used to find the object while it is
     * on the stack.
     * @param value the value to be associated with that key.  It may be null.
     *
     * @return a ThreadContext that can be given to <code>restore</code> to
     * restore the stack to its state before the <code>push</code>.
     *
     * @exception IllegalArgumentException if <code>key</code> is null.
     */
    public static ThreadContext push(String key, Object value)
            throws IllegalArgumentException {
        if (key == null)
            throw new IllegalArgumentException("null key");

        ThreadContext oldContext = getContext();
        if (oldContext == null)
            oldContext = new ThreadContext(null, null, null);  // make sentinel
        ThreadContext newContext = new ThreadContext(oldContext, key, value);
        setContext(newContext);
        return oldContext;
    }

    /**
     * <p>Return an object that can later be supplied to <code>restore</code>
     * to restore the context stack to its current state.  The object can
     * also be given to <code>setInitialContext</code>.</p>
     *
     * @return a ThreadContext that represents the current context stack.
     */
    public static ThreadContext getThreadContext() {
        return getContext();
    }

    /**
     * <p>Restore the context stack to an earlier state.  This typically
     * undoes the effect of one or more <code>push</code> calls.</p>
     *
     * @param oldContext the state to return.  This is usually the return
     * value of an earlier <code>push</code> operation.
     *
     * @exception NullPointerException if <code>oldContext</code> is null.
     * @exception IllegalArgumentException if <code>oldContext</code>
     * does not represent a context from this thread, or if that
     * context was undone by an earlier <code>restore</code>.
     */
    public static void restore(ThreadContext oldContext)
            throws NullPointerException, IllegalArgumentException {
        /* The following test is not strictly necessary in the code as it
           stands today, since the reference to "oldContext.key" would
           generate a NullPointerException anyway.  But if someone
           didn't notice that during subsequent changes, they could
           accidentally permit restore(null) with the semantics of
           trashing the context stack.  */
        if (oldContext == null)
            throw new NullPointerException();

        /* Check that the restored context is in the stack.  */
        for (ThreadContext context = getContext();
             context != oldContext;
             context = context.previous) {
            if (context == null) {
                throw new IllegalArgumentException("Restored context is not " +
                                                   "contained in current " +
                                                   "context");
            }
        }

        /* Discard the sentinel if the stack is empty.  This means that it
           is an error to call "restore" a second time with the
           ThreadContext value that means an empty stack.  That's why we
           don't say that it is all right to restore the stack to the
           state it was already in.  */
        if (oldContext.key == null)
            oldContext = null;

        setContext(oldContext);
    }

    /**
     * <p>Set the initial context of the calling thread to a context obtained
     * from another thread.  After this call, the calling thread will see
     * the same results from the <code>get</code> method as the thread
     * from which the <code>context</code> argument was obtained, at the
     * time it was obtained.</p>
     *
     * <p>The <code>context</code> argument must be the result of an earlier
     * <code>push</code> or <code>getThreadContext</code> call.  It is an
     * error (which may or may not be detected) if this context has been
     * undone by a <code>restore</code>.</p>
     *
     * <p>The context stack of the calling thread must be empty before this
     * call, i.e., there must not have been a <code>push</code> not undone
     * by a subsequent <code>restore</code>.</p>
     *
     * @exception IllegalArgumentException if the context stack was
     * not empty before the call.  An implementation may also throw this
     * exception if <code>context</code> is no longer current in the
     * thread from which it was obtained.
     */
    /* We rely on the fact that ThreadContext objects are immutable.
       This means that we don't have to check that the "context"
       argument is valid.  It necessarily represents the head of a
       valid chain of ThreadContext objects, even if the thread from
       which it was obtained has subsequently been set to a point
       later in that chain using "restore".  */
    public void setInitialContext(ThreadContext context)
            throws IllegalArgumentException {
        /* The following test assumes that we discard sentinels when the
           stack is empty.  */
        if (getContext() != null)
            throw new IllegalArgumentException("previous context not empty");
        setContext(context);
    }

    private static ThreadContext getContext() {
        return localContext.get();
    }

    private static void setContext(ThreadContext context) {
        localContext.set(context);
    }

    private static ThreadLocal<ThreadContext> localContext =
            new ThreadLocal<ThreadContext>();
}
