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

import jdk.internal.foreign.MemorySessionImpl;
import jdk.internal.javac.PreviewFeature;

/**
 * An arena controls the lifecycle of memory segments, providing both flexible allocation and timely deallocation.
 * <p>
 * An arena has a {@linkplain #scope() scope}, called the arena scope. When the arena is {@linkplain #close() closed},
 * the arena scope is no longer {@linkplain SegmentScope#isAlive() alive}. As a result, all the
 * segments associated with the arena scope are invalidated, safely and atomically, their backing memory regions are
 * deallocated (where applicable) and can no longer be accessed after the arena is closed:
 *
 * {@snippet lang = java:
 * try (Arena arena = Arena.openConfined()) {
 *     MemorySegment segment = MemorySegment.allocateNative(100, arena.scope());
 *     ...
 * } // memory released here
 *}
 *
 * Furthermore, an arena is a {@link SegmentAllocator}. All the segments {@linkplain #allocate(long, long) allocated} by the
 * arena are associated with the arena scope. This makes arenas extremely useful when interacting with foreign code, as shown below:
 *
 * {@snippet lang = java:
 * try (Arena arena = Arena.openConfined()) {
 *     MemorySegment nativeArray = arena.allocateArray(ValueLayout.JAVA_INT, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
 *     MemorySegment nativeString = arena.allocateUtf8String("Hello!");
 *     MemorySegment upcallStub = linker.upcallStub(handle, desc, arena.scope());
 *     ...
 * } // memory released here
 *}
 *
 * <h2 id = "thread-confinement">Safety and thread-confinement</h2>
 *
 * Arenas provide strong temporal safety guarantees: a memory segment allocated by an arena cannot be accessed
 * <em>after</em> the arena has been closed. The cost of providing this guarantee varies based on the
 * number of threads that have access to the memory segments allocated by the arena. For instance, if an arena
 * is always created and closed by one thread, and the memory segments associated with the arena's scope are always
 * accessed by that same thread, then ensuring correctness is trivial.
 * <p>
 * Conversely, if an arena allocates segments that can be accessed by multiple threads, or if the arena can be closed
 * by a thread other than the accessing thread, then ensuring correctness is much more complex. For example, a segment
 * allocated with the arena might be accessed <em>while</em> another thread attempts, concurrently, to close the arena.
 * To provide the strong temporal safety guarantee without forcing every client, even simple ones, to incur a performance
 * impact, arenas are divided into <em>thread-confined</em> arenas, and <em>shared</em> arenas.
 * <p>
 * Confined arenas, support strong thread-confinement guarantees. Upon creation, they are assigned an
 * {@linkplain #isCloseableBy(Thread) owner thread}, typically the thread which initiated the creation operation.
 * The segments created by a confined arena can only be {@linkplain SegmentScope#isAccessibleBy(Thread) accessed}
 * by the owner thread. Moreover, any attempt to close the confined arena from a thread other than the owner thread will
 * fail with {@link WrongThreadException}.
 * <p>
 * Shared arenas, on the other hand, have no owner thread. The segments created by a shared arena
 * can be {@linkplain SegmentScope#isAccessibleBy(Thread) accessed} by any thread. This might be useful when
 * multiple threads need to access the same memory segment concurrently (e.g. in the case of parallel processing).
 * Moreover, a shared arena {@linkplain #isCloseableBy(Thread) can be closed} by any thread.
 *
 * @since 20
 */
@PreviewFeature(feature=PreviewFeature.Feature.FOREIGN)
public interface Arena extends SegmentAllocator, AutoCloseable {

    /**
     * Returns a native memory segment with the given size (in bytes) and alignment constraint (in bytes).
     * The returned segment is associated with the arena scope.
     * The segment's {@link MemorySegment#address() address} is the starting address of the
     * allocated off-heap memory region backing the segment, and the address is
     * aligned according the provided alignment constraint.
     *
     * @implSpec
     * The default implementation of this method is equivalent to the following code:
     * {@snippet lang = java:
     * MemorySegment.allocateNative(bytesSize, byteAlignment, scope());
     *}
     * More generally implementations of this method must return a native segment featuring the requested size,
     * and that is compatible with the provided alignment constraint. Furthermore, for any two segments
     * {@code S1, S2} returned by this method, the following invariant must hold:
     *
     * {@snippet lang = java:
     * S1.overlappingSlice(S2).isEmpty() == true
     *}
     *
     * @param byteSize the size (in bytes) of the off-heap memory block backing the native memory segment.
     * @param byteAlignment the alignment constraint (in bytes) of the off-heap region of memory backing the native memory segment.
     * @return a new native memory segment.
     * @throws IllegalArgumentException if {@code bytesSize < 0}, {@code alignmentBytes <= 0}, or if {@code alignmentBytes}
     * is not a power of 2.
     * @throws IllegalStateException if the arena has already been {@linkplain #close() closed}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope().isAccessibleBy(T) == false}.
     * @see MemorySegment#allocateNative(long, long, SegmentScope)
     */
    @Override
    default MemorySegment allocate(long byteSize, long byteAlignment) {
        return MemorySegment.allocateNative(byteSize, byteAlignment, scope());
    }

    /**
     * {@return the arena scope}
     */
    SegmentScope scope();

    /**
     * Closes this arena. If this method completes normally, the arena scope is no longer {@linkplain SegmentScope#isAlive() alive},
     * and all the memory segments associated with it can no longer be accessed. Furthermore, any off-heap region of memory backing the
     * segments associated with that scope are also released.
     *
     * @apiNote This operation is not idempotent; that is, closing an already closed arena <em>always</em> results in an
     * exception being thrown. This reflects a deliberate design choice: failure to close an arena might reveal a bug
     * in the underlying application logic.
     *
     * @see SegmentScope#isAlive()
     *
     * @throws IllegalStateException if the arena has already been closed.
     * @throws IllegalStateException if the arena scope is {@linkplain SegmentScope#whileAlive(Runnable) kept alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code isCloseableBy(T) == false}.
     */
    @Override
    void close();

    /**
     * {@return {@code true} if the provided thread can close this arena}
     * @param thread the thread to be tested.
     */
    boolean isCloseableBy(Thread thread);

    /**
     * {@return a new confined arena, owned by the current thread}
     */
    static Arena openConfined() {
        return MemorySessionImpl.createConfined(Thread.currentThread()).asArena();
    }

    /**
     * {@return a new shared arena}
     */
    static Arena openShared() {
        return MemorySessionImpl.createShared().asArena();
    }
}
