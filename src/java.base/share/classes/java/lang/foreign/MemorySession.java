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
 * Segments associated with a memory session can only be accessed while the session is {@linkplain #isAlive() alive}.
 * <p>
 * Memory sessions can be <em>unbounded</em> or <em>bounded</em>. An unbounded memory session is obtained by calling
 * {@link MemorySession#global()}. An unbounded memory session is always alive. As a result, the segments associated
 * with an unbounded session are always accessible and their backing regions of memory are never deallocated. Moreover,
 * memory segments associated with unbounded sessions can be {@linkplain #isAccessibleBy(Thread) accessed} from any thread.
 * <p>
 * Conversely, a bounded memory session has a start and an end. Bounded memory sessions can be managed either
 * explicitly, (i.e. using an {@linkplain Arena arena}) or implicitly, by the garbage collector. When a bounded memory
 * session ends, it is no longer {@linkplain #isAlive() alive}, and subsequent operations
 * on the segments associated with that bounded session (e.g. {@link MemorySegment#get(ValueLayout.OfInt, long)})
 * will fail with {@link IllegalStateException}. Moreover, to prevent temporal safety, access to memory segments associated with
 * bounded sessions might be <a href="Arena.html#thread-confinement">restricted to specific threads</a>.
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
 * if a program references to one or more segments associated with that session. This means that memory segments associated
 * with implicitly managed sessions can be safely {@linkplain #isAccessibleBy(Thread) accessed} from multiple threads.
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
     * {@return {@code true} if the provided thread can access and/or obtain segments associated with this memory session}
     * @param thread the thread to be tested.
     */
    boolean isAccessibleBy(Thread thread);

    /**
     * Runs a critical action while this memory session is kept alive.
     * @param action the action to be run.
     * @throws IllegalStateException if this session is not {@linkplain MemorySession#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code isAccessibleBy(T) == false}.
     */
    void whileAlive(Runnable action);

    /**
     * Creates a new bounded memory session that is managed, implicitly, by the garbage collector.
     * The segments associated with the returned session can be
     * {@linkplain MemorySession#isAccessibleBy(Thread) accessed} by multiple threads.
     *
     * @return a new bounded memory session that is managed, implicitly, by the garbage collector.
     */
    static MemorySession implicit() {
        return MemorySessionImpl.createImplicit(CleanerFactory.cleaner());
    }

    /**
     * Obtains an unbounded memory session. The segments associated with the returned session can be
     * {@linkplain MemorySession#isAccessibleBy(Thread) accessed} by multiple threads.
     *
     * @return an unbounded memory session.
     */
    static MemorySession global() {
        return MemorySessionImpl.GLOBAL;
    }
}
