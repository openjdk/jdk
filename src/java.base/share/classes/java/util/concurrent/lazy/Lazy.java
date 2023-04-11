package java.util.concurrent.lazy;

import jdk.internal.javac.PreviewFeature;
import jdk.internal.util.concurrent.lazy.LazyMapper;
import jdk.internal.util.concurrent.lazy.LazySingleMapper;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * This class provides common factories and configuration classes for all
 * Lazy class variants.
 */
@PreviewFeature(feature = PreviewFeature.Feature.LAZY)
public final class Lazy {

    // Suppresses default constructor, ensuring non-instantiability.
    private Lazy() {}

    /**
     * The State indicates the current state of a Lazy instance:
     * <ul>
     *     <li><a id="empty"><b>EMPTY</b></a>
     *     <p> No value is present (initial state).</p></li>
     *     <li><a id="constructing"><b>CONSTRUCTING</b></a>
     *     <p> A value is being constructed but the value is not yet available (transient state).</p></li>
     *     <li><a id="present"><b>PRESENT</b></a>
     *     <p> A value is present and is available via an accessor (final state).</p></li>
     *     <li><a id="error"><b>ERROR</b></a>
     *     <p> The construction of tha value failed and a value will never be present (final state).
     *     The error is available via an accessor for some implementations.</p></li>
     * </ul>
     */
    public enum State {
        /**
         * Indicates a value is not present and is not about to be constructed.
         */
        EMPTY,  // ABSENT?
        /**
         * Indicates a value is being constructed but is not yet available.
         */
        CONSTRUCTING, // Todo: Consider dropping this state
        /**
         * Indicates a value is present. This is a <em>final state</em>.
         */
        PRESENT,
        /**
         * Indicates an error has occured during construction of the value. This is a <em>final state</em>.
         */
        ERROR;

        /**
         * {@return if this state is final (e.g. can never change)}.
         */
        static boolean isFinal(State state) {
            return state == PRESENT ||
                    state == ERROR;
        }
    }

    static final int EMPTY_ORDINAL = 0;
    static final int PRESENT_ORDINAL = 2;
    static final int ERROR_ORDINAL = 3;

    /**
     * The Evaluation indicates the erliest point at which a Lazy can be evaluated:
     * <ul>
     *     <li><a id="compilation"><b>COMPILATION</b></a>
     *     <p> The value can be evaluated at compile time.</p></li>
     *     <li><a id="distillation"><b>DISTILLATION</b></a>
     *     <p> The value can be evaluated at distillation time.</p></li>
     *     <li><a id="creation"><b>CREATION</b></a>
     *     <p> The value can be evaluated upon creating the Lazy (in another background thread).</p></li>
     *     <li><a id="at-use"><b>AT_USE</b></a>
     *     <p> The value cannot be evaluated before being used (default evaluation).</p></li>
     * </ul>
     */
    public enum Evaluation {
        /**
         * Indicates the value cannot be evaluated before being used (default evaluation).
         */
        AT_USE,
        /**
         * Indicates the value can be evaluated upon creating the Lazy (in the same thread)
         */
        CREATION,
        /**
         * Indicates the value can be evaluated upon creating the Lazy (in another background thread)
         */
        CREATION_BACKGROUND,
        /**
         * Indicates the value can be evaluated at distillation time.
         */
        DISTILLATION,
        /**
         * Indicates the value can be evaluated at compile time.
         */
        COMPILATION
    }

    /**
     * {@return a new empty LazyReference with no pre-set supplier}.
     * <p>
     * If an attempt is made to invoke the {@link LazyReference#get()} method when no element is present,
     * an exception will be thrown.
     * <p>
     * {@snippet lang = java:
     *    LazyReference<T> lazy = LazyReference.ofEmpty();
     *    T value = lazy.getOrNull();
     *    assertIsNull(value); // Value is initially null
     *    // ...
     *    T value = lazy.supplyIfEmpty(Value::new);
     *    assertNotNull(value); // Value is non-null
     *}
     *
     * @param <T> The type of the value
     */
    public static <T> LazyReference<T> ofEmpty() {
        return new LazyReference<>(Evaluation.AT_USE, null);
    }

    /**
     * {@return a LazyReference with the provided {@code presetSupplier}}.
     * <p>
     * If an attempt is made to invoke the {@link LazyReference#get()} method when no element is present,
     * the provided {@code presetSupplier} will automatically be invoked as specified by
     * {@link LazyReference#supplyIfEmpty(Supplier)}.
     * <p>
     * {@snippet lang = java:
     *    LazyReference<T> lazy = Lazy.of(Value::new);
     *    // ...
     *    T value = lazy.get();
     *}
     *
     * @param <T>            The type of the value
     * @param presetSupplier to invoke when lazily constructing a value
     */
    public static <T> LazyReference<T> of(Supplier<? extends T> presetSupplier) {
        Objects.requireNonNull(presetSupplier);
        return new LazyReference<>(Evaluation.AT_USE, presetSupplier);
    }

