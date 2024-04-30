/*
 * Copyright (c) 1995, 2023, Oracle and/or its affiliates. All rights reserved.
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

package java.lang;

import java.io.PrintStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import jdk.internal.misc.VM;

/**
 * A thread group represents a set of threads. In addition, a thread group can
 * also include other thread groups. The thread groups form a tree in which
 * every thread group except the initial thread group has a parent.
 *
 * <p> A thread group has a name and maximum priority. The name is specified
 * when creating the group and cannot be changed. The group's maximum priority
 * is the maximum priority for threads created in the group. It is initially
 * inherited from the parent thread group but may be changed using the {@link
 * #setMaxPriority(int) setMaxPriority} method.
 *
 * <p> A thread group is weakly <a href="ref/package-summary.html#reachability">
 * <em>reachable</em></a> from its parent group so that it is eligible for garbage
 * collection when there are no {@linkplain Thread#isAlive() live} threads in the
 * group and the thread group is otherwise <i>unreachable</i>.
 *
 * <p> Unless otherwise specified, passing a {@code null} argument to a constructor
 * or method in this class will cause a {@link NullPointerException} to be thrown.
 *
 * <h2><a id="virtualthreadgroup">Thread groups and virtual threads</a></h2>
 * The Java runtime creates a special thread group for
 * <a href="Thread.html#virtual-threads">virtual threads</a>. This group is
 * returned by the {@link Thread#getThreadGroup() Thread.getThreadGroup} method
 * when invoked on a virtual thread. The thread group differs to other thread
 * groups in that its maximum priority is fixed and cannot be changed with the
 * {@link #setMaxPriority(int) setMaxPriority} method.
 * Virtual threads are not included in the estimated thread count returned by the
 * {@link #activeCount() activeCount} method, are not enumerated by the {@link
 * #enumerate(Thread[]) enumerate} method, and are not interrupted by the {@link
 * #interrupt() interrupt} method.
 *
 * @apiNote
 * Thread groups provided a way in early Java releases to group threads and provide
 * a form of <i>job control</i> for threads. Thread groups supported the isolation
 * of applets and defined methods intended for diagnostic purposes. It should be
 * rare for new applications to create ThreadGroups and interact with this API.
 *
 * @since   1.0
 */
public class ThreadGroup implements Thread.UncaughtExceptionHandler {
    /**
     * All fields are accessed directly by the VM and from JVMTI functions.
     * Operations that require synchronization on more than one group in the
     * tree should synchronize on the parent group before synchronizing on
     * the child group.
     */
    private final ThreadGroup parent;
    private final String name;
    private volatile int maxPriority;
    private volatile boolean daemon;

    // strongly reachable from this group
    private int ngroups;
    private ThreadGroup[] groups;

    // weakly reachable from this group
    private int nweaks;
    private WeakReference<ThreadGroup>[] weaks;

    /**
     * Creates the top-level "system" ThreadGroup.
     *
     * @apiNote This method is invoked by the VM early startup.
     */
    private ThreadGroup() {
        this.parent = null;
        this.name = "system";
        this.maxPriority = Thread.MAX_PRIORITY;
    }

    /**
     * Creates a ThreadGroup without any permission or other checks.
     */
    ThreadGroup(ThreadGroup parent, String name, int maxPriority, boolean daemon) {
        this.parent = parent;
        this.name = name;
        this.maxPriority = maxPriority;
        if (daemon)
            this.daemon = true;
        if (VM.isBooted()) {
            parent.synchronizedAddWeak(this);
        } else {
            // keep strong reference to the "main" and other groups created
            // early in the VM startup to avoid the use of weak references
            // during when starting the reference handler thread.
            parent.synchronizedAddStrong(this);
        }
    }

    private ThreadGroup(Void unused, ThreadGroup parent, String name) {
        this(parent, name, parent.maxPriority, parent.daemon);
    }

    private static Void checkParentAccess(ThreadGroup parent) {
        parent.checkAccess();
        return null;
    }

