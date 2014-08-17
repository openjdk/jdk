/*
 * Copyright (c) 2008, 2012, Oracle and/or its affiliates. All rights reserved.
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

package sun.tracing.dtrace;

import java.lang.reflect.Method;

/**
 * Container class for JVM interface native methods
 *
 * @since 1.7
 */
class JVM {

    static {
        java.security.AccessController.doPrivileged(
            new java.security.PrivilegedAction<Void>() {
                public Void run() {
                    System.loadLibrary("jsdt");
                    return null;
                }
            });
    }

    static long activate(String moduleName, DTraceProvider[] providers) {
        return activate0(moduleName, providers);
    }

    static void dispose(long handle) {
        dispose0(handle);
    }

    static boolean isEnabled(Method m) {
        return isEnabled0(m);
    }

    static boolean isSupported() {
        return isSupported0();
    }

    static Class<?> defineClass(
            ClassLoader loader, String name, byte[] b, int off, int len) {
        return defineClass0(loader, name, b, off, len);
    }

    private static native long activate0(
        String moduleName, DTraceProvider[] providers);
    private static native void dispose0(long activation_handle);
    private static native boolean isEnabled0(Method m);
    private static native boolean isSupported0();
    private static native Class<?> defineClass0(
        ClassLoader loader, String name, byte[] b, int off, int len);
}
