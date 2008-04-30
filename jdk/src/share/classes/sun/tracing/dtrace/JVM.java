/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
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
              new sun.security.action.LoadLibraryAction("jsdt"));
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