    /**
     * {@return a builder that can be used to build a custom LazyReference}.
     * @param <T> type of the value the LazyReference will handle.
     */
    // Todo: Figure out a better way for determining the type (e.g. type token)
    public static <T> LazyReference.Builder<T> builder() {
        return new LazyReference.LazyReferenceBuilder<>();
    }

    /**
     * {@return a new empty LazyReferenceArray with no pre-set mapper}.
     * <p>
     * If an attempt is made to invoke the {@link LazyReferenceArray#apply(int)} method when no element is present,
     * an exception will be thrown.
     * <p>
     * {@snippet lang = java:
     *    // Cache the first 64 users
     *    private static final LazyReferenceArray<User> lazy = Lazy.ofEmptyArray(64);
     *    ...
     *    Connection c = ...
     *    User value = lazy.computeIfEmpty(42, i -> findUserById(c, i));
     *    assertNotNull(value); // Value is non-null
     *}
     *
     * @param <T>  The type of the values
     * @param size the size of the array
     */
    public static <T> LazyReferenceArray<T> ofEmptyArray(int size) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        return new LazyReferenceArray<>(size);
    }

    /**
     * {@return a new empty LazyReferenceArray with a pre-set mapper}.
     * <p>
     * If an attempt is made to invoke the {@link LazyReferenceArray#apply(int)} ()} method when no element is present,
     * the provided {@code presetMapper} will automatically be invoked as specified by
     * {@link LazyReferenceArray#computeIfEmpty(int, IntFunction)}.
     * <p>
     * {@snippet lang = java:
     *    LazyReferenceArray<Value> lazy = Lazy.ofArray(32, index -> new Value(1L << index));
     *    // ...
     *    Value value = lazy.get(16);
     *}
     *
     * @param <T>          The type of the values
     * @param size         the size of the array
     * @param presetMapper to invoke when lazily constructing a value
     */
    public static <T> LazyReferenceArray<T> ofArray(int size,
                                                    IntFunction<? extends T> presetMapper) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        Objects.requireNonNull(presetMapper);
        return new LazyReferenceArray<>(size, presetMapper);
    }

    /**
     * {@return a Function that can map any of the provided collection of {@code keys} to values (of type V)
     * lazily computed and recorded by the provided {@code mapper} or {@linkplain Optional#empty() Optional.empty()}
     * if a key that is not part of the provided collection of {@code keys} is provided to the returned Function}.
     * <p>
     * If an attempt is made to invoke the {@link LazyReferenceArray#apply(int)} ()} method when no element is present,
     * the provided {@code presetMapper} will automatically be invoked as specified by
     * {@link LazyReferenceArray#computeIfEmpty(int, IntFunction)}.
     * <p>
     * {@snippet lang = java:
     *    Function<String, Optional<String>> pageCache = Lazy.ofMapper(
     *                      List.of("home", "products", "contact"), DbTools::lookupPage);
     *    // ...
     *     String pageName = ...;
     *
     *    String text = pageCache.apply(pageName)
     *                      .orElseGet(() -> lookupPage(pageName));
     *    // ...
     *    String lookupPage(String pageName) {
     *      // Gets the HTML code for the named page from the content database
     *    }
     *}
     *
     * @param <K> the type of keys maintained by this mapper
     * @param <V> the type of mapped values
     * @param keys to be mapped
     * @param mapper to apply when computing and recording values
     */
    public static <K, V> Function<K, Optional<V>> ofMapper(Collection<K> keys,
                                                           Function<? super K, ? extends V> mapper) {
        Objects.requireNonNull(keys);
        Objects.requireNonNull(mapper);
        return new LazySingleMapper<>(keys, mapper);
    }

    /**
     * {@return a Function that will lazily evaluate and record the provided collection
     * of {@code keyMappers} to values of type V or {@linkplain Optional#empty() Optional.empty()}
     * if a key that is not a part of the provided collection of {@code keyMappers} is
     * provided to the returned Function}.
     * <p>
     * {@snippet lang = java:
     *    Function<Integer, Optional<String>> lazy = Lazy.ofMapper(List.of(
     *            new KeyMapper(400, this::loadBadRequestFromDb),
     *            new KeyMapper(401, this::loadUnaothorizedFromDb),
     *            new KeyMapper(403, this::loadForbiddenFromDb),
     *            new KeyMapper(404, this::loadNotFoundFromDb)
     *         );
     *    // ...
     *    if (returnCode >= 400) {
     *        response.println(lazy.apply(returnCode)
     *                             .orElse("<!DOCTYPE html><title>Oops: "+returnCode+"</title>"));
     *    }
     *}
     *
     * @param <K> the type of keys maintained by this mapper
     * @param <V> the type of mapped values
     * @param keyMappers to be lazily evaluated and recorded
     */
    public static <K, V> Function<K, Optional<V>> ofMapper(Collection<KeyMapper<K, V>> keyMappers) {
        Objects.requireNonNull(keyMappers);
        return new LazyMapper<>(keyMappers);
    }

}
