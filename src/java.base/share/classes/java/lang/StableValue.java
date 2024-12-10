/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import jdk.internal.lang.stable.StableValueImpl;
import jdk.internal.lang.stable.StableValueFactories;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * A stable value is a holder of deferred immutable data.
 * <p>
 * A {@linkplain StableValue {@code StableValue<T>}} is created using the factory method
 * {@linkplain StableValue#unset() {@code StableValue.unset()}}. When created, the
 * stable value is <em>unset</em>, which means it holds no value. Its holder value, of
 * type {@code T}, can be <em>set</em> by calling
 * {@linkplain #trySet(Object) trySet()}, {@linkplain #setOrThrow(Object) setOrThrow()},
 * or {@linkplain #computeIfUnset(Supplier) computeIfUnset()}. Once set, the holder value
 * can never change and can be retrieved by calling
 * {@linkplain #orElseThrow() orElseThrow()}, {@linkplain #orElse(Object) orElse()}, or
 * {@linkplain #computeIfUnset(Supplier) computeIfUnset()}.
 * <p>
 * A stable value that is <em>set</em> is treated as a constant by the JVM, enabling the
 * same performance optimizations that are available for {@code final} fields.
 * As such, stable values can be used to replace {@code final} fields in cases where
 * <em>at-most-once</em> update semantics is crucial, but where the eager initialization
 * semantics associated with {@code final} fields is too restrictive.
 * <p>
 * Consider the following example where a stable value field "{@code logger}" is an
 * immutable holder of a value of type {@code Logger} and that is initially created
 * as <em>unset</em>, which means it holds no value. Later in the example, the
 * state of the "{@code logger}" field is checked and if it is still <em>unset</em>,
 * a holder value is <em>set</em>:
 *
 * {@snippet lang = java:
 * class Component {
 *
 *    // Creates a new unset stable value with no holder value
 *    // @link substring="unset" target="#unset" :
 *    private final StableValue<Logger> logger = StableValue.unset();
 *
 *    Logger getLogger() {
 *        if (!logger.isSet()) {
 *            logger.trySet(Logger.create(Component.class));
 *        }
 *         return logger.orThrow();
 *    }
 *
 *    void process() {
 *        logger.get().info("Process started");
 *        // ...
 *    }
 * }
 *}
 * <p>
 * Note that the holder value can only be set at most once.
 * <p>
 * To guarantee that, even under races, only one instance of {@code Logger} is ever
 * created, the {@linkplain #computeIfUnset(Supplier) computeIfUnset()} method can be used
 * instead, where the holder is atomically and lazily computed via a lambda expression:
 *
 * {@snippet lang = java:
 * class Component {
 *
 *    // Creates a new unset stable value with no holder value
 *    // @link substring="unset" target="#unset" :
 *    private final StableValue<Logger> logger = StableValue.unset();
 *
 *    Logger getLogger() {
 *        return logger.computeIfUnset( () -> Logger.create(Component.class) );
 *    }
 *
 *    void process() {
 *        logger.get().info("Process started");
 *        // ...
 *    }
 * }
 *}
 * <p>
 * The {@code getLogger()} method calls {@code logger.computeIfUnset()} on the
 * stable value to retrieve its holder value. If the stable value is <em>unset</em>, then
 * {@code computeIfUnset()} evaluates and sets the holder value; the holder value is then
 * returned to the client. In other words, {@code computeIfUnset()} guarantees that a
 * stable value's holder value is <em>set</em> before it is used.
 * <p>
 * Furthermore, {@code computeIfUnset()} guarantees that the lambda expression provided is
 * evaluated only once, even when {@code logger.computeIfUnset()} is invoked concurrently.
 * This property is crucial as evaluation of the lambda expression may have side effects,
 * e.g., the call above to {@code Logger.getLogger()} may result in storage resources
 * being prepared.
 *
 * <h2 id="stable-functions">Stable Functions</h2>
 * Stable values provide the foundation for higher-level functional abstractions. A
 * <em>stable supplier</em> is a supplier that computes a value and then caches it into
 * a backing stable value storage for later use. A stable supplier is created via the
 * {@linkplain StableValue#ofSupplier(Supplier) StableValue.ofSupplier()} factory,
 * by providing an original {@linkplain Supplier} which is invoked when the
 * stable supplier is first accessed:
 *
 * {@snippet lang = java:
 *     class Component {
 *
 *         private final Supplier<Logger> logger =
 *                 // @link substring="ofSupplier" target="#ofSupplier(Supplier)" :
 *                 StableValue.ofSupplier( () -> Logger.getLogger(Component.class) );
 *
 *         void process() {
 *            logger.get().info("Process started");
 *            // ...
 *         }
 *     }
 *}
 * A stable supplier encapsulates access to its backing stable value storage. This means
 * that code inside {@code Component} can obtain the logger object directly from the
 * stable supplier, without having to go through an accessor method like {@code getLogger()}.
 * <p>
 * A <em>stable int function</em> is a function that takes an {@code int} parameter and
 * uses it to compute a result that is then cached into the backing stable value storage
 * for that parameter value. A stable int function is created via the
 * {@linkplain StableValue#ofIntFunction(int, IntFunction) StableValue.ofIntFunction()}
 * factory. Upon creation, the input range (i.e. [0, size)) is specified together with
 * an original {@linkplain IntFunction} which is invoked at most once per input value. In
 * effect, the stable int function will act like a cache for the original {@linkplain IntFunction}:
 *
 * {@snippet lang = java:
 *     class SqrtUtil {
 *
 *         private static final IntFunction<Double> SQRT =
 *                 // @link substring="ofIntFunction" target="#ofIntFunction(int,IntFunction)" :
 *                 StableValue.ofIntFunction(10, StrictMath::sqrt);
 *
 *         double sqrt9() {
 *             return SQRT.apply(9); // Eventually constant folds to 3.0 at runtime
 *         }
 *
 *     }
 *}
 * <p>
 * A <em>stable function</em> is a function that takes a parameter (of type {@code T}) and
 * uses it to compute a result that is then cached into the backing stable value storage
 * for that parameter value. A stable function is created via the
 * {@linkplain StableValue#ofFunction(Set, Function) StableValue.ofFunction()} factory.
 * Upon creation, the input {@linkplain Set} is specified together with an original {@linkplain Function}
 * which is invoked at most once per input value. In effect, the stable function will act
 * like a cache for the original {@linkplain Function}:
 *
 * {@snippet lang = java:
 *     class SqrtUtil {
 *
 *         private static final Function<Integer, Double> SQRT =
 *                 // @link substring="ofFunction" target="#ofFunction(Set,Function)" :
 *                 StableValue.ofFunction(Set.of(1, 2, 4, 8, 16, 32), StrictMath::sqrt);
 *
 *         double sqrt16() {
 *             return SQRT.apply(16); // Eventually constant folds to 4.0 at runtime
 *         }
 *
 *     }
 *}
 *
 * <h2 id="stable-collections">Stable Collections</h2>
 * Stable values can also be used as backing storage for
 * {@linkplain Collection##unmodifiable unmodifiable collections}. A <em>stable list</em>
 * is an unmodifiable list, backed by an array of stable values. The stable list elements
 * are computed when they are first accessed, using the provided {@linkplain IntFunction}:
 *
 * {@snippet lang = java:
 *     class SqrtUtil {
 *
 *         private static final List<Double> SQRT =
 *                 // @link substring="ofList" target="#ofList(int,IntFunction)" :
 *                 StableValue.ofList(10, StrictMath::sqrt);
 *
 *         double sqrt9() {
 *             return SQRT.apply(9); // Eventually constant folds to 3.0 at runtime
 *         }
 *
 *     }
 *}
 * <p>
 * Note: In the example above, there is a constructor in the {@code Component}
 *       class that takes an {@code int} parameter.
 * <p>
 * Similarly, a <em>stable map</em> is an unmodifiable map whose keys are known at
 * construction. The stable map values are computed when they are first accessed,
 * using the provided {@linkplain Function}:
 *
 * {@snippet lang = java:
 *     class SqrtUtil {
 *
 *         private static final Map<Integer, Double> SQRT =
 *                 // @link substring="ofMap" target="#ofMap(Set,Function)" :
 *                 StableValue.ofMap(Set.of(1, 2, 4, 8, 16, 32), StrictMath::sqrt);
 *
 *         double sqrt16() {
 *             return SQRT.apply(16); // Eventually constant folds to 4.0 at runtime
 *         }
 *
 *     }
 *}
 *
 * <h2 id="composition">Composing stable values</h2>
 * A stable value can depend on other stable values, thereby creating a dependency graph
 * that can be lazily computed but where access to individual elements still can be
 * constant-folded. In the following example, a single {@code Foo} and a {@code Bar}
 * instance (that is dependent on the {@code Foo} instance) are lazily created, both of
 * which are held by stable values:
 * {@snippet lang = java:
 *     class Dependency {
 *
 *         public static class Foo {
 *              // ...
 *          }
 *
 *         public static class Bar {
 *             public Bar(Foo foo) {
 *                  // ...
 *             }
 *         }
 *
 *         private static final Supplier<Foo> FOO = StableValue.ofSupplier(Foo::new);
 *         private static final Supplier<Bar> BAR = StableValue.ofSupplier(() ->  new Bar(FOO.get()));
 *
 *         public static Foo foo() {
 *             return FOO.get();
 *         }
 *
 *         public static Bar bar() {
 *             return BAR.get();
 *         }
 *
 *     }
 *}
 * Calling {@code bar()} will create the {@code Bar} singleton if it is not already
 * created. Upon such a creation, the dependent {@code Foo} will first be created if
 * the {@code Foo} does not already exist.
 * <p>
 * Here is another example where a more complex dependency graph is created in which
 * integers in the Fibonacci delta series are lazily computed:
 * {@snippet lang = java:
 *     class Fibonacci {
 *
 *         private static final int MAX_SIZE_INT = 46;
 *
 *         private static final IntFunction<Integer> FIB =
 *                 StableValue.ofIntFunction(MAX_SIZE_INT, Fibonacci::fib);
 *
 *         public static int fib(int n) {
 *             return n < 2
 *                     ? n
 *                     : FIB.apply(n - 1) + FIB.apply(n - 2);
 *         }
 *
 *     }
 * }
 * Both {@code FIB} and {@code Fibonacci::fib} recurses into each other. Because the
 * stable int function {@code FIB} caches intermediate results, the initial
 * computational complexity is reduced from exponential to linear compared to a
 * traditional non-caching recursive fibonacci method. Once computed, the VM can
 * constant-fold expressions like {@code Fibonacci.fib(10)}.
 * <p>
 * The fibonacci example above is a dependency graph with no circular dependencies (i.e.,
 * it is a dependency tree). If there are circular dependencies in a dependency graph,
 * a stable value will eventually throw a {@linkplain StackOverflowError} upon referencing
 * elements in a circularity.
 *
 * <h2 id="thread-safety">Thread Safety</h2>
 * A holder value is guaranteed to be set at most once. If competing threads are
 * racing to set a stable value, only one update succeeds, while other updates are
 * blocked until the stable value becomes set.
 * <p>
 * The at-most-once write operation on a stable value (e.g. {@linkplain #trySet(Object) trySet()})
 * <a href="{@docRoot}/java.base/java/util/concurrent/package-summary.html#MemoryVisibility"><i>happens-before</i></a>
 * any subsequent read operation (e.g. {@linkplain #orElseThrow()}).
 * <p>
 * The method {@linkplain StableValue#computeIfUnset(Supplier) computeIfUnset()}
 * guarantees that the provided {@linkplain Supplier} is invoked successfully at most
 * once even under race. Since stable functions and stable collections are built on top
 * of {@linkplain StableValue#computeIfUnset(Supplier) computeIfUnset()} they too are
 * thread safe and guarantee at-most-once-per-input invocation.
 *
 * <h2 id="miscellaneous">Miscellaneous</h2>
 * Except for a StableValue's holder value itself, all method parameters must be
 * <em>non-null</em> or a {@link NullPointerException} will be thrown.
 * <p>
 * Stable functions and collections are not {@link Serializable} as this would require
 * {@linkplain #ofList(int, IntFunction) mappers} to be {@link Serializable} as well,
 * which would introduce security vulnerabilities.
 * <p>
 * As objects can be set via stable values but never removed, this can be a source
 * of unintended memory leaks. A stable value's set values are
 * {@linkplain java.lang.ref##reachability strongly reachable}. Clients are advised that
 * {@linkplain java.lang.ref##reachability reachable} stable values will hold set values
 * perpetually.
 *
 * @implSpec Implementing classes of {@linkplain StableValue} are free to synchronize on
 *           {@code this} and consequently, care should be taken whenever
 *           (directly or indirectly) synchronizing on a {@code StableValue}. Failure to
 *           do this may lead to deadlock. Stable functions and collections on the
 *           other hand are guaranteed <em>not to synchronize</em> on {@code this}.
 *
 * @implNote A {@linkplain StableValue} is mainly intended to be a non-public field in
 *           a class and is usually neither exposed directly via accessors nor passed as
 *           a method parameter.
 *           Instance fields explicitly declared as {@code StableValue} or one-dimensional
 *           arrays thereof are eligible for certain JVM optimizations where normal
 *           instance fields are not. This comes with restrictions on reflective
 *           modifications. Although most ways of reflective modification of such fields
 *           are disabled, it is strongly discouraged to circumvent these protection means
 *           as reflectively modifying such fields may lead to unspecified behavior.
 *
 * @param <T> type of the holder value
 *
 * @since 25
 */
@PreviewFeature(feature = PreviewFeature.Feature.STABLE_VALUES)
public sealed interface StableValue<T>
        permits StableValueImpl {

    // Principal methods

    /**
     * {@return {@code true} if the holder value was set to the provided
     *          {@code value}, {@code false} otherwise}
     * <p>
     * When this method returns, the holder value is always set.
     *
     * @param value to set
     */
    boolean trySet(T value);

    /**
     * {@return the holder value if set, otherwise, returns the provided
     *          {@code other} value}
     *
     *
     * @param other to return if the holder value is not set
     */
    T orElse(T other);

    /**
     * {@return the holder value if set, otherwise, throws {@code NoSuchElementException}}
     *
     * @throws NoSuchElementException if no holder value is set
     */
    T orElseThrow();

    /**
     * {@return {@code true} if the holder value is set, {@code false} otherwise}
     */
    boolean isSet();

    /**
     * {@return the holder value; if unset, first attempts to compute and set the
     *          holder value using the provided {@code supplier}}
     * <p>
     * The provided {@code supplier} is guaranteed to be invoked at most once if it
     * completes without throwing an exception.
     * <p>
     * If the supplier throws an (unchecked) exception, the exception is rethrown, and no
     * holder value is set. The most common usage is to construct a new object serving
     * as a lazily computed value or memoized result, as in:
     *
     * <pre> {@code
     * Value witness = stable.computeIfUnset(Value::new);
     * }</pre>
     * <p>
     * When this method returns successfully, the holder value is always set.
     *
     * @implSpec The implementation logic is equivalent to the following steps for this
     *           {@code stable}:
     *
     * <pre> {@code
     * if (stable.isSet()) {
     *     return stable.get();
     * } else {
     *     V newValue = supplier.get();
     *     stable.setOrThrow(newValue);
     *     return newValue;
     * }
     * }</pre>
     * Except it is thread-safe and will only return the same witness value
     * regardless if invoked by several threads. Also, the provided {@code supplier}
     * will only be invoked once even if invoked from several threads unless the
     * {@code supplier} throws an exception.
     *
     * @param  supplier to be used for computing the holder value
     */
    T computeIfUnset(Supplier<? extends T> supplier);

    // Convenience methods

    /**
     * Sets the holder value to the provided {@code value}, or,
     * if already set, throws {@code IllegalStateException}.
     * <p>
     * When this method returns (or throws an exception), the holder value is always set.
     *
     * @param value to set
     * @throws IllegalStateException if the holder value was already set
     */
    void setOrThrow(T value);

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
     * An unset stable value has no holder value.
     *
     * @param <T> type of the holder value
     */
    static <T> StableValue<T> unset() {
        return StableValueFactories.unset();
    }

    /**
     * {@return a new set stable value holding the provided {@code value}}
     *
     * @param value holder value to set
     * @param <T>   type of the holder value
     */
    static <T> StableValue<T> of(T value) {
        return StableValueFactories.of(value);
    }

    /**
     * {@return a new unset stable supplier}
     * <p>
     * The returned {@linkplain Supplier supplier} is a caching supplier that records
     * the value of the provided {@code original} supplier upon being first accessed via
     * the returned supplier's {@linkplain Supplier#get() get()} method.
     * <p>
     * The provided {@code original} supplier is guaranteed to be successfully invoked
     * at most once even in a multi-threaded environment. Competing threads invoking the
     * returned supplier's {@linkplain Supplier#get() get()} method when a value is
     * already under computation will block until a value is computed or an exception is
     * thrown by the computing thread.
     * <p>
     * If the provided {@code original} supplier throws an exception, it is relayed
     * to the initial caller and no holder value is recorded.
     *
     * @param original supplier used to compute a cached value
     * @param <T>      the type of results supplied by the returned supplier
     */
    static <T> Supplier<T> ofSupplier(Supplier<? extends T> original) {
        Objects.requireNonNull(original);
        return StableValueFactories.ofSupplier(original);
    }

    /**
     * {@return a new unset stable int function}
     * <p>
     * The returned {@link IntFunction int function} is a caching int function that,
     * for each allowed input, records the values of the provided {@code original}
     * int function upon being first accessed via the returned int function's
     * {@linkplain IntFunction#apply(int) apply()} method.
     * <p>
     * The provided {@code original} int function is guaranteed to be successfully invoked
     * at most once per allowed input, even in a multi-threaded environment. Competing
     * threads invoking the returned int function's
     * {@linkplain IntFunction#apply(int) apply()} method when a value is already under
     * computation will block until a value is computed or an exception is thrown by
     * the computing thread.
     * <p>
     * If the provided {@code original} int function throws an exception, it is relayed
     * to the initial caller and no holder value is recorded.
     *
     * @param size     the size of the allowed inputs in {@code [0, size)}
     * @param original IntFunction used to compute cached values
     * @param <R>      the type of results delivered by the returned IntFunction
     */
    static <R> IntFunction<R> ofIntFunction(int size,
                                            IntFunction<? extends R> original) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        Objects.requireNonNull(original);
        return StableValueFactories.ofIntFunction(size, original);
    }

    /**
     * {@return a new unset stable function}
     * <p>
     * The returned {@link Function function} is a caching function that, for each allowed
     * input in the given set of {@code inputs}, records the values of the provided
     * {@code original} function upon being first accessed via the returned function's
     * {@linkplain Function#apply(Object) apply()} method.
     * <p>
     * The provided {@code original} function is guaranteed to be successfully invoked
     * at most once per allowed input, even in a multi-threaded environment. Competing
     * threads invoking the returned function's {@linkplain Function#apply(Object) apply()}
     * method when a value is already under computation will block until a value is
     * computed or an exception is thrown by the computing thread.
     * <p>
     * If the provided {@code original} function throws an exception, it is relayed to
     * the initial caller and no holder value is recorded.
     *
     * @param inputs   the set of allowed input values
     * @param original Function used to compute cached values
     * @param <T>      the type of the input to the returned Function
     * @param <R>      the type of results delivered by the returned Function
     */
    static <T, R> Function<T, R> ofFunction(Set<? extends T> inputs,
                                            Function<? super T, ? extends R> original) {
        Objects.requireNonNull(inputs);
        Objects.requireNonNull(original);
        return StableValueFactories.ofFunction(inputs, original);
    }

    /**
     * {@return a new unset stable list with the provided {@code size}}
     * <p>
     * The returned list is an {@linkplain Collection##unmodifiable unmodifiable} list
     * whose size is known at construction. The list's elements are computed via the
     * provided {@code mapper} when they are first accessed
     * (e.g. via {@linkplain List#get(int) List::get}).
     * <p>
     * The provided {@code mapper} int function is guaranteed to be successfully invoked
     * at most once per list index, even in a multi-threaded environment. Competing
     * threads accessing an element already under computation will block until an element
     * is computed or an exception is thrown by the computing thread.
     * <p>
     * If the provided {@code mapper} throws an exception, it is relayed to the initial
     * caller and no value for the element is recorded.
     * <p>
     * The returned list and its {@link List#subList(int, int) subList} views implement
     * the {@link RandomAccess} interface.
     *
     * @param size   the size of the returned list
     * @param mapper to invoke whenever an element is first accessed
     *               (may return {@code null})
     * @param <E>    the type of elements in the returned list
     */
    static <E> List<E> ofList(int size,
                              IntFunction<? extends E> mapper) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        Objects.requireNonNull(mapper);
        return StableValueFactories.ofList(size, mapper);
    }

    /**
     * {@return a new unset stable map with the provided {@code keys}}
     * <p>
     * The returned map is an {@linkplain Collection##unmodifiable unmodifiable} map whose
     * keys are known at construction. The map's values are computed via the provided
     * {@code mapper} when they are first accessed
     * (e.g. via {@linkplain Map#get(Object) Map::get}).
     * <p>
     * The provided {@code mapper} function is guaranteed to be successfully invoked
     * at most once per key, even in a multi-threaded environment. Competing
     * threads accessing a value already under computation will block until an element
     * is computed or an exception is thrown by the computing thread.
     * <p>
     * If the provided {@code mapper} throws an exception, it is relayed to the initial
     * caller and no value associated with the provided key is recorded.
     *
     * @param keys   the keys in the returned map
     * @param mapper to invoke whenever an associated value is first accessed
     *               (may return {@code null})
     * @param <K>    the type of keys maintained by the returned map
     * @param <V>    the type of mapped values in the returned map
     */
    static <K, V> Map<K, V> ofMap(Set<K> keys,
                                  Function<? super K, ? extends V> mapper) {
        Objects.requireNonNull(keys);
        Objects.requireNonNull(mapper);
        return StableValueFactories.ofMap(keys, mapper);
    }

}
