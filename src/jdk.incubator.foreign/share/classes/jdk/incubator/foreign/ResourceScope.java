/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.foreign;

import jdk.internal.foreign.ResourceScopeImpl;
import jdk.internal.ref.CleanerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Spliterator;

/**
 * A resource scope manages the lifecycle of one or more resources. Resources (e.g. {@link MemorySegment}) associated
 * with a resource scope can only be accessed while the resource scope is {@linkplain #isAlive() alive},
 * and by the {@linkplain #ownerThread() thread} associated with the resource scope (if any).
 *
 * <h2>Deterministic deallocation</h2>
 *
 * Resource scopes support <em>deterministic deallocation</em>; that is, they can be {@linkplain ResourceScope#close() closed}
 * explicitly. When a resource scope is closed, it is no longer {@link #isAlive() alive}, and subsequent
 * operations on resources associated with that scope (e.g. attempting to access a {@link MemorySegment} instance)
 * will fail with {@link IllegalStateException}.
 * <p>
 * Closing a resource scope will cause all the {@linkplain #addCloseAction(Runnable) close actions} associated with that scope to be called.
 * Moreover, closing a resource scope might trigger the releasing of the underlying memory resources associated with said scope; for instance:
 * <ul>
 *     <li>closing the scope associated with a {@linkplain MemorySegment#allocateNative(long, long, ResourceScope) native memory segment}
 *     results in <em>freeing</em> the native memory associated with it;</li>
 *     <li>closing the scope associated with a {@linkplain MemorySegment#mapFile(Path, long, long, FileChannel.MapMode, ResourceScope) mapped memory segment}
 *     results in the backing memory-mapped file to be unmapped;</li>
 *     <li>closing the scope associated with an {@linkplain CLinker#upcallStub(MethodHandle, FunctionDescriptor, ResourceScope) upcall stub}
 *     results in releasing the stub;</li>
 *     <li>closing the scope associated with a {@linkplain VaList variable arity list} results in releasing the memory
 *     associated with that variable arity list instance.</li>
 * </ul>
 *
 * <h2>Implicit deallocation</h2>
 *
 * Resource scopes can be associated with a {@link Cleaner} instance, so that they are also closed automatically,
 * once the scope instance becomes <a href="../../../java/lang/ref/package.html#reachability">unreachable</a>.
 * This can be useful to allow for predictable, deterministic resource deallocation, while still preventing accidental
 * native memory leaks. In case a managed resource scope is closed explicitly, no further action will be taken when
 * the scope becomes unreachable; that is, {@linkplain #addCloseAction(Runnable) close actions} associated with a
 * resource scope, whether managed or not, are called <em>exactly once</em>.
 *
 * <h2><a id = "global-scope">Global scope</a></h2>
 *
 * An important implicit resource scope is the so called {@linkplain #globalScope() global scope}; the global scope is
 * a resource scope that cannot be closed, either explicitly or implicitly. As a result, the global scope will never
 * attempt to release resources associated with it. Examples of resources associated with the global scope are:
 * <ul>
 *     <li>heap segments created from {@linkplain MemorySegment#ofArray(int[]) arrays} or
 *     {@linkplain MemorySegment#ofByteBuffer(ByteBuffer) buffers};</li>
 *     <li>variable arity lists {@linkplain VaList#ofAddress(MemoryAddress, ResourceScope) obtained} from raw memory addresses;
 *     <li>native symbols {@linkplain SymbolLookup#lookup(String) obtained} from a {@linkplain SymbolLookup#loaderLookup() loader lookup},
 *     or from the {@link CLinker}.</li>
 * </ul>
 * In other words, the global scope is used to indicate that the lifecycle of one or more resources must, where
 * needed, be managed independently by clients.
 *
 * <h2><a id = "thread-confinement">Thread confinement</a></h2>
 *
 * Resource scopes can be divided into two categories: <em>thread-confined</em> resource scopes, and <em>shared</em>
 * resource scopes.
 * <p>
 * {@linkplain #newConfinedScope() Confined resource scopes}, support strong thread-confinement guarantees. Upon creation,
 * they are assigned an {@linkplain #ownerThread() owner thread}, typically the thread which initiated the creation operation.
 * After creating a confined resource scope, only the owner thread will be allowed to directly manipulate the resources
 * associated with this resource scope. Any attempt to perform resource access from a thread other than the
 * owner thread will result in a runtime failure.
 * <p>
 * {@linkplain #newSharedScope() Shared resource scopes}, on the other hand, have no owner thread;
 * as such, resources associated with shared resource scopes can be accessed by multiple threads.
 * This might be useful when multiple threads need to access the same resource concurrently (e.g. in the case of parallel processing).
 * For instance, a client might obtain a {@link Spliterator} from a segment backed by a shared scope, which can then be used to slice the
 * segment and allow multiple threads to work in parallel on disjoint segment slices. The following code can be used to sum
 * all int values in a memory segment in parallel:
 *
 * <blockquote><pre>{@code
try (ResourceScope scope = ResourceScope.newSharedScope()) {
    SequenceLayout SEQUENCE_LAYOUT = MemoryLayout.sequenceLayout(1024, ValueLayout.JAVA_INT);
    MemorySegment segment = MemorySegment.allocateNative(SEQUENCE_LAYOUT, scope);
    int sum = segment.elements(ValueLayout.JAVA_INT).parallel()
                        .mapToInt(s -> s.get(ValueLayout.JAVA_INT, 0))
                        .sum();
}
 * }</pre></blockquote>
 *
 * <p>
 * Shared resource scopes, while powerful, must be used with caution: if one or more threads accesses
 * a resource associated with a shared scope while the scope is being closed from another thread, an exception might occur on both
 * the accessing and the closing threads. Clients should refrain from attempting to close a shared resource scope repeatedly
 * (e.g. keep calling {@link #close()} until no exception is thrown). Instead, clients of shared resource scopes
 * should always ensure that proper synchronization mechanisms (e.g. using temporal dependencies, see below) are put in place
 * so that threads closing shared resource scopes can never race against threads accessing resources managed by same scopes.
 *
 * <h2>Temporal dependencies</h2>
 *
 * Resource scopes can depend on each other. More specifically, a scope can feature
 * {@linkplain #keepAlive(ResourceScope) temporal dependencies} on one or more other resource scopes.
 * Such a resource scope cannot be closed (either implicitly or explicitly) until <em>all</em> the scopes it depends on
 * have also been closed.
 * <p>
 * This can be useful when clients need to perform a critical operation on a memory segment, during which they have
 * to ensure that the scope associated with that segment will not be closed; this can be done as follows:
 *
 * <blockquote><pre>{@code
MemorySegment segment = ...
try (ResourceScope criticalScope = ResourceScope.newConfinedScope()) {
    criticalScope.keepAlive(segment.scope());
    <critical operation on segment>
}
 * }</pre></blockquote>
 *
 * Note that a resource scope does not become <a href="../../../java/lang/ref/package.html#reachability">unreachable</a>
 * until all the scopes it depends on have been closed.
 *
 * @implSpec
 * Implementations of this interface are immutable, thread-safe and <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 */
