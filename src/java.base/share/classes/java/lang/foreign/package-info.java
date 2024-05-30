/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

/**
 * <p>Provides low-level access to memory and functions outside the Java runtime.
 *
 * <h2 id="fma">Foreign memory access</h2>
 *
 * <p>
 * The main abstraction introduced to support foreign memory access is
 * {@link java.lang.foreign.MemorySegment}, that models a contiguous region of memory,
 * residing either inside or outside the Java heap. Memory segments are typically
 * allocated using an {@link java.lang.foreign.Arena}, which controls the lifetime of
 * the regions of memory backing the segments it allocates. The contents of a
 * memory segment can be described using a {@link java.lang.foreign.MemoryLayout memory layout},
 * which provides basic operations to query sizes, offsets, and alignment constraints.
 * Memory layouts also provide an alternate, more abstract way, to
 * <a href=MemorySegment.html#segment-deref>access memory segments</a> using
 * {@linkplain java.lang.foreign.MemoryLayout#varHandle(java.lang.foreign.MemoryLayout.PathElement...) var handles},
 * which can be computed using <a href="MemoryLayout.html#layout-paths"><em>layout paths</em></a>.
 * <p>
 * For example, to allocate an off-heap region of memory big enough to hold 10 values of
 * the primitive type {@code int}, and fill it with values ranging from {@code 0} to
 * {@code 9}, we can use the following code:
 *
 * {@snippet lang = java:
 * try (Arena arena = Arena.ofConfined()) {
 *     MemorySegment segment = arena.allocate(10 * 4);
 *     for (int i = 0 ; i < 10 ; i++) {
 *         segment.setAtIndex(ValueLayout.JAVA_INT, i, i);
 *     }
 * }
 * }
 *
 * This code creates a <em>native</em> memory segment, that is, a memory segment backed
 * by off-heap memory; the size of the segment is 40 bytes, enough to store 10 values of
 * the primitive type {@code int}.   The native segment is allocated using a
 * {@linkplain java.lang.foreign.Arena#ofConfined() confined arena}. As such, access to
 * the native segment is restricted to the current thread (the thread that created the
 * arena). Moreover, when the arena is closed, the native segment is invalidated, and
 * its backing region of memory is deallocated. Note the use of the <em>try-with-resources</em>
 * construct: this idiom ensures that the off-heap region of memory backing the native
 * segment will be released at the end of the block, according to the semantics described
 * in Section {@jls 14.20.3} of <cite>The Java Language Specification</cite>.
 * <p>
 * Memory segments provide strong safety guarantees when it comes to memory access.
 * First, when accessing a memory segment, the access coordinates are validated
 * (upon access), to make sure that access does not occur at any address that resides
 * <em>outside</em> the boundaries of the memory segment used by the access operation.
 * We call this guarantee <em>spatial safety</em>; in other words, access to
 * memory segments is bounds-checked, in the same way as array access is, as described in
 * Section {@jls 15.10.4} of <cite>The Java Language Specification</cite>.
 * <p>
 * Additionally, to prevent a region of memory from being accessed <em>after</em> it has
 * been deallocated (i.e. <em>use-after-free</em>), a segment is also validated
 * (upon access) to make sure that the arena from which it has been obtained has not
 * been closed. We call this guarantee <em>temporal safety</em>.
 * <p>
 * Together, spatial and temporal safety ensure that each memory access operation either
 * succeeds - and accesses a valid location within the region of memory backing the
 * memory segment - or fails.
 *
 * <h2 id="ffa">Foreign function access</h2>
 *
 * The key abstractions introduced to support foreign function access are
 * {@link java.lang.foreign.SymbolLookup}, {@link java.lang.foreign.FunctionDescriptor} and
 * {@link java.lang.foreign.Linker}. The first is used to look up symbols inside
 * libraries; the second is used to model the signature of foreign functions, while the
 * third is used to link foreign functions as {@link java.lang.invoke.MethodHandle}
 * instances, so that clients can perform foreign function calls directly in Java,
 * without the need for intermediate layers of C/C++ code (as is the case with the
 * <a href="{@docRoot}/../specs/jni/index.html">Java Native Interface (JNI)</a>).
 * <p>
 * For example, to compute the length of a string using the C standard library function
 * {@code strlen} on a Linux/x64 platform, we can use the following code:
 *
 * {@snippet lang = java:
 * Linker linker = Linker.nativeLinker();
 * SymbolLookup stdlib = linker.defaultLookup();
 * MethodHandle strlen = linker.downcallHandle(
 *     stdlib.findOrThrow("strlen"),
 *     FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
 * );
 *
 * try (Arena arena = Arena.ofConfined()) {
 *     MemorySegment cString = arena.allocateFrom("Hello");
 *     long len = (long)strlen.invokeExact(cString); // 5
 * }
 *}
 *
 * Here, we obtain a {@linkplain java.lang.foreign.Linker#nativeLinker() native linker}
 * and we use it to {@linkplain java.lang.foreign.SymbolLookup#findOrThrow(java.lang.String) look up}
 * the {@code strlen} function in the standard C library; a <em>downcall method handle</em>
 * targeting said function is subsequently
 * {@linkplain java.lang.foreign.Linker#downcallHandle(FunctionDescriptor, Linker.Option...) obtained}.
 * To complete the linking successfully, we must provide a
 * {@link java.lang.foreign.FunctionDescriptor} instance, describing the signature of the
 * {@code strlen} function. From this information, the linker will uniquely determine
 * the sequence of steps which will turn the method handle invocation (here performed
 * using {@link java.lang.invoke.MethodHandle#invokeExact(java.lang.Object...)})
 * into a foreign function call, according to the rules specified by the ABI of the
 * underlying platform.
 * <p>
 * The {@link java.lang.foreign.Arena} class also provides many useful methods for
 * interacting with foreign code, such as
 * {@linkplain java.lang.foreign.SegmentAllocator#allocateFrom(java.lang.String) converting}
 * Java strings into zero-terminated, UTF-8 strings, as demonstrated in the above example.
 *
 * <h2 id="restricted">Restricted methods</h2>
 *
 * Some methods in this package are considered <em>restricted</em>. Restricted methods
 * are typically used to bind native foreign data and/or functions to first-class
 * Java API elements which can then be used directly by clients. For instance the
 * restricted method {@link java.lang.foreign.MemorySegment#reinterpret(long)} can be
 * used to create a fresh segment with the same address and temporal bounds, but with
 * the provided size. This can be useful to resize memory segments obtained when
 * interacting with native functions.
 * <p>
 * Binding foreign data and/or functions is generally unsafe and, if done incorrectly,
 * can result in VM crashes, or memory corruption when the bound Java API element
 * is accessed. For instance, incorrectly resizing a native memory segment using
 * {@link java.lang.foreign.MemorySegment#reinterpret(long)} can lead to a JVM crash, or,
 * worse, lead to silent memory corruption when attempting to access the resized segment.
 * For these reasons, it is crucial for code that calls a restricted method to never pass
 * arguments that might cause incorrect binding of foreign data and/or functions to
 * a Java API.
 * <p>
 * Given the potential danger of restricted methods, the Java runtime issues a warning on
 * the standard error stream every time a restricted method is invoked. Such warnings can
 * be disabled by granting access to restricted methods to selected modules. This can be
 * done either via implementation-specific command line options or programmatically, e.g.
 * by calling {@link java.lang.ModuleLayer.Controller#enableNativeAccess(java.lang.Module)}.
 * <p>
 * For every class in this package, unless specified otherwise, any method arguments of
 * reference type must not be {@code null}, and any null argument will elicit a
 * {@code NullPointerException}. This fact is not individually documented for methods of
 * this API.
 *
 * @apiNote Usual memory model guarantees (see {@jls 17.4}) do not apply when accessing
 * native memory segments as these segments are backed by off-heap regions of memory.
 *
 * @implNote
 * In the reference implementation, access to restricted methods can be granted to
 * specific modules using the command line option {@code --enable-native-access=M1,M2, ... Mn},
 * where {@code M1}, {@code M2}, {@code ... Mn} are module names (for the unnamed module,
 * the special value {@code ALL-UNNAMED} can be used). If this option is specified,
 * access to restricted methods are only granted to the modules listed by that option.
 * If this option is not specified, access to restricted methods is enabled for all
 * modules, but access to restricted methods will result in runtime warnings.
 *
 * @spec jni/index.html Java Native Interface Specification
 *
 * @since 22
 */
package java.lang.foreign;

