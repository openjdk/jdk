/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.rmi.runtime;

/**
 * A utility class for creating threads. The constructors take a
 * variety of parameters to configure the thread. The run() method
 * creates and sets up the thread and returns it, but does not
 * start it.
 *
 * All constructors allow the choice of the Runnable for the new
 * thread to execute, the name of the new thread (which will be
 * prefixed with "RMI "), and whether or not it will be a daemon
 * thread.
 *
 * The new thread may be created in the system thread group (the root
 * of the thread group tree) or an internally created non-system
 * thread group, as specified at construction of this class.
 *
 * The new thread will have the system class loader as its initial
 * context class loader (that is, its context class loader will NOT be
 * inherited from the current thread).
 *
 * @author      Peter Jones
 **/
public final class NewThreadAction {

    /** cached reference to the system (root) thread group */
    static final ThreadGroup systemThreadGroup;
    static {
        ThreadGroup group = Thread.currentThread().getThreadGroup();
        ThreadGroup parent;
        while ((parent = group.getParent()) != null) {
            group = parent;
        }
        systemThreadGroup = group;
    }


    /**
     * Special child of the system thread group for running tasks that
     * may execute user code. The need for a separate thread group may
     * be a vestige of it having had a different security policy from
     * the system thread group, so this might no longer be necessary.
     */
    static final ThreadGroup userThreadGroup = new ThreadGroup(systemThreadGroup, "RMI Runtime");

    private final ThreadGroup group;
    private final Runnable runnable;
    private final String name;
    private final boolean daemon;

    NewThreadAction(ThreadGroup group, Runnable runnable,
                    String name, boolean daemon)
    {
        this.group = group;
        this.runnable = runnable;
        this.name = name;
        this.daemon = daemon;
    }

    /**
     * Creates an action that will create a new thread in the
     * system thread group.
     *
     * @param   runnable the Runnable for the new thread to execute
     *
     * @param   name the name of the new thread
     *
     * @param   daemon if true, new thread will be a daemon thread;
     * if false, new thread will not be a daemon thread
     */
    public NewThreadAction(Runnable runnable, String name, boolean daemon) {
        this(systemThreadGroup, runnable, name, daemon);
    }

    /**
     * Creates an action that will create a new thread.
     *
     * @param   runnable the Runnable for the new thread to execute
     *
     * @param   name the name of the new thread
     *
     * @param   daemon if true, new thread will be a daemon thread;
     * if false, new thread will not be a daemon thread
     *
     * @param   user if true, thread will be created in a non-system
     * thread group; if false, thread will be created in the system
     * thread group
     */
    public NewThreadAction(Runnable runnable, String name, boolean daemon,
                           boolean user)
    {
        this(user ? userThreadGroup : systemThreadGroup,
             runnable, name, daemon);
    }

    public Thread run() {
        Thread t = new Thread(group, runnable, "RMI " + name);
        t.setContextClassLoader(ClassLoader.getSystemClassLoader());
        t.setDaemon(daemon);
        return t;
    }
}