public sealed interface ResourceScope extends AutoCloseable permits ResourceScopeImpl {
    /**
     * Is this resource scope alive?
     * @return true, if this resource scope is alive.
     * @see ResourceScope#close()
     */
    boolean isAlive();

    /**
     * The thread owning this resource scope.
     * @return the thread owning this resource scope, or {@code null} if this resource scope is shared.
     */
    Thread ownerThread();

    /**
     * Closes this resource scope. As a side effect, if this operation completes without exceptions, this scope will be marked
     * as <em>not alive</em>, and subsequent operations on resources associated with this scope will fail with {@link IllegalStateException}.
     * Additionally, upon successful closure, all native resources associated with this resource scope will be released.
     *
     * @apiNote This operation is not idempotent; that is, closing an already closed resource scope <em>always</em> results in an
     * exception being thrown. This reflects a deliberate design choice: resource scope state transitions should be
     * manifest in the client code; a failure in any of these transitions reveals a bug in the underlying application
     * logic.
     *
     * @throws IllegalStateException if one of the following condition is met:
     * <ul>
     *     <li>this resource scope is not <em>alive</em>
     *     <li>this resource scope is confined, and this method is called from a thread other than the thread owning this resource scope</li>
     *     <li>this resource scope is shared and a resource associated with this scope is accessed while this method is called</li>
     *     <li>one or more scopes which {@linkplain #keepAlive(ResourceScope) depend} on this resource scope have not been closed.
     * </ul>
     * @throws UnsupportedOperationException if this resource scope is the {@linkplain #globalScope() global scope}.
     */
    void close();

