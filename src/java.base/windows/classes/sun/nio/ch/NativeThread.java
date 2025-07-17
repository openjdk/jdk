/*
 * Copyright (c) 2002, 2023, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.ch;


// Signalling operations on native threads

public class NativeThread {
    private static final long VIRTUAL_THREAD_ID = -1L;

    /**
     * Returns the id of the current native thread if the platform can signal
     * native threads, 0 if the platform can not signal native threads, or
     * -1L if the current thread is a virtual thread.
     */
    public static long current() {
        if (Thread.currentThread().isVirtual()) {
            return VIRTUAL_THREAD_ID;
        } else {
            // no support for signalling threads on Windows
            return 0;
        }
    }

    /**
     * Signals the given native thread.
     *
     * @throws IllegalArgumentException if tid is not a token to a native thread
     */
    static void signal(long tid) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns true the tid is the id of a native thread.
     */
    static boolean isNativeThread(long tid) {
        return false;
    }

    /**
     * Returns true if tid is -1L.
     * @see #current()
     */
    static boolean isVirtualThread(long tid) {
        return (tid == VIRTUAL_THREAD_ID);
    }
}
