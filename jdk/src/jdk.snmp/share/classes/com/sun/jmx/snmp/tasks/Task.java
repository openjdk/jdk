// NPCTE fix for bugId 4510777, esc 532372, MR October 2001
// file Task.java created for this bug fix
/*
 * Copyright (c) 2002, 2003, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.jmx.snmp.tasks;

/**
 * This interface is implemented by objects that can be executed
 * by a {@link com.sun.jmx.snmp.tasks.TaskServer}.
 * <p>A <code>Task</code> object implements two methods:
 * <ul><li><code>public void run(): </code> from
 *               {@link java.lang.Runnable}</li>
 * <ul>This method is called by the {@link com.sun.jmx.snmp.tasks.TaskServer}
 *     when the task is executed.</ul>
 * <li><code>public void cancel(): </code></li>
 * <ul>This method is called by the {@link com.sun.jmx.snmp.tasks.TaskServer}
 *     if the <code>TaskServer</code> is stopped before the
 *     <code>Task</code> is executed.</ul>
 * </ul>
 * An implementation of {@link com.sun.jmx.snmp.tasks.TaskServer} shall call
 * either <code>run()</code> or <code>cancel()</code>.
 * Whether the task is executed synchronously in the current
 * thread (when calling <code>TaskServer.submitTask()</code> or in a new
 * thread dedicated to the task, or in a daemon thread, depends on the
 * implementation of the <code>TaskServer</code> through which the task
 * is executed.
 * <p>The implementation of <code>Task</code> must not make any
 * assumption on the implementation of the <code>TaskServer</code> through
 * which it will be executed.
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 * @see com.sun.jmx.snmp.tasks.TaskServer
 *
 * @since 1.5
 **/
public interface Task extends Runnable {
    /**
     * Cancel the submitted task.
     * The implementation of this method is Task-implementation dependent.
     * It could involve some message logging, or even call the run() method.
     * Note that only one of run() or cancel() will be called - and exactly
     * one.
     **/
    public void cancel();
}
