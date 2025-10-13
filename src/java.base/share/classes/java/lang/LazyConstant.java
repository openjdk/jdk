/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package java.lang;

import jdk.internal.javac.PreviewFeature;
import jdk.internal.lang.LazyConstantImpl;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * A lazy constant is a deferred, shallowly immutable constant to be computed at
 * a later time via an underlying computing function.
 * <p>
 * A lazy constant is created using the factory method
 * {@linkplain LazyConstant#of(Supplier)}. When created, the lazy constant is
 * <em>not initialized</em>, which means the constant is not yet set. The constant,
 * of type {@code T}, can then be <em>initialized</em> (and retrieved) by calling
 * {@linkplain #get()}. The first time {@linkplain #get()} is called, an underlying
 * <em>computing function</em> will be invoked which would compute
 * the constant. The computing function is provided at construction. Once initialized,
 * the constant can <em>never change</em> and can be retrieved over and over again
 * by subsequent {@linkplain #get() get} invocations.
 * <p>
 * The term "shallowly immutable" means the reference to the constant will never change
 * once it is initialized. However, the referenced object itself may or may not
 * be mutable. Hence, immutability can only be guaranteed at the first initial, shallow
 * level.
 * <p>
 * Consider the following example where a lazy constant field "{@code logger}" is a
 * shallowly immutable holder of a constant of type {@code Logger}:
 *
 * {@snippet lang = java:
 * public class Component {
 *
 *    // Creates a new uninitialized lazy constant
 *    private final LazyConstant<Logger> logger =
 *            // @link substring="of" target="#of" :
 *            LazyConstant.of( () -> Logger.create(Component.class) );
 *
 *    public void process() {
 *        logger.get().info("Process started");
 *        // ...
 *    }
 * }
 *}
 * <p>
 * Initially, the lazy constant is <em>not initialized</em>, until {@code logger.get()}
 * evaluates the computing function, and initializes the constant to the result;
 * the result is then returned to the client. Hence, {@linkplain #get()} guarantees that
 * a lazy constant is <em>initialized</em> before it returns, barring any exceptions.
 * <p>
 * Furthermore, {@linkplain #get()} guarantees that, out of several threads trying to
 * invoke the computing function simultaneously, only one is ever selected for
 * computation. This property is crucial as evaluation of the computing function may have
 * side effects, for example, the call above to {@code Logger.create()} may result in
 * storage resources being prepared.
 *
 * <h2 id="exception-handling">Exception handling</h2>
 * If the computing function returns {@code null}, a {@linkplain NullPointerException}
 * is thrown. Hence, a lazy constant can never be {@code null}. Clients that want to
 * use a nullable constant can wrap the value into an {@linkplain Optional} holder.
 * <p>
 * If the computing function recursively invokes itself (directly or indirectly via
 * the lazy constant), an {@linkplain IllegalStateException} is thrown and the lazy
 * constant is not initialized.
 * <p>
 * If the computing function throws any unchecked exception or {@linkplain Error}, that
 * {@linkplain Throwable} is propagated to the caller, and the lazy constant remains
 * uninitialized. In other words, upon an unsuccessful invocation of
 * the computing function, neither a constant, the exception, nor the fact that
 * an exception was thrown are ever stored in the lazy constant.
 *
 * <h2 id="composition">Composing lazy constants</h2>
 * A lazy constant can depend on other lazy constants, forming a dependency graph
 * that can be lazily computed but where access to individual elements can still be
 * performant. In the following example, a single {@code Foo} and a {@code Bar}
 * instance (that is dependent on the {@code Foo} instance) are lazily created, both of
 * which are held by lazy constants:
 *
 * {@snippet lang = java:
 * public final class DependencyUtil {
 *
 *     private DependencyUtil() {}
 *
 *     public static class Foo {
 *          // ...
 *      }
 *
 *     public static class Bar {
 *         public Bar(Foo foo) {
 *              // ...
 *         }
 *     }
 *
 *     private static final LazyConstant<Foo> FOO = LazyConstant.of( Foo::new );
 *     private static final LazyConstant<Bar> BAR = LazyConstant.of( () -> new Bar(FOO.get()) );
 *
 *     public static Foo foo() {
 *         return FOO.get();
 *     }
 *
 *     public static Bar bar() {
 *         return BAR.get();
 *     }
 *
 * }
 *}
 * Calling {@code BAR.get()} will create the {@code Bar} singleton if it is not already
 * created. Upon such a creation, a dependent {@code Foo} will first be created if
 * the {@code Foo} does not already exist.
 *
 * <h2 id="thread-safety">Thread Safety</h2>
 * A lazy constant is guaranteed to be initialized atomically and at most once. If
 * competing threads are racing to initialize a lazy constant, only one updating
 * thread runs the computing function, while the other threads are blocked until
 * the constant is initialized, after which the other threads observe the lazy constant
 * is initialized and leave the constant unchanged and will never invoke any computation.
 * <p>
 * The invocation of the computing function and the resulting initialization of the constant
 * {@linkplain java.util.concurrent##MemoryVisibility <em>happens-before</em>}
 * the initialized constant is read. Hence, the initialized constant, including any
 * {@code final} fields of any newly created objects, are safely published.
 * <p>
 * The computing function runs on the caller’s thread.
 * <p>
 * If a thread that is blocked by another computing thread is interrupted, this is not
 * acted upon by the lazy constant (e.g., the thread’s interrupted status is not
 * cleared, and it does not throw InterruptedException; interruption does not cancel
 * initialization).
 * <p>
 * If the computing function blocks indefinitely, other threads operating on this
 * lazy constant may block indefinitely; no timeouts or cancellations are provided.
 *
 * <h2 id="performance">Performance</h2>
 * A lazy constant can never change after it has been initialized. Therefore,
 * a JVM implementation may, for an initialized lazy constant, elide all future reads
 * of that lazy constant, and instead directly use any constant that it has previously
 * observed. This is true if the reference to the lazy constant is a VM constant
 * (e.g. in cases where the lazy constant itself is stored in a
 * {@code static final} field) or forms a trusted chain to such a VM constant via
 * one or more layers of a {@linkplain Record record} fields or final fields
 * in hidden classes.
 *
 * @apiNote As a lazy constant can be initialized with an object but, it is not
 *          possible to ever remove that object, this can be a source of an unintended
 *          memory leak. In other words, a lazy constant
 *          {@linkplain java.lang.ref##reachability strongly references} the object
 *          it was initialized with. Hence, a lazy constant will hold the object it
 *          was initialized with until the lazy constant itself is collected (if ever).
 *          <p>
 *          A {@code LazyConstant} that has a type parameter {@code T} that is an
 *          array type (of arbitrary rank) will only allow the JVM to treat the
 *          <em>array reference</em> as a constant but <em>not its components</em>.
 *          Instead, a {@linkplain List#ofLazy(int, IntFunction) lazy list} of
 *          arbitrary depth can be used, which provides constant components.
 *          More generally, a lazy constant can hold other lazy constants of
 *          arbitrary depth and still provide transitive constancy.
 *          <p>
 *          The {@code LazyConstant} type is not {@link Serializable}.
 *          <p>
 *          It is not recommended putting lazy constants into equality-based collections
 *          (or similar constructs) prior to initialization if the underlying functions
 *          have side effects or may fail.
 *          <p>
 *          Use in static initializers may interact with class initialization order;
 *          cyclic initialization may result in initialization errors per JLS 12.4.
 *
 * @implSpec Except for {@linkplain #equals(Object) equals(obj)} and
 *           {@linkplain #orElse(Object) orElse(other)} parameters; all method parameters
 *           must be <em>non-null</em> or a {@link NullPointerException} will be thrown.
 *
 * @implNote
 *           A lazy constant is free to synchronize on itself. Hence, care must be
 *           taken when directly or indirectly synchronizing on a lazy constant.
 *
 * @param <T> type of the constant
 *
 * @since 26
 *
 * @see Optional
 * @see Supplier
 * @see List#ofLazy(int, IntFunction)
 * @see Map#ofLazy(Set, Function)
 * @jls 12.2 Initialization of Classes and Interfaces
 * @jls 17.4.5 Happens-before Order
 */
@PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
public sealed interface LazyConstant<T>
        extends Supplier<T>
        permits LazyConstantImpl {

    /**
     * {@return the constant if initialized, otherwise, returns {@code other}}
     * <p>
     * This method never triggers initialization of this lazy constant and will observe
     * initialization by other threads atomically (i.e., it returns the initialized
     * value if and only if the initialization has already completed).
     *
     * @param other value to return if the constant is not initialized
     *              (can be {@code null})
     */
    T orElse(T other);

    /**
     * {@return the initialized constant. If not initialized, first computes and
     *          initializes the constant using the computing function}
     * <p>
     * After this method returns successfully, the constant is guaranteed to be
     * initialized.
     * <p>
     * If the computing function throws, the throwable is relayed to the caller and
     * the lazy constant remains uninitialized; a subsequent call to get() may then
     * attempt the computation again.
     */
    T get();

    /**
     * {@return {@code true} if the constant is initialized, {@code false} otherwise}
     * <p>
     * This method never triggers initialization of this lazy constant and will observe
     * changes in the initialization state made by other threads atomically.
     */
    boolean isInitialized();


    // Object methods

    /**
     * Indicates whether some other object is "equal to" this lazy constant.
     * <p>
     * The other object is considered equal if:
     * <ul>
     * <li>it is also an instance of {@code LazyConstant} and;
     * <li>the constant values obtained via {@linkplain #get()} are "equal to"
     * each other via {@code equals()}.
     * </ul>
     * <p>
     * In other words, equality is based solely on the initialized constants, not on
     * computing functions or lazy constants' identities.
     * <p>
     * This method may trigger initialization of this lazy constant and/or the provided
     * {@code obj}, if it is an instance of a {@linkplain LazyConstant}. Consequently,
     * this method might block or throw.
     *
     * @implSpec The order of potential initialization triggering is specified as:
     * <ol>
     *     <li>{@code this} lazy constant</li>
     *     <li>{@code obj} lazy constant</li>
     * </ol>
     *
     * @param obj an object to be tested for equality (can be {@code null})
     * @return {@code true} if the other object is "equal to" this object
     *         otherwise {@code false}
     */
    @Override
    boolean equals(Object obj);

    /**
     * {@return the hash code of this constant}
     * <p>
     * This method may trigger initialization of this lazy constant. Consequently,
     * this method might block or throw.
     */
    @Override
    int hashCode();

    /**
     * {@return a non-initializing string suitable for debugging}
     * <p>
     * This method never triggers initialization of this lazy constant and will observe
     * initialization by other threads atomically (i.e., it observes the initialized
     * value if and only if the initialization has already completed).
     * <p>
     * If this lazy constant is initialized, the {@linkplain Object#toString()} of the
     * initialized constant will be returned, otherwise, an implementation dependent
     * string is returned that indicates this lazy constant is not yet initialized.
     */
    @Override
    String toString();

    // Factories

    /**
     * {@return a lazy constant to be computed later via the provided
     *          {@code computingFunction}}
     * <p>
     * The returned lazy constant strongly references the provided
     * {@code computingFunction} until initialization completes successfully; after
     * which the computing function is no longer strongly referenced and becomes
     * eligible for garbage collection.
     * <p>
     * If the provided computing function already is an instance of LazyConstant, the
     * method is free to return the provided computing function directly.
     *
     * @param computingFunction in the form of a Supplier to be used to compute
     *                          the constant
     * @param <T>               type of the constant
     *
     */
    @SuppressWarnings("unchecked")
    static <T> LazyConstant<T> of(Supplier<? extends T> computingFunction) {
        Objects.requireNonNull(computingFunction);
        if (computingFunction instanceof LazyConstant<? extends T> lc) {
            return (LazyConstant<T>) lc;
        }
        return LazyConstantImpl.ofLazy(computingFunction);
    }

}
