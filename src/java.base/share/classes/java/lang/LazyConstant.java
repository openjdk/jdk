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
 * {@linkplain #get()}. The firsts time {@linkplain #get()} is called, an underlying
 * <em>computing function</em> will be invoked which would compute
 * the constant. The computing function is provided at construction. Once initialized,
 * the constant can <em>never change</em> and can be retrieved over and over again
 * by subsequent {@linkplain #get() get} invocations.
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
 * a lazy constant is <em>initialized</em> before it returns, baring any exceptions.
 * <p>
 * Furthermore, {@linkplain #get()} guarantees that, out several threads trying to invoke
 * the computing function simultaneously, only one is ever evaluated. This property is
 * crucial as evaluation of the computing function may have side effects, for example,
 * the call above to {@code Logger.create()} may result in storage resources being
 * prepared.
 * <p>
 * If the computing function returns {@code null}, a {@linkplain NullPointerException}
 * is thrown. Hence, a lazy constant can never be {@code null}. Clients that want to
 * use a nullable constant can wrap the value into an {@linkplain Optional} holder.
 *
 * <h2 id="composition">Composing lazy constants</h2>
 * A lazy constant can depend on other computed constants, forming a dependency graph
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
 * A lazy constant is guaranteed to be initialized at most once. If competing
 * threads are racing to initialize a lazy constant, only one update computes, while
 * the other updates are blocked until the constant is initialized, whereafter the other
 * updates observes the lazy constant is initialized and leave the constant unchanged
 * and will never invoke any computation.
 * <p>
 * The at-most-once write operation on a lazy constant that succeeds
 * (e.g., via an initial {@linkplain #get()} operation)
 * {@linkplain java.util.concurrent##MemoryVisibility <em>happens-before</em>}
 * any other read operation (e.g. {@linkplain #get()}).
 * A write operation can be initiated via invoking either:
 * <ul>
 *     <li>{@link #get()};</li>
 *     <li>{@link #hashCode()}, or</li>
 *     <li>{@link #equals(Object)} where the other object is a lazy constant.</li>
 * </ul>
 * A read operation can be either:
 * <ul>
 *     <li>a {@link #get()};</li>
 *     <li>an {@link #isInitialized()};</li>
 *     <li>a {@link #hashCode()}, or</li>
 *     <li>a {@link #equals(Object)} where the other object is a lazy constant.</li>
 * </ul>
 * <p>
 * Invocations of the computing function (via any of the write-initiating operations
 * like {@link #get()}) form a total order of zero or more exceptional invocations
 * followed by zero or one successful invocation of the computing function.
 *
 * <h2 id="performance">Performance</h2>
 * As a lazy constant can never change after it has been initialized. Therefore,
 * a JVM implementation may, for an initialized lazy constant, elide all future reads
 * of that lazy constant, and instead directly use any constant that it has previously
 * observed. This is true if the reference to the lazy constant is a VM constant
 * (e.g. in cases where the lazy constant itself is stored in a
 * {@code static final} field) or forms a trusted chain to such a VM constant via
 * one or more layers of a {@linkplain Record record} fields or final fields
 * in hidden classes.
 *
 * @implSpec Except for {@linkplain #equals(Object) equals(obj)} parameters; all
 *           method parameters must be <em>non-null</em> or a {@link NullPointerException}
 *           will be thrown.
 *
 * @implNote As a lazy constant can be initialized with an object but, it is not
 *           possible to ever remove that object, this can be a source of an unintended
 *           memory leak. In other words, a lazy constant
 *           {@linkplain java.lang.ref##reachability strongly references} the object
 *           it was initialized with. Hence, a lazy constant will hold the object it
 *           was initialized with until the lazy constant itself is collected (if ever).
 *           <p>
 *           A {@code LazyConstant} that has a type parameter {@code T} that is an
 *           array type (of arbitrary rank) will only allow the JVM to treat the
 *           <em>array reference</em> as a constant but <em>not its components</em>.
 *           Instead, a {@linkplain List#ofLazy(int, IntFunction) a computed list} of
 *           arbitrary depth can be used, which provides constant components.
 *           More generally, a lazy constant can hold other lazy constants of
 *           arbitrary depth and still provide transitive constantness.
 *           <p>
 *           A {@code LazyConstant} is not {@link Serializable}.
 *           <p>
 *           Lazy constants strongly references its underlying computing function
 *           used to compute values so long as the lazy constant is not
 *           initialized, after which the computing function is no longer strongly
 *           referenced and may be collected.
 *           <p>
 *           A lazy constant is free to synchronize on itself. Hence, care must be
 *           taken when directly or indirectly synchronizing on a lazy constant.
 *
 * @param <T> type of the constant
 *
 * @see List#ofLazy(int, IntFunction)
 * @see Map#ofLazy(Set, Function)
 * @since 26
 */
@PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
public sealed interface LazyConstant<T>
        extends Supplier<T>
        permits LazyConstantImpl {

    /**
     * {@return the constant if initialized, otherwise, returns {@code other}}
     *
     * @param other value to return if the constant is not initialized
     */
    T orElse(T other);

    /**
     * {@return the initialized constant. If not initialized, first computes and
     *          initializes the constant using the computing function}
     * <p>
     * After this method returns successfully, the constant is guaranteed to be
     * initialized.
     *
     * @throws NullPointerException if the computing function returns {@code null}
     * @throws RuntimeException if an exception is thrown while executing the
     *         computing function
     */
    T get();

    /**
     * {@return {@code true} if the constant is initialized, {@code false} otherwise}
     */
    boolean isInitialized();


    // Object methods

    /**
     * Indicates whether some other object is "equal to" this lazy constant.
     * The other object is considered equal if:
     * <ul>
     * <li>it is also a {@code LazyConstant} and;
     * <li>the constant values obtained via {@linkplain #get()} are "equal to"
     * each other via {@code equals()}.
     * </ul>
     *
     * @param obj an object to be tested for equality
     * @throws NullPointerException if the computing function returns {@code null}
     * @throws RuntimeException if an exception is thrown while executing the
     *         computing function
     * @return {@code true} if the other object is "equal to" this object
     *         otherwise {@code false}
     */
    boolean equals(Object obj);

    /**
     * {@return the hash code of this constant}
     *
     * @throws NullPointerException if the computing function returns {@code null}
     * @throws RuntimeException if an exception is thrown while executing the
     *         computing function
     */
    int hashCode();

    // Factories

    /**
     * {@return a new lazy constant to be computed later via the provided
     *          {@code computingFunction}}
     *
     * @param computingFunction in the form of a Supplier to be used to compute
     *                          the constant
     * @param <T>               type of the constant
     *
     */
    static <T> LazyConstant<T> of(Supplier<? extends T> computingFunction) {
        Objects.requireNonNull(computingFunction);
        return LazyConstantImpl.ofLazy(computingFunction);
    }

}
