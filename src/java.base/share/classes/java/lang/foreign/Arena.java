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
 * An arena allocates and manages the lifecycle of native segments.
 * <p>
 * An arena is a {@linkplain AutoCloseable closeable} segment allocator that is associated with a {@link #session() memory session}.
 * This session is created with the arena, and is closed when the arena is {@linkplain #close() closed}.
 * Furthermore, all the native segments {@linkplain #allocate(long, long) allocated} by the arena are associated
 * with that session.
 * <p>
 * The <a href="MemorySession.html#thread-confinement">confinement properties</a> of the session associated with an
 * arena are determined by the factory used to create the arena. For instance, an arena created with {@link #openConfined()}
 * is associated with a <em>confined</em> memory session. Conversely, an arena created with {@link #openShared()} is
 * associated with a <em>shared</em> memory session.
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
 * @since 20
 */
@PreviewFeature(feature=PreviewFeature.Feature.FOREIGN)
public interface Arena extends SegmentAllocator, AutoCloseable {

    /**
     * Creates a native memory segment with the given size (in bytes), alignment constraint (in bytes).
     * The returned segment is associated with the same memory session associated with this arena.
     * The {@link MemorySegment#address()} of the returned memory segment is the starting address of the
     * allocated off-heap memory region backing the segment. Moreover, the {@linkplain MemorySegment#address() address}
     * of the returned segment is aligned according the provided alignment constraint.
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
     * @throws WrongThreadException if this method is called from a thread other than the thread
     * {@linkplain MemorySession#isOwnedBy(Thread) owning} the session associated with this arena.
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
     * Closes this arena. This closes the {@linkplain #session() session} associated with this arena and invalidates
     * all the memory segments associated with it. Any off-heap region of memory backing the segments associated with
     * that memory session are also released.
     * @throws IllegalStateException if the session associated with this arena is not {@linkplain MemorySession#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread other than the thread
     * {@linkplain MemorySession#isOwnedBy(Thread) owning} the session associated with this arena.
     */
    @Override
    void close();

    /**
     * Creates a new arena, associated with a new confined session.
     * @return a new arena, associated with a new confined session.
     */
    static Arena openConfined() {
        return makeArena(MemorySessionImpl.createConfined(Thread.currentThread()));
    }

    /**
     * Creates a new arena, associated with a new shared session.
     * @return a new arena, associated with a new shared session.
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
        };
    }
}