    /**
     * Add a custom cleanup action which will be executed when the resource scope is closed.
     * The order in which custom cleanup actions are invoked once the scope is closed is unspecified.
     * @param runnable the custom cleanup action to be associated with this scope.
     * @throws IllegalStateException if this scope has been closed, or if access occurs from
     * a thread other than the thread owning this scope.
     */
    void addCloseAction(Runnable runnable);

    /**
     * Creates a temporal dependency between this scope and the target scope. As a result, the target scope cannot
     * be {@linkplain #close() closed} <em>before</em> this scope.
     * @implNote A given scope can support up to {@link Integer#MAX_VALUE} pending keep alive requests.
     * @param target the scope that needs to be kept alive.
     * @throws IllegalArgumentException if {@code target == this}.
     * @throws IllegalStateException if this scope or {@code target} have been closed, or if access occurs from
     * a thread other than the thread owning this scope or {@code target}.
     */
    void keepAlive(ResourceScope target);

    /**
     * Creates a new confined scope.
     * @return a new confined scope.
     */
    static ResourceScope newConfinedScope() {
        return ResourceScopeImpl.createConfined(Thread.currentThread(), null);
    }

    /**
     * Creates a new confined scope, managed by the provided cleaner instance.
     * @param cleaner the cleaner to be associated with the returned scope.
     * @return a new confined scope, managed by {@code cleaner}.
     */
    static ResourceScope newConfinedScope(Cleaner cleaner) {
        Objects.requireNonNull(cleaner);
        return ResourceScopeImpl.createConfined(Thread.currentThread(), cleaner);
    }

    /**
     * Creates a new shared scope.
     * @return a new shared scope.
     */
    static ResourceScope newSharedScope() {
        return ResourceScopeImpl.createShared(null);
    }

    /**
     * Creates a new shared scope, managed by the provided cleaner instance.
     * @param cleaner the cleaner to be associated with the returned scope.
     * @return a new shared scope, managed by {@code cleaner}.
     */
    static ResourceScope newSharedScope(Cleaner cleaner) {
        Objects.requireNonNull(cleaner);
        return ResourceScopeImpl.createShared(cleaner);
    }

    /**
     * Creates a new shared scope, managed by a private {@link Cleaner} instance. Equivalent to (but likely more efficient than)
     * the following code:
     * <pre>{@code
    newSharedScope(Cleaner.create());
     * }</pre>
     * @return a shared scope, managed by a private {@link Cleaner} instance.
     */
    static ResourceScope newImplicitScope() {
        return newSharedScope(CleanerFactory.cleaner());
    }

    /**
     * Returns the <a href="ResourceScope.html#global-scope"><em>global scope</em></a>.
     * @return the <a href="ResourceScope.html#global-scope"><em>global scope</em></a>.
     */
    static ResourceScope globalScope() {
        return ResourceScopeImpl.GLOBAL;
    }
}
