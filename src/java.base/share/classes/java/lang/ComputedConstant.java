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
import jdk.internal.lang.stable.ComputedConstantImpl;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * A computed constant is a constant that can be computed at any time by any thread
 * but at most once.
 * <p>
 * A computed constant is typically created using the factory method
 * {@linkplain ComputedConstant#of(Supplier)}. When created this way,
 * the computed constant is <em>unset</em>, which means its constant is not yet set.
 * The constant, of type {@code T}, can then be <em>set</em> (and retrieved) by calling
 * {@linkplain #get()} for the first time. Once set, the constant can _never change_ and
 * can be retrieved again by subsequent {@linkplain #get() get} invocations.
 * <p>
 * Consider the following example where a computed constant field "{@code logger}" is a
 * shallowly immutable holder of a constant of type {@code Logger} and that is initially
 * created as <em>unset</em>, which means the constant is not set. Later in the example,
 * the constant of the "{@code logger}" field is computed and retrieved via the provided
 * lambda:
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
 * evaluates the given supplier, and sets the constant to the result; the result is then
 * returned to the client. Hence, {@linkplain #get()} guarantees that a
 * computer constant is <em>set</em> before it returns baring any exceptions.
 * <p>
 * Furthermore, {@linkplain #get()} guarantees that, out several threads trying to invoke
 * the provided supplier simultaneously, only one is ever evaluated. This property is
 * crucial as evaluation of the supplier may have side effects, for example, the call
 * above to {@code Logger.create()} may result in storage resources being prepared.
 *
 * <h2 id="composition">Composing computed constants</h2>
 * A computed constant can depend on other computed constants, forming a dependency graph
 * that can be lazily computed but where access to individual elements can still be
 * performant. In the following example, a single {@code Foo} and a {@code Bar}
 * instance (that is dependent on the {@code Foo} instance) are lazily created, both of
 * which are held by computed constants:
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
 *
 * <h2 id="thread-safety">Thread Safety</h2>
 * A computed constant is guaranteed to be set at most once. If competing
 * threads are racing to set a computed constant, only one update computes, while the other
 * updates are blocked until the constant is set, whereafter the other updates
 * observes the computed constant is set and leave the constant unchanged and will never
 * invoke any computation.
 * <p>
 * The at-most-once write operation on a computed constant that succeeds
 * (i.e., via an initial {@linkplain #get()} operation)
 * {@linkplain java.util.concurrent##MemoryVisibility <em>happens-before</em>}
 * any other successful read operation (e.g. {@linkplain #get()}).
 * A successful read operation can be either:
 * <ul>
 *     <li>a {@link #get()}, or</li>
 *     <li>an {@link #isSet()}</li>
 * </ul>
 * <p>
 * Invocations of {@link #get()} form a total order of zero or
 * more exceptional invocations followed by zero (if the constant was already set) or one
 * successful invocation.
 *
 * <h2 id="performance">Performance</h2>
 * As a computed constant can never change after it has been set, a JVM
 * implementation may, for a set computed constant, elide all future reads of that
 * computed constant, and instead directly use any constant that it has previously observed.
 * This is true if the reference to the computed constant is a VM constant (e.g. in cases where
 * the computed constant itself is stored in a {@code static final} field) or forms a
 * trusted chain to such a VM constant via zero or more layers of a
 * {@linkplain Record record} fields or final fields in hidden classes.
 *
 * @implSpec Except for {@linkplain #equals(Object) equals(obj)} parameters; all
 *           method parameters must be <em>non-null</em> or a {@link NullPointerException}
 *           will be thrown.
 *
 * @implNote As objects can be set via computed constants but never removed, this can be a
 *           source of unintended memory leaks. A computed constant is
 *           {@linkplain java.lang.ref##reachability strongly reachable}.
 *           Be advised that reachable computed constants will hold their constants until
 *           the computed constant itself is collected.
 *           <p>
 *           A {@code ComputedConstant} that has a type parameter {@code T} that is an array
 *           type (of arbitrary rank) will only allow the JVM to treat the
 *           <em>array reference</em> as a constant but <em>not its components</em>.
 *           Instead, a {@linkplain List#ofComputed(int, IntFunction) a stable list} of arbitrary
 *           depth can be used, which provides stable components. More generally, a
 *           computed constant can hold other computed constants of arbitrary depth and
 *           still provide transitive constantness.
 *           <p>
 *           A {@code ComputedConstant} is not {@link Serializable}.
 *           <p>
 *           Computed constants strongly references its underlying
 *           supplier used to compute values so long as no constant is computed,
 *           after which the underlying function is not strongly referenced
 *           anymore and may be collected.
 *
 * @param <T> type of the constant
 *
 * @since 26
 */
@PreviewFeature(feature = PreviewFeature.Feature.STABLE_VALUES)
public sealed interface ComputedConstant<T> permits ComputedConstantImpl {

    /**
     * {@return the set constant. If not set, first computes and sets the constant}
     *
     * @throws RuntimeException if the computed constant's underlying supplier throws a
     *         RuntimeException
     * @throws Error if the computed constant's underlying supplier throws an Error
     */
    T get();

    /**
     * {@return {@code true} if the constant is set, {@code false} otherwise}
     */
    boolean isSet();

    // Object methods

    /**
     * {@return {@code true} if {@code this == obj}, {@code false} otherwise}
     *
     * @param obj to check for equality
     */
    boolean equals(Object obj);

    /**
     * {@return the {@linkplain System#identityHashCode(Object) identity hash code} of
     *          {@code this} object}
     */
    int hashCode();

    // Factories

    /**
     * {@return a new computed constant which is to be computed using the provided
     *          {@code underlying} supplier}
     * <p>
     * The returned {@linkplain ComputedConstant computed constant} will record
     * the value of the provided {@code underlying} supplier upon being first accessed via
     * the returned computed constant's {@linkplain ComputedConstant#get() get()} method.
     * <p>
     * The provided {@code underlying} supplier is guaranteed to be successfully invoked
     * at most once even in a multi-threaded environment. Competing threads invoking the
     * returned computed constant's {@linkplain ComputedConstant#get() get()} method when
     * a value is already under computation will block until a value is computed or
     * an exception or error is thrown by the computing thread. If no exception occurs,
     * the competing threads will then observe the newly computed constant and will then
     * never execute the {@code underlying} supplier.
     * <p>
     * If the provided {@code underlying} supplier throws an exception or an Error, it is
     * rethrown to the initial caller and no constant is recorded.
     * <p>
     * If the provided {@code underlying} supplier returns {@code null},
     * a {@linkplain NullPointerException} is thrown. Hence, a computed constant
     * can never be {@code null}. Clients that want to use a nullable constant
     * can wrap the value into an {@linkplain Optional} holder.
     * <p>
     * If the provided {@code underlying} supplier recursively calls the returned
     * computed constant, an {@linkplain IllegalStateException} will be thrown.
     * <p>
     * As the returned computed constant is a stable constant, it confers certain
     * performance optimization opportunities to the VM.
     *
     * @param underlying supplier used to compute the constant
     * @param <T>        type of the constant
     *
     * @see StableValue
     * @since 26
     */
    @PreviewFeature(feature = PreviewFeature.Feature.STABLE_VALUES)
    static <T> ComputedConstant<T> of(Supplier<? extends T> underlying) {
        Objects.requireNonNull(underlying);
        return ComputedConstantImpl.of(underlying);
    }

    /**
     * {@return a new computed constant with the preset {@code constant}}
     * <p>
     * As the returned computed constant is a stable constant, it confers certain
     * performance optimization opportunities to the VM.
     *
     * @param constant to use as a preset
     * @param <T>      type of the constant
     *
     * @see StableValue
     * @since 26
     */
    @PreviewFeature(feature = PreviewFeature.Feature.STABLE_VALUES)
    static <T> ComputedConstant<T> ofPreset(T constant) {
        Objects.requireNonNull(constant);
        return ComputedConstantImpl.ofPreset(constant);
    }

}
