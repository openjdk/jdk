/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
import jdk.internal.ref.CleanerFactory;

/**
 * A memory session models the lifetime of a memory segment.
 * <p>
 * Segments associated with a memory session can only be accessed while the session is {@linkplain #isAlive() alive},
 * and by the {@linkplain #isOwnedBy(Thread) thread} associated with the session (if any).
 * <p>
 * Memory sessions can be <em>unbounded</em> or <em>bounded</em>. An unbounded memory session is obtained by calling
 * {@link MemorySession#global()}. An unbounded memory session is always alive. As a result, the segments associated
 * with an unbounded session are always accessible and their backing regions of memory are never deallocated. Moreover,
 * memory segments associated with unbounded sessions can be accessed from any thread.
 * <p>
 * Conversely, a bounded memory session has a start and an end. Bounded memory sessions can be managed either
 * explicitly, (i.e. using an {@linkplain Arena arena}) or implicitly, by the garbage collector. When a bounded memory
 * session ends, it is no longer {@linkplain #isAlive() alive}, and subsequent operations
 * on the segments associated with that bounded session (e.g. {@link MemorySegment#get(ValueLayout.OfInt, long)})
 * will fail with {@link IllegalStateException}. Moreover, to prevent temporal safety, access to memory segments associated with
 * bounded sessions might be restricted to specific threads (see below).
 *
 * <h2 id = "thread-confinement">Bounded memory sessions and thread-confinement</h2>
 *
 * Bounded memory sessions provide strong temporal safety guarantees: a memory segment associated with a bounded
 * session can only be accessed <em>while</em> the session is alive. The costs associated with maintaining this
 * safety invariant can vary greatly, depending on how many threads have access to the memory segment associated to a
 * memory session. For instance, if a memory session is created and closed by one thread, and the segments associated
 * with it are only ever accessed by that very same thread, ensuring correctness is simple: either the thread accessing
 * the segment finds that the segment session is still alive, in which case access succeeds, or it finds that the segment
 * session is no longer alive, in which case access fails with an exception.
 * <p>
 * Conversely, if a bounded session is associated with segments that can be accessed by multiple threads, or if the
 * session can be closed by a thread other than the accessing thread, the situation is much more complex. For instance,
 * a segment {@linkplain Arena#allocate(long, long) allocated} in an arena, can be accessed <em>while</em> the associated
 * arena is being closed, concurrently, by another thread. Even in this case, memory sessions provide strong temporal
 * safety guarantees, but doing so can incur in a higher performance impact: the Java runtime has to determine that
 * no segment associated with a bounded session is being accessed, before marking a session can be marked as not alive.
 * <p>
 * For this reason, bounded memory sessions can be further divided into two categories: <em>thread-confined</em> memory sessions,
 * and <em>shared</em> memory sessions.
 * <p>
 * Confined memory sessions, support strong thread-confinement guarantees. Upon creation, they are assigned an
 * {@linkplain #isOwnedBy(Thread) owner thread}, typically the thread which initiated the creation operation.
 * After creating a confined memory session, only the owner thread will be allowed to directly manipulate the segments
 * associated with the memory session. Any attempt to perform resource access from a thread other than the
 * owner thread will fail with {@link WrongThreadException}.
 * <p>
 * Shared memory sessions, on the other hand, have no owner thread; as such, segments associated with shared memory sessions
 * can be accessed by multiple threads. This might be useful when multiple threads need to access the same resource concurrently
 * (e.g. in the case of parallel processing).
 *
 * <h2 id="implicit">Implicitly-managed bounded memory sessions</h2>
 *
 * Managing bounded memory session explicitly, using arenas, while powerful, must be used with caution. An arena must always
 * be closed when no longer in use (this is done using {@link Arena#close()}). A failure to do so
 * might result in memory leaks. To mitigate this problem, clients can obtain an {@linkplain MemorySession#implicit() obtain}
 * bounded memory sessions that are managed implicitly, by the garbage collector. These sessions end at some unspecified
 * time <em>after</em> they become <a href="../../../java/lang/ref/package.html#reachability">unreachable</a>, as shown below:
 *
 * {@snippet lang = java:
 * MemorySegment segment = MemorySegment.allocateNative(100, MemorySession.implicit());
 * ...
 * segment = null; // the session might end after this instruction
 *}
 *
 * Bounded sessions that are managed implicitly can be useful to manage long-lived segments, where timely deallocation
 * is not critical, or in unstructured cases where the boundaries of the lifetime associated with a memory session
 * cannot be easily determined. As shown in the example above, a memory session that is managed implicitly cannot end
 * if a program references to one or more segments associated with that session. This means that implicitly managed
 * session can naturally support safe access from multiple threads.
 *
 * @implSpec
 * Implementations of this interface are thread-safe.
 *
 * @see Arena
 * @see MemorySegment
 * @see SymbolLookup
 * @see Linker
 * @see VaList
 *
 * @since 19
 */
@PreviewFeature(feature=PreviewFeature.Feature.FOREIGN)
public sealed interface MemorySession permits MemorySessionImpl {

    /**
     * {@return {@code true}, if this memory session is alive}
     */
    boolean isAlive();

    /**
     * {@return test if the provided thread is the owner thread associated with this memory session}
     * @param thread the thread to be compared against this session's owner thread.
     */
    boolean isOwnedBy(Thread thread);

    /**
     * Runs a critical action while this memory session is kept alive.
     * @param action the action to be run.
     */
    void whileAlive(Runnable action);

    /**
     * Creates a new bounded memory session that is managed, implicitly, by the garbage collector.
     * The returned session can be shared across threads.
     *
     * @return a new bounded memory session that is managed, implicitly, by the garbage collector.
     */
    static MemorySession implicit() {
        return MemorySessionImpl.createImplicit(CleanerFactory.cleaner());
    }

    /**
     * {@return an unbounded memory session}
     */
    static MemorySession global() {
        return MemorySessionImpl.GLOBAL;
    }
}