    /**
     * Constructs a new thread group. The parent of this new group is
     * the thread group of the currently running thread.
     * <p>
     * The {@code checkAccess} method of the parent thread group is
     * called with no arguments; this may result in a security exception.
     *
     * @param   name   the name of the new thread group, can be {@code null}
     * @throws  SecurityException  if the current thread cannot create a
     *               thread in the specified thread group.
     * @see     java.lang.ThreadGroup#checkAccess()
     */
    public ThreadGroup(String name) {
        this(Thread.currentThread().getThreadGroup(), name);
    }

    /**
     * Creates a new thread group. The parent of this new group is the
     * specified thread group.
     * <p>
     * The {@code checkAccess} method of the parent thread group is
     * called with no arguments; this may result in a security exception.
     *
     * @param     parent   the parent thread group.
     * @param     name     the name of the new thread group, can be {@code null}
     * @throws    SecurityException  if the current thread cannot create a
     *               thread in the specified thread group.
     * @see     java.lang.ThreadGroup#checkAccess()
     */
    @SuppressWarnings("this-escape")
    public ThreadGroup(ThreadGroup parent, String name) {
        this(checkParentAccess(parent), parent, name);
    }

    /**
     * Returns the name of this thread group.
     *
     * @return  the name of this thread group, may be {@code null}
     */
    public final String getName() {
        return name;
    }

    /**
     * Returns the parent of this thread group.
     * <p>
     * First, if the parent is not {@code null}, the
     * {@code checkAccess} method of the parent thread group is
     * called with no arguments; this may result in a security exception.
     *
     * @return  the parent of this thread group. The top-level thread group
     *          is the only thread group whose parent is {@code null}.
     * @throws  SecurityException  if the current thread cannot modify
     *               this thread group.
     * @see        java.lang.ThreadGroup#checkAccess()
     * @see        java.lang.SecurityException
     * @see        java.lang.RuntimePermission
     */
    public final ThreadGroup getParent() {
        if (parent != null)
            parent.checkAccess();
        return parent;
    }

    /**
     * Returns the maximum priority of this thread group. This is the maximum
     * priority for new threads created in the thread group.
     *
     * @return  the maximum priority for new threads created in the thread group
     * @see     #setMaxPriority
     */
    public final int getMaxPriority() {
        return maxPriority;
    }

    /**
     * {@return the daemon status of this thread group}
     * The daemon status is not used for anything.
     *
     * @deprecated This method originally indicated if the thread group is a
     *             <i>daemon thread group</i> that is automatically destroyed
     *             when its last thread terminates. The concept of daemon
     *             thread group no longer exists.
     *             A thread group is eligible to be GC'ed when there are no
     *             live threads in the group and it is otherwise unreachable.
     */
    @Deprecated(since="16", forRemoval=true)
    public final boolean isDaemon() {
        return daemon;
    }

    /**
     * Returns false.
     *
     * @return false
     *
     * @deprecated This method originally indicated if the thread group is
     *             destroyed. The ability to destroy a thread group and the
     *             concept of a destroyed thread group no longer exists.
     *             A thread group is eligible to be GC'ed when there are no
     *             live threads in the group and it is otherwise unreachable.
     *
     * @since   1.1
     */
    @Deprecated(since="16", forRemoval=true)
    public boolean isDestroyed() {
        return false;
    }

    /**
     * Sets the daemon status of this thread group.
     * The daemon status is not used for anything.
     * <p>
     * First, the {@code checkAccess} method of this thread group is
     * called with no arguments; this may result in a security exception.
     *
     * @param      daemon the daemon status
     * @throws     SecurityException  if the current thread cannot modify
     *               this thread group.
     * @see        java.lang.SecurityException
     * @see        java.lang.ThreadGroup#checkAccess()
     *
     * @deprecated This method originally configured whether the thread group is
     *             a <i>daemon thread group</i> that is automatically destroyed
     *             when its last thread terminates. The concept of daemon thread
     *             group no longer exists. A thread group is eligible to be GC'ed
     *             when there are no live threads in the group and it is otherwise
     *             unreachable.
     */
    @Deprecated(since="16", forRemoval=true)
    public final void setDaemon(boolean daemon) {
        checkAccess();
        this.daemon = daemon;
    }

