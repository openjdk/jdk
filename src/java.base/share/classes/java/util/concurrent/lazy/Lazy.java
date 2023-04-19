/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package java.util.concurrent.lazy;

import jdk.internal.javac.PreviewFeature;
import jdk.internal.util.concurrent.lazy.LazyMapper;
import jdk.internal.util.concurrent.lazy.LazySingleMapper;
import jdk.internal.util.concurrent.lazy.StandardEmptyLazyReference;
import jdk.internal.util.concurrent.lazy.StandardEmptyLazyReferenceBuilder;
import jdk.internal.util.concurrent.lazy.StandardLazyReference;
import jdk.internal.util.concurrent.lazy.StandardLazyReferenceBuilder;
import jdk.internal.util.concurrent.lazy.array.StandardEmptyLazyArray;
import jdk.internal.util.concurrent.lazy.array.StandardLazyArray;
import jdk.internal.util.concurrent.lazy.array.TranslatedEmptyLazyArray;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * This class provides common factories and builders for all
 * Lazy types.
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.LAZY)
public final class Lazy {

    // Suppresses default constructor, ensuring non-instantiability.
    private Lazy() {
    }

    /**
     * The State indicates the current state of a Lazy instance.
     * <p>
     * The following values are supported:
     * <ul>
     *     <li><a id="empty"><b>{@link State#EMPTY}</b></a>
     *     <p> No value is present (initial non-final state).</p></li>
     *     <li><a id="constructing"><b>{@link State#CONSTRUCTING}</b></a>
     *     <p> A value is being constructed but the value is not yet available (transient state).</p></li>
     *     <li><a id="present"><b>{@link State#PRESENT}</b></a>
     *     <p> A value is present and is available via an accessor (final state).</p></li>
     *     <li><a id="error"><b>{@link State#ERROR}</b></a>
     *     <p> The construction of tha value failed and a value will never be present (final state).
     *     The error is available via either the {@link BaseLazyReference#exception()} or
     *     the {@link BaseLazyArray#exception(int)} accessor.</p></li>
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
        CONSTRUCTING,
        /**
         * Indicates a value is present. This is a <em>final state</em>.
         */
        PRESENT,
        /**
         * Indicates an error has occured during construction of the value. This is a <em>final state</em>.
         */
        ERROR;

