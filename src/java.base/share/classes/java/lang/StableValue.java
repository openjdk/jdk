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
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * A stable value is an object that represents deferred immutable data.
 * <p>
 * In the Java programming language, a field could either be immutable (i.e. declared
 * {@code final}) or mutable (i.e. not declared {@code final}). Immutable fields confer
 * many performance optimizations but must be set exactly once by the creating thread
 * in the constructor or in the static initializer. Mutable fields cannot be optimized in
 * the same way but can be set an arbitrary number of times, at any time, by any thread in
 * any place in the code.
 * {@code StableValue} provides a means to eventually obtain the same performance
 * optimizations as for an immutable field while providing almost the same degree of
 * initialization freedom as for a mutable field, where the only difference is that a
 * stable value can be set <em>at most once</em>. In effect, a stable value represents
 * <em>deferred immutable data</em>.
 * <p>
 * The {@code StableValue} API provides several factory methods that provide stable
 * immutable holder objects that have <em>unset</em> values. By executing methods on a
 * stable value object, holder values can be <em>set</em>.
 * <p>
 * Consider the following example with a stable value "{@code VAL}" which
 * here is an immutable holder of a value of type {@linkplain Integer} and that is
 * initially created as <em>unset</em>, which means it holds no value. Later, the
 * holder value is <em>set</em> to {@code 42} (unless it was not previously <em>set</em>).
 * Once <em>set</em>, the holder value of "{@code VAL}" can be retrieved via the method
 * {@linkplain StableValue#orElseThrow()}.
 *
 * {@snippet lang = java:
 *     // Creates a new stable value with no holder value
 *     // @link substring="of" target="#of" :
 *     static final StableValue<Integer> VAL = StableValue.of();
 *
 *     // ... logic that may or may not set the holder value of `VAL`
 *
 *     // @link substring="trySet(42)" target="#trySet(Object)"
 *     if (VAL.trySet(42)) {
 *        System.out.println("The holder value was set to 42.");
 *     } else {
 *        // @link substring="orElseThrow" target="#orElseThrow"
 *        System.out.println("The holder value was already set to " + VAL.orElseThrow());
 *     }
 *}
 * <p>
 * Note that the holder value can only be set at most once, even when several threads are
 * racing to set the holder value. Only one thread is selected as the winner.
 * <p>
 * In this other example, a stable "{@code logger}" field is declared and is accessed
 * via a {@code getLogger} method in which the holder value is lazily computed via a
 * provided lambda expression:
 * {@snippet lang = java:
 *    class Component {
 *
 *        // @link substring="of" target="#of" :
 *        private final StableValue<Logger> logger = StableValue.of();
 *
 *        private Logger getLogger() {
 *            return logger.computeIfUnset( () -> Logger.getLogger("org.app.Component") );
 *        }
 *
 *        void process() {
 *            getLogger().info("Process started");
 *            // ...
 *        }
 *
 *    }
 *}
 * The {@code getLogger()} method calls {@code logger.computeIfUnset()} on the
 * stable value to retrieve its holder value. If the stable value is <em>unset</em>, then
 * {@code computeIfUnset()} evaluates and sets the holder value; the holder value is then
 * returned to the client. In other words, {@code computeIfUnset()} guarantees that a
 * stable value's holder value is <em>set</em> before it is used.
 * <p>
 * Even though the stable value, once <em>set</em>, is immutable, its holder value is not
 * required to be <em>set</em> upfront. Rather, it can be <em>set</em> on demand.
 * Furthermore, {@code computeIfUnset()} guarantees that the lambda expression provided is
 * evaluated only once, even when {@code logger.computeIfUnset()} is invoked concurrently.
 * This property is crucial as evaluation of the lambda expression may have side effects,
 * e.g., the call above to {@code Logger.getLogger()} may result in storage resources
 * being prepared.
 * <p>
 * A {@linkplain StableValue} is mainly intended to be a member of a holding class (as
 * shown in the examples above) and is usually neither exposed directly via accessors nor
 * passed as a method parameter.
 *
 * <h2 id="stable-functions">Stable Functions</h2>
 * Stable functions are thread-safe, caching, and lazily computed functions that, for each
 * allowed input (if any), record the values of some original function upon being first
 * accessed via the stable function's sole abstract method.
 * <p>
 * All the stable functions guarantee the provided original function is invoked
 * successfully at most once per valid input even in a multithreaded environment.
 * <p>
 * A <em>stable supplier</em> allows a more convenient construct compared to a
 * {@linkplain StableValue} in the sense that a field declaration can also specify how
 * the holder value of a stable value is to be set, but without actually setting its
 * holder value upfront:
 * {@snippet lang = java:
 *     class Component {
 *
 *         private final Supplier<Logger> logger =
 *                 // @link substring="ofSupplier" target="#ofSupplier(Supplier)" :
 *                 StableValue.ofSupplier( () -> Logger.getLogger("org.app.Component") );
 *
 *         void process() {
 *            logger.get().info("Process started");
 *            // ...
 *         }
 *     }
 *}
 * This also allows the stable supplier to be accessed directly, without going through
 * an accessor method like {@code getLogger()} in the previous example.
 * <p>
 * A <em>stable int function</em> stores values in an array of stable values where
 * the elements' holder value are computed the first time a particular input value
 * is provided.
 * When the stable int function is first created --
 * via the {@linkplain StableValue#ofIntFunction(int, IntFunction)
 * StableValue.ofIntFunction()} factory -- the input range (i.e. {@code [0, size)}) is
 * specified together with an original {@linkplain IntFunction} which is invoked
 * at most once per input value. In effect, the stable int function will act like a cache
 * for the original {@code IntFunction}:
 * {@snippet lang = java:
 *     class SqrtUtil {
 *
 *         private static final IntFunction<Double> SQRT =
 *                 // @link substring="ofIntFunction" target="#ofIntFunction(int,IntFunction)" :
 *                 StableValue.ofIntFunction(10, Math::sqrt);
 *
 *         double sqrt9() {
 *             return SQRT.apply(9); // Constant folds to 3.0
 *         }
 *
 *     }
 *}
 * <p>
 * A <em>stable function</em> stores values in an array of stable values where
 * the elements' holder value are computed the first time a particular input value
 * is provided.
 * When the stable function is first created --
 * via the {@linkplain StableValue#ofFunction(Set, Function) StableValue.ofFunction()}
 * factory -- the input Set is specified together with an original {@linkplain Function}
 * which is invoked at most once per input value. In effect, the stable function will act
 * like a cache for the original {@code Function}:
 * {@snippet lang = java:
 *     class SqrtUtil {
 *
 *         private static final Function<Integer, Double> SQRT =
 *                 // @link substring="ofFunction" target="#ofFunction(Set,Function)" :
 *                 StableValue.ofFunction(Set.of(1, 2, 4, 8, 16, 32), Math::sqrt);
 *
 *         double sqrt16() {
 *             return SQRT.apply(16); // Constant folds to 4.0
 *         }
 *
 *     }
 *}
 *
 * <h2 id="stable-collections">Stable Collections</h2>
 * Stable collections are thread-safe, caching, lazily computed, shallowly immutable
 * collections that, for each allowed input, record the values of some original mapper
 * upon being first accessed (directly or indirectly) via the collection's get method.
 * <p>
 * All the stable collections guarantee the provided original mapper is invoked
 * successfully at most once per valid input even in a multithreaded environment.
 * <p>
 * A <em>stable list</em> is similar to a stable int function but provides a full
 * implementation of an immutable {@linkplain List}. This is useful when interacting with
 * collection-based methods. Here is an example of how a stable list can be used to hold
 * a pool of order controllers:
 * {@snippet lang = java:
 *    class Application {
 *        static final int POOL_SIZE = 16;
 *
 *        static final List<OrderController> ORDER_CONTROLLERS =
 *                // @link substring="ofList" target="#ofList(int,IntFunction)" :
 *                StableValue.ofList(POOL_SIZE, OrderController::new);
 *
 *        public static OrderController orderController() {
 *            long index = Thread.currentThread().threadId() % POOL_SIZE;
 *            return ORDER_CONTROLLERS.get((int) index);
 *        }
 *    }
 * }
 * <p>
 * Note: In the example above, there is a constructor in the {@code OrderController}
 *       class that takes an {@code int} parameter.
 * <p>
 * A <em>stable map</em> is similar to a stable function but provides a full
 * implementation of an immutable {@linkplain Map}. This is useful when interacting with
 * collection-based methods. Here is how a stable map can be used as a cache for
 * square roots for certain input values given at creation:
 * {@snippet lang = java:
 *     class SqrtUtil {
 *
 *         private static final Map<Integer, Double> SQRT =
 *                 // @link substring="ofMap" target="#ofMap(Set,Function)" :
 *                 StableValue.ofMap(Set.of(1, 2, 4, 8, 16, 32), Math::sqrt);
 *
 *         double sqrt16() {
 *             return SQRT.apply(16); // Constant folds to 4.0
 *         }
 *
 *     }
 *}
 * <h2 id="constant-folding">Constant Folding</h2>
 * Once a stable value is <em>set</em>, the holder value is eligible for
 * <em>constant-folding</em> optimizations provided the following requirements are
 * fulfilled:
 * <ul>
 *     <li>There is a trusted constant root in the form of a {@code static final} field.</li>
 *     <li>There is a path from the constant root to said <em>set</em> holder value via
 *         one or more trusted edges where each trusted edge can be either:
 *         <ul>
 *             <li>A component in a {@linkplain Record}.</li>
 *             <li>A {@code final} field of declared type {@linkplain StableValue} in any class,</li>
 *             <li>An element from a {@code final} field of declared type {@linkplain StableValue StableValue[]} in any class.</li>
 *             <li>A {@code final} field in a hidden class.</li>
 *         </ul>
 *     </li>
 * </ul>
 * It is expected that the types of trusted edges in the JDK will increase
 * over time and that eventually, <em>all</em> {@code final} fields will be trusted.
 *
 * <h2 id="memory-consistency">Memory Consistency Properties</h2>
 * All stores made to a holder value before being set are guaranteed to be seen by
 * any thread that obtains the holder value via a stable value.
 * <p>
 * More formally, the action of attempting to interact (i.e. via load or store operations)
 * with a StableValue's holder value (e.g. via {@link StableValue#trySet} or
 * {@link StableValue#orElseThrow()}) forms a
 * <a href="{@docRoot}/java.base/java/util/concurrent/package-summary.html#MemoryVisibility"><i>happens-before</i></a>
 * relation between any other action of attempting to interact with the
 * StableValue's holder value. The same happens-before guarantees extend to
 * stable functions and stable collections; any action of attempting to interact via a
 * valid input value {@code I} <em>happens-before</em> any subsequent action of attempting
 * to interact via a valid input value {@code J} if, and only if, {@code I} and {@code J}
 * {@linkplain Object#equals(Object) equals()}.
 *
 * <h2 id="miscellaneous">Miscellaneous</h2>
 * Except for a StableValue's holder value itself, all method parameters must be
 * <em>non-null</em> or a {@link NullPointerException} will be thrown.
 *
 * @implSpec Implementing classes of {@linkplain StableValue} are free to synchronize on
 *           {@code this} and consequently, care should be taken whenever
 *           (directly or indirectly) synchronizing on a {@code StableValue}. Failure to
 *           do this may lead to deadlock. Stable functions and collections on the
 *           other hand are guaranteed <em>not to synchronize</em> on {@code this}.
 *
 * @implNote Instance fields explicitly declared as {@code StableValue} or one-dimensional
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
     * {@return the holder value; if unset, first attempts to compute the holder value
     *          using the provided {@code supplier}}
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
     * if already set, throws {@link IllegalStateException}.
     * <p>
     * When this method returns (or throws an Exception), the holder value is always set.
     *
     * @param value to set
     * @throws IllegalStateException if the holder value was already set
     */
    void setOrThrow(T value);

    // Factories

    /**
     * {@return a new stable value with no holder value}
     * <p>
     * The returned {@linkplain StableValue stable value} is a thin, atomic, thread-safe,
     * set-at-most-once, stable value with no holder value eligible for certain JVM
     * optimizations once the holder value is set.
     *
     * @param <T> type of the holder value
     */
    static <T> StableValue<T> of() {
        return StableValueFactories.of();
    }

    /**
     * {@return a new stable supplier with no holder value}
     * <p>
     * The returned stable {@linkplain Supplier supplier} is a thread-safe, caching,
     * and lazily computed supplier that records the value of the provided {@code original}
     * supplier upon being first accessed via the returned supplier's
     * {@linkplain Supplier#get() get()} method.
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
     * {@return a new stable int function with no holder values}
     * <p>
     * The returned stable {@link IntFunction int function} is a thread-safe, caching,
     * and lazily computed int function that, for each allowed input, records the values
     * of the provided {@code original} int function upon being first accessed via the
     * returned int function's {@linkplain IntFunction#apply(int) apply()} method.
     * <p>
     * The provided {@code original} int function is guaranteed to be successfully invoked
     * at most once per allowed input, even in a multi-threaded environment. Competing
     * threads invoking the returned inr function's
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
     * {@return a new stable function with no holder values}
     * <p>
     * The returned stable {@link Function function} is a stable, thread-safe,
     * caching, and lazily computed function that, for each allowed input in the given
     * set of {@code inputs}, records the values of the provided {@code original} function
     * upon being first accessed via the returned function's
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
     * {@return a new stable list of the provided {@code size} with no holder values}
     * <p>
     * The returned {@linkplain List list} is a stable, thread-safe, caching,
     * lazily computed, and shallowly immutable list where the individual elements of the
     * list are lazily computed via the provided {@code mapper} whenever an element is
     * first accessed (directly or indirectly), for example via the returned list's
     * {@linkplain List#get(int) get()} method.
     * <p>
     * The provided {@code mapper} int function is guaranteed to be successfully invoked
     * at most once per list index, even in a multi-threaded environment. Competing
     * threads accessing an element already under computation will block until an element
     * is computed or an exception is thrown by the computing thread.
     * <p>
     * If the provided {@code mapper} throws an exception, it is relayed to the initial
     * caller and no holder value for the element is recorded.
     * <p>
     * The returned List is not {@link Serializable} as this would require the provided
     * {@code mapper} to be {@link Serializable} as well, which would create security
     * concerns.
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
     * {@return a new stable map with the provided {@code keys} with no holder values}
     * <p>
     * The returned {@linkplain Map map} is a stable, thread-safe, caching,
     * lazily computed, and shallowly immutable map where the individual values of the map
     * are lazily computed via the provided {@code mapper} whenever an element is first
     * accessed (directly or indirectly), for example via the returned map's
     * {@linkplain Map#get(Object) get()} method.
     * <p>
     * If the provided {@code mapper} throws an exception, it is relayed to the initial
     * caller and no holder value associated with the provided key is recorded.
     * <p>
     * The returned Map is not {@link Serializable} as this would require the provided
     * {@code mapper} to be {@link Serializable} as well, which would create security
     * concerns.
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
