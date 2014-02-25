/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package sun.lwawt.macosx;

import java.awt.EventQueue;


public class CThreading {
    static String APPKIT_THREAD_NAME = "AppKit Thread";

    static boolean isEventQueue() {
        return EventQueue.isDispatchThread();
    }

    static boolean isAppKit() {
        return APPKIT_THREAD_NAME.equals(Thread.currentThread().getName());
    }

    static boolean assertEventQueue() {
        final boolean isEventQueue = isEventQueue();
        assert isEventQueue : "Threading violation: not EventQueue thread";
        return isEventQueue;
    }

    static boolean assertNotEventQueue() {
        final boolean isNotEventQueue = isEventQueue();
        assert isNotEventQueue : "Threading violation: EventQueue thread";
        return isNotEventQueue;
    }

    static boolean assertAppKit() {
        final boolean isAppKitThread = isAppKit();
        assert isAppKitThread : "Threading violation: not AppKit thread";
        return isAppKitThread;
    }

    static boolean assertNotAppKit() {
        final boolean isNotAppKitThread = !isAppKit();
        assert isNotAppKitThread : "Threading violation: AppKit thread";
        return isNotAppKitThread;
    }
}
