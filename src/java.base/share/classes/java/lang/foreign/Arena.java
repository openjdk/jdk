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

import java.util.Objects;

/**
 * An arena allocates and manages the lifecycle of native segments.
 * <p>
 * An arena is a {@linkplain AutoCloseable closeable} segment allocator that has a bounded {@linkplain MemorySession memory session}.
 * The arena's session starts when the arena is created, and ends when the arena is {@linkplain #close() closed}.
 * All native segments {@linkplain #allocate(long, long) allocated} by the arena are associated with its session, and
 * cannot be accessed after the arena is closed.
 * <p>
 * An arena is extremely useful when interacting with foreign code, as shown below:
 *
 * {@snippet lang = java:
 * try (Arena arena = Arena.openConfined()) {
 *     MemorySegment nativeArray = arena.allocateArray(ValueLayout.JAVA_INT, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
 *     MemorySegment nativeString = arena.allocateUtf8String("Hello!");
 *     MemorySegment upcallStub = linker.upcallStub(handle, desc, arena.session());
 *     ...
 * } // memory released here
 *}
 *
 * <h2 id = "thread-confinement">Safety and thread-confinement</h2>
 *
 * Arenas provide strong temporal safety guarantees: a memory segment allocated by an arena cannot be accessed
 * <em>after</em> the arena has been closed. The costs associated with maintaining this safety invariant can vary greatly,
 * depending on how many threads have access to the memory segments allocated by the arena. For instance, if an arena
 * is created and closed by one thread, and the segments associated with it are only ever accessed by that very same thread,
 * then ensuring correctness is simple.
 * <p>
 * Conversely, if an arena allocates segments that can be accessed by multiple threads, or if the arena can be closed
 * by a thread other than the accessing thread, the situation is much more complex. For instance, a segment might be accessed
 * <em>while</em> the associated arena is being closed, concurrently, by another thread. Even in this extreme case,
 * arenas must provide strong temporal safety guarantees, but doing so can incur in a higher performance impact.
 * For this reason, arenas can be divided into two categories: <em>thread-confined</em> arenas,
 * and <em>shared</em> arenas.
 * <p>
 * Confined arenas, support strong thread-confinement guarantees. Upon creation, they are assigned an
 * {@linkplain #isOwnedBy(Thread) owner thread}, typically the thread which initiated the creation operation.
 * The segments created by a confined arena can only be {@linkplain MemorySession#isAccessibleBy(Thread) accessed}
 * by the thread that created the arena. Moreover, any attempt to close the confined arena from a thread other than the owner thread will
 * fail with {@link WrongThreadException}.
 * <p>
 * Shared memory sessions, on the other hand, have no owner thread. The segments created by a shared arena
 * can be {@linkplain MemorySession#isAccessibleBy(Thread) accessed} by multiple threads. This might be useful when
 * multiple threads need to access the same memory segment concurrently (e.g. in the case of parallel processing).
 * Moreover, a shared arena can be closed by any thread.
 *
 * @since 20
 */
@PreviewFeature(feature=PreviewFeature.Feature.FOREIGN)
public interface Arena extends SegmentAllocator, AutoCloseable {

    /**
     * Creates a native memory segment with the given size (in bytes) and alignment constraint (in bytes).
     * The returned segment is associated with the arena's memory session.
     * The segment's {@link MemorySegment#address() address} is the starting address of the
     * allocated off-heap memory region backing the segment, and the address is 
     * aligned according the provided alignment constraint.
     *
     * @implSpec
     * The default implementation of this method is equivalent to the following code:
     * {@snippet lang=java :
     * MemorySegment.allocateNative(bytesSize, byteAlignment, session());
     * }
     *
     * @param byteSize the size (in bytes) of the off-heap memory block backing the native memory segment.
     * @param byteAlignment the alignment constraint (in bytes) of the off-heap region of memory backing the native memory segment.
     * @return a new native memory segment.
     * @throws IllegalArgumentException if {@code bytesSize < 0}, {@code alignmentBytes <= 0}, or if {@code alignmentBytes}
     * is not a power of 2.
     * @throws IllegalStateException if the session associated with this arena is not {@linkplain MemorySession#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code session.isAccessibleBy(T) == false}.
     * @see MemorySegment#allocateNative(long, long, MemorySession)
     */
    @Override
    default MemorySegment allocate(long byteSize, long byteAlignment) {
        return MemorySegment.allocateNative(byteSize, byteAlignment, session());
    }

    /**
     * {@return the session associated with this arena}
     */
    MemorySession session();

    /**
     * Closes this arena. If this method completes normally, the arena session becomes not {@linkplain MemorySession#isAlive() alive},
     * and all the memory segments associated with it can no longer be accessed. Furthermore, any off-heap region of memory backing the
     * segments associated with that memory session are also released.
     * @throws IllegalStateException if the session associated with this arena is not {@linkplain MemorySession#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code isOwnedBy(T) == false}.
     */
    @Override
    void close();

    /**
     * {@return {@code true} if the provided thread can close this arena}
     * @param thread the thread to be tested.
     */
    boolean isOwnedBy(Thread thread);

    /**
     * Creates a new confined arena.
     * @return a new confined arena.
     */
    static Arena openConfined() {
        return makeArena(MemorySessionImpl.createConfined(Thread.currentThread()));
    }

    /**
     * Creates a new shared arena.
     * @return a new shared arena.
     */
    static Arena openShared() {
        return makeArena(MemorySessionImpl.createShared());
    }

    private static Arena makeArena(MemorySessionImpl sessionImpl) {
        return new Arena() {
            @Override
            public MemorySession session() {
                return sessionImpl;
            }

            @Override
            public void close() {
                sessionImpl.close();
            }

            @Override
            public boolean isOwnedBy(Thread thread) {
                Objects.requireNonNull(thread);
                return sessionImpl.ownerThread() == thread;
            }
        };
    }
}