    /**
     * Sets the maximum priority of the group. The maximum priority of the
     * <a href="ThreadGroup.html#virtualthreadgroup"><em>ThreadGroup for virtual
     * threads</em></a> is not changed by this method (the new priority is ignored).
     * Threads in the thread group (or subgroups) that already have a higher
     * priority are not affected by this method.
     * <p>
     * First, the {@code checkAccess} method of this thread group is
     * called with no arguments; this may result in a security exception.
     * <p>
     * If the {@code pri} argument is less than
     * {@link Thread#MIN_PRIORITY} or greater than
     * {@link Thread#MAX_PRIORITY}, the maximum priority of the group
     * remains unchanged.
     * <p>
     * Otherwise, the priority of this ThreadGroup object is set to the
     * smaller of the specified {@code pri} and the maximum permitted
     * priority of the parent of this thread group. (If this thread group
     * is the system thread group, which has no parent, then its maximum
     * priority is simply set to {@code pri}.) Then this method is
     * called recursively, with {@code pri} as its argument, for
     * every thread group that belongs to this thread group.
     *
     * @param      pri   the new priority of the thread group.
     * @throws     SecurityException  if the current thread cannot modify
     *               this thread group.
     * @see        #getMaxPriority
     * @see        java.lang.SecurityException
     * @see        java.lang.ThreadGroup#checkAccess()
     */
    public final void setMaxPriority(int pri) {
        checkAccess();
        if (pri >= Thread.MIN_PRIORITY && pri <= Thread.MAX_PRIORITY) {
            synchronized (this) {
                if (parent == null) {
                    maxPriority = pri;
                } else if (this != Thread.virtualThreadGroup()) {
                    maxPriority = Math.min(pri, parent.maxPriority);
                }
                subgroups().forEach(g -> g.setMaxPriority(pri));
            }
        }
    }

