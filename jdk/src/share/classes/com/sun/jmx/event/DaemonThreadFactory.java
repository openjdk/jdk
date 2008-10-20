/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.jmx.event;

import com.sun.jmx.remote.util.ClassLogger;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class DaemonThreadFactory implements ThreadFactory {
    public DaemonThreadFactory(String nameTemplate) {
        this(nameTemplate, null);
    }

    // nameTemplate should be a format with %d in it, which will be replaced
    // by a sequence number of threads created by this factory.
    public DaemonThreadFactory(String nameTemplate, ThreadGroup threadGroup) {
        if (logger.debugOn()) {
            logger.debug("DaemonThreadFactory",
                    "Construct a new daemon factory: "+nameTemplate);
        }

        if (threadGroup == null) {
            SecurityManager s = System.getSecurityManager();
            threadGroup = (s != null) ? s.getThreadGroup() :
                                  Thread.currentThread().getThreadGroup();
        }

        this.nameTemplate = nameTemplate;
        this.threadGroup = threadGroup;
    }

    public Thread newThread(Runnable r) {
        final String name =
                String.format(nameTemplate, threadNumber.getAndIncrement());
        Thread t = new Thread(threadGroup, r, name, 0);
        t.setDaemon(true);
        if (t.getPriority() != Thread.NORM_PRIORITY)
            t.setPriority(Thread.NORM_PRIORITY);

        if (logger.debugOn()) {
            logger.debug("newThread",
                    "Create a new daemon thread with the name "+t.getName());
        }

        return t;
    }

    private final String nameTemplate;
    private final ThreadGroup threadGroup;
    private final AtomicInteger threadNumber = new AtomicInteger(1);

    private static final ClassLogger logger =
        new ClassLogger("com.sun.jmx.event", "DaemonThreadFactory");
}
