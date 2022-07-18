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

import java.lang.ref.Cleaner;
import java.util.Objects;

import jdk.internal.foreign.MemorySessionImpl;
import jdk.internal.javac.PreviewFeature;

/**
 * A memory session manages the lifecycle of one or more resources. Resources (e.g. {@link MemorySegment}) associated
 * with a memory session can only be accessed while the memory session is {@linkplain #isAlive() alive},
 * and by the {@linkplain #ownerThread() thread} associated with the memory session (if any).
 * <p>
 * Memory sessions can be closed. When a memory session is closed, it is no longer {@linkplain #isAlive() alive},
 * and subsequent operations on resources associated with that session (e.g. attempting to access a {@link MemorySegment} instance)
 * will fail with {@link IllegalStateException}.
 * <p>
 * A memory session is associated with one or more {@linkplain #addCloseAction(Runnable) close actions}. Close actions
 * can be used to specify the cleanup code that must run when a given resource (or set of resources) is no longer in use.
 * When a memory session is closed, the {@linkplain #addCloseAction(Runnable) close actions}
 * associated with that session are executed (in unspecified order). For instance, closing the memory session associated with
 * one or more {@linkplain MemorySegment#allocateNative(long, long, MemorySession) native memory segments} results in releasing
 * the off-heap memory associated with said segments.
 * <p>
 * The {@linkplain #global() global session} is a memory session that cannot be closed.
 * As a result, resources associated with the global session are never released. Examples of resources associated with
 * the global memory session are {@linkplain MemorySegment#ofArray(int[]) heap segments}.
 *
 * <h2 id = "thread-confinement">Thread confinement</h2>
 *
 * Memory sessions can be divided into two categories: <em>thread-confined</em> memory sessions, and <em>shared</em>
 * memory sessions.
 * <p>
 * Confined memory sessions, support strong thread-confinement guarantees. Upon creation,
 * they are assigned an {@linkplain #ownerThread() owner thread}, typically the thread which initiated the creation operation.
 * After creating a confined memory session, only the owner thread will be allowed to directly manipulate the resources
 * associated with this memory session. Any attempt to perform resource access from a thread other than the
 * owner thread will fail with {@link WrongThreadException}.
 * <p>
 * Shared memory sessions, on the other hand, have no owner thread; as such, resources associated with shared memory sessions
 * can be accessed by multiple threads. This might be useful when multiple threads need to access the same resource concurrently
 * (e.g. in the case of parallel processing).
 *
 * <h2 id="closeable">Closeable memory sessions</h2>
 *
 * When a session is associated with off-heap resources, it is often desirable for said resources to be released in a timely fashion,
 * rather than waiting for the session to be deemed <a href="../../../java/lang/ref/package.html#reachability">unreachable</a>
 * by the garbage collector. In this scenario, a client might consider using a {@linkplain #isCloseable() <em>closeable</em>} memory session.
 * Closeable memory sessions are memory sessions that can be {@linkplain MemorySession#close() closed} deterministically, as demonstrated
 * in the following example:
 *
 * {@snippet lang=java :
 * try (MemorySession session = MemorySession.openConfined()) {
 *    MemorySegment segment1 = MemorySegment.allocateNative(100);
 *    MemorySegment segment1 = MemorySegment.allocateNative(200);
 *    ...
 * } // all memory released here
 * }
 *
 * The above code creates a confined, closeable session. Then it allocates two segments associated with that session.
 * When the session is {@linkplain #close() closed} (above, this is done implicitly, using the <em>try-with-resources construct</em>),
 * all memory allocated within the session will be released
 * <p>
 * Closeable memory sessions, while powerful, must be used with caution. Closeable memory sessions must be closed
 * when no longer in use, either explicitly (by calling the {@link #close} method), or implicitly (by wrapping the use of
 * a closeable memory session in a <em>try-with-resources construct</em>). A failure to do so might result in memory leaks.
 * To mitigate this problem, closeable memory sessions can be associated with a {@link Cleaner} instance,
 * so that they are also closed automatically, once the session instance becomes <a href="../../../java/lang/ref/package.html#reachability">unreachable</a>.
 * This can be useful to allow for predictable, deterministic resource deallocation, while still preventing accidental
 * native memory leaks. In case a client closes a memory session managed by a cleaner, no further action will be taken when
 * the session becomes unreachable; that is, {@linkplain #addCloseAction(Runnable) close actions} associated with a
 * memory session, whether managed or not, are called <em>exactly once</em>.
 *
 * <h2 id="non-closeable">Non-closeable views</h2>
 *
 * There are situations in which it might not be desirable for a memory session to be reachable from one or
 * more resources associated with it. For instance, an API might create a private memory session, and allocate
 * a memory segment, and then expose one or more slices of this segment to its clients. Since the API's memory session
 * would be reachable from the slices (using the {@link MemorySegment#session()} accessor), it might be possible for
 * clients to compromise the API (e.g. by closing the session prematurely). To avoid leaking private memory sessions
 * to untrusted clients, an API can instead return segments based on a non-closeable view of the session it created, as follows:
 *
 * {@snippet lang=java :
 * MemorySession session = MemorySession.openConfined();
 * MemorySession nonCloseableSession = session.asNonCloseable();
 * MemorySegment segment = MemorySegment.allocateNative(100, nonCloseableSession);
 * segment.session().close(); // throws
 * session.close(); //ok
 * }
 *
 * In other words, only the owner of the original {@code session} object can close the session. External clients can only
 * access the non-closeable session, and have no access to the underlying API session.
 *
 * @implSpec
 * Implementations of this interface are thread-safe.
 *
 * @see MemorySegment
 * @see SymbolLookup
 * @see Linker
 * @see VaList
 *
 * @since 19
 */
