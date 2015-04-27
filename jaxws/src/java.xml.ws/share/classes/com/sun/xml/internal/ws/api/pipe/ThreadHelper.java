/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.api.pipe;

import java.lang.reflect.Constructor;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * Simple utility class to instantiate correct Thread instance
 * depending on runtime context (jdk/non-jdk usage)
 *
 * @author miroslav.kos@oracle.com
 */
final class ThreadHelper {

    private static final String SAFE_THREAD_NAME = "sun.misc.ManagedLocalsThread";
    private static final Constructor THREAD_CONSTRUCTOR;

    // no instantiating wanted
    private ThreadHelper() {
    }

    static {
        THREAD_CONSTRUCTOR = AccessController.doPrivileged(
                new PrivilegedAction<Constructor> () {
                    @Override
                    public Constructor run() {
                        try {
                            Class cls = Class.forName(SAFE_THREAD_NAME);
                            if (cls != null) {
                                return cls.getConstructor(Runnable.class);
                            }
                        } catch (ClassNotFoundException ignored) {
                        } catch (NoSuchMethodException ignored) {
                        }
                        return null;
                    }
                }
        );
    }

    static Thread createNewThread(final Runnable r) {
        if (isJDKInternal()) {
            return AccessController.doPrivileged(
                    new PrivilegedAction<Thread>() {
                        @Override
                        public Thread run() {
                            try {
                                return (Thread) THREAD_CONSTRUCTOR.newInstance(r);
                            } catch (Exception e) {
                                return new Thread(r);
                            }
                        }
                    }
            );
        } else {
            return new Thread(r);
        }
    }

    private static boolean isJDKInternal() {
        String className = ThreadHelper.class.getName();
        return className.contains(".internal.");
    }
}
