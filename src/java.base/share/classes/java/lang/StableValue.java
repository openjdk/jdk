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
 * Stable values are objects that represent immutable data and are treated as constants
 * by the JVM, enabling the same performance optimizations that are possible by marking
 * a field final. Yet, stable values offer greater flexibility as to the timing of
 * initialization compared to final fields.
 * <p>
 * There are three categories of stable values and six variants of stable value objects :
 * <ul>
 *     <li>{@linkplain StableValue##stable-holder Stable Holder}
 *     <ul>
 *         <li>Stable value: {@linkplain StableValue {@code StableValue<T>}}</li>
 *     </ul>
 *     </li>
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
 * <h2 id="stable-holder">Stable Holder</h2>
 * A <em>stable value</em> is of type {@linkplain StableValue {@code StableValue<T>}} and
 * is a holder of underlying data of type {@code T}. It can be used to defer
 * initialization of its underlying data while retaining the same performance as if
 * a field of type {@code T} was declared {@code final}:
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
 * <p>
 * A {@linkplain StableValue} is mainly intended to be a member of a holding class (as
 * shown in the example above) and is usually neither exposed directly via accessors nor
 * passed as a method parameter.
 *
 * <h3>Initializing the underlying data</h3>
 * A {@linkplain StableValue}'s underlying data can be initialized to a {@code value} of
 * type {@code T} via three instance methods:
 * <ul>
 *     <li>{@linkplain StableValue#trySet(Object) {@code trySet(T value)}}</li>
 *     <li>{@linkplain StableValue#setOrThrow(Object) {@code setOrThrow(T value)}}</li>
 *     <li>{@linkplain StableValue#computeIfUnset(Supplier) {@code computeIfUnset(Supplier<? extends T>} supplier)}</li>
 * </ul>
 * <p>
 * Here is an example of how the underlying data can be initialized imperatively
 * using a pre-computed value (here {@code 42}) using the method
 * {@linkplain StableValue#trySet(Object) trySet()}. The method will return {@code true}
 * if the underlying data was indeed initialized, or it will return {@code false} if
 * the underlying data was already initialized:
 *
 * {@snippet lang=java :
 *     StableValue<Integer> stableValue = StableValue.of();
 *     // ...
 *     if (stableValue.trySet(42)) {
 *        System.out.println("The underlying data was initialized to 42.");
 *     } else {
 *        System.out.println("The value was already initialized.");
 *     }
 * }
 * <p>
 * A similar way to initialize the underlying data imperatively using a pre-computed
 * value, is to invoke the method {@linkplain StableValue#setOrThrow(Object)
 * setOrThrow()}. The method will initialize the underlying data if uninitialized,
 * otherwise it will throw {@linkplain IllegalStateException}:
 *
 * {@snippet lang=java :
 *     StableValue<Integer> stableValue = StableValue.of();
 *     // ...
 *     // Throws IllegalStateException if the underlying data is already initialized
 *     int val = stableValue.setOrThrow(42); // 42
 * }
 * <p>
 * Finally, the underlying data can be initialized via
 * {@linkplain StableValue#computeIfUnset(Supplier) {@code computeIfUnset(Supplier<? extends T>} supplier)}
 * as shown in the {@linkplain StableValue##stable-holder initial example} in this class.
 * It should be noted that a {@linkplain  StableValue} guarantees the provided
 * {@code supplier} is invoked at most once if it did not throw an exception even in
 * a multithreaded environment.
 *
 * <h3>Retrieving the underlying data</h3>
 * The underlying data of a stable value can be retrieved using two instance methods:
 * <ul>
 *     <li>{@linkplain StableValue#orElse(Object) {@code orElse(T other)}}</li>
 *     <li>{@linkplain StableValue#orElseThrow() {@code orElseThrow()}}</li>
 * </ul>
 * <p>
 * By invoking the method {@linkplain StableValue#orElse(Object) orElse(other)}. The
 * method will return the underlying data if initialized, otherwise it will return
 * the provided {@code other} value:
 *
 * {@snippet lang=java :
 *     StableValue<Integer> stableValue = StableValue.of();
 *     // ...
 *     int val = stableValue.orElse(13); // The underlying data if set, otherwise 13
 * }
 * Another way is by means of the {@linkplain StableValue#orElseThrow() orElseThrow()}
 * method which will retrieve the underlying data if initialized or throw
 * {@linkplain NoSuchElementException} if the underlying data was not initialized:
 *
 * {@snippet lang=java :
 *     StableValue<Integer> stableValue = StableValue.of();
 *     // ...
 *     int val = stableValue.orElseThrow(); // The underlying data, else throws
 * }
 *
 * <h3>Determining the presence of underlying data</h3>
 * The presence of underlying data can be examined using the method
 * {@linkplain StableValue#isSet() isSet()}. The method will return {@code true} if the
 * underlying data is initialized, otherwise it will return {@code false}.
 *
 * <h2 id="stable-functions">Stable Functions</h2>
 * There are three stable functions:
 * <ul>
 *     <li>Stable supplier</li>
 *     <li>Stable int function</li>
 *     <li>Stable function</li>
 * </ul>
 * <p>
 * All the stable functions guarantee the provided {@code original} function is invoked
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
 *             StableValue.ofSupplier( () -> Logger.getLogger("org.app.Component") );
 *
 *         void process() {
 *            logger.get().info("Process started");
 *            // ...
 *         }
 *     }
 *}
 * This also allows the stable supplier to be accessed directly, without going through
 * an accessor method like {@code getLogger()} in the first example of this class.
 * <p>
 * A <em>stable int function</em> stores values in an array of stable values where
 * the underlying values are computed the first time a particular input value is provided.
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
 *             StableValue.ofIntFunction(10, Math::sqrt);
 *
 *         double sqrt9() {
 *             return SQRT.apply(9); // Constant folds to 3.0
 *         }
 *
 *     }
 *}
 * <p>
 * A <em>stable function</em> stores values in an array of stable values where
 * the underlying values are computed the first time a particular input value is provided.
 * When the stable function is first created --
 * via the {@linkplain StableValue#ofFunction(Set, Function) StableValue.ofFunction()}
 * factory -- the input set is specified together with an original {@linkplain Function}
 * which is invoked at most once per input value. In effect, the stable function will act
 * like a cache for the original {@code Function}:
 * {@snippet lang = java:
 *     class SqrtUtil {
 *
 *         private static final Function<Integer, Double> SQRT =
 *             StableValue.ofFunction(Set.of(1, 2, 4, 8, 16, 32), Math::sqrt);
 *
 *         double sqrt16() {
 *             return SQRT.apply(16); // Constant folds to 4.0
 *         }
 *
 *     }
 *}
 *
 * <h2 id="stable-collections">Stable Collections</h2>
 * There are two stable collections:
 * <ul>
 *     <li>Stable list</li>
 *     <li>Stable map</li>
 * </ul>
 * <p>
 * All the stable collections guarantee the provided {@code original} function is
 * invoked successfully at most once per valid input even in a multithreaded environment.
 * <p>
 * A <em>stable list</em> is similar to a stable int function, but provides a full
 * implementation of an immutable {@linkplain List}. This is useful when interacting with
 * collection-based methods. Here is an example how a stable list can be used to hold
 * a pool of order controllers:
 * {@snippet lang = java:
 *    class Application {
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
 * A <em>stable map</em> is similar to a stable function, but provides a full
 * implementation of an immutable {@linkplain Map}. This is useful when interacting with
 * collection-based methods. Here is how a stable map can be used as a cache for
 * square root values:
 * {@snippet lang = java:
 *     class SqrtUtil {
 *
 *         private static final Map<Integer, Double> SQRT =
 *             StableValue.ofMap(Set.of(1, 2, 4, 8, 16, 32), Math::sqrt);
 *
 *         double sqrt16() {
 *             return SQRT.apply(16); // Constant folds to 4.0
 *         }
 *
 *     }
 *}
 *
 * <h2 id="memory-consistency">Memory Consistency Properties</h2>
 * Actions on the presumptive underlying data in a thread prior to calling a method that
 * <i>sets</i> the underlying data are seen by any other thread that <i>observes</i> a
 * set underlying data.
 * <p>
 * More generally, the action of attempting to interact (i.e. via load or store operations)
 * with a StableValue's underlying data (e.g. via {@link StableValue#trySet} or
 * {@link StableValue#orElseThrow()}) forms a
 * <a href="{@docRoot}/java.base/java/util/concurrent/package-summary.html#MemoryVisibility"><i>happens-before</i></a>
 * relation between any other action of attempting to interact with the
 * StableValue's underlying data. The same happens-before guarantees extend to
 * stable functions and stable collections; any action of attempting to interact via a
 * valid input value {@code I} <em>happens-before</em> any subsequent action of attempting
 * to interact via a valid input value {@code J} if, and only if, {@code I} and {@code J}
 * {@linkplain Object#equals(Object) equals() }
 * <p>
 * Except for a StableValue's underlying data itself, all method parameters must be
 * <em>non-null</em> or a {@link NullPointerException} will be thrown.
 *
 * @implSpec Implementing classes of {@linkplain StableValue} are free to synchronize on
 *           {@code this} and consequently, care should be taken whenever
 *           (directly or indirectly) synchronizing on a StableValue. Failure to do this
 *           may lead to deadlock. Stable functional constructs and collections on
 *           the other hand are guaranteed not to not synchronize on {@code this}.
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
 * @since 24
 */
@PreviewFeature(feature = PreviewFeature.Feature.STABLE_VALUES)
public sealed interface StableValue<T>
        permits StableValueImpl {

    // Principal methods

    /**
     * {@return {@code true} if the underlying data was set to the provided
     * {@code underlyingData}, otherwise returns {@code false}}
     * <p>
     * When this method returns, the underlying data is always set.
     *
     * @param underlyingData to set
     */
    boolean trySet(T underlyingData);

    /**
     * {@return the underlying data if set, otherwise return the
     * {@code other} value}
     *
     * @param other to return if the underlying data is not set
     */
    T orElse(T other);

    /**
     * {@return the underlying data if set, otherwise throws {@code NoSuchElementException}}
     *
     * @throws NoSuchElementException if no underlying data is set
     */
    T orElseThrow();

    /**
     * {@return {@code true} if the underlying data is set, {@code false} otherwise}
     */
    boolean isSet();

    /**
     * {@return the underlying data if set, otherwise attempts to compute and set
     * new underlying data using the provided {@code supplier}, returning the
     * newly set underlying data}
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
     *
     * @implSpec The implementation logic is equivalent to the following steps for this
     * {@code stable}:
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
     * @throws StackOverflowError if the provided {@code supplier} recursively
     *         invokes the provided {@code supplier} upon being invoked.
     */
    T computeIfUnset(Supplier<? extends T> supplier);

    // Convenience methods

    /**
     * Sets the underlying data to the provided {@code underlyingData}, or,
     * if already set, throws {@link IllegalStateException}}
     * <p>
     * When this method returns (or throws an Exception), the underlying data is
     * always set.
     *
     * @param underlyingData to set
     * @throws IllegalStateException if the underlying data is already set
     */
    default void setOrThrow(T underlyingData) {
        if (!trySet(underlyingData)) {
            throw new IllegalStateException("Cannot set the underlying data to " + underlyingData +
                    " because the underlying data is already set: " + this);
        }
    }

    // Factories

    /**
     * {@return a new thin, atomic, thread-safe, set-at-most-once, {@linkplain StableValue}
     * with no underlying data eligible for certain JVM optimizations once the
     * underlying data is set}
     *
     * @param <T> type of the underlying data
     */
    static <T> StableValue<T> of() {
        return StableValueFactories.of();
    }

    /**
     * {@return a new stable, thread-safe, caching, lazily computed
     * {@linkplain Supplier supplier} that records the value of the provided
     * {@code original} supplier upon being first accessed via
     * {@linkplain Supplier#get() Supplier::get}}
     * <p>
     * The provided {@code original} supplier is guaranteed to be successfully invoked
     * at most once even in a multi-threaded environment. Competing threads invoking the
     * {@linkplain Supplier#get() Supplier::get} method when a value is already under
     * computation will block until a value is computed or an exception is thrown by the
     * computing thread.
     * <p>
     * If the {@code original} Supplier invokes the returned Supplier recursively,
     * a {@linkplain StackOverflowError} will be thrown when the returned
     * Supplier's {@linkplain Supplier#get() Supplier::get} method is invoked.
     * <p>
     * If the provided {@code original} supplier throws an exception, it is relayed
     * to the initial caller and no value is recorded.
     *
     * @param original supplier used to compute a memoized value
     * @param <T>      the type of results supplied by the returned supplier
     */
    static <T> Supplier<T> ofSupplier(Supplier<? extends T> original) {
        Objects.requireNonNull(original);
        return StableValueFactories.ofSupplier(original);
    }

    /**
     * {@return a new stable, thread-safe, caching, lazily computed
     * {@link IntFunction } that, for each allowed input, records the values of the
     * provided {@code original} IntFunction upon being first accessed via
     * {@linkplain IntFunction#apply(int) IntFunction::apply}}
     * <p>
     * The provided {@code original} IntFunction is guaranteed to be successfully invoked
     * at most once per allowed input, even in a multi-threaded environment. Competing
     * threads invoking the {@linkplain IntFunction#apply(int) IntFunction::apply} method
     * when a value is already under computation will block until a value is computed or
     * an exception is thrown by the computing thread.
     * <p>
     * If the {@code original} IntFunction invokes the returned IntFunction recursively
     * for a particular input value, a {@linkplain StackOverflowError} will be thrown when
     * the returned IntFunction's {@linkplain IntFunction#apply(int) IntFunction::apply}
     * method is invoked.
     * <p>
     * If the provided {@code original} IntFunction throws an exception, it is relayed
     * to the initial caller and no value is recorded.
     *
     * @param size     the size of the allowed inputs in {@code [0, size)}
     * @param original IntFunction used to compute a memoized value
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
     * {@return a new stable, thread-safe, caching, lazily computed {@link Function}
     * that, for each allowed input in the given set of {@code inputs}, records the
     * values of the provided {@code original} Function upon being first accessed via
     * {@linkplain Function#apply(Object) Function::apply}}
     * <p>
     * The provided {@code original} Function is guaranteed to be successfully invoked
     * at most once per allowed input, even in a multi-threaded environment. Competing
     * threads invoking the {@linkplain Function#apply(Object) Function::apply} method
     * when a value is already under computation will block until a value is computed or
     * an exception is thrown by the computing thread.
     * <p>
     * If the {@code original} Function invokes the returned Function recursively
     * for a particular input value, a {@linkplain StackOverflowError} will be thrown when
     * the returned Function's {@linkplain Function#apply(Object) Function::apply} method
     * is invoked.
     * <p>
     * If the provided {@code original} Function throws an exception, it is relayed
     * to the initial caller and no value is recorded.
     *
     * @param inputs   the set of allowed input values
     * @param original Function used to compute a memoized value
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
     * {@return a shallowly immutable, lazy, stable List of the provided {@code size}
     * where the individual elements of the list are lazily computed via the provided
     * {@code mapper} whenever an element is first accessed (directly or indirectly),
     * for example via {@linkplain List#get(int) List::get}}
     * <p>
     * The provided {@code mapper} IntFunction is guaranteed to be successfully invoked
     * at most once per list index, even in a multi-threaded environment. Competing
     * threads accessing an element already under computation will block until an element
     * is computed or an exception is thrown by the computing thread.
     * <p>
     * If the {@code mapper} IntFunction invokes the returned IntFunction recursively
     * for a particular index, a {@linkplain StackOverflowError} will be thrown when the
     * returned List's {@linkplain List#get(int) List::get} method is invoked.
     * <p>
     * If the provided {@code mapper} IntFunction throws an exception, it is relayed
     * to the initial caller and no element is recorded.
     * <p>
     * The returned List is not {@link Serializable}.
     *
     * @param size   the size of the returned list
     * @param mapper to invoke whenever an element is first accessed (may return null)
     * @param <T>    the type of elements in the returned list
     */
    static <T> List<T> ofList(int size, IntFunction<? extends T> mapper) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        Objects.requireNonNull(mapper);
        return SharedSecrets.getJavaUtilCollectionAccess().stableList(size, mapper);
    }

    /**
     * {@return a shallowly immutable, lazy, stable Map of the provided {@code keys}
     * where the associated values of the maps are lazily computed via the provided
     * {@code mapper} whenever a value is first accessed (directly or indirectly), for
     * example via {@linkplain Map#get(Object) Map::get}}
     * <p>
     * The provided {@code mapper} Function is guaranteed to be successfully invoked
     * at most once per key, even in a multi-threaded environment. Competing
     * threads accessing an associated value already under computation will block until
     * an associated value is computed or an exception is thrown by the computing thread.
     * <p>
     * If the {@code mapper} Function invokes the returned Map recursively
     * for a particular key, a {@linkplain StackOverflowError} will be thrown when the
     * returned Map's {@linkplain Map#get(Object) Map::get}} method is invoked.
     * <p>
     * If the provided {@code mapper} Function throws an exception, it is relayed
     * to the initial caller and no value is recorded.
     * <p>
     * The returned Map is not {@link Serializable}.
     *
     * @param keys   the keys in the returned map
     * @param mapper to invoke whenever an associated value is first accessed
     *                (may return null)
     * @param <K>    the type of keys maintained by the returned map
     * @param <V>    the type of mapped values in the returned map
     */
    static <K, V> Map<K, V> ofMap(Set<K> keys, Function<? super K, ? extends V> mapper) {
        Objects.requireNonNull(keys);
        Objects.requireNonNull(mapper);
        return SharedSecrets.getJavaUtilCollectionAccess().stableMap(keys, mapper);
    }

}
