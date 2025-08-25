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

package java.lang.invoke;

import jdk.internal.access.SharedSecrets;
import jdk.internal.foreign.Utils;
import jdk.internal.javac.PreviewFeature;
import jdk.internal.invoke.stable.InternalStableValue;
import jdk.internal.invoke.stable.StandardStableValue;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * A stable value is a holder of contents that can be set at most once.
 * <p>
 * A {@code StableValue<T>} is typically created using the factory method
 * {@linkplain StableValue#of() {@code StableValue.of()}}. When created this way,
 * the stable value is <em>unset</em>, which means it holds no <em>contents</em>.
 * Its contents, of type {@code T}, can be <em>set</em> by calling
 * {@linkplain #trySet(Object) trySet()},  or {@linkplain #orElseSet(Supplier) orElseSet()}.
 * Once set, the contents can never change and can be retrieved by calling
 * {@linkplain #get() orElseThrow()} , {@linkplain #orElse(Object) () orElse()},
 * or {@linkplain #orElseSet(Supplier) orElseSet()}.
 * <p>
 * Consider the following example where a stable value field "{@code logger}" is a
 * shallowly immutable holder of contents of type {@code Logger} and that is initially
 * created as <em>unset</em>, which means it holds no contents. Later in the example, the
 * state of the "{@code logger}" field is checked and if it is still <em>unset</em>,
 * the contents is <em>set</em>:
 *
 * {@snippet lang = java:
 * public class Component {
 *
 *    // Creates a new unset stable value with no contents
 *    // @link substring="of" target="#of" :
 *    private final StableValue<Logger> logger = StableValue.of();
 *
 *    private Logger getLogger() {
 *        if (!logger.isSet()) {
 *            logger.trySet(Logger.create(Component.class));
 *        }
 *        return logger.get();
 *    }
 *
 *    public void process() {
 *        getLogger().info("Process started");
 *        // ...
 *    }
 * }
 *}
 * <p>
 * If {@code getLogger()} is called from several threads, several instances of
 * {@code Logger} might be created. However, the contents can only be set at most once
 * meaning the first writer wins.
 * <p>
 * In order to guarantee that, even under races, only one instance of {@code Logger} is
 * ever created, the {@linkplain #orElseSet(Supplier) orElseSet()} method can be used
 * instead, where the contents are lazily computed, and atomically set, via a
 * {@linkplain Supplier supplier}. In the example below, the supplier is provided in the
 * form of a lambda expression:
 *
 * {@snippet lang = java:
 * public class Component {
 *
 *    // Creates a new unset stable value with no contents
 *    // @link substring="of" target="#of" :
 *    private final StableValue<Logger> logger = StableValue.of();
 *
 *    private Logger getLogger() {
 *        return logger.orElseSet( () -> Logger.create(Component.class) );
 *    }
 *
 *    public void process() {
 *        getLogger().info("Process started");
 *        // ...
 *    }
 * }
 *}
 * <p>
 * The {@code getLogger()} method calls {@code logger.orElseSet()} on the stable value to
 * retrieve its contents. If the stable value is <em>unset</em>, then {@code orElseSet()}
 * evaluates the given supplier, and sets the contents to the result; the result is then
 * returned to the client. In other words, {@code orElseSet()} guarantees that a
 * stable value's contents is <em>set</em> before it returns.
 * <p>
 * Furthermore, {@code orElseSet()} guarantees that out of one or more suppliers provided,
 * only at most one is ever evaluated, and that one is only ever evaluated once,
 * even when {@code logger.orElseSet()} is invoked concurrently. This property is crucial
 * as evaluation of the supplier may have side effects, for example, the call above to
 * {@code Logger.create()} may result in storage resources being prepared.
 *
 * <h2 id="stable-functions">Stable Functions</h2>
 * Stable values provide the foundation for higher-level functional abstractions. A
 * <em>stable supplier</em> is a supplier that computes a value and then caches it into
 * a backing stable value storage for subsequent use. A stable supplier is created via the
 * {@linkplain Supplier#ofCaching(Supplier) Supplier.ofLazy()} factory, by
 * providing an underlying {@linkplain Supplier} which is invoked when the stable supplier
 * is first accessed:
 *
 * {@snippet lang = java:
 * public class Component {
 *
 *     private final Supplier<Logger> logger =
 *             // @link substring="ofLazyFinal" target="Supplier#ofLazyFinal(Supplier)" :
 *             Supplier.ofCaching( () -> Logger.getLogger(Component.class) );
 *
 *     public void process() {
 *        logger.get().info("Process started");
 *        // ...
 *     }
 * }
 *}
 * A stable supplier encapsulates access to its backing stable value storage. This means
 * that code inside {@code Component} can obtain the logger object directly from the
 * stable supplier, without having to go through an accessor method like {@code getLogger()}.
 *
 * <h2 id="stable-collections">Stable Collections</h2>
 * Stable values can also be used as backing storage for
 * {@linkplain Collection##unmodifiable unmodifiable collections}. A <em>lazy stable list</em>
 * is an unmodifiable list, backed by an array of stable values. The lazy stable list's
 * elements are computed when they are first accessed, using a provided {@linkplain IntFunction}:
 *
 * {@snippet lang = java:
 * final class PowerOf2Util {
 *
 *     private PowerOf2Util() {}
 *
 *     private static final int SIZE = 6;
 *     private static final IntFunction<Integer> UNDERLYING_POWER_OF_TWO = v -> 1 << v;
 *
 *     // @link substring="ofLazy" target="List#ofLazy(int,IntFunction)" :
 *     private static final List<Integer> POWER_OF_TWO = List.ofLazy(SIZE, UNDERLYING_POWER_OF_TWO);
 *
 *     public static int powerOfTwo(int a) {
 *         return POWER_OF_TWO.get(a);
 *     }
 * }
 *
 * int result = PowerOf2Util.powerOfTwo(4);   // May eventually constant fold to 16 at runtime
 *
 *}
 * <p>
 * Similarly, a <em>lazy stable map</em> is an unmodifiable map whose keys are known at
 * construction. The lazy stable map's values are computed when they are first accessed,
 * using a provided {@linkplain Function}:
 *
 * {@snippet lang = java:
 * class Log2Util {
 *
 *     private Log2Util() {}
 *
 *     private static final Set<Integer> KEYS = Set.of(1, 2, 4, 8, 16, 32);
 *
 *     private static final UnaryOperator<Integer> UNDERLYING_LOG2 = i -> 31 - Integer.numberOfLeadingZeros(i);
 *
 *     // @link substring="ofLazy" target="java.util.Map#ofLazy(Set,Function)" :
 *     private static final Map<Integer, INTEGER> LOG2 = Map.ofLazy(CACHED_KEYS, UNDERLYING_LOG2);
 *
 *     public static int log2(int a) {
 *          return LOG2.get(a);
 *     }
 *
 * }
 *
 * int result = Log2Util.log2(16);   // May eventually constant fold to 4 at runtime
 *
 *}
 *
 * <h2 id="composition">Composing stable values</h2>
 * A stable value can depend on other stable values, forming a dependency graph
 * that can be lazily computed but where access to individual elements can still be
 * performant. In the following example, a single {@code Foo} and a {@code Bar}
 * instance (that is dependent on the {@code Foo} instance) are lazily created, both of
 * which are held by stable values:
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
 *     private static final Supplier<Foo> FOO = Supplier.ofCaching(Foo::new);
 *     private static final Supplier<Bar> BAR = Supplier.ofCaching(() -> new Bar(FOO.get()));
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
 * Calling {@code bar()} will create the {@code Bar} singleton if it is not already
 * created. Upon such a creation, the dependent {@code Foo} will first be created if
 * the {@code Foo} does not already exist.
 * <p>
 * Another example, which has a more complex dependency graph, is to compute the
 * Fibonacci sequence lazily:
 * {@snippet lang = java:
 * public final class Fibonacci {
 *
 *     private Fibonacci() {}
 *
 *     private static final int MAX_SIZE_INT = 46;
 *
 *     private static final List<Integer> FIB = List.ofLazy(MAX_SIZE_INT, Fibonacci::fib);
 *
 *     public static int fib(int n) {
 *         return n < 2
 *                 ? n
 *                 : FIB.get(n - 1) + FIB.get(n - 2);
 *     }
 *
 * }
 *}
 * Both {@code FIB} and {@code Fibonacci::fib} recurse into each other. Because the
 * lazy stable list {@code FIB} caches intermediate results, the initial
 * computational complexity is reduced from exponential to linear compared to a
 * traditional non-caching recursive fibonacci method. Once computed, the VM is free to
 * constant-fold expressions like {@code Fibonacci.fib(5)}.
 * <p>
 * The fibonacci example above is a directed acyclic graph (i.e.,
 * it has no circular dependencies and is therefore a dependency tree):
 *{@snippet lang=text :
 *
 *              ___________fib(5)____________
 *             /                             \
 *       ____fib(4)____                  ____fib(3)____
 *      /              \                /              \
 *    fib(3)          fib(2)          fib(2)          fib(1)
 *   /     \         /     \         /     \
 * fib(2) fib(1)   fib(1) fib(0)   fib(1) fib(0)
 *}
 *
 * If there are circular dependencies in a dependency graph, a stable value will
 * eventually throw an {@linkplain IllegalStateException} upon referencing elements in
 * a circularity.
 *
 * <h2 id="thread-safety">Thread Safety</h2>
 * The contents of a stable value is guaranteed to be set at most once. If competing
 * threads are racing to set a stable value, only one update succeeds, while the other
 * updates are blocked until the stable value is set, whereafter the other updates
 * observes the stable value is set and leave the stable value unchanged.
 * <p>
 * The at-most-once write operation on a stable value that succeeds
 * (e.g. {@linkplain #trySet(Object) trySet()})
 * {@linkplain java.util.concurrent##MemoryVisibility <em>happens-before</em>}
 * any successful read operation (e.g. {@linkplain #get()}).
 * A successful write operation can be either:
 * <ul>
 *     <li>a {@link #trySet(Object)} that returns {@code true}, or</li>
 *     <li>an {@link #orElseSet(Supplier)} that successfully runs the supplier</li>
 * </ul>
 * A successful read operation can be either:
 * <ul>
 *     <li>a {@link #get()} that does not throw,</li>
 *     <li>a {@link #orElse(Object) orElse(other)} that does not return the {@code other} value</li>
 *     <li>an {@link #orElseSet(Supplier)} that does not {@code throw}, or</li>
 *     <li>an {@link #isSet()} that returns {@code true}</li>
 * </ul>
 * <p>
 * The method {@link #orElseSet(Supplier)} guarantees that the provided
 * {@linkplain Supplier} is invoked successfully at most once, even under race.
 * Invocations of {@link #orElseSet(Supplier)} form a total order of zero or
 * more exceptional invocations followed by zero (if the contents were already set) or one
 * successful invocation. Since stable functions and stable collections are built on top
 * of the same principles as {@linkplain StableValue#orElseSet(Supplier) orElseSet()} they
 * too are thread safe and guarantee at-most-once-per-input invocation.
 *
 * <h2 id="performance">Performance</h2>
 * As the contents of a stable value can never change after it has been set, a JVM
 * implementation may, for a set stable value, elide all future reads of that
 * stable value, and instead directly use any contents that it has previously observed.
 * This is true if the reference to the stable value is a constant (e.g. in cases where
 * the stable value itself is stored in a {@code static final} field). Stable functions
 * and collections are built on top of StableValue. As such, they might also be eligible
 * for the same JVM optimizations as for StableValue.
 *
 * @implSpec Implementing classes of {@code StableValue} are free to synchronize on
 *           {@code this} and consequently, it should be avoided to
 *           (directly or indirectly) synchronize on a {@code StableValue}. Hence,
 *           synchronizing on {@code this} may lead to deadlock.
 *           <p>
 *           Except for an {@linkplain #orElse(Object) orElse(other)} parameter, or and
 *           an {@linkplain #equals(Object) equals(obj)} parameter; all
 *           method parameters must be <em>non-null</em> or a {@link NullPointerException}
 *           will be thrown.
 *
 * @implNote A {@code StableValue} is mainly intended to be a non-public field in
 *           a class and is usually neither exposed directly via accessors nor passed as
 *           a method parameter.
 *           <p>
 *           Stable functions and collections make reasonable efforts to provide
 *           {@link Object#toString()} operations that do not trigger evaluation
 *           of the internal stable values when called.
 *           Stable collections have {@link Object#equals(Object)} operations that try
 *           to minimize evaluation of the internal stable values when called.
 *           <p>
 *           As objects can be set via stable values but never removed, this can be a
 *           source of unintended memory leaks. A stable value's contents are
 *           {@linkplain java.lang.ref##reachability strongly reachable}.
 *           Be advised that reachable stable values will hold their set contents until
 *           the stable value itself is collected.
 *           <p>
 *           A {@code StableValue} that has a type parameter {@code T} that is an array
 *           type (of arbitrary rank) will only allow the JVM to treat the
 *           <em>array reference</em> as a stable value but <em>not its components</em>.
 *           Instead, a {@linkplain List#ofLazy(int, IntFunction) a stable list} of arbitrary
 *           depth can be used, which provides stable components. More generally, a
 *           stable value can hold other stable values of arbitrary depth and still
 *           provide transitive constantness.
 *           <p>
 *           Stable values, functions, and collections are not {@link Serializable}.
 *           <p>
 *           Stable values, collections and functions strongly references its underlying
 *           function used to compute values so long as there are values remaining to
 *           be computed after which the underlying function is not strongly referenced
 *           anymore and may be collected.
 *
 * @param <T> type of the contents
 *
 * @since 26
 */
@PreviewFeature(feature = PreviewFeature.Feature.STABLE_VALUES)
public sealed interface StableValue<T>
        permits InternalStableValue, StandardStableValue {

    /**
     * Tries to set the contents of this StableValue to the provided {@code contents}.
     * The contents of this StableValue can only be set once, implying this method only
     * returns {@code true} once.
     * <p>
     * When this method returns, the contents of this StableValue is always set.
     *
     * @return {@code true} if the contents of this StableValue was set to the
     *         provided {@code contents}, {@code false} otherwise
     * @param contents to set
     * @throws IllegalStateException if a supplier invoked by {@link #orElseSet(Supplier)}
     *         recursively attempts to set this stable value by calling this method
     *         directly or indirectly
     * @throws NullPointerException if the provided {@code content} is {@code null}
     */
    boolean trySet(T contents);

    /**
     * {@return the contents if set, otherwise, returns {@code other}}
     *
     * @param other value to return if no contents is set
     */
    T orElse(T other);

    /**
     * {@return the contents if set, otherwise, throws {@code NoSuchElementException}}
     *
     * @throws NoSuchElementException if no contents is set
     */
    T get();

    /**
     * {@return {@code true} if the contents is set, {@code false} otherwise}
     */
    boolean isSet();

    /**
     * {@return the contents; if unset, first attempts to compute and set the
     *          contents using the provided {@code supplier}}
     * <p>
     * The provided {@code supplier} is guaranteed to be invoked at most once if it
     * completes without throwing an exception. If this method is invoked several times
     * with different suppliers, only one of them will be invoked provided it completes
     * without throwing an exception.
     * <p>
     * If the supplier throws an (unchecked) exception, the exception is rethrown and no
     * contents is set. The most common usage is to construct a new object serving
     * as a lazily computed value or memoized result, as in:
     *
     * {@snippet lang=java:
     * Value v = stable.orElseSet(Value::new);
     * }
     * <p>
     * When this method returns successfully the contents is always set.
     * <p>
     * The provided {@code supplier} will only be invoked once even if invoked from
     * several threads unless the {@code supplier} throws an exception.
     * <p>
     * If the provided {@code supplier} returns {@code null},
     * a {@linkplain NullPointerException} is thrown.
     *
     * @param  supplier to be used for computing the contents, if not previously set
     * @throws IllegalStateException if the provided {@code supplier} recursively
     *                               attempts to set this stable value
     */
    T orElseSet(Supplier<? extends T> supplier);

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
     * {@return a new unset stable value}
     * <p>
     * An unset stable value has no contents.
     *
     * @param <T> type of the contents
     */
    static <T> StableValue<T> of() {
        return StandardStableValue.of();
    }

    /**
     * {@return a new pre-set stable value with the provided {@code contents}}
     *
     * @param contents to set
     * @param <T>     type of the contents
     * @throws NullPointerException if the provided {@code contents} is {@code null}
     */
    static <T> StableValue<T> of(T contents) {
        Objects.requireNonNull(contents);
        return StandardStableValue.ofPreset(contents);
    }

    /**
     * {@return a new unmodifiable list of the provided {@code size} containing
     *          unset stable value elements}
     * <p>
     * The returned list is equivalent to creating an unmodifiable list as in this example:
     * {@snippet lang = java:
     * return Stream.generate(StableValue::<E>of)
     *            .limit(size)
     *            .toList();
     *}
     * except the returned implementation can be more performant and dense.
     *
     * @implNote The implementation is free to return distinct stable value elements
     *           upon retrieving successive elements with the same index. Despite this,
     *           any returned stable value always reflects the correct stable value
     *           contents at any time.
     *
     * @param size of the returned list
     * @param <E>  type of the contents
     * @throws IllegalArgumentException if the provided {@code size} is negative
     *
     * @since 26
     */
    static <E> List<StableValue<E>> ofList(int size) {
        Utils.checkNonNegativeArgument(size, "size");
        return SharedSecrets.getJavaUtilCollectionAccess().denseStableList(size);
    }

    /**
     * {@return a new unmodifiable list containing set stable value with values from
     *          the provided array of {@code elements}}
     * <p>
     * The returned list is equivalent to creating an unmodifiable list as in this example:
     * {@snippet lang = java:
     * return Arrays.stream(elements)
     *            .map(StableValue::of)
     *            .toList();
     *}
     * except the returned implementation can be more performant and dense.
     *
     * @implNote The implementation is free to return distinct stable value elements
     *           upon retrieving successive elements with the same index. Despite this,
     *           any returned stable value always reflects the correct stable value
     *           contents at any time.
     *
     * @param elements in the returned list
     * @param <E>      type of the contents
     * @throws NullPointerException if the provided array of {@code elements}
     *         is {@code null} or contains a {@code null} component
     *
     * @since 26
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    // Note: This factory has another name than SV::ofList so that confusion
    //       around if a first parameter is a size or an element can be avoided.
    static <E> List<StableValue<E>> ofPresetList(E... elements) {
        // Protect against TOC-TOU attacks
        final E[] copy = Arrays.copyOf(elements, elements.length);
        for (E e : copy) {
            Objects.requireNonNull(e);
        }
        return SharedSecrets.getJavaUtilCollectionAccess().presetStableList(copy);
    }
}
