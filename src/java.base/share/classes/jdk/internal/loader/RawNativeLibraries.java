/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.loader;

import jdk.internal.misc.VM;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RawNativeLibraries has the following properties:
 * 1. Native libraries loaded in this RawNativeLibraries instance are
 *    not JNI native libraries.  Hence JNI_OnLoad and JNI_OnUnload will
 *    be ignored.  No support for linking of native method.
 * 2. Native libraries not auto-unloaded.  They may be explicitly unloaded
 *    via NativeLibraries::unload.
 * 3. No relationship with class loaders.
 */
public final class RawNativeLibraries {
    final Map<String, RawNativeLibraryImpl> libraries = new ConcurrentHashMap<>();
    final Class<?> caller;

    private RawNativeLibraries(MethodHandles.Lookup trustedCaller) {
        this.caller = trustedCaller.lookupClass();
    }

    /**
     * Creates a RawNativeLibraries instance that has no relationship with
     * any class loaders and disabled auto unloading.
     *
     * This static factory method is restricted for JDK trusted class use.
     */
    public static RawNativeLibraries newInstance(MethodHandles.Lookup trustedCaller) {
        if (!trustedCaller.hasFullPrivilegeAccess() ||
                !VM.isSystemDomainLoader(trustedCaller.lookupClass().getClassLoader())) {
            throw new InternalError(trustedCaller + " does not have access to raw native library loading");
        }
        return new RawNativeLibraries(trustedCaller);
    }

    /*
     * Load a native library from the given path.  Returns null if the given
     * library is determined to be non-loadable, which is system-dependent.
     *
     * @param path the path of the native library
     */
    @SuppressWarnings("removal")
    public NativeLibrary load(Path path) {
        String name = AccessController.doPrivileged(new PrivilegedAction<>() {
            public String run() {
                try {
                    return path.toRealPath().toString();
                } catch (IOException e) {
                    return null;
                }
            }
        });
        if (name == null) {
            return null;
        }
        return load(name);
    }

    /**
     * Load a native library of the given pathname, which is platform-specific.
     * Returns null if it fails to load the given pathname.
     *
     * If the given pathname does not contain a name-separator character,
     * for example on Unix a slash character, the library search strategy
     * is system-dependent for example on Unix, see dlopen.
     *
     * @apiNote
     * The {@code pathname} argument is platform-specific.
     * {@link System#mapLibraryName} can be used to convert a name to
     * a platform-specific pathname:
     * {@snippet
     *     RawNativeLibraries libs = RawNativeLibraries.newInstance(MethodHandles.lookup());
     *     NativeLibrary lib = libs.load(System.mapLibraryName("blas"));
     * }
     *
     * @param pathname the pathname of the native library
     * @see System#mapLibraryName(String)
     */
    public NativeLibrary load(String pathname) {
         return libraries.computeIfAbsent(pathname, this::get);
    }

    private RawNativeLibraryImpl get(String pathname) {
        RawNativeLibraryImpl lib = new RawNativeLibraryImpl(caller, pathname);
        if (!lib.open()) {
            return null;
        }
        return lib;
    }

    /*
     * Unloads the given native library.
     */
    public void unload(NativeLibrary lib) {
        Objects.requireNonNull(lib);
        if (!libraries.remove(lib.name(), lib)) {
            throw new IllegalArgumentException(lib.name() + " not loaded by this RawNativeLibraries instance");
        }
        RawNativeLibraryImpl nl = (RawNativeLibraryImpl)lib;
        nl.close();
    }

    static class RawNativeLibraryImpl extends NativeLibrary {
        // the name of the raw native library.
        final String name;
        // opaque handle to raw native library, used in native code.
        long handle;

        RawNativeLibraryImpl(Class<?> fromClass, String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public long find(String name) {
            return findEntry0(handle, name);
        }

        /*
         * Loads the named native library.
         */
        boolean open() {
            if (handle != 0) {
                throw new InternalError("Native library " + name + " has been loaded");
            }
            return load0(this, name);
        }

        /*
         * Close this native library.
         */
        void close() {
            unload0(name, handle);
        }
    }

    private static native boolean load0(RawNativeLibraryImpl impl, String name);
    private static native void unload0(String name, long handle);
}

