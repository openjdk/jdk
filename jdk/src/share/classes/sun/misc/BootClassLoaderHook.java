/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.misc;

import java.io.File;
import java.io.IOException;

/**
 * BootClassLoaderHook defines an interface for a hook to inject
 * into the bootstrap class loader.
 *
 * In jkernel build, the sun.jkernel.DownloadManager is set as
 * a BootClassLoaderHook by the jkernel VM after the VM is initialized.
 *
 * In other JDK builds, no hook is set.
 */
public abstract class BootClassLoaderHook {
    private static BootClassLoaderHook bootLoaderHook = null;
    public static synchronized BootClassLoaderHook getHook() {
        return bootLoaderHook;
    }

    public static synchronized void setHook(BootClassLoaderHook hook) {
        if (!VM.isBooted()) {
            throw new InternalError("hook can only be set after VM is booted");
        }
        if (bootLoaderHook != null) {
            throw new InternalError("hook should not be reinitialized");
        }
        bootLoaderHook = hook;
    }

    protected BootClassLoaderHook() {
    }

    /**
     * A method to be invoked before a class loader loads
     * a bootstrap class.
     *
     * @param classname the binary name of the class
     */
    public static void preLoadClass(String classname) {
        BootClassLoaderHook hook = getHook();
        if (hook != null) {
            hook.loadBootstrapClass(classname);
        }
    }

    /**
     * A method to be invoked before a class loader loads
     * a resource.
     *
     * @param resourcename the resource name
     */
    public static void preLoadResource(String resourcename) {
        BootClassLoaderHook hook = getHook();
        if (hook != null) {
            hook.getBootstrapResource(resourcename);
        }
    }

    /**
     * A method to be invoked before a library is loaded.
     *
     * @param libname the name of the library
     */
    public static void preLoadLibrary(String libname) {
        BootClassLoaderHook hook = getHook();
        if (hook != null) {
            hook.loadLibrary(libname);
        }
    }

    private static final File[] EMPTY_FILE_ARRAY = new File[0];

    /**
     * Returns bootstrap class paths added by the hook.
     */
    public static File[] getBootstrapPaths() {
        BootClassLoaderHook hook = getHook();
        if (hook != null) {
            return hook.getBootstrapPaths();
        } else {
            return EMPTY_FILE_ARRAY;
        }
    }

    /**
     * Returns a pathname of a JAR or class that the hook loads
     * per this loadClass request; or null.
     *
     * @param classname the binary name of the class
     */
    public abstract String loadBootstrapClass(String className);

    /**
     * Returns a pathname of a resource file that the hook loads
     * per this getResource request; or null.
     *
     * @param resourceName the resource name
     */
    public abstract String getBootstrapResource(String resourceName);

    /**
     * Returns true if the hook successfully performs an operation per
     * this loadLibrary request; or false if it fails.
     *
     * @param libname the name of the library
     */
    public abstract boolean loadLibrary(String libname);

    /**
     * Returns additional boot class paths added by the hook that
     * should be searched by the boot class loader.
     */
    public abstract File[] getAdditionalBootstrapPaths();

    /**
     * Returns true if the current thread is in the process of doing
     * a prefetching operation.
     */
    public abstract boolean isCurrentThreadPrefetching();

    /**
     * Returns true if the hook successfully prefetches the specified file.
     *
     * @param name a platform independent pathname
     */
    public abstract boolean prefetchFile(String name);
}