@PreviewFeature(feature=PreviewFeature.Feature.FOREIGN)
public sealed interface MemorySession extends AutoCloseable, SegmentAllocator permits MemorySessionImpl, MemorySessionImpl.NonCloseableView {

    /**
     * {@return {@code true}, if this memory session is alive}
     */
    boolean isAlive();

    /**
     * {@return {@code true}, if this session is a closeable memory session}.
     */
    boolean isCloseable();

    /**
     * {@return the owner thread associated with this memory session, or {@code null} if this session is shared
     * across multiple threads}
     */
    Thread ownerThread();

    /**
     * Runs a critical action while this memory session is kept alive.
     * @param action the action to be run.
     */
    void whileAlive(Runnable action);

    /**
     * Adds a custom cleanup action which will be executed when the memory session is closed.
     * The order in which custom cleanup actions are invoked once the memory session is closed is unspecified.
     * @apiNote The provided action should not keep a strong reference to this memory session, so that implicitly
     * closed sessions can be handled correctly by a {@link Cleaner} instance.
     * @param runnable the custom cleanup action to be associated with this memory session.
     * @throws IllegalStateException if this memory session is not {@linkplain #isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread other than the thread
     * {@linkplain #ownerThread() owning} this memory session.
     */
    void addCloseAction(Runnable runnable);

    /**
     * Closes this memory session. If this operation completes without exceptions, this session
     * will be marked as <em>not alive</em>, the {@linkplain #addCloseAction(Runnable) close actions} associated
     * with this session will be executed, and all the resources associated with this session will be released.
     *
     * @apiNote This operation is not idempotent; that is, closing an already closed memory session <em>always</em> results in an
     * exception being thrown. This reflects a deliberate design choice: memory session state transitions should be
     * manifest in the client code; a failure in any of these transitions reveals a bug in the underlying application
     * logic.
     *
     * @see MemorySession#isAlive()
     *
     * @throws IllegalStateException if this memory session is not {@linkplain #isAlive() alive}.
     * @throws IllegalStateException if this session is {@linkplain #whileAlive(Runnable) kept alive} by another client.
     * @throws WrongThreadException if this method is called from a thread other than the thread
     * {@linkplain #ownerThread() owning} this memory session.
     * @throws UnsupportedOperationException if this memory session is not {@linkplain #isCloseable() closeable}.
     */
    void close();

