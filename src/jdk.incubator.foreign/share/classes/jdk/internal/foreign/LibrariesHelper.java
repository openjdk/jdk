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
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;
import jdk.internal.loader.NativeLibraries;
import jdk.internal.loader.NativeLibrary;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class LibrariesHelper {
    private LibrariesHelper() {}

    private static final NativeLibraries nativeLibraries =
            NativeLibraries.rawNativeLibraries(LibrariesHelper.class, true);

    private static final Map<NativeLibrary, WeakReference<ResourceScope>> loadedLibraries = new ConcurrentHashMap<>();

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

    public static LibraryLookup getDefaultLibrary() {
        return LibraryLookupImpl.DEFAULT_LOOKUP;
    }

    static LibraryLookupImpl lookup(Supplier<NativeLibrary> librarySupplier, String notFoundMsg) {
        NativeLibrary library = librarySupplier.get();
        if (library == null) {
            throw new IllegalArgumentException(notFoundMsg);
        }
        ResourceScope[] holder = new ResourceScope[1];
        try {
            WeakReference<ResourceScope> scopeRef = loadedLibraries.computeIfAbsent(library, lib -> {
                ResourceScopeImpl s = ResourceScopeImpl.createImplicitScope();
                holder[0] = s; // keep the scope alive at least until the outer method returns
                s.addOrCleanupIfFail(ResourceScopeImpl.ResourceList.ResourceCleanup.ofRunnable(() -> {
                    nativeLibraries.unload(library);
                    loadedLibraries.remove(library);
                }));
                return new WeakReference<>(s);
            });
            return new LibraryLookupImpl(library, scopeRef.get());
        } finally {
            Reference.reachabilityFence(holder);
        }
    }

    //Todo: in principle we could expose a scope accessor, so that users could unload libraries at will
    static final class LibraryLookupImpl implements LibraryLookup {
        final NativeLibrary library;
        final MemorySegment librarySegment;

        LibraryLookupImpl(NativeLibrary library, ResourceScope scope) {
            this.library = library;
            this.librarySegment = MemoryAddress.NULL.asSegment(Long.MAX_VALUE, scope);
        }

        @Override
        public final Optional<MemoryAddress> lookup(String name) {
            try {
                Objects.requireNonNull(name);
                MemoryAddress addr = MemoryAddress.ofLong(library.lookup(name));
                return Optional.of(librarySegment.asSlice(addr).address());
            } catch (NoSuchMethodException ex) {
                return Optional.empty();
            }
        }

        @Override
        public final Optional<MemorySegment> lookup(String name, MemoryLayout layout) {
            try {
                Objects.requireNonNull(name);
                Objects.requireNonNull(layout);
                MemoryAddress addr = MemoryAddress.ofLong(library.lookup(name));
                if (addr.toRawLongValue() % layout.byteAlignment() != 0) {
                    throw new IllegalArgumentException("Bad layout alignment constraints: " + layout.byteAlignment());
                }
                return Optional.of(librarySegment.asSlice(addr, layout.byteSize()));
            } catch (NoSuchMethodException ex) {
                return Optional.empty();
            }
        }

        static LibraryLookup DEFAULT_LOOKUP = new LibraryLookupImpl(NativeLibraries.defaultLibrary, ResourceScopeImpl.GLOBAL);
    }

    /* used for testing */
    public static int numLoadedLibraries() {
        return loadedLibraries.size();
    }
}