        /**
         * {@return if this state is final (e.g. can no longer change)}.
         */
        static boolean isFinal(State state) {
            return state == PRESENT ||
                    state == ERROR;
        }
    }

    /**
     * The Evaluation indicates the erliest point at which a Lazy can be evaluated.
     * <p>
     * The following values are supported:
     * <ul>
     *     <li><a id="at-use"><b>{@link Evaluation#AT_USE}</b></a>
     *     <p> The value cannot be evaluated before being used (default evaluation).</p></li>
     *     <li><a id="post-creation"><b>{@link Evaluation#POST_CREATION}</b></a>
     *     <p> The value can be evaluated after the Lazy has been created (in another background thread).</p></li>
     *     <li><a id="creation"><b>{@link Evaluation#CREATION}</b></a>
     *     <p> The value can be evaluated upon creating the Lazy (in the same thread).</p></li>
     *     <li><a id="distillation"><b>{@link Evaluation#DISTILLATION}</b></a>
     *     <p> The value can be evaluated at distillation time.</p></li>
     *     <li><a id="compilation"><b>{@link Evaluation#COMPILATION}</b></a>
     *     <p> The value can be evaluated at compile time.</p></li>
     * </ul>
     */
    public enum Evaluation {
        /**
         * Indicates the value cannot be evaluated before being used (default evaluation).
         */
        AT_USE,
        /**
         * Indicates the value can be evaluated after the Lazy has been created (in another background thread).
         */
        POST_CREATION,
        /**
         * Indicates the value can be evaluated upon defining the Lazy (in the same thread).
         */
        CREATION,
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
     * {@return a new EmptyLazyReference with no pre-set supplier}.
     * <p>
     * {@snippet lang = java:
     *     class Fox {
     *
     *         private final EmptyLazyReference<String> lazy = Lazy.ofEmpty();
     *
     *         String init(String color) {
     *             return lazy.apply(() -> "The quick " + color + " fox");
     *         }
     *     }
     *}
     *
     * @param <V> The type of the value
     */
    public static <V> EmptyLazyReference<V> ofEmpty() {
        return new StandardEmptyLazyReference<>();
    }

    /**
     * {@return a LazyReference with the provided {@code presetSupplier}}.
     * <p>
     * If an attempt is made to invoke the {@link LazyReference#get()} method when no element is present,
     * the provided {@code presetSupplier} will automatically be invoked as specified by
     * {@link EmptyLazyReference#apply(Object)}.
     * <p>
     * {@snippet lang = java:
     *     class DemoPreset {
     *
     *         private static final LazyReference<Foo> FOO = Lazy.of(Foo::new);
     *
     *         public Foo theBar() {
     *             // Foo is lazily constructed and recorded here upon first invocation
     *             return FOO.get();
     *         }
     *     }
     *}
     *
     * @param <V>            The type of the value
     * @param presetSupplier to invoke when lazily constructing a value
     */
    public static <V> LazyReference<V> of(Supplier<? extends V> presetSupplier) {
        Objects.requireNonNull(presetSupplier);
        return new StandardLazyReference<>(presetSupplier);
    }

    /**
     * {@return a builder that can be used to build a custom EmptyLazyReference}.
     *
     * @param <V> type of the value the EmptyLazyReference will handle.
     *            Here is how a lazy value can be pre-computed:
     * {@snippet lang = java:
     *      class DemoPrecomputed {
     *
     *         private static final EmptyLazyReference<Foo> lazy = Lazy.<Foo>emptyBuilder()
     *                 .withValue(new Foo())
     *                 .build();
     *
     *         public static void main(String[] args) throws InterruptedException {
     *             // lazy is already pre-computed here
     *             System.out.println("lazy.apply(Foo::new) = " + lazy.apply(Foo::new));
     *         }
     *     }
     *}
     */
    // Todo: Figure out a better way for determining the type (e.g. type token)
    public static <V> EmptyLazyReference.Builder<V> emptyBuilder() {
        return new StandardEmptyLazyReferenceBuilder<>();
    }

    /**
     * {@return a builder that can be used to build a custom LazyReference}.
     * <p>
     * Here is how a lazy value can be computed in the background and that may already be computed
     * when first requested from user code:
     * {@snippet lang = java:
     *     class DemoBackground {
     *
     *         private static final LazyReference<Foo> lazy = Lazy.builder(Foo::new)
     *                 .withEarliestEvaluation(Lazy.Evaluation.POST_CREATION)
     *                 .build();
     *
     *         public static void main(String[] args) throws InterruptedException {
     *             Thread.sleep(1000);
     *             // lazy is likely already pre-computed here by a background thread
     *             System.out.println("lazy.get() = " + lazy.get());
     *         }
     *     }
     *}
     *
     * @param <V>            type of the value the LazyReference will handle.
     * @param presetSupplier to use when computing and storing the value
     */
    public static <V> LazyReference.Builder<V> builder(Supplier<? extends V> presetSupplier) {
        Objects.requireNonNull(presetSupplier);
        return new StandardLazyReferenceBuilder<>(presetSupplier);
    }

    /**
     * {@return a new EmptyLazyArray with no pre-set mapper}.
     * <p>
     * Below an example of how an EmptyLazyArray is used as a cache:
     * {@snippet lang = java:
     *     class UserCache {
     *
     *         // Cache the first 64 users
     *         private static final EmptyLazyArray<User> USER_CACHE = Lazy.ofEmptyArray(64);
     *
     *         public User user(int id) {
     *             Connection c = getDatabaseConnection();
     *             return USER_CACHE.computeIfEmpty(id, i -> findUserById(c, i));
     *         }
     *     }
     *}
     *
     * @param <V>  The type of the values
     * @param size the size of the array
     */
    public static <V> EmptyLazyArray<V> ofEmptyArray(int size) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        return new StandardEmptyLazyArray<>(size);
    }

    /**
     * {@return a new EmptyLazyArray with a pre-set mapper}.
     * <p>
     * Below, an example of how to cache values in an array is shown:
     * {@snippet lang = java:
     * class DemoArray {
     *
     * private static final LazyArray<Value> VALUE_PO2_CACHE =
     * Lazy.ofArray(32, index -> new Value(1L << index));
     *
     * public Value powerOfTwoValue(int n) {
     * if (n < 0 || n >= VALUE_PO2_CACHE.length()) {
     * throw new IllegalArgumentException(Integer.toString(n));
     * }
     *
     * return VALUE_PO2_CACHE.apply(n);
     * }
     * }
     *}
     *
     * @param <V>          The type of the values
     * @param size         the size of the array
     * @param presetMapper to invoke when lazily constructing a value
     */
    public static <V> LazyArray<V> ofArray(int size,
                                           IntFunction<? extends V> presetMapper) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        Objects.requireNonNull(presetMapper);
        return new StandardLazyArray<>(size, presetMapper);
    }

    /**
     * {@return a new EmptyLazyArray with no pre-set mapper and with the index translated
     * by the provided {@code translation}}.
     * <p>
     * Translated lazy arrays are useful for caching certain values as shown in the example below:
     * <p>
     * {@snippet lang = java:
     *     class DemoFibMapped {
     *
     *          static int fib(int n) {
     *             return (n <= 1)
     *                     ? n
     *                     : fib(n - 1) + fib(n - 2);
     *         }
     *
     *         private static final EmptyLazyArray<Integer> FIB_10_CACHE =
     *                 Lazy.ofEmptyTranslatedArray(3, 10);
     *
     *         // Only works for values up to ~30
     *
     *         static int cachedFib(int n) {
     *             if (n <= 1)
     *                 return n;
     *             return FIB_10_CACHE.computeIfEmpty(n, DemoFibMapped::fib);
     *         }
     *     }
     *}
     *
     * @param <V>         The type of the values
     * @param size        the size of the array
     * @param translation the translation factor to use
     */
    public static <V> EmptyLazyArray<V> ofEmptyTranslatedArray(int size,
                                                               int translation) {
        if (size < 0 || translation < 0) {
            throw new IllegalArgumentException();
        }
        if ((long) size * translation > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Out of int range.");
        }
        return new TranslatedEmptyLazyArray<>(size, translation);
    }

    /**
     * {@return a Function that can map any of the provided collection of {@code keys} to values (of type V)
     * lazily computed and recorded by the provided {@code mapper} or {@linkplain Optional#empty() Optional.empty()}
     * if a key that is not part of the provided collection of {@code keys} is provided to the returned Function}.
     * <p>
     * Here is an example of how to construct a cache for three pre-known strings:
     * {@snippet lang = java:
     *     class DemoLazyMapper {
     *
     *         private final Function<String, Optional<String>> pageCache = Lazy.mapping(
     *                 List.of("home", "products", "contact"), DbTools::lookupPage);
     *
     *         public String renderPage(String pageName) {
     *             return pageCache.apply(pageName)
     *                     .orElseGet(() -> DbTools.lookupPage(pageName));
     *         }
     *     }
     *}
     *
     * @param <K>    the type of keys maintained by this mapper
     * @param <V>    the type of mapped values
     * @param keys   to be mapped
     * @param mapper to apply when computing and recording values
     */
    public static <K, V> Function<K, Optional<V>> mapping(Collection<K> keys,
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
     * class DemoErrorPageMapper {
     *
     *         private static final Function<Integer, Optional<String>> lazy =
     *                 Lazy.mapping(
     *                         List.of(
     *                                 KeyMapper.of(400, DbTools::loadBadRequestPage),
     *                                 KeyMapper.of(401, DbTools::loadUnaothorizedPage),
     *                                 KeyMapper.of(403, DbTools::loadForbiddenPage),
     *                                 KeyMapper.of(404, DbTools::loadNotFoundPage)
     *                         )
     *                 );
     *
     *         public String servePage(Request request) {
     *             int returnCode = check(request);
     *             if (returnCode >= 400) {
     *                 return lazy.apply(returnCode)
     *                         .orElse("<!DOCTYPE html><title>Oops: " + returnCode + "</title>");
     *             }
     *             return render(request);
     *         }
     *     }
     *}
     *
     * @param <K>        the type of keys maintained by this mapper
     * @param <V>        the type of mapped values
     * @param keyMappers to be lazily evaluated and recorded
     */
    public static <K, V> Function<K, Optional<V>> mapping(Collection<KeyMapper<K, V>> keyMappers) {
        Objects.requireNonNull(keyMappers);
        return new LazyMapper<>(keyMappers);
    }

}
