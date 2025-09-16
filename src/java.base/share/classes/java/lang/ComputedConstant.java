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
import jdk.internal.lang.ComputedConstantImpl;

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
 * A computed constant is a deferred shallowly immutable constant to be computed at
 * a later time by an underlying computing function.
 * <p>
 * A computed constant is created using the factory method
 * {@linkplain ComputedConstant#of(Supplier)}. When created, the computed constant is
 * <em>unset</em>, which means the constant is not yet set. The constant, of type
 * {@code T}, can then be <em>set</em> (and retrieved) by calling
 * {@linkplain #get()}. The firsts time {@linkplain #get()} is called, an underlying
 * <em>computing function</em> will be invoked which would compute
 * the constant. The computing function is provided at construction. Once set,
 * the constant can <em>never change</em> and can be retrieved over and over again
 * by subsequent {@linkplain #get() get} invocations.
 * <p>
 * Consider the following example where a computed constant field "{@code logger}" is a
 * shallowly immutable holder of a constant of type {@code Logger}:
 *
 * {@snippet lang = java:
 * public class Component {
 *
 *    // Creates a new unset computed constant
 *    // @link substring="of" target="#of" :
 *    private final ComputedConstant<Logger> logger =
 *            ComputedConstant.of( () -> Logger.create(Component.class) );
 *
 *    public void process() {
 *        logger.get().info("Process started");
 *        // ...
 *    }
 * }
 *}
 * <p>
 * Initially, the computed constant is <em>unset</em>, until {@code logger.get()}
 * evaluates the computing function, and sets the constant to the result;
 * the result is then returned to the client. Hence, {@linkplain #get()} guarantees that
 * a computer constant is <em>set</em> before it returns, baring any exceptions.
 * <p>
 * Furthermore, {@linkplain #get()} guarantees that, out several threads trying to invoke
 * the computing function simultaneously, only one is ever evaluated. This property is
 * crucial as evaluation of the computing function may have side effects, for example,
 * the call above to {@code Logger.create()} may result in storage resources being
 * prepared.
 *
 * <h2 id="composition">Composing computed constants</h2>
 * A computed constant can depend on other computed constants, forming a dependency graph
 * that can be lazily computed but where access to individual elements can still be
 * performant. In the following example, a single {@code Foo} and a {@code Bar}
 * instance (that is dependent on the {@code Foo} instance) are lazily created, both of
 * which are held by computed constants:
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
 *     private static final ComputedConstant<Foo> FOO = ComputedConstant.of(Foo::new);
 *     private static final ComputedConstant<Bar> BAR = ComputedConstant.of(() -> new Bar(FOO.get()));
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
 * <p>
 * If the computing function returns {@code null}, a {@linkplain NullPointerException}
 * is thrown. Hence, a computed constant can never be {@code null}. Clients that want to
 * use a nullable constant can wrap the value into an {@linkplain Optional} holder.
 *
 * <h2 id="thread-safety">Thread Safety</h2>
 * A computed constant is guaranteed to be set at most once. If competing
 * threads are racing to set a computed constant, only one update computes, while
 * the other updates are blocked until the constant is set, whereafter the other updates
 * observes the computed constant is set and leave the constant unchanged and will never
 * invoke any computation.
 * <p>
 * The at-most-once write operation on a computed constant that succeeds
 * (e.g., via an initial {@linkplain #get()} operation)
 * {@linkplain java.util.concurrent##MemoryVisibility <em>happens-before</em>}
 * any other read operation (e.g. {@linkplain #get()}).
 * A write operation can be initiated via invoking either:
 * <ul>
 *     <li>{@link #get()};</li>
 *     <li>{@link #hashCode()}, or</li>
 *     <li>{@link #equals(Object)} where the other object is a computed constant.</li>
 * </ul>
 * A read operation can be either:
 * <ul>
 *     <li>a {@link #get()};</li>
 *     <li>an {@link #isSet()};</li>
 *     <li>a {@link #hashCode()}, or</li>
 *     <li>a {@link #equals(Object)} where the other object is a computed constant.</li>
 * </ul>
 * <p>
 * Invocations of the computing function (via any of the write-initiating operations
 * like {@link #get()}) form a total order of zero or more exceptional invocations
 * followed by zero or one successful invocation of the computing function.
 *
 * <h2 id="performance">Performance</h2>
 * As a computed constant can never change after it has been set. Therefore, a JVM
 * implementation may, for a set computed constant, elide all future reads of that
 * computed constant, and instead directly use any constant that it has previously
 * observed. This is true if the reference to the computed constant is a VM constant
 * (e.g. in cases where the computed constant itself is stored in a
 * {@code static final} field) or forms a trusted chain to such a VM constant via
 * one or more layers of a {@linkplain Record record} fields or final fields
 * in hidden classes.
 *
 * @implSpec Except for {@linkplain #equals(Object) equals(obj)} parameters; all
 *           method parameters must be <em>non-null</em> or a {@link NullPointerException}
 *           will be thrown.
 *
 * @implNote As an object can be set via computed constants but never removed, this can be
 *           a source of an unintended memory leak. A computed constant is
 *           {@linkplain java.lang.ref##reachability strongly reachable}.
 *           Be advised that reachable computed constants will hold their constants until
 *           the computed constants themselves are collected.
 *           <p>
 *           A {@code ComputedConstant} that has a type parameter {@code T} that is an
 *           array type (of arbitrary rank) will only allow the JVM to treat the
 *           <em>array reference</em> as a constant but <em>not its components</em>.
 *           Instead, a {@linkplain List#ofComputed(int, IntFunction) a computed list} of
 *           arbitrary depth can be used, which provides constant components.
 *           More generally, a computed constant can hold other computed constants of
 *           arbitrary depth and still provide transitive constantness.
 *           <p>
 *           A {@code ComputedConstant} is not {@link Serializable}.
 *           <p>
 *           Computed constants strongly references its underlying computing function
 *           used to compute values so long as no constant is computed, after which
 *           the computing function is not strongly referenced anymore and may be
 *           collected.
 *           <p>
 *           A computed constant is free to synchronize on itself. Hence, care must be
 *           taken when directly or indirectly synchronizing on a computed constant.
 *
 * @param <T> type of the constant
 *
 * @see List#ofComputed(int, IntFunction)
 * @see Map#ofComputed(Set, Function)
 * @since 26
 */
@PreviewFeature(feature = PreviewFeature.Feature.COMPUTED_CONSTANTS)
public sealed interface ComputedConstant<T>
        extends Supplier<T>
        permits ComputedConstantImpl {

    /**
     * {@return the constant if set, otherwise, returns {@code other}}
     *
     * @param other value to return if the constant is not set
     */
    T orElse(T other);

    /**
     * {@return the set constant. If not set, first computes and sets the constant using
     *          the computing function}
     *
     * @throws NullPointerException if the computing function returns {@code null}
     * @throws RuntimeException if an exception is thrown while executing the
     *         computing function
     */
    T get();

    /**
     * {@return {@code true} if the contents is set, {@code false} otherwise}
     */
    boolean isSet();


    // Object methods

    /**
     * Indicates whether some other object is "equal to" this computed constant.
     * The other object is considered equal if:
     * <ul>
     * <li>it is also a {@code ComputedConstant} and;
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
     * {@return he hash code of this constant}
     *
     * @throws NullPointerException if the computing function returns {@code null}
     * @throws RuntimeException if an exception is thrown while executing the
     *         computing function
     */
    int hashCode();

    // Factories

    /**
     * {@return a new computed constant to be computed later using the provided
     *          {@code computingFunction}}
     *
     * @param computingFunction in the form of a Supplier used to compute the constant
     * @param <T>               type of the constant
     *
     */
    static <T> ComputedConstant<T> of(Supplier<? extends T> computingFunction) {
        Objects.requireNonNull(computingFunction);
        return ComputedConstantImpl.ofComputed(computingFunction);
    }

}
