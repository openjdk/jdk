/*
 * Copyright 1995-1997 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.applet;

/**
 * This class defines an applet thread group.
 *
 * @author      Arthur van Hoff
 */
public class AppletThreadGroup extends ThreadGroup {

    /**
     * Constructs a new thread group for an applet.
     * The parent of this new group is the thread
     * group of the currently running thread.
     *
     * @param   name   the name of the new thread group.
     */
    public AppletThreadGroup(String name) {
        this(Thread.currentThread().getThreadGroup(), name);
    }

    /**
     * Creates a new thread group for an applet.
     * The parent of this new group is the specified
     * thread group.
     *
     * @param     parent   the parent thread group.
     * @param     name     the name of the new thread group.
     * @exception  NullPointerException  if the thread group argument is
     *               <code>null</code>.
     * @exception  SecurityException  if the current thread cannot create a
     *               thread in the specified thread group.
     * @see     java.lang.SecurityException
     * @since   JDK1.1.1
     */
    public AppletThreadGroup(ThreadGroup parent, String name) {
        super(parent, name);
        setMaxPriority(Thread.NORM_PRIORITY - 1);
    }
}
