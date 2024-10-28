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

import jdk.internal.access.SharedSecrets;
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
 * A value that has the same performance as a final field but can be freely initialized.
 * <p>
 * In the Java programming language, a field could either be immutable (i.e. declared
 * {@code final}) or mutable (i.e. not declared {@code final}):
 * <p>
 * Stable values are objects that represent deferred immutable data and are treated
 * as constants by the JVM, enabling the same performance optimizations that are
 * possible by marking a field {@code final}. Yet, stable values offer greater flexibility
 * as to the timing of initialization compared to {@code final} fields.
 * <p>
 * The characteristics of the various kinds of storage are summarized in the following table:
 * <br/>
 * <blockquote>
 * <table class="plain">
 *     <caption style="display:none">Storage kind comparison</caption>
 *     <thead>
 *     <tr>
 *         <th scope="col">Storage kind</th>
 *         <th scope="col">#Updates</th>
 *         <th scope="col">Update location</th>
 *         <th scope="col">Constant folding</th>
 *         <th scope="col">Concurrent updated</th>
 *     </tr>
 *     </thead>
 *     <tbody>
 *     <tr>
 *         <th scope="row" style="font-weight:normal">Mutable (non-{@code final})</th>
 *         <td style="text-align:center;">[0, &infin;)</td>
 *         <td style="text-align:center;">Anywhere</td>
 *         <td style="text-align:center;">No</td>
 *         <td style="text-align:center;">Yes</td>
 *     </tr>
 *     <tr>
 *         <th scope="row" style="font-weight:normal">Stable (StableValue)</th>
 *         <td style="text-align:center;">[0, 1]</td>
 *         <td style="text-align:center;">Anywhere</td>
 *         <td style="text-align:center;">Yes, after update</td>
 *         <td style="text-align:center;">Yes, by winner</td>
 *      </tr>
 *      <tr>
 *         <th scope="row" style="font-weight:normal">Immutable ({@code final})</th>
 *         <td style="text-align:center;">1</td>
 *         <td style="text-align:center;">Constructor or static initializer</td>
 *         <td style="text-align:center;">Yes</td>
 *         <td style="text-align:center;">No</td>
 *      </tr>
 *      </tbody>
 * </table>
 * </blockquote>
 * <p>
 * A <em>stable value</em> is an object of type {@linkplain StableValue {@code StableValue<T>}}
 * and is a holder of underlying data of type {@code T}. It can be used to defer
 * initialization of its underlying data while retaining the same performance as if
 * a field of type {@code T} was declared {@code final}. Once the underlying data has
 * been initialized, the stable value usually acts as a constant for the rest of the
 * program's execution. In effect, a stable value provides a way of achieving deferred
 * immutability.
 * <p>
 * A stable value can be used <em>imperatively</em> or <em>functionally</em> as outlined
 * hereunder.
 * <h2 id="imperative-use">Imperative use</h2>
 * Imperative use often entails more low-level usage, interacting directly with the
 * underlying data. Here is an example of how the underlying data can be initialized
 * imperatively using a pre-computed value (here {@code 42}) using the method
 * {@linkplain StableValue#trySet(Object) trySet()}. The method will return {@code true}
 * if the underlying data was indeed initialized, or it will return {@code false} if
 * the underlying data was already initialized:
 *
 * {@snippet lang=java :
 *     // Creates a new stable value with no underlying data
 *     StableValue<Integer> stableValue = StableValue.of();
 *     // ... logic that may or may not set the underlying data of stableValue
 *     if (stableValue.trySet(42)) {
 *        System.out.println("The underlying data was initialized to 42.");
 *     } else {
 *        System.out.println("The value was already initialized to " + stableValue.orElseThrow());
 *     }
 * }
 * Note that the underlying data can only be initialized at most once, even when several
 * threads are racing to initialize the underlying data. Only one thread is selected as
 * the winner.
 *
 * <h2 id="functional-use">Functional use</h2>
 * Functional use of a stable value entails providing a
 * {@linkplain Supplier {@code Supplier<? extends T>}} to the instance method
 * {@linkplain StableValue#computeIfUnset(Supplier) computeIfUnset()} whereby the
 * provided supplier is automatically invoked if there is no underlying data as shown in
 * this example:
 *
 * {@snippet lang = java:
 *    class Component {
 *
 *        private final StableValue<Logger> logger = StableValue.of();
 *
 *        Logger getLogger() {
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
 * stable value to retrieve its underlying data. If the stable value is unset, then
 * {@code computeIfUnset()} initializes the underlying data, causing the stable value to
 * become set; the underlying data is then returned to the client. In other words,
 * {@code computeIfUnset()} guarantees that a stable value's underlying data is
 * initialized before it is used.
 * <p>
 * Even though the stable value, once set, is immutable, its underlying data is not
 * required to be initialized upfront. Rather, it can be initialized on demand. Furthermore,
 * {@code computeIfUnset()} guarantees that the lambda expression provided is evaluated
 * only once, even when {@code logger.computeIfUnset()} is invoked concurrently.
 * This property is crucial as evaluation of the lambda expression may have side effects,
 * e.g., the call above to {@code Logger.getLogger()} may result in storage resources
 * being prepared.
 * <p>
 * A {@linkplain StableValue} is mainly intended to be a member of a holding class (as
 * shown in the examples above) and is usually neither exposed directly via accessors nor
 * passed as a method parameter.
 * <p>
 * There are two additional categories of stable values:
 * <ul>
 *     <li>{@linkplain StableValue##stable-functions Stable Functions}
 *     <ul>
 *         <li>Stable supplier: {@linkplain Supplier {@code Supplier<T>}}</li>
 *         <li>Stable int function: {@linkplain IntFunction {@code IntFunction<R>}}</li>
 *         <li>Stable function: {@linkplain Function {@code Function<T, R>}}</li>
 *     </ul>
 *     </li>
 *     <li>{@linkplain StableValue##stable-collections Stable Collections}
 *     <ul>
 *         <li>Stable list: {@linkplain List {@code List<E>}}</li>
 *         <li>Stable map: {@linkplain Map {@code Map<K, V>}}</li>
 *     </ul>
 *     </li>
 * </ul>
 *
 * <h2 id="stable-functions">Stable Functions</h2>
 * Stable functions are thread-safe, caching, lazily computed functions that, for each
 * allowed input (if any), record the values of some original function upon being first
 * accessed via the stable function's sole abstract method.
 * <p>
 * All the stable functions guarantee the provided original function is invoked
 * successfully at most once per valid input even in a multithreaded environment.
 * <p>
 * A <em>stable supplier</em> allows a more convenient construct compared to a
 * {@linkplain StableValue} in the sense that a field declaration can also specify how
 * the underlying data of a stable value is to be initialized, but without actually
 * initializing its underlying data upfront:
 * {@snippet lang = java:
 *     class Component {
 *
 *         private final Supplier<Logger> logger =
 *                 StableValue.ofSupplier( () -> Logger.getLogger("org.app.Component") );
 *
 *         void process() {
 *            logger.get().info("Process started");
 *            // ...
 *         }
 *     }
 *}
 * This also allows the stable supplier to be accessed directly, without going through
 * an accessor method like {@code getLogger()} in the
 * {@linkplain StableValue##functional-use functional example} above.
 * <p>
 * A <em>stable int function</em> stores values in an array of stable values where
 * the elements' underlying data are computed the first time a particular input value
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
 * the elements' underlying data are computed the first time a particular input value
 * is provided.
 * When the stable function is first created --
 * via the {@linkplain StableValue#ofFunction(Set, Function) StableValue.ofFunction()}
 * factory -- the input set is specified together with an original {@linkplain Function}
 * which is invoked at most once per input value. In effect, the stable function will act
 * like a cache for the original {@code Function}:
 * {@snippet lang = java:
 *     class SqrtUtil {
 *
 *         private static final Function<Integer, Double> SQRT =
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
 *                 StableValue.ofMap(Set.of(1, 2, 4, 8, 16, 32), Math::sqrt);
 *
 *         double sqrt16() {
 *             return SQRT.apply(16); // Constant folds to 4.0
 *         }
 *
 *     }
 *}
 *
 * <h2 id="memory-consistency">Memory Consistency Properties</h2>
 * All stores made to underlying data before being set are guaranteed to be seen by
 * any thread that obtains the data via a stable value.
 * <p>
 * More formally, the action of attempting to interact (i.e. via load or store operations)
 * with a StableValue's underlying data (e.g. via {@link StableValue#trySet} or
 * {@link StableValue#orElseThrow()}) forms a
 * <a href="{@docRoot}/java.base/java/util/concurrent/package-summary.html#MemoryVisibility"><i>happens-before</i></a>
 * relation between any other action of attempting to interact with the
 * StableValue's underlying data. The same happens-before guarantees extend to
 * stable functions and stable collections; any action of attempting to interact via a
 * valid input value {@code I} <em>happens-before</em> any subsequent action of attempting
 * to interact via a valid input value {@code J} if, and only if, {@code I} and {@code J}
 * {@linkplain Object#equals(Object) equals()}.
 *
 * <h2 id="miscellaneous">Miscellaneous</h2>
 * Except for a StableValue's underlying data itself, all method parameters must be
 * <em>non-null</em> or a {@link NullPointerException} will be thrown.
 *
 * @implSpec Implementing classes of {@linkplain StableValue} are free to synchronize on
 *           {@code this} and consequently, care should be taken whenever
 *           (directly or indirectly) synchronizing on a StableValue. Failure to do this
 *           may lead to deadlock. Stable functions and collections on the other hand are
 *           guaranteed <em>not to synchronize</em> on {@code this}.
 *
 * @implNote Instance fields explicitly declared as StableValue or one-dimensional arrays
 *           thereof are eligible for certain JVM optimizations where normal instance
 *           fields are not. This comes with restrictions on reflective modifications.
 *           Although most ways of reflective modification of such fields are disabled,
 *           it is strongly discouraged to circumvent these protection means as
 *           reflectively modifying such fields may lead to unspecified behavior.
 *
 * @param <T> type of the underlying data
 *
 * @since 25
 */
@PreviewFeature(feature = PreviewFeature.Feature.STABLE_VALUES)
public sealed interface StableValue<T>
        permits StableValueImpl {

    // Principal methods

    /**
     * {@return {@code true} if the underlying data was set to the provided
     *          {@code underlyingData}, otherwise returns {@code false}}
     * <p>
     * When this method returns, the underlying data is always set.
     *
     * @param underlyingData to set
     */
    boolean trySet(T underlyingData);

    /**
     * {@return the underlying data if set, otherwise, return the provided
     *          {@code other} value}
     *
     *
     * @param other to return if the underlying data is not set
     */
    T orElse(T other);

    /**
     * {@return the underlying data if set, otherwise, throws {@code NoSuchElementException}}
     *
     * @throws NoSuchElementException if no underlying data is set
     */
    T orElseThrow();

    /**
     * {@return {@code true} if the underlying data is set, {@code false} otherwise}
     */
    boolean isSet();

    /**
     * {@return the underlying data; if not set, first attempts to compute the
     *          underlying data using the provided {@code supplier}}
     * <p>
     * The provided {@code supplier} is guaranteed to be invoked at most once if it
     * completes without throwing an exception.
     * <p>
     * If the supplier throws an (unchecked) exception, the exception is rethrown, and no
     * underlying data is set. The most common usage is to construct a new object serving
     * as a lazily computed value or memoized result, as in:
     *
     * <pre> {@code
     * Value witness = stable.computeIfUnset(Value::new);
     * }</pre>
     * <p>
     * When this method returns successfully, the underlying data is always set.
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
     * @param  supplier to be used for computing the underlying data
     */
    T computeIfUnset(Supplier<? extends T> supplier);

    // Convenience methods

    /**
     * Sets the underlying data to the provided {@code underlyingData}, or,
     * if already set, throws {@link IllegalStateException}.
     * <p>
     * When this method returns (or throws an Exception), the underlying data is
     * always set.
     *
     * @param underlyingData to set
     * @throws IllegalStateException if the underlying data is already set
     */
    void setOrThrow(T underlyingData);

    // Factories

    /**
     * {@return a new stable value with no underlying data}
     * <p>
     * The returned {@linkplain StableValue stable value} is a thin, atomic, thread-safe,
     * set-at-most-once, stable value with no underlying data eligible for certain JVM
     * optimizations once the underlying data is set.
     *
     * @param <T> type of the underlying data
     */
    static <T> StableValue<T> of() {
        return StableValueFactories.of();
    }

    /**
     * {@return a new stable supplier with no underlying data}
     * <p>
     * The returned stable {@linkplain Supplier supplier} is a thread-safe, caching,
     * lazily computed supplier that records the value of the provided {@code original}
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
     * to the initial caller and no value is recorded.
     *
     * @param original supplier used to compute a cached value
     * @param <T>      the type of results supplied by the returned supplier
     */
    static <T> Supplier<T> ofSupplier(Supplier<? extends T> original) {
        Objects.requireNonNull(original);
        return StableValueFactories.ofSupplier(original);
    }

    /**
     * {@return a new stable int function with no underlying data}
     * <p>
     * The returned stable {@link IntFunction int function} is a thread-safe, caching,
     * lazily computed int function that, for each allowed input, records the values of
     * the provided {@code original} int function upon being first accessed via the
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
     * to the initial caller and no value is recorded.
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
     * {@return a new stable function with no underlying data}
     * <p>
     * The returned stable {@link Function function} is a stable, thread-safe,
     * caching, lazily computed function that, for each allowed input in the given
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
     * the initial caller and no value is recorded.
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
     * {@return a new stable list of the provided {@code size} with no underlying data}
     * <p>
     * The returned {@linkplain List list} is a stable, thread-safe, caching,
     * lazily computed, shallowly immutable list where the individual elements of the list
     * are lazily computed via the provided {@code mapper} whenever an element is first
     * accessed (directly or indirectly), for example via the returned list's
     * {@linkplain List#get(int) get()} method.
     * <p>
     * The provided {@code mapper} int function is guaranteed to be successfully invoked
     * at most once per list index, even in a multi-threaded environment. Competing
     * threads accessing an element already under computation will block until an element
     * is computed or an exception is thrown by the computing thread.
     * <p>
     * If the provided {@code mapper} throws an exception, it is relayed to the initial
     * caller and no element is recorded.
     * <p>
     * The returned List is not {@link Serializable} as this would require the provided
     * {@code mapper} to be {@link Serializable} as well which would create security
     * concerns.
     *
     * @param size   the size of the returned list
     * @param mapper to invoke whenever an element is first accessed
     *               (may return {@code null})
     * @param <E>    the type of elements in the returned list
     */
    static <E> List<E> ofList(int size, IntFunction<? extends E> mapper) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        Objects.requireNonNull(mapper);
        return SharedSecrets.getJavaUtilCollectionAccess().stableList(size, mapper);
    }

    /**
     * {@return a new stable map with the provided {@code keys} with no underlying data}
     * <p>
     * The returned {@linkplain Map map} is a stable, thread-safe, caching,
     * lazily computed, shallowly immutable map where the individual values of the map
     * are lazily computed via the provided {@code mapper} whenever an element is first
     * accessed (directly or indirectly), for example via the returned map's
     * {@linkplain Map#get(Object)} get()} method.
     * <p>
     * If the provided {@code mapper} throws an exception, it is relayed to the initial
     * caller and no value is recorded.
     * <p>
     * The returned Map is not {@link Serializable} as this would require the provided
     * {@code mapper} to be {@link Serializable} as well which would create security
     * concerns.
     *
     * @param keys   the keys in the returned map
     * @param mapper to invoke whenever an associated value is first accessed
     *               (may return {@code null})
     * @param <K>    the type of keys maintained by the returned map
     * @param <V>    the type of mapped values in the returned map
     */
    static <K, V> Map<K, V> ofMap(Set<K> keys, Function<? super K, ? extends V> mapper) {
        Objects.requireNonNull(keys);
        Objects.requireNonNull(mapper);
        return SharedSecrets.getJavaUtilCollectionAccess().stableMap(keys, mapper);
    }

}