    /**
     * Returns a non-closeable view of this memory session. If this session is {@linkplain #isCloseable() non-closeable},
     * this session is returned. Otherwise, this method returns a non-closeable view of this memory session.
     * @apiNote a non-closeable view of a memory session {@code S} keeps {@code S} reachable. As such, {@code S}
     * cannot be closed implicitly (e.g. by a {@link Cleaner}) as long as one or more non-closeable views of {@code S}
     * are reachable.
     * @return a non-closeable view of this memory session.
     */
    MemorySession asNonCloseable();

    /**
     * Compares the specified object with this memory session for equality. Returns {@code true} if and only if the specified
     * object is also a memory session, and it refers to the same memory session as this memory session.
     * {@linkplain #asNonCloseable() A non-closeable view} {@code V} of a memory session {@code S} is considered
     * equal to {@code S}.
     *
     * @param that the object to be compared for equality with this memory session.
     * @return {@code true} if the specified object is equal to this memory session.
     */
    @Override
    boolean equals(Object that);

    /**
     * {@return the hash code value for this memory session}
     */
    @Override
    int hashCode();

    /**
     * Allocates a native segment, using this session. Equivalent to the following code:
     * {@snippet lang=java :
     * MemorySegment.allocateNative(size, align, this);
     * }
     *
     * @throws IllegalStateException if this memory session is not {@linkplain #isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread other than the thread
     * {@linkplain #ownerThread() owning} this memory session.
     * @return a new native segment, associated with this session.
     */
    @Override
    default MemorySegment allocate(long bytesSize, long bytesAlignment) {
        return MemorySegment.allocateNative(bytesSize, bytesAlignment, this);
    }

    /**
     * Creates a closeable confined memory session.
     * @return a new closeable confined memory session.
     */
    static MemorySession openConfined() {
        return MemorySessionImpl.createConfined(Thread.currentThread(), null);
    }

    /**
     * Creates a closeable confined memory session, managed by the provided cleaner instance.
     * @param cleaner the cleaner to be associated with the returned memory session.
     * @return a new closeable confined memory session, managed by {@code cleaner}.
     */
    static MemorySession openConfined(Cleaner cleaner) {
        Objects.requireNonNull(cleaner);
        return MemorySessionImpl.createConfined(Thread.currentThread(), cleaner);
    }

    /**
     * Creates a closeable shared memory session.
     * @return a new closeable shared memory session.
     */
    static MemorySession openShared() {
        return MemorySessionImpl.createShared(null);
    }

    /**
     * Creates a closeable shared memory session, managed by the provided cleaner instance.
     * @param cleaner the cleaner to be associated with the returned memory session.
     * @return a new closeable shared memory session, managed by {@code cleaner}.
     */
    static MemorySession openShared(Cleaner cleaner) {
        Objects.requireNonNull(cleaner);
        return MemorySessionImpl.createShared(cleaner);
    }

    /**
     * Creates a non-closeable shared memory session, managed by a private {@link Cleaner} instance.
     * Equivalent to (but likely more efficient than) the following code:
     * {@snippet lang=java :
     * openShared(Cleaner.create()).asNonCloseable();
     * }
     * @return a non-closeable shared memory session, managed by a private {@link Cleaner} instance.
     */
    static MemorySession openImplicit() {
        return MemorySessionImpl.createImplicit();
    }

    /**
     * Returns the global memory session.
     * @return the global memory session.
     */
    static MemorySession global() {
        return MemorySessionImpl.GLOBAL;
    }
}
