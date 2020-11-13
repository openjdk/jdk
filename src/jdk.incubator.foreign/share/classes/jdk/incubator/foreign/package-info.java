/*
 *  Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 * <p> Classes to support low-level, safe and efficient memory access.
 * <p>
 * The key abstractions introduced by this package are {@link jdk.incubator.foreign.MemorySegment} and {@link jdk.incubator.foreign.MemoryAddress}.
 * The first models a contiguous memory region, which can reside either inside or outside the Java heap; the latter models an address - which can
 * sometimes be expressed as an offset into a given segment. A memory address represents the main access coordinate of a memory access var handle, which can be obtained
 * using the combinator methods defined in the {@link jdk.incubator.foreign.MemoryHandles} class. Finally, the {@link jdk.incubator.foreign.MemoryLayout} class
 * hierarchy enables description of <em>memory layouts</em> and basic operations such as computing the size in bytes of a given
 * layout, obtain its alignment requirements, and so on. Memory layouts also provide an alternate, more abstract way, to produce
 * memory access var handles, e.g. using <a href="MemoryLayout.html#layout-paths"><em>layout paths</em></a>.
 *
 * For example, to allocate an off-heap memory region big enough to hold 10 values of the primitive type {@code int}, and fill it with values
 * ranging from {@code 0} to {@code 9}, we can use the following code:
 *
 * <pre>{@code
static final VarHandle intHandle = MemoryHandles.varHandle(int.class, ByteOrder.nativeOrder());

try (MemorySegment segment = MemorySegment.allocateNative(10 * 4)) {
    MemoryAddress base = segment.baseAddress();
    for (long i = 0 ; i < 10 ; i++) {
       intHandle.set(base.addOffset(i * 4), (int)i);
    }
}
 * }</pre>
 *
 * Here we create a var handle, namely {@code intHandle}, to manipulate values of the primitive type {@code int}, at
 * a given memory location. Also, {@code intHandle} is stored in a {@code static} and {@code final} field, to achieve
 * better performance and allow for inlining of the memory access operation through the {@link java.lang.invoke.VarHandle}
 * instance. We then create a <em>native</em> memory segment, that is, a memory segment backed by
 * off-heap memory; the size of the segment is 40 bytes, enough to store 10 values of the primitive type {@code int}.
 * The segment is created inside a <em>try-with-resources</em> construct: this idiom ensures that all the memory resources
 * associated with the segment will be released at the end of the block, according to the semantics described in
 * Section {@jls 14.20.3} of <cite>The Java Language Specification</cite>. Inside the try-with-resources block, we initialize
 * the contents of the memory segment; more specifically, if we view the memory segment as a set of 10 adjacent slots,
 * {@code s[i]}, where {@code 0 <= i < 10}, where the size of each slot is exactly 4 bytes, the initialization logic above will set each slot
 * so that {@code s[i] = i}, again where {@code 0 <= i < 10}.
 *
 * <h2><a id="deallocation"></a>Deterministic deallocation</h2>
 *
 * When writing code that manipulates memory segments, especially if backed by memory which resides outside the Java heap, it is
 * crucial that the resources associated with a memory segment are released when the segment is no longer in use, by calling the {@link jdk.incubator.foreign.MemorySegment#close()}
 * method either explicitly, or implicitly, by relying on try-with-resources construct (as demonstrated in the example above).
 * Closing a given memory segment is an <em>atomic</em> operation which can either succeed - and result in the underlying
 * memory associated with the segment to be released, or <em>fail</em> with an exception.
 * <p>
 * The deterministic deallocation model differs significantly from the implicit strategies adopted within other APIs, most
 * notably the {@link java.nio.ByteBuffer} API: in that case, when a native byte buffer is created (see {@link java.nio.ByteBuffer#allocateDirect(int)}),
 * the underlying memory is not released until the byte buffer reference becomes <em>unreachable</em>. While implicit deallocation
 * models such as this can be very convenient - clients do not have to remember to <em>close</em> a direct buffer - such models can also make it
 * hard for clients to ensure that the memory associated with a direct buffer has indeed been released.
 *
 * <h2><a id="safety"></a>Safety</h2>
 *
 * This API provides strong safety guarantees when it comes to memory access. First, when dereferencing a memory segment using
 * a memory address, such an address is validated (upon access), to make sure that it does not point to a memory location
 * which resides <em>outside</em> the boundaries of the memory segment it refers to. We call this guarantee <em>spatial safety</em>;
 * in other words, access to memory segments is bounds-checked, in the same way as array access is, as described in
 * Section {@jls 15.10.4} of <cite>The Java Language Specification</cite>.
 * <p>
 * Since memory segments can be closed (see above), a memory address is also validated (upon access) to make sure that
 * the segment it belongs to has not been closed prematurely. We call this guarantee <em>temporal safety</em>. Note that,
 * in the general case, guaranteeing temporal safety can be hard, as multiple threads could attempt to access and/or close
 * the same memory segment concurrently. The memory access API addresses this problem by imposing strong
 * <a href="MemorySegment.html#thread-confinement"><em>thread-confinement</em></a> guarantees on memory segments: each
 * memory segment is associated with an owner thread, which is the only thread that can either access or close the segment.
 * <p>
 * Together, spatial and temporal safety ensure that each memory access operation either succeeds - and accesses a valid
 * memory location - or fails.
 */
package jdk.incubator.foreign;
