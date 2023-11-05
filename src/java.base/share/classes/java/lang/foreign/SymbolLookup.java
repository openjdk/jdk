/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.foreign;

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.foreign.MemorySessionImpl;
import jdk.internal.foreign.Utils;
import jdk.internal.javac.Restricted;
import jdk.internal.loader.BuiltinClassLoader;
import jdk.internal.loader.NativeLibrary;
import jdk.internal.loader.RawNativeLibraries;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;

import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * A <em>symbol lookup</em> retrieves the address of a symbol in one or more libraries.
 * A symbol is a named entity, such as a function or a global variable.
 * <p>
 * A symbol lookup is created with respect to a particular library (or libraries). Subsequently, the {@link SymbolLookup#find(String)}
 * method takes the name of a symbol and returns the address of the symbol in that library.
 * <p>
 * The address of a symbol is modelled as a zero-length {@linkplain MemorySegment memory segment}. The segment can be used in different ways:
 * <ul>
 *     <li>It can be passed to a {@link Linker} to create a downcall method handle, which can then be used to call the foreign function at the segment's address.</li>
 *     <li>It can be passed to an existing {@linkplain Linker#downcallHandle(FunctionDescriptor, Linker.Option...) downcall method handle}, as an argument to the underlying foreign function.</li>
 *     <li>It can be {@linkplain MemorySegment#set(AddressLayout, long, MemorySegment) stored} inside another memory segment.</li>
 *     <li>It can be used to access the region of memory backing a global variable (this requires
 *     {@linkplain MemorySegment#reinterpret(long) resizing} the segment first).</li>
 * </ul>
 *
 * <h2 id="obtaining">Obtaining a symbol lookup</h2>
 *
 * The factory methods {@link #libraryLookup(String, Arena)} and {@link #libraryLookup(Path, Arena)}
 * create a symbol lookup for a library known to the operating system. The library is specified by either its name or a path.
 * The library is loaded if not already loaded. The symbol lookup, which is known as a <em>library lookup</em>, and its
 * lifetime is controlled by an {@linkplain Arena arena}. For instance, if the provided arena is a
 * confined arena, the library associated with the symbol lookup is unloaded when the confined arena
 * is {@linkplain Arena#close() closed}:
 *
 * {@snippet lang = java:
 * try (Arena arena = Arena.ofConfined()) {
 *     SymbolLookup libGL = SymbolLookup.libraryLookup("libGL.so", arena); // libGL.so loaded here
 *     MemorySegment glGetString = libGL.find("glGetString").orElseThrow();
 *     ...
 * } //  libGL.so unloaded here
 *}
 * <p>
 * If a library was previously loaded through JNI, i.e., by {@link System#load(String)}
 * or {@link System#loadLibrary(String)}, then the library was also associated with a particular class loader. The factory
 * method {@link #loaderLookup()} creates a symbol lookup for all the libraries associated with the caller's class loader:
 *
 * {@snippet lang=java :
 * System.loadLibrary("GL"); // libGL.so loaded here
 * ...
 * SymbolLookup libGL = SymbolLookup.loaderLookup();
 * MemorySegment glGetString = libGL.find("glGetString").orElseThrow();
 * }
 *
 * This symbol lookup, which is known as a <em>loader lookup</em>, is dynamic with respect to the libraries associated
 * with the class loader. If other libraries are subsequently loaded through JNI and associated with the class loader,
 * then the loader lookup will expose their symbols automatically.
 * <p>
 * Note that a loader lookup only exposes symbols in libraries that were previously loaded through JNI, i.e.,
 * by {@link System#load(String)} or {@link System#loadLibrary(String)}. A loader lookup does not expose symbols in libraries
 * that were loaded in the course of creating a library lookup:
 *
 * {@snippet lang = java:
 * libraryLookup("libGL.so", arena).find("glGetString").isPresent(); // true
 * loaderLookup().find("glGetString").isPresent(); // false
 *}
 *
 * Note also that a library lookup for library {@code L} exposes symbols in {@code L} even if {@code L} was previously loaded
 * through JNI (the association with a class loader is immaterial to the library lookup):
 *
 * {@snippet lang = java:
 * System.loadLibrary("GL"); // libGL.so loaded here
 * libraryLookup("libGL.so", arena).find("glGetString").isPresent(); // true
 *}
 *
 * <p>
 * Finally, each {@link Linker} provides a symbol lookup for libraries that are commonly used on the OS and processor
 * combination supported by that {@link Linker}. This symbol lookup, which is known as a <em>default lookup</em>,
 * helps clients to quickly find addresses of well-known symbols. For example, a {@link Linker} for Linux/x64 might choose to
 * expose symbols in {@code libc} through the default lookup:
 *
 * {@snippet lang = java:
 * Linker nativeLinker = Linker.nativeLinker();
 * SymbolLookup stdlib = nativeLinker.defaultLookup();
 * MemorySegment malloc = stdlib.find("malloc").orElseThrow();
 *}
 *
 * @since 22
 */
@FunctionalInterface
public interface SymbolLookup {

    /**
     * Returns the address of the symbol with the given name.
     * @param name the symbol name.
     * @return a zero-length memory segment whose address indicates the address of the symbol, if found.
     */
    Optional<MemorySegment> find(String name);

    /**
     * {@return a composed symbol lookup that returns result of finding the symbol with this lookup if found,
     * otherwise returns the result of finding the symbol with the other lookup}
     *
     * @apiNote This method could be used to chain multiple symbol lookups together, e.g. so that symbols could
     * be retrieved, in order, from multiple libraries:
     * {@snippet lang = java:
     * var lookup = SymbolLookup.libraryLookup("foo", arena)
     *         .or(SymbolLookup.libraryLookup("bar", arena))
     *         .or(SymbolLookup.loaderLookup());
     *}
     * The above code creates a symbol lookup that first searches for symbols in the "foo" library. If no symbol is found
     * in "foo" then "bar" is searched. Finally, if a symbol is not found in neither "foo" nor "bar", the {@linkplain
     * SymbolLookup#loaderLookup() loader lookup} is used.
     *
     * @param other the symbol lookup that should be used to look for symbols not found in this lookup.
     */
    default SymbolLookup or(SymbolLookup other) {
        Objects.requireNonNull(other);
        return name -> find(name).or(() -> other.find(name));
    }

    /**
     * Returns a symbol lookup for symbols in the libraries associated with the caller's class loader.
     * <p>
     * A library is associated with a class loader {@code CL} when the library is loaded via an invocation of
     * {@link System#load(String)} or {@link System#loadLibrary(String)} from code in a class defined by {@code CL}.
     * If that code makes further invocations of {@link System#load(String)} or {@link System#loadLibrary(String)},
     * then more libraries are loaded and associated with {@code CL}. The symbol lookup returned by this method is always
     * current: it reflects all the libraries associated with the relevant class loader, even if they were loaded after
     * this method returned.
     * <p>
     * Libraries associated with a class loader are unloaded when the class loader becomes
     * <a href="../../../java/lang/ref/package.html#reachability">unreachable</a>. The symbol lookup
     * returned by this method is associated with an automatic {@linkplain MemorySegment.Scope scope} which keeps the caller's
     * class loader reachable. Therefore, libraries associated with the caller's class loader are kept loaded
     * (and their symbols available) as long as a loader lookup for that class loader, or any of the segments
     * obtained by it, is reachable.
     * <p>
     * In cases where this method is called from a context where there is no caller frame on the stack
     * (e.g. when called directly from a JNI attached thread), the caller's class loader defaults to the
     * {@linkplain ClassLoader#getSystemClassLoader system class loader}.
     *
     * @return a symbol lookup for symbols in the libraries associated with the caller's class loader.
     * @see System#load(String)
     * @see System#loadLibrary(String)
     */
    @CallerSensitive
    static SymbolLookup loaderLookup() {
        Class<?> caller = Reflection.getCallerClass();
        // If there's no caller class, fallback to system loader
        ClassLoader loader = caller != null ?
                caller.getClassLoader() :
                ClassLoader.getSystemClassLoader();
        Arena loaderArena;// builtin loaders never go away
        if ((loader == null || loader instanceof BuiltinClassLoader)) {
            loaderArena = Arena.global();
        } else {
            MemorySessionImpl session = MemorySessionImpl.createHeap(loader);
            loaderArena = session.asArena();
        }
        return name -> {
            Objects.requireNonNull(name);
            if (Utils.containsNullChars(name)) return Optional.empty();
            JavaLangAccess javaLangAccess = SharedSecrets.getJavaLangAccess();
            // note: ClassLoader::findNative supports a null loader
            long addr = javaLangAccess.findNative(loader, name);
            return addr == 0L ?
                    Optional.empty() :
                    Optional.of(MemorySegment.ofAddress(addr)
                                    .reinterpret(loaderArena, null));
        };
    }

    /**
     * Loads a library with the given name (if not already loaded) and creates a symbol lookup for symbols in that library.
     * The lifetime of the returned library lookup is controlled by the provided arena.
     * For instance, if the provided arena is a confined arena, the library
     * associated with the returned lookup will be unloaded when the provided confined arena is
     * {@linkplain Arena#close() closed}.
     *
     * @implNote The process of resolving a library name is OS-specific. For instance, in a POSIX-compliant OS,
     * the library name is resolved according to the specification of the {@code dlopen} function for that OS.
     * In Windows, the library name is resolved according to the specification of the {@code LoadLibrary} function.
     *
     * @param name the name of the library in which symbols should be looked up.
     * @param arena the arena associated with symbols obtained from the returned lookup.
     * @return a new symbol lookup suitable to find symbols in a library with the given name.
     * @throws IllegalStateException if {@code arena.scope().isAlive() == false}
     * @throws WrongThreadException if {@code arena} is a confined arena, and this method is called from a
     *         thread {@code T}, other than the arena's owner thread
     * @throws IllegalArgumentException if {@code name} does not identify a valid library
     * @throws IllegalCallerException If the caller is in a module that does not have native access enabled
     */
    @CallerSensitive
    @Restricted
    static SymbolLookup libraryLookup(String name, Arena arena) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass(), SymbolLookup.class, "libraryLookup");
        if (Utils.containsNullChars(name)) {
            throw new IllegalArgumentException("Cannot open library: " + name);
        }
        return libraryLookup(name, RawNativeLibraries::load, arena);
    }

    /**
     * Loads a library from the given path (if not already loaded) and creates a symbol lookup for symbols
     * in that library. The lifetime of the returned library lookup is controlled by the provided arena.
     * For instance, if the provided arena is a confined arena, the library
     * associated with the returned lookup will be unloaded when the provided confined arena is
     * {@linkplain Arena#close() closed}.
     *
     * @implNote On Linux, the functionalities provided by this factory method and the returned symbol lookup are
     * implemented using the {@code dlopen}, {@code dlsym} and {@code dlclose} functions.
     * @param path the path of the library in which symbols should be looked up.
     * @param arena the arena associated with symbols obtained from the returned lookup.
     * @return a new symbol lookup suitable to find symbols in a library with the given path.
     * @throws IllegalStateException if {@code arena.scope().isAlive() == false}
     * @throws WrongThreadException if {@code arena} is a confined arena, and this method is called from a
     *         thread {@code T}, other than the arena's owner thread
     * @throws IllegalArgumentException if {@code path} does not point to a valid library
     * @throws IllegalCallerException If the caller is in a module that does not have native access enabled
     */
    @CallerSensitive
    @Restricted
    static SymbolLookup libraryLookup(Path path, Arena arena) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass(), SymbolLookup.class, "libraryLookup");
        return libraryLookup(path, RawNativeLibraries::load, arena);
    }

    private static <Z> SymbolLookup libraryLookup(Z libDesc, BiFunction<RawNativeLibraries, Z, NativeLibrary> loadLibraryFunc, Arena libArena) {
        Objects.requireNonNull(libDesc);
        Objects.requireNonNull(libArena);
        // attempt to load native library from path or name
        RawNativeLibraries nativeLibraries = RawNativeLibraries.newInstance(MethodHandles.lookup());
        NativeLibrary library = loadLibraryFunc.apply(nativeLibraries, libDesc);
        if (library == null) {
            throw new IllegalArgumentException("Cannot open library: " + libDesc);
        }
        // register hook to unload library when 'libScope' becomes not alive
        MemorySessionImpl.toMemorySession(libArena).addOrCleanupIfFail(new MemorySessionImpl.ResourceList.ResourceCleanup() {
            @Override
            public void cleanup() {
                nativeLibraries.unload(library);
            }
        });
        return name -> {
            Objects.requireNonNull(name);
            if (Utils.containsNullChars(name)) return Optional.empty();
            long addr = library.find(name);
            return addr == 0L ?
                    Optional.empty() :
                    Optional.of(MemorySegment.ofAddress(addr)
                            .reinterpret(libArena, null));
        };
    }
}
