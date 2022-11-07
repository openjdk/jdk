/*
 *  Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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

/**
 * <p>Provides low-level access to memory and functions outside the Java runtime.
 *
 * <h2 id="fma">Foreign memory access</h2>
 *
 * <p>
 * The main abstraction introduced to support foreign memory access is {@link java.lang.foreign.MemorySegment}, which
 * models a contiguous region of memory, residing either inside or outside the Java heap. The contents of a memory
 * segment can be described using a {@link java.lang.foreign.MemoryLayout memory layout}, which provides
 * basic operations to query sizes, offsets and alignment constraints. Memory layouts also provide
 * an alternate, more abstract way, to <a href=MemorySegment.html#segment-deref>access memory segments</a>
 * using {@linkplain java.lang.foreign.MemoryLayout#varHandle(java.lang.foreign.MemoryLayout.PathElement...) access var handles},
 * which can be computed using <a href="MemoryLayout.html#layout-paths"><em>layout paths</em></a>.
 *
 * For example, to allocate an off-heap region of memory big enough to hold 10 values of the primitive type {@code int},
 * and fill it with values ranging from {@code 0} to {@code 9}, we can use the following code:
 *
 * {@snippet lang = java:
 * MemorySegment segment = MemorySegment.allocateNative(10 * 4, MemorySession.implicit());
 * for (int i = 0 ; i < 10 ; i++) {
 *     segment.setAtIndex(ValueLayout.JAVA_INT, i, i);
 * }
 *}
 *
 * This code creates a <em>native</em> memory segment, that is, a memory segment backed by
 * off-heap memory; the size of the segment is 40 bytes, enough to store 10 values of the primitive type {@code int}.
 * The segment is associated with a memory session that is {@linkplain java.lang.foreign.MemorySession#implicit() implicitly} closed,
 * by the garbage collector. As such, the off-heap memory backing the native segment will be released at some unspecified
 * point <em>after</em> the segment becomes <a href="../../../java/lang/ref/package.html#reachability">unreachable</a>.
 * This is similar to what happens with direct buffers created via {@link java.nio.ByteBuffer#allocateDirect(int)}.
 * It is also possible to manage the lifecycle of allocated native segments more directly, as shown in a later section.
 * <p>
 * Inside a loop, we then initialize the contents of the memory segment; note how the
 * {@linkplain java.lang.foreign.MemorySegment#setAtIndex(ValueLayout.OfInt, long, int) access method}
 * accepts a {@linkplain java.lang.foreign.ValueLayout value layout}, which specifies the size, alignment constraint,
 * byte order as well as the Java type ({@code int}, in this case) associated with the access operation. More specifically,
 * if we view the memory segment as a set of 10 adjacent slots, {@code s[i]}, where {@code 0 <= i < 10},
 * where the size of each slot is exactly 4 bytes, the initialization logic above will set each slot
 * so that {@code s[i] = i}, again where {@code 0 <= i < 10}.
 *
 * <h3 id="deallocation">Deterministic deallocation</h3>
 *
 * When writing code that manipulates memory segments, especially if backed by memory which resides outside the Java heap, it is
 * often crucial that the resources associated with a memory segment are released when the segment is no longer in use,
 * and in a timely fashion. For this reason, there might be cases where waiting for the garbage collector to determine that a segment
 * is <a href="../../../java/lang/ref/package.html#reachability">unreachable</a> is not optimal.
 * Clients that operate under these assumptions might want to programmatically release the memory backing a memory segment.
 * This can be done, using the {@link java.lang.foreign.Arena} abstraction, as shown below:
 *
 * {@snippet lang = java:
 * try (Arena arena = Arena.openConfined()) {
 *     MemorySegment segment = arena.allocate(10 * 4);
 *     for (int i = 0 ; i < 10 ; i++) {
 *         segment.setAtIndex(ValueLayout.JAVA_INT, i, i);
 *     }
 * }
 *}
 *
 * This example is almost identical to the prior one; this time we first create an arena
 * which is used to allocate multiple native segments which share the same life-cycle. That is, all the segments
 * allocated by the arena will be associated with the same {@linkplain java.lang.foreign.MemorySession memory session}.
 * Note the use of the <em>try-with-resources</em> construct: this idiom ensures that the off-heap region of memory backing the
 * native segment will be released at the end of the block, according to the semantics described in Section {@jls 14.20.3}
 * of <cite>The Java Language Specification</cite>.
 *
 * <h3 id="safety">Safety</h3>
 *
 * This API provides strong safety guarantees when it comes to memory access. First, when dereferencing a memory segment,
 * the access coordinates are validated (upon access), to make sure that access does not occur at any address which resides
 * <em>outside</em> the boundaries of the memory segment used by the access operation. We call this guarantee <em>spatial safety</em>;
 * in other words, access to memory segments is bounds-checked, in the same way as array access is, as described in
 * Section {@jls 15.10.4} of <cite>The Java Language Specification</cite>.
 * <p>
 * Since memory segments can be closed (see above), segments are also validated (upon access) to make sure that
 * the memory session associated with the segment being accessed has not been closed prematurely.
 * We call this guarantee <em>temporal safety</em>. Together, spatial and temporal safety ensure that each memory access
 * operation either succeeds - and accesses a valid location of the region of memory backing the memory segment - or fails.
 *
 * <h2 id="ffa">Foreign function access</h2>
 * The key abstractions introduced to support foreign function access are {@link java.lang.foreign.SymbolLookup},
 * {@link java.lang.foreign.FunctionDescriptor} and {@link java.lang.foreign.Linker}. The first is used to look up symbols
 * inside libraries; the second is used to model the signature of foreign functions, while the third provides
 * linking capabilities which allows modelling foreign functions as {@link java.lang.invoke.MethodHandle} instances,
 * so that clients can perform foreign function calls directly in Java, without the need for intermediate layers of C/C++
 * code (as is the case with the <a href="{@docRoot}/../specs/jni/index.html">Java Native Interface (JNI)</a>).
 * <p>
 * For example, to compute the length of a string using the C standard library function {@code strlen} on a Linux x64 platform,
 * we can use the following code:
 *
 * {@snippet lang = java:
 * Linker linker = Linker.nativeLinker();
 * SymbolLookup stdlib = linker.defaultLookup();
 * MethodHandle strlen = linker.downcallHandle(
 *     stdlib.find("strlen").get(),
 *     FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
 * );
 *
 * try (Arena arena = Arena.openConfined()) {
 *     MemorySegment cString = arena.allocateUtf8String("Hello");
 *     long len = (long)strlen.invoke(cString); // 5
 * }
 *}
 *
 * Here, we obtain a {@linkplain java.lang.foreign.Linker#nativeLinker() native linker} and we use it
 * to {@linkplain java.lang.foreign.SymbolLookup#find(java.lang.String) look up} the {@code strlen} symbol in the
 * standard C library; a <em>downcall method handle</em> targeting said symbol is subsequently
 * {@linkplain java.lang.foreign.Linker#downcallHandle(FunctionDescriptor, Linker.Option...) obtained}.
 * To complete the linking successfully, we must provide a {@link java.lang.foreign.FunctionDescriptor} instance,
 * describing the signature of the {@code strlen} function.
 * From this information, the linker will uniquely determine the sequence of steps which will turn
 * the method handle invocation (here performed using {@link java.lang.invoke.MethodHandle#invoke(java.lang.Object...)})
 * into a foreign function call, according to the rules specified by the ABI of the underlying platform.
 * The {@link java.lang.foreign.Arena} class also provides many useful methods for
 * interacting with foreign code, such as
 * {@linkplain java.lang.foreign.SegmentAllocator#allocateUtf8String(java.lang.String) converting} Java strings into
 * zero-terminated, UTF-8 strings, as demonstrated in the above example.
 *
 * <h3 id="upcalls">Upcalls</h3>
 * The {@link java.lang.foreign.Linker} interface also allows clients to turn an existing method handle (which might point
 * to a Java method) into a memory segment, so that Java code can effectively be passed to other foreign functions.
 * For instance, we can write a method that compares two integer values, as follows:
 *
 * {@snippet lang=java :
 * class IntComparator {
 *     static int intCompare(MemorySegment addr1, MemorySegment addr2) {
 *         return addr1.get(ValueLayout.JAVA_INT, 0) -
 *                addr2.get(ValueLayout.JAVA_INT, 0);
 *
 *     }
 * }
 * }
 *
 * The above method accesses two foreign memory segments containing an integer value, and performs a simple comparison
 * by returning the difference between such values. We can then obtain a method handle which targets the above static
 * method, as follows:
 *
 * {@snippet lang = java:
 * FunctionDescriptor intCompareDescriptor = FunctionDescriptor.of(ValueLayout.JAVA_INT,
 *                                                                 ValueLayout.ADDRESS.asUnbounded(),
 *                                                                 ValueLayout.ADDRESS.asUnbounded());
 * MethodHandle intCompareHandle = MethodHandles.lookup().findStatic(IntComparator.class,
 *                                                 "intCompare",
 *                                                 Linker.upcallType(comparFunction));
 *}
 *
 * As before, we need to create a {@link java.lang.foreign.FunctionDescriptor} instance, this time describing the signature
 * of the function pointer we want to create. The descriptor can be used to
 * {@linkplain java.lang.foreign.FunctionDescriptor#toMethodType() derive} a method type
 * that can be used to look up the method handle for {@code IntComparator.intCompare}.
 * <p>
 * Now that we have a method handle instance, we can turn it into a fresh function pointer,
 * using the {@link java.lang.foreign.Linker} interface, as follows:
 *
 * {@snippet lang = java:
 * MemorySession session = ...
 * MemorySegment comparFunc = Linker.nativeLinker().upcallStub(
 *     intCompareHandle, intCompareDescriptor, session);
 * );
 *}
 *
 * The {@link java.lang.foreign.FunctionDescriptor} instance created in the previous step is then used to
 * {@linkplain java.lang.foreign.Linker#upcallStub(java.lang.invoke.MethodHandle, FunctionDescriptor, MemorySession) create}
 * a new upcall stub; the layouts in the function descriptors allow the linker to determine the sequence of steps which
 * allow foreign code to call the stub for {@code intCompareHandle} according to the rules specified by the ABI of the
 * underlying platform.
 * The lifecycle of the upcall stub is tied to the {@linkplain java.lang.foreign.MemorySession memory session}
 * provided when the upcall stub is created. This same session is made available by the {@link java.lang.foreign.MemorySegment}
 * instance returned by that method.
 *
 * <h2 id="restricted">Restricted methods</h2>
 * Some methods in this package are considered <em>restricted</em>. Restricted methods are typically used to bind native
 * foreign data and/or functions to first-class Java API elements which can then be used directly by clients. For instance
 * the restricted method {@link java.lang.foreign.MemorySegment#ofAddress(long, long, MemorySession)}
 * can be used to create a fresh segment with the given spatial bounds out of a native address.
 * <p>
 * Binding foreign data and/or functions is generally unsafe and, if done incorrectly, can result in VM crashes, or memory corruption when the bound Java API element is accessed.
 * For instance, in the case of {@link java.lang.foreign.MemorySegment#ofAddress(long, long, MemorySession)},
 * if the provided spatial bounds are incorrect, a client of the segment returned by that method might crash the VM, or corrupt
 * memory when attempting to access said segment. For these reasons, it is crucial for code that calls a restricted method
 * to never pass arguments that might cause incorrect binding of foreign data and/or functions to a Java API.
 * <p>
 * Access to restricted methods can be controlled using the command line option {@code --enable-native-access=M1,M2, ... Mn},
 * where {@code M1}, {@code M2}, {@code ... Mn} are module names (for the unnamed module, the special value {@code ALL-UNNAMED}
 * can be used). If this option is specified, access to restricted methods is only granted to the modules listed by that
 * option. If this option is not specified, access to restricted methods is enabled for all modules, but
 * access to restricted methods will result in runtime warnings.
 * <p>
 * For every class in this package, unless specified otherwise, any method arguments of reference
 * type must not be null, and any null argument will elicit a {@code NullPointerException}.  This fact is not individually
 * documented for methods of this API.
 */
package java.lang.foreign;
