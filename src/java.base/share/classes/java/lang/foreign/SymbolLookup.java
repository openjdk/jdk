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

package java.lang.foreign;

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.foreign.MemorySessionImpl;
import jdk.internal.javac.PreviewFeature;
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
 *     <li>It can be {@linkplain MemorySegment#set(ValueLayout.OfAddress, long, MemorySegment) stored} inside another memory segment.</li>
 *     <li>It can be used to access the region of memory backing a global variable (this might require
 *     {@link MemorySegment#ofAddress(long, long, SegmentScope) resizing} the segment first).</li>
 * </ul>
 *
 * <h2 id="obtaining">Obtaining a symbol lookup</h2>
 *
 * The factory methods {@link #libraryLookup(String, SegmentScope)} and {@link #libraryLookup(Path, SegmentScope)}
 * create a symbol lookup for a library known to the operating system. The library is specified by either its name or a path.
 * The library is loaded if not already loaded. The symbol lookup, which is known as a <em>library lookup</em>, is associated
 * with a {@linkplain  SegmentScope scope}; when the scope becomes not {@link SegmentScope#isAlive()}, the library is unloaded:
 *
 * {@snippet lang = java:
 * try (Arena arena = Arena.openConfined()) {
 *     SymbolLookup libGL = SymbolLookup.libraryLookup("libGL.so", arena.scope()); // libGL.so loaded here
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
 * libraryLookup("libGL.so", scope).find("glGetString").isPresent(); // true
 * loaderLookup().find("glGetString").isPresent(); // false
 *}
 *
 * Note also that a library lookup for library {@code L} exposes symbols in {@code L} even if {@code L} was previously loaded
 * through JNI (the association with a class loader is immaterial to the library lookup):
 *
 * {@snippet lang = java:
 * System.loadLibrary("GL"); // libGL.so loaded here
 * libraryLookup("libGL.so", scope).find("glGetString").isPresent(); // true
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
 */
@PreviewFeature(feature=PreviewFeature.Feature.FOREIGN)
@FunctionalInterface
public interface SymbolLookup {

    /**
     * Returns the address of the symbol with the given name.
     * @param name the symbol name.
     * @return a zero-length memory segment whose address indicates the address of the symbol, if found.
     */
    Optional<MemorySegment> find(String name);

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
     * returned by this method is backed by a scope that is always alive and which keeps the caller's
     * class loader reachable. Therefore, libraries associated with the caller's class
     * loader are kept loaded (and their symbols available) as long as a loader lookup for that class loader is reachable.
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
        SegmentScope loaderScope = (loader == null || loader instanceof BuiltinClassLoader) ?
                SegmentScope.global() : // builtin loaders never go away
                MemorySessionImpl.heapSession(loader);
        return name -> {
            Objects.requireNonNull(name);
            JavaLangAccess javaLangAccess = SharedSecrets.getJavaLangAccess();
            // note: ClassLoader::findNative supports a null loader
            long addr = javaLangAccess.findNative(loader, name);
            return addr == 0L ?
                    Optional.empty() :
                    Optional.of(MemorySegment.ofAddress(addr, 0L, loaderScope));
        };
    }

    /**
     * Loads a library with the given name (if not already loaded) and creates a symbol lookup for symbols in that library.
     * The library will be unloaded when the provided scope becomes
     * not {@linkplain SegmentScope#isAlive() alive}, if no other library lookup is still using it.
     * @implNote The process of resolving a library name is OS-specific. For instance, in a POSIX-compliant OS,
     * the library name is resolved according to the specification of the {@code dlopen} function for that OS.
     * In Windows, the library name is resolved according to the specification of the {@code LoadLibrary} function.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param name the name of the library in which symbols should be looked up.
     * @param scope the scope associated with symbols obtained from the returned lookup.
     * @return a new symbol lookup suitable to find symbols in a library with the given name.
     * @throws IllegalArgumentException if {@code name} does not identify a valid library.
     * @throws IllegalCallerException If the caller is in a module that does not have native access enabled.
     */
    @CallerSensitive
    static SymbolLookup libraryLookup(String name, SegmentScope scope) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass(), SymbolLookup.class, "libraryLookup");
        return libraryLookup(name, RawNativeLibraries::load, scope);
    }

    /**
     * Loads a library from the given path (if not already loaded) and creates a symbol lookup for symbols
     * in that library. The library will be unloaded when the provided scope becomes
     * not {@linkplain SegmentScope#isAlive() alive}, if no other library lookup is still using it.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @implNote On Linux, the functionalities provided by this factory method and the returned symbol lookup are
     * implemented using the {@code dlopen}, {@code dlsym} and {@code dlclose} functions.
     * @param path the path of the library in which symbols should be looked up.
     * @param scope the scope associated with symbols obtained from the returned lookup.
     * @return a new symbol lookup suitable to find symbols in a library with the given path.
     * @throws IllegalArgumentException if {@code path} does not point to a valid library.
     * @throws IllegalCallerException If the caller is in a module that does not have native access enabled.
     */
    @CallerSensitive
    static SymbolLookup libraryLookup(Path path, SegmentScope scope) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass(), SymbolLookup.class, "libraryLookup");
        return libraryLookup(path, RawNativeLibraries::load, scope);
    }

    private static <Z> SymbolLookup libraryLookup(Z libDesc, BiFunction<RawNativeLibraries, Z, NativeLibrary> loadLibraryFunc, SegmentScope libScope) {
        Objects.requireNonNull(libDesc);
        Objects.requireNonNull(libScope);
        // attempt to load native library from path or name
        RawNativeLibraries nativeLibraries = RawNativeLibraries.newInstance(MethodHandles.lookup());
        NativeLibrary library = loadLibraryFunc.apply(nativeLibraries, libDesc);
        if (library == null) {
            throw new IllegalArgumentException("Cannot open library: " + libDesc);
        }
        // register hook to unload library when 'libScope' becomes not alive
        ((MemorySessionImpl) libScope).addOrCleanupIfFail(new MemorySessionImpl.ResourceList.ResourceCleanup() {
            @Override
            public void cleanup() {
                nativeLibraries.unload(library);
            }
        });
        return name -> {
            Objects.requireNonNull(name);
            long addr = library.find(name);
            return addr == 0L ?
                    Optional.empty() :
                    Optional.of(MemorySegment.ofAddress(addr, 0, libScope));
        };
    }
}
