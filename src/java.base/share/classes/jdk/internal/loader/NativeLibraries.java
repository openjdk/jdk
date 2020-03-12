/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.ref.CleanerFactory;
import jdk.internal.util.StaticProperty;

import java.io.File;
import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Native libraries are loaded via {@link System#loadLibrary(String)},
 * {@link System#load(String)}, {@link Runtime#loadLibrary(String)} and
 * {@link Runtime#load(String)}.  They are caller-sensitive.
 *
 * Each class loader has a NativeLibraries instance to register all of its
 * loaded native libraries.  System::loadLibrary (and other APIs) only
 * allows a native library to be loaded by one class loader, i.e. one
 * NativeLibraries instance.  Any attempt to load a native library that
 * has already been loaded by a class loader with another class loader
 * will fail.
 */
public final class NativeLibraries {

    private final Map<String, NativeLibrary> libraries = new ConcurrentHashMap<>();
    private final ClassLoader loader;
    private final Class<?> caller;      // may be null.  If not null, this is used as
                                        // fromClass as a fast-path.  See loadLibrary(String name).
    private final boolean searchJavaLibraryPath;

    public NativeLibraries(ClassLoader loader) {
        // for null loader, default the caller to this class and
        // do not search java.library.path
        this(loader, loader != null ? null : NativeLibraries.class, loader != null ? true : false);
    }
    public NativeLibraries(ClassLoader loader, Class<?> caller, boolean searchJavaLibraryPath) {
        if (caller != null && caller.getClassLoader() != loader) {
            throw new IllegalArgumentException(caller.getName() + " must be defined by " + loader);
        }
        this.loader = loader;
        this.caller = caller;
        this.searchJavaLibraryPath = searchJavaLibraryPath;
    }

    /*
     * Find the address of the given symbol name from the native libraries
     * loaded in this NativeLibraries instance.
     */
    public long find(String name) {
        if (libraries.isEmpty())
            return 0;

        // the native libraries map may be updated in another thread
        // when a native library is being loaded.  No symbol will be
        // searched from it yet.
        for (NativeLibrary lib : libraries.values()) {
            long entry = lib.find(name);
            if (entry != 0) return entry;
        }
        return 0;
    }

    /*
     * Load a native library from the given file.  Returns null if file does not exist.
     *
     * @param fromClass the caller class calling System::loadLibrary
     * @param file the path of the native library
     * @throws UnsatisfiedLinkError if any error in loading the native library
     */
    public NativeLibrary loadLibrary(Class<?> fromClass, File file) {
        // Check to see if we're attempting to access a static library
        String name = findBuiltinLib(file.getName());
        boolean isBuiltin = (name != null);
        if (!isBuiltin) {
            name = AccessController.doPrivileged(new PrivilegedAction<>() {
                public String run() {
                    try {
                        return file.exists() ? file.getCanonicalPath() : null;
                    } catch (IOException e) {
                        return null;
                    }
                }
            });
            if (name == null) {
                return null;
            }
        }
        return loadLibrary(fromClass, name, isBuiltin);
    }

    /**
     * Returns a NativeLibrary of the given name.
     *
     * @param fromClass the caller class calling System::loadLibrary
     * @param name      library name
     * @param isBuiltin built-in library
     * @throws UnsatisfiedLinkError if the native library has already been loaded
     *      and registered in another NativeLibraries
     */
    private NativeLibrary loadLibrary(Class<?> fromClass, String name, boolean isBuiltin) {
        ClassLoader loader = (fromClass == null) ? null : fromClass.getClassLoader();
        if (this.loader != loader) {
            throw new InternalError(fromClass.getName() + " not allowed to load library");
        }

        synchronized (loadedLibraryNames) {
            // find if this library has already been loaded and registered in this NativeLibraries
            NativeLibrary cached = libraries.get(name);
            if (cached != null) {
                return cached;
            }

            // cannot be loaded by other class loaders
            if (loadedLibraryNames.contains(name)) {
                throw new UnsatisfiedLinkError("Native Library " + name +
                        " already loaded in another classloader");
            }

            /*
             * When a library is being loaded, JNI_OnLoad function can cause
             * another loadLibrary invocation that should succeed.
             *
             * We use a static stack to hold the list of libraries we are
             * loading because this can happen only when called by the
             * same thread because this block is synchronous.
             *
             * If there is a pending load operation for the library, we
             * immediately return success; otherwise, we raise
             * UnsatisfiedLinkError.
             */
            for (NativeLibraryImpl lib : nativeLibraryContext) {
                if (name.equals(lib.name())) {
                    if (loader == lib.fromClass.getClassLoader()) {
                        return lib;
                    } else {
                        throw new UnsatisfiedLinkError("Native Library " +
                                name + " is being loaded in another classloader");
                    }
                }
            }

            NativeLibraryImpl lib = new NativeLibraryImpl(fromClass, name, isBuiltin);
            // load the native library
            nativeLibraryContext.push(lib);
            try {
                if (!lib.open()) return null;
            } finally {
                nativeLibraryContext.pop();
            }
            // register the loaded native library
            loadedLibraryNames.add(name);
            libraries.put(name, lib);
            return lib;
        }
    }

    /**
     * Loads a native library from the system library path and java library path.
     *
     * @param name library name
     *
     * @throws UnsatisfiedLinkError if the native library has already been loaded
     *      and registered in another NativeLibraries
     */
    public NativeLibrary loadLibrary(String name) {
        assert name.indexOf(File.separatorChar) < 0;
        assert caller != null;

        return loadLibrary(caller, name);
    }

    /**
     * Loads a native library from the system library path and java library path.
     *
     * @param name library name
     * @param fromClass the caller class calling System::loadLibrary
     *
     * @throws UnsatisfiedLinkError if the native library has already been loaded
     *      and registered in another NativeLibraries
     */
    public NativeLibrary loadLibrary(Class<?> fromClass, String name) {
        assert name.indexOf(File.separatorChar) < 0;

        NativeLibrary lib = findFromPaths(LibraryPaths.SYS_PATHS, fromClass, name);
        if (lib == null && searchJavaLibraryPath) {
            lib = findFromPaths(LibraryPaths.USER_PATHS, fromClass, name);
        }
        return lib;
    }

    private NativeLibrary findFromPaths(String[] paths, Class<?> fromClass, String name) {
        for (String path : paths) {
            File libfile = new File(path, System.mapLibraryName(name));
            NativeLibrary nl = loadLibrary(fromClass, libfile);
            if (nl != null) {
                return nl;
            }
            libfile = ClassLoaderHelper.mapAlternativeName(libfile);
            if (libfile != null) {
                nl = loadLibrary(fromClass, libfile);
                if (nl != null) {
                    return nl;
                }
            }
        }
        return null;
    }

    /**
     * NativeLibraryImpl denotes a loaded native library instance.
     * Each NativeLibraries contains a map of loaded native libraries in the
     * private field {@code libraries}.
     *
     * Every native library requires a particular version of JNI. This is
     * denoted by the private {@code jniVersion} field.  This field is set by
     * the VM when it loads the library, and used by the VM to pass the correct
     * version of JNI to the native methods.
     */
    static class NativeLibraryImpl implements NativeLibrary {
        // the class from which the library is loaded, also indicates
        // the loader this native library belongs.
        final Class<?> fromClass;
        // the canonicalized name of the native library.
        // or static library name
        final String name;
        // Indicates if the native library is linked into the VM
        final boolean isBuiltin;

        // opaque handle to native library, used in native code.
        long handle;
        // the version of JNI environment the native library requires.
        int jniVersion;

        NativeLibraryImpl(Class<?> fromClass, String name, boolean isBuiltin) {
            this.fromClass = fromClass;
            this.name = name;
            this.isBuiltin = isBuiltin;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public long find(String name) {
            return findEntry0(this, name);
        }

        /*
         * Loads the native library and registers for cleanup when its
         * associated class loader is unloaded
         */
        boolean open() {
            if (handle != 0) {
                throw new InternalError("Native library " + name + " has been loaded");
            }

            if (!load(this, name, isBuiltin)) return false;

            // register the class loader for cleanup when unloaded
            // builtin class loaders are never unloaded
            ClassLoader loader = fromClass != null ? fromClass.getClassLoader() : null;
            if (loader != null &&
                    loader != ClassLoaders.platformClassLoader() &&
                    loader != ClassLoaders.appClassLoader()) {
                CleanerFactory.cleaner().register(loader, new Unloader(name, handle, isBuiltin));
            }
            return true;
        }
    }

    /*
     * The run() method will be invoked when this class loader becomes
     * phantom reachable to unload the native library.
     */
    static class Unloader implements Runnable {
        // This represents the context when a native library is unloaded
        // and getFromClass() will return null,
        static final NativeLibraryImpl UNLOADER =
                new NativeLibraryImpl(null, "dummy", false);

        final String name;
        final long handle;
        final boolean isBuiltin;

        Unloader(String name, long handle, boolean isBuiltin) {
            if (handle == 0) {
                throw new IllegalArgumentException(
                        "Invalid handle for native library " + name);
            }

            this.name = name;
            this.handle = handle;
            this.isBuiltin = isBuiltin;
        }

        @Override
        public void run() {
            synchronized (NativeLibraries.loadedLibraryNames) {
                /* remove the native library name */
                NativeLibraries.loadedLibraryNames.remove(name);
                NativeLibraries.nativeLibraryContext.push(UNLOADER);
                try {
                    unload(name, isBuiltin, handle);
                } finally {
                    NativeLibraries.nativeLibraryContext.pop();
                }
            }
        }
    }

    /*
     * Holds system and user library paths derived from the
     * {@code java.library.path} and {@code sun.boot.library.path} system
     * properties. The system properties are eagerly read at bootstrap, then
     * lazily parsed on first use to avoid initialization ordering issues.
     */
    static class LibraryPaths {
        // The paths searched for libraries
        static final String[] SYS_PATHS = ClassLoaderHelper.parsePath(StaticProperty.sunBootLibraryPath());
        static final String[] USER_PATHS = ClassLoaderHelper.parsePath(StaticProperty.javaLibraryPath());
    }

    // All native libraries we've loaded.
    // This also serves as the lock to obtain nativeLibraries
    // and write to nativeLibraryContext.
    private static final Set<String> loadedLibraryNames = new HashSet<>();

    // native libraries being loaded
    private static Deque<NativeLibraryImpl> nativeLibraryContext = new ArrayDeque<>(8);

    // Invoked in the VM to determine the context class in JNI_OnLoad
    // and JNI_OnUnload
    private static Class<?> getFromClass() {
        if (nativeLibraryContext.isEmpty()) { // only default library
            return Object.class;
        }
        return nativeLibraryContext.peek().fromClass;
    }

    // JNI FindClass expects the caller class if invoked from JNI_OnLoad
    // and JNI_OnUnload is NativeLibrary class
    private static native boolean load(NativeLibraryImpl impl, String name, boolean isBuiltin);
    private static native void unload(String name, boolean isBuiltin, long handle);
    private static native String findBuiltinLib(String name);
    private static native long findEntry0(NativeLibraryImpl lib, String name);
}
