/*
 *  Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * <p> Classes to support low-level and efficient foreign memory/function access, directly from Java.
 *
 * <h2>Foreign memory access</h2>
 *
 * <p>
 * The main abstractions introduced to support foreign memory access is {@link jdk.incubator.foreign.MemorySegment}, which
 * models a contiguous memory region, which can reside either inside or outside the Java heap.
 * A memory segment represents the main access coordinate of a memory access var handle, which can be obtained
 * using the combinator methods defined in the {@link jdk.incubator.foreign.MemoryHandles} class; a set of
 * common dereference and copy operations is provided also by the {@link jdk.incubator.foreign.MemorySegment} class, which can
 * be useful for simple, non-structured access. Finally, the {@link jdk.incubator.foreign.MemoryLayout} class
 * hierarchy enables description of <em>memory layouts</em> and basic operations such as computing the size in bytes of a given
 * layout, obtain its alignment requirements, and so on. Memory layouts also provide an alternate, more abstract way, to produce
 * memory access var handles, e.g. using <a href="MemoryLayout.html#layout-paths"><em>layout paths</em></a>.
 *
 * For example, to allocate an off-heap memory region big enough to hold 10 values of the primitive type {@code int}, and fill it with values
 * ranging from {@code 0} to {@code 9}, we can use the following code:
 *
 * {@snippet lang=java :
 * MemorySegment segment = MemorySegment.allocateNative(10 * 4, ResourceScope.newImplicitScope());
 * for (int i = 0 ; i < 10 ; i++) {
 *     segment.setAtIndex(ValueLayout.JAVA_INT, i, i);
 * }
 * }
 *
 * This code creates a <em>native</em> memory segment, that is, a memory segment backed by
 * off-heap memory; the size of the segment is 40 bytes, enough to store 10 values of the primitive type {@code int}.
 * Inside a loop, we then initialize the contents of the memory segment; note how the
 * {@linkplain jdk.incubator.foreign.MemorySegment#setAtIndex(ValueLayout.OfInt, long, int) dereference method}
 * accepts a {@linkplain jdk.incubator.foreign.ValueLayout value layout}, which specifies the size, alignment constraints,
 * byte order as well as the Java type ({@code int}, in this case) associated with the dereference operation. More specifically,
 * if we view the memory segment as a set of 10 adjacent slots, {@code s[i]}, where {@code 0 <= i < 10},
 * where the size of each slot is exactly 4 bytes, the initialization logic above will set each slot
 * so that {@code s[i] = i}, again where {@code 0 <= i < 10}.
 *
 * <h3><a id="deallocation"></a>Deterministic deallocation</h3>
 *
 * When writing code that manipulates memory segments, especially if backed by memory which resides outside the Java heap, it is
 * often crucial that the resources associated with a memory segment are released when the segment is no longer in use,
 * and in a timely fashion. For this reason, there might be cases where waiting for the garbage collector to determine that a segment
 * is <a href="../../../java/lang/ref/package.html#reachability">unreachable</a> is not optimal.
 * Clients that operate under these assumptions might want to programmatically release the memory associated
 * with a memory segment. This can be done, using the {@link jdk.incubator.foreign.ResourceScope} abstraction, as shown below:
 *
 * {@snippet lang=java :
 * try (ResourceScope scope = ResourceScope.newConfinedScope()) {
 *     MemorySegment segment = MemorySegment.allocateNative(10 * 4, scope);
 *     for (int i = 0 ; i < 10 ; i++) {
 *         segment.setAtIndex(ValueLayout.JAVA_INT, i, i);
 *     }
 * }
 * }
 *
 * This example is almost identical to the prior one; this time we first create a so called <em>resource scope</em>,
 * which is used to <em>bind</em> the life-cycle of the segment created immediately afterwards. Note the use of the
 * <em>try-with-resources</em> construct: this idiom ensures that all the memory resources associated with the segment will be released
 * at the end of the block, according to the semantics described in Section {@jls 14.20.3} of <cite>The Java Language Specification</cite>.
 *
 * <h3><a id="safety"></a>Safety</h3>
 *
 * This API provides strong safety guarantees when it comes to memory access. First, when dereferencing a memory segment,
 * the access coordinates are validated (upon access), to make sure that access does not occur at an address which resides
 * <em>outside</em> the boundaries of the memory segment used by the dereference operation. We call this guarantee <em>spatial safety</em>;
 * in other words, access to memory segments is bounds-checked, in the same way as array access is, as described in
 * Section {@jls 15.10.4} of <cite>The Java Language Specification</cite>.
 * <p>
 * Since memory segments can be closed (see above), segments are also validated (upon access) to make sure that
 * the resource scope associated with the segment being accessed has not been closed prematurely.
 * We call this guarantee <em>temporal safety</em>. Together, spatial and temporal safety ensure that each memory access
 * operation either succeeds - and accesses a valid memory location - or fails.
 *
 * <h2>Foreign function access</h2>
 * The key abstractions introduced to support foreign function access are {@link jdk.incubator.foreign.SymbolLookup},
 * {@link jdk.incubator.foreign.MemoryAddress} and {@link jdk.incubator.foreign.CLinker}.
 * The first is used to lookup symbols inside native libraries; the second is used to model native addresses (more on that later),
 * while the third provides linking capabilities which allows modelling foreign functions as {@link java.lang.invoke.MethodHandle} instances,
 * so that clients can perform foreign function calls directly in Java, without the need for intermediate layers of native
 * code (as it's the case with the <a href="{@docRoot}/../specs/jni/index.html">Java Native Interface (JNI)</a>).
 * <p>
 * For example, to compute the length of a string using the C standard library function {@code strlen} on a Linux x64 platform,
 * we can use the following code:
 *
 * {@snippet lang=java :
 * var linker = CLinker.systemCLinker();
 * MethodHandle strlen = linker.downcallHandle(
 *     linker.lookup("strlen").get(),
 *     FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
 * );
 *
 * try (var scope = ResourceScope.newConfinedScope()) {
 *     var cString = MemorySegment.allocateNative(5 + 1, scope);
 *     cString.setUtf8String("Hello");
 *     long len = (long)strlen.invoke(cString); // 5
 * }
 * }
 *
 * Here, we obtain a {@linkplain jdk.incubator.foreign.CLinker#systemCLinker() linker instance} and we use it
 * to {@linkplain jdk.incubator.foreign.CLinker#lookup(java.lang.String) lookup} the {@code strlen} symbol in the
 * standard C library; a <em>downcall method handle</em> targeting said symbol is subsequently
 * {@linkplain jdk.incubator.foreign.CLinker#downcallHandle(jdk.incubator.foreign.FunctionDescriptor) obtained}.
 * To complete the linking successfully, we must provide a {@link jdk.incubator.foreign.FunctionDescriptor} instance,
 * describing the signature of the {@code strlen} function.
 * From this information, the linker will uniquely determine the sequence of steps which will turn
 * the method handle invocation (here performed using {@link java.lang.invoke.MethodHandle#invoke(java.lang.Object...)})
 * into a foreign function call, according to the rules specified by the platform C ABI.
 * The {@link jdk.incubator.foreign.MemorySegment} class also provides many useful methods for
 * interacting with native code, such as converting Java strings
 * {@linkplain jdk.incubator.foreign.MemorySegment#setUtf8String(long, java.lang.String) into} native strings and
 * {@linkplain jdk.incubator.foreign.MemorySegment#getUtf8String(long) back}, as demonstrated in the above example.
 *
 * <h3>Foreign addresses</h3>
 *
 * When a memory segment is created from Java code, the segment properties (spatial bounds, temporal bounds and confinement)
 * are fully known at segment creation. But when interacting with native libraries, clients will often receive <em>raw</em> pointers;
 * such pointers have no spatial bounds (example: does the C type {@code char*} refer to a single {@code char} value,
 * or an array of {@code char} values, of given size?), no notion of temporal bounds, nor thread-confinement.
 * <p>
 * Raw pointers are modelled using the {@link jdk.incubator.foreign.MemoryAddress} class. When clients receive a
 * memory address instance from a foreign function call, they can perform memory dereference on it directly,
 * using one of the many <em>unsafe</em>
 * {@linkplain jdk.incubator.foreign.MemoryAddress#get(jdk.incubator.foreign.ValueLayout.OfInt, long) dereference methods}
 * provided:
 *
 * {@snippet lang=java :
 * MemoryAddress addr = ... //obtain address from native code
 * int x = addr.get(ValueLayout.JAVA_INT, 0);
 * }
 *
 * Alternatively, the client can
 * {@linkplain jdk.incubator.foreign.MemorySegment#ofAddress(jdk.incubator.foreign.MemoryAddress, long, jdk.incubator.foreign.ResourceScope) create}
 * a memory segment <em>unsafely</em>. This allows the client to inject extra knowledge about spatial bounds which might,
 * for instance, be available in the documentation of the foreign function which produced the native address.
 * Here is how an unsafe segment can be created from a native address:
 *
 * {@snippet lang=java :
 * ResourceScope scope = ... // initialize a resource scope object
 * MemoryAddress addr = ... //obtain address from native code
 * MemorySegment segment = MemorySegment.ofAddress(addr, 4, scope); // segment is 4 bytes long
 * int x = segment.get(ValueLayout.JAVA_INT, 0);
 * }
 *
 * <h3>Upcalls</h3>
 * The {@link jdk.incubator.foreign.CLinker} interface also allows to turn an existing method handle (which might point
 * to a Java method) into a memory address, so that Java code can effectively be passed to other foreign functions.
 * For instance, we can write a method that compares two integer values, as follows:
 *
 * {@snippet lang=java :
 * class IntComparator {
 *     static int intCompare(MemoryAddress addr1, MemoryAddress addr2) {
 *         return addr1.get(ValueLayout.JAVA_INT, 0) - addr2.get(ValueLayout.JAVA_INT, 0);
 *     }
 * }
 * }
 *
 * The above method dereferences two memory addresses containing an integer value, and performs a simple comparison
 * by returning the difference between such values. We can then obtain a method handle which targets the above static
 * method, as follows:
 *
 * {@snippet lang=java :
 * FunctionDescriptor intCompareDescriptor = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS);
 * MethodHandle intCompareHandle = MethodHandles.lookup().findStatic(IntComparator.class,
 *                                                 "intCompare",
 *                                                 CLinker.upcallType(comparFunction));
 * }
 *
 * As before, we need to create a {@link jdk.incubator.foreign.FunctionDescriptor} instance, this time describing the signature
 * of the function pointer we want to create. The descriptor can be used to
 * {@linkplain jdk.incubator.foreign.CLinker#upcallType(jdk.incubator.foreign.FunctionDescriptor) derive} a method type
 * that can be used to lookup the method handle for {@code IntComparator.intCompare}.
 * <p>
 * Now that we have a method handle instance, we can turn it into a fresh function pointer,
 * using the {@link jdk.incubator.foreign.CLinker} interface, as follows:
 *
 * {@snippet lang=java :
 * ResourceScope scope = ...
 * Addressable comparFunc = CLinker.systemCLinker().upcallStub(
 *     intCompareHandle, intCompareDescriptor, scope);
 * );
 * }
 *
 * The {@link jdk.incubator.foreign.FunctionDescriptor} instance created in the previous step is then used to
 * {@linkplain jdk.incubator.foreign.CLinker#upcallStub(java.lang.invoke.MethodHandle, jdk.incubator.foreign.FunctionDescriptor, jdk.incubator.foreign.ResourceScope) create}
 * a new upcall stub; the layouts in the function descriptors allow the linker to determine the sequence of steps which
 * allow foreign code to call the stub for {@code intCompareHandle} according to the rules specified by the platform C ABI.
 * The lifecycle of the upcall stub returned by is tied to the {@linkplain jdk.incubator.foreign.ResourceScope resource scope}
 * provided when the upcall stub is created. This same scope is made available by the {@link jdk.incubator.foreign.NativeSymbol}
 * instance returned by that method.
 *
 * <a id="restricted"></a>
 * <h2>Restricted methods</h2>
 * Some methods in this package are considered <em>restricted</em>. Restricted methods are typically used to bind native
 * foreign data and/or functions to first-class Java API elements which can then be used directly by clients. For instance
 * the restricted method {@link MemorySegment#ofAddress(MemoryAddress, long, ResourceScope)}
 * can be used to create a fresh segment with given spatial bounds out of a native address.
 * <p>
 * Binding foreign data and/or functions is generally unsafe and, if done incorrectly, can result in VM crashes, or memory corruption when the bound Java API element is accessed.
 * For instance, in the case of {@link MemorySegment#ofAddress(MemoryAddress, long, ResourceScope)},
 * if the provided spatial bounds are incorrect, a client of the segment returned by that method might crash the VM, or corrupt
 * memory when attempting to dereference said segment. For these reasons, it is crucial for code that calls a restricted method
 * to never pass arguments that might cause incorrect binding of foreign data and/or functions to a Java API.
 * <p>
 * Access to restricted methods is <em>disabled</em> by default; to enable restricted methods, the command line option
 * {@code --enable-native-access} must mention the name of the caller's module.
 */
package jdk.incubator.foreign;
