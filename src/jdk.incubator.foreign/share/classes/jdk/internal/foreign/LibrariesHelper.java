/*
 *  Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.  Oracle designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Oracle in the LICENSE file that accompanied this code.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */
package jdk.internal.foreign;

import jdk.incubator.foreign.MemoryAddress;

import java.io.File;
import jdk.incubator.foreign.LibraryLookup;
import jdk.internal.loader.NativeLibraries;
import jdk.internal.loader.NativeLibrary;
import jdk.internal.ref.CleanerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class LibrariesHelper {
    private LibrariesHelper() {}

    private final static NativeLibraries nativeLibraries =
            NativeLibraries.rawNativeLibraries(LibrariesHelper.class, true);

    private final static Map<NativeLibrary, AtomicInteger> loadedLibraries = new IdentityHashMap<>();

    /**
     * Load the specified shared library.
     *
     * @param name Name of the shared library to load.
     */
    public static LibraryLookup loadLibrary(String name) {
        return lookup(() -> nativeLibraries.loadLibrary(LibrariesHelper.class, name),
                "Library not found: " + name);
    }

    /**
     * Load the specified shared library.
     *
     * @param path Path of the shared library to load.
     */
    public static LibraryLookup load(String path) {
        File file = new File(path);
        if (!file.isAbsolute()) {
            throw new UnsatisfiedLinkError(
                    "Expecting an absolute path of the library: " + path);
        }
        return lookup(() -> nativeLibraries.loadLibrary(LibrariesHelper.class, file),
                "Library not found: " + path);
    }

    // return the absolute path of the library of given name by searching
    // in the given array of paths.
    private static Optional<Path> findLibraryPath(Path[] paths, String libName) {
         return Arrays.stream(paths).
              map(p -> p.resolve(System.mapLibraryName(libName))).
              filter(Files::isRegularFile).map(Path::toAbsolutePath).findFirst();
    }

    public static LibraryLookup getDefaultLibrary() {
        return LibraryLookupImpl.DEFAULT_LOOKUP;
    }

    synchronized static LibraryLookupImpl lookup(Supplier<NativeLibrary> librarySupplier, String notFoundMsg) {
        NativeLibrary library = librarySupplier.get();
        if (library == null) {
            throw new IllegalArgumentException(notFoundMsg);
        }
        AtomicInteger refCount = loadedLibraries.computeIfAbsent(library, lib -> new AtomicInteger());
        refCount.incrementAndGet();
        LibraryLookupImpl lookup = new LibraryLookupImpl(library);
        CleanerFactory.cleaner().register(lookup, () -> tryUnload(library));
        return lookup;
    }

    synchronized static void tryUnload(NativeLibrary library) {
        AtomicInteger refCount = loadedLibraries.get(library);
        if (refCount.decrementAndGet() == 0) {
            loadedLibraries.remove(library);
            nativeLibraries.unload(library);
        }
    }

    static class LibraryLookupImpl implements LibraryLookup {
        final NativeLibrary library;

        LibraryLookupImpl(NativeLibrary library) {
            this.library = library;
        }

        @Override
        public Optional<Symbol> lookup(String name) {
            try {
                Objects.requireNonNull(name);
                MemoryAddress addr = MemoryAddress.ofLong(library.lookup(name));
                return Optional.of(new Symbol() { // inner class - retains a link to enclosing lookup
                    @Override
                    public String name() {
                        return name;
                    }

                    @Override
                    public MemoryAddress address() {
                        return addr;
                    }
                });
            } catch (NoSuchMethodException ex) {
                return Optional.empty();
            }
        }

        static LibraryLookup DEFAULT_LOOKUP = new LibraryLookupImpl(NativeLibraries.defaultLibrary);
    }

    /* used for testing */
    public static int numLoadedLibraries() {
        return loadedLibraries.size();
    }
}