    /**
     * Tests if this thread group is either the thread group
     * argument or one of its ancestor thread groups.
     *
     * @param   g   a thread group, can be {@code null}
     * @return  {@code true} if this thread group is the thread group
     *          argument or one of its ancestor thread groups;
     *          {@code false} otherwise.
     */
    public final boolean parentOf(ThreadGroup g) {
        for (; g != null ; g = g.parent) {
            if (g == this) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines if the currently running thread has permission to
     * modify this thread group.
     * <p>
     * If there is a security manager, its {@code checkAccess} method
     * is called with this thread group as its argument. This may result
     * in throwing a {@code SecurityException}.
     *
     * @throws     SecurityException  if the current thread is not allowed to
     *               access this thread group.
     * @see        java.lang.SecurityManager#checkAccess(java.lang.ThreadGroup)
     * @deprecated This method is only useful in conjunction with
     *       {@linkplain SecurityManager the Security Manager}, which is
     *       deprecated and subject to removal in a future release.
     *       Consequently, this method is also deprecated and subject to
     *       removal. There is no replacement for the Security Manager or this
     *       method.
     */
    @Deprecated(since="17", forRemoval=true)
    public final void checkAccess() {
        @SuppressWarnings("removal")
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkAccess(this);
        }
    }

    /**
     * Returns an estimate of the number of {@linkplain Thread#isAlive() live}
     * platform threads in this thread group and its subgroups. Virtual threads
     * are not included in the estimate. This method recursively iterates over
     * all subgroups in this thread group.
     *
     * <p> The value returned is only an estimate because the number of
     * threads may change dynamically while this method traverses internal
     * data structures, and might be affected by the presence of certain
     * system threads. This method is intended primarily for debugging
     * and monitoring purposes.
     *
     * @return  an estimate of the number of live threads in this thread
     *          group and in any other thread group that has this thread
     *          group as an ancestor
     */
    public int activeCount() {
        int n = 0;
        for (Thread thread : Thread.getAllThreads()) {
            ThreadGroup g = thread.getThreadGroup();
            if (parentOf(g)) {
                n++;
            }
        }
        return n;
    }

    /**
     * Copies into the specified array every {@linkplain Thread#isAlive() live}
     * platform thread in this thread group and its subgroups. Virtual threads
     * are not enumerated by this method.
     *
     * <p> An invocation of this method behaves in exactly the same
     * way as the invocation
     *
     * <blockquote>
     * {@linkplain #enumerate(Thread[], boolean) enumerate}{@code (list, true)}
     * </blockquote>
     *
     * @param  list
     *         an array into which to put the list of threads
     *
     * @return  the number of threads put into the array
     *
     * @throws  SecurityException
     *          if {@linkplain #checkAccess checkAccess} determines that
     *          the current thread cannot access this thread group
     */
    public int enumerate(Thread[] list) {
        return enumerate(list, true);
    }

    /**
     * Copies into the specified array every {@linkplain Thread#isAlive() live}
     * platform thread in this thread group. Virtual threads are not enumerated
     * by this method. If {@code recurse} is {@code true}, this method recursively
     * enumerates all subgroups of this thread group and references to every live
     * platform thread in these subgroups are also included. If the array is too
     * short to hold all the threads, the extra threads are silently ignored.
     *
     * <p> An application might use the {@linkplain #activeCount activeCount}
     * method to get an estimate of how big the array should be, however
     * <i>if the array is too short to hold all the threads, the extra threads
     * are silently ignored.</i>  If it is critical to obtain every live
     * thread in this thread group, the caller should verify that the returned
     * int value is strictly less than the length of {@code list}.
     *
     * <p> Due to the inherent race condition in this method, it is recommended
     * that the method only be used for debugging and monitoring purposes.
     *
     * @param  list
     *         an array into which to put the list of threads
     *
     * @param  recurse
     *         if {@code true}, recursively enumerate all subgroups of this
     *         thread group
     *
     * @return  the number of threads put into the array
     *
     * @throws  SecurityException
     *          if {@linkplain #checkAccess checkAccess} determines that
     *          the current thread cannot access this thread group
     */
    public int enumerate(Thread[] list, boolean recurse) {
        Objects.requireNonNull(list);
        checkAccess();
        int n = 0;
        if (list.length > 0) {
            for (Thread thread : Thread.getAllThreads()) {
                ThreadGroup g = thread.getThreadGroup();
                if (g == this || (recurse && parentOf(g))) {
                    list[n++] = thread;
                    if (n == list.length) {
                        // list full
                        break;
                    }
                }
            }
        }
        return n;
    }

    /**
     * Returns an estimate of the number of groups in this thread group and its
     * subgroups. Recursively iterates over all subgroups in this thread group.
     *
     * <p> The value returned is only an estimate because the number of
     * thread groups may change dynamically while this method traverses
     * internal data structures. This method is intended primarily for
     * debugging and monitoring purposes.
     *
     * @return  the number of thread groups with this thread group as
     *          an ancestor
     */
    public int activeGroupCount() {
        int n = 0;
        for (ThreadGroup group : synchronizedSubgroups()) {
            n = n + group.activeGroupCount() + 1;
        }
        return n;
    }

    /**
     * Copies into the specified array references to every subgroup in this
     * thread group and its subgroups.
     *
     * <p> An invocation of this method behaves in exactly the same
     * way as the invocation
     *
     * <blockquote>
     * {@linkplain #enumerate(ThreadGroup[], boolean) enumerate}{@code (list, true)}
     * </blockquote>
     *
     * @param  list
     *         an array into which to put the list of thread groups
     *
     * @return  the number of thread groups put into the array
     *
     * @throws  SecurityException
     *          if {@linkplain #checkAccess checkAccess} determines that
     *          the current thread cannot access this thread group
     */
    public int enumerate(ThreadGroup[] list) {
        return enumerate(list, true);
    }

    /**
     * Copies into the specified array references to every subgroup in this
     * thread group. If {@code recurse} is {@code true}, this method recursively
     * enumerates all subgroups of this thread group and references to every
     * thread group in these subgroups are also included.
     *
     * <p> An application might use the
     * {@linkplain #activeGroupCount activeGroupCount} method to
     * get an estimate of how big the array should be, however <i>if the
     * array is too short to hold all the thread groups, the extra thread
     * groups are silently ignored.</i>  If it is critical to obtain every
     * subgroup in this thread group, the caller should verify that
     * the returned int value is strictly less than the length of
     * {@code list}.
     *
     * <p> Due to the inherent race condition in this method, it is recommended
     * that the method only be used for debugging and monitoring purposes.
     *
     * @param  list
     *         an array into which to put the list of thread groups
     *
     * @param  recurse
     *         if {@code true}, recursively enumerate all subgroups
     *
     * @return  the number of thread groups put into the array
     *
     * @throws  SecurityException
     *          if {@linkplain #checkAccess checkAccess} determines that
     *          the current thread cannot access this thread group
     */
    public int enumerate(ThreadGroup[] list, boolean recurse) {
        Objects.requireNonNull(list);
        checkAccess();
        return enumerate(list, 0, recurse);
    }

    /**
     * Add a reference to each subgroup to the given array, starting at
     * the given index. Returns the new index.
     */
    private int enumerate(ThreadGroup[] list, int i, boolean recurse) {
        List<ThreadGroup> subgroups = synchronizedSubgroups();
        for (int j = 0; j < subgroups.size() && i < list.length; j++) {
            ThreadGroup group = subgroups.get(j);
            list[i++] = group;
            if (recurse) {
                i = group.enumerate(list, i, true);
            }
        }
        return i;
    }

    /**
     * Interrupts all {@linkplain Thread#isAlive() live} platform threads in
     * this thread group and its subgroups.
     *
     * @throws     SecurityException  if the current thread is not allowed
     *               to access this thread group or any of the threads in
     *               the thread group.
     * @see        java.lang.Thread#interrupt()
     * @see        java.lang.SecurityException
     * @see        java.lang.ThreadGroup#checkAccess()
     * @since      1.2
     */
    public final void interrupt() {
        checkAccess();
        for (Thread thread : Thread.getAllThreads()) {
            ThreadGroup g = thread.getThreadGroup();
            if (parentOf(g)) {
                thread.interrupt();
            }
        }
    }

    /**
     * Does nothing.
     *
     * @deprecated This method was originally specified to destroy an empty
     *             thread group. The ability to explicitly destroy a thread group
     *             no longer exists. A thread group is eligible to be GC'ed when
     *             there are no live threads in the group and it is otherwise
     *             unreachable.
     */
    @Deprecated(since="16", forRemoval=true)
    public final void destroy() {
    }

    /**
     * Prints information about this thread group to the standard
     * output. This method is useful only for debugging.
     */
    public void list() {
        Map<ThreadGroup, List<Thread>> map = new HashMap<>();
        for (Thread thread : Thread.getAllThreads()) {
            ThreadGroup group = thread.getThreadGroup();
            // group is null when thread terminates
            if (group != null && parentOf(group)) {
                map.computeIfAbsent(group, k -> new ArrayList<>()).add(thread);
            }
        }
        list(map, System.out, 0);
    }

    private void list(Map<ThreadGroup, List<Thread>> map, PrintStream out, int indent) {
        out.print(" ".repeat(indent));
        out.println(this);
        indent += 4;
        List<Thread> threads = map.get(this);
        if (threads != null) {
            for (Thread thread : threads) {
                out.print(" ".repeat(indent));
                out.println(thread);
            }
        }
        for (ThreadGroup group : synchronizedSubgroups()) {
            group.list(map, out, indent);
        }
    }

    /**
     * Called by the Java Virtual Machine when a thread in this
     * thread group stops because of an uncaught exception, and the thread
     * does not have a specific {@link Thread.UncaughtExceptionHandler}
     * installed.
     * <p>
     * The {@code uncaughtException} method of
     * {@code ThreadGroup} does the following:
     * <ul>
     * <li>If this thread group has a parent thread group, the
     *     {@code uncaughtException} method of that parent is called
     *     with the same two arguments.
     * <li>Otherwise, this method checks to see if there is a
     *     {@linkplain Thread#getDefaultUncaughtExceptionHandler default
     *     uncaught exception handler} installed, and if so, its
     *     {@code uncaughtException} method is called with the same
     *     two arguments.
     * <li>Otherwise, a message containing the thread's name, as returned
     *     from the thread's {@link Thread#getName getName} method, and a
     *     stack backtrace, using the {@code Throwable}'s {@link
     *     Throwable#printStackTrace() printStackTrace} method, is
     *     printed to the {@linkplain System#err standard error stream}.
     * </ul>
     * <p>
     * Applications can override this method in subclasses of
     * {@code ThreadGroup} to provide alternative handling of
     * uncaught exceptions.
     *
     * @param   t   the thread that is about to exit.
     * @param   e   the uncaught exception.
     */
    public void uncaughtException(Thread t, Throwable e) {
        if (parent != null) {
            parent.uncaughtException(t, e);
        } else {
            Thread.UncaughtExceptionHandler ueh =
                Thread.getDefaultUncaughtExceptionHandler();
            if (ueh != null) {
                ueh.uncaughtException(t, e);
            } else {
                System.err.print("Exception in thread \"" + t.getName() + "\" ");
                e.printStackTrace(System.err);
            }
        }
    }

    /**
     * Returns a string representation of this Thread group.
     *
     * @return  a string representation of this thread group.
     */
    public String toString() {
        return getClass().getName()
                + "[name=" + getName()
                + ",maxpri=" + getMaxPriority()
                + "]";
    }

    /**
     * Add a strongly reachable subgroup.
     */
    private void synchronizedAddStrong(ThreadGroup group) {
        synchronized (this) {
            if (groups == null) {
                groups = new ThreadGroup[4];
            } else if (groups.length == ngroups) {
                groups = Arrays.copyOf(groups, ngroups + 4);
            }
            groups[ngroups++] = group;
        }
    }

    /**
     * Add a weakly reachable subgroup.
     */
    private void synchronizedAddWeak(ThreadGroup group) {
        synchronized (this) {
            if (weaks == null) {
                @SuppressWarnings({"unchecked", "rawtypes"})
                WeakReference<ThreadGroup>[] array = new WeakReference[4];
                weaks = array;
            } else {
                // expunge
                for (int i = 0; i < nweaks; ) {
                    ThreadGroup g = weaks[i].get();
                    if (g == null) {
                        removeWeak(i);
                    } else {
                        i++;
                    }
                }
                // expand to make space if needed
                if (weaks.length == nweaks) {
                    weaks = Arrays.copyOf(weaks, nweaks + 4);
                }
            }
            weaks[nweaks++] = new WeakReference<>(group);
        }
    }

    /**
     * Remove the weakly reachable group at the given index of the weaks array.
     */
    private void removeWeak(int index) {
        assert Thread.holdsLock(this) && index < nweaks;
        int last = nweaks - 1;
        if (index < nweaks)
            weaks[index] = weaks[last];
        weaks[last] = null;
        nweaks--;
    }

    /**
     * Returns a snapshot of the subgroups.
     */
    private List<ThreadGroup> synchronizedSubgroups() {
        synchronized (this) {
            return subgroups();
        }
    }

    /**
     * Returns a snapshot of the subgroups as an array, used by JVMTI.
     */
    private ThreadGroup[] subgroupsAsArray() {
        List<ThreadGroup> groups = synchronizedSubgroups();
        return groups.toArray(new ThreadGroup[0]);
    }

    /**
     * Returns a snapshot of the subgroups.
     */
    private List<ThreadGroup> subgroups() {
        assert Thread.holdsLock(this);
        List<ThreadGroup> snapshot = new ArrayList<>();
        for (int i = 0; i < ngroups; i++) {
            snapshot.add(groups[i]);
        }
        for (int i = 0; i < nweaks; ) {
            ThreadGroup g = weaks[i].get();
            if (g == null) {
                removeWeak(i);
            } else {
                snapshot.add(g);
                i++;
            }
        }
        return snapshot;
    }
}
