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

/**
 * A small toolkit of classes supporting lock-free, thread-safe
 * use of lazily initialized values with superior performance.  Providers of
 * lazy values are guaranteed to be invoked at most one time.  This contrasts
 * to {@link java.util.concurrent.atomic.AtomicReferenceArray } where any number
 * of updates can be done and where there is no simple way to atomically compute
 * a value (guaranteed to only be computed once) if missing.
 *  <p>
 * The lazy implementations are optimized for the case where there are N invocations
 * trying to obtain a value and where N >> 1, for example where N is > 2<sup>20</sup>.
 *
 * <h2 id="lazy">Lazy</h2>
 *
 * Instances of the many lazy types are obtained via the {@link java.util.concurrent.lazy.Lazy} class
 * using factory methods and builders.
 *
 * <h3 id="lazyreference">LazyReference</h3>
 *
 * In its simplest form, Lazy can provide atomic lazy evaluation using a <em>preset-supplier</em>:
 *
 * {@snippet class="PackageInfoSnippets" region="DemoPreset"}
 *
 * {@snippet lang = java :
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
 * The performance of the example above is on pair with using an inner/private class
 * holding a lazily initialized variable but with no overhead imposed by the extra
 * class as illustraded hereunder:
 *
 {@snippet lang = java :
 *     class DemoHolder {
 *
 *         public Foo theBar() {
 *             class Holder {
 *                 private static final Foo FOO = new Foo();
 *             }
 *
 *             // Foo is lazily constructed and recorded here upon first invocation
 *             return Holder.FOO;
 *         }
 *     }
 *}
 *
 * On some occasions, a preset-supplier might not be known a priori or the
 * lazy value to compute might depend on values not known at declaration time.  In
 * such cases, an empty LazyReference can be obtained and used as exemplified below:
 * {@snippet lang = java:
 *     class Fox {
 *
 *         private final LazyReference<String> lazy = Lazy.ofEmpty();
 *
 *         String init(String color) {
 *             return lazy.supplyIfEmpty(() -> "The quick " + color + " fox");
 *         }
 *     }
 *}
 *
 * A custom configurable LazyReference can be obtained via the
 * {@linkplain java.util.concurrent.lazy.Lazy#builder() builder} method.
 * Here is how a lazy value can be computed in the background and that may already be computed
 * when first requested from user code:
 * {@snippet lang = java:
 *     class DemoBackground {
 *
 *         private static final LazyReference<Foo> lazy = Lazy.<Foo>builder()
 *                 .withSupplier(Foo::new)
 *                 .withEarliestEvaluation(Lazy.Evaluation.CREATION_BACKGROUND)
 *                 .build();
 *
 *         public static void main(String[] args) throws InterruptedException {
 *             Thread.sleep(1000);
 *             // lazy is likely already pre-computed here by a background thread
 *             System.out.println("lazy.get() = " + lazy.get());
 *         }
 *     }
 * }
 *
 * {@code LazyReference<T>} implements {@code Supplier<T>} allowing simple
 * interoperability with legacy code and less specific type declaration
 * as shown in the example hereunder:
 * {@snippet lang = java:
 *     class SupplierDemo {
 *
 *         // Eager Supplier of Foo
 *         private static final Supplier<Foo> EAGER_FOO = Foo::new;
 *
 *         // Turns an eager Supplier into a caching lazy Supplier
 *         private static final Supplier<Foo> LAZILY_CACHED_FOO = Lazy.of(EAGER_FOO);
 *
 *         public static void main(String[] args) {
 *             // Lazily compute the one-and-only Foo
 *             Foo theFoo = LAZILY_CACHED_FOO.get();
 *         }
 *     }
 *}
 * LazyReference contains additional methods for checking its
 * {@linkplain java.util.concurrent.lazy.LazyReference#state() state} and getting
 * any {@linkplain java.util.concurrent.lazy.LazyReference#exception()} that might be thrown
 * by the provider.
 *
 * <h3 id="lazyarray">LazyArray</h3>
 *
 * Arrays of lazy values (i.e. {@link java.util.concurrent.lazy.LazyReferenceArray}) can also be
 * obtained via {@link java.util.concurrent.lazy.Lazy} factory methods in the same way as
 * for LazyReference instance but with an extra initial arity, indicating the desired length/index
 * of the array:
 * {@snippet lang = java:
 *     class DemoArray {
 *
 *         private static final LazyReferenceArray<Value> VALUE_PO2_CACHE =
 *                 Lazy.ofArray(32, index -> new Value(1L << index));
 *
 *         public Value powerOfTwoValue(int n) {
 *             if (n < 0 || n >= VALUE_PO2_CACHE.length()) {
 *                 throw new IllegalArgumentException(Integer.toString(n));
 *             }
 *
 *             return VALUE_PO2_CACHE.apply(n);
 *         }
 *     }
 * }
 * As can be seen above, an array takes an {@link java.util.function.IntFunction} rather
 * than a {@link java.util.function.Supplier }, allowing custom values to be
 * computed and entered into the array depending on the current index being used.
 *
 * As was the case for LazyReference, empty LazyReferenceArray instances can also be
 * constructed, allowing lazy mappers known at a later stage to be used:
 * {@snippet lang = java:
 *     class UserCache {
 *
 *         // Cache the first 64 users
 *         private static final LazyReferenceArray<User> USER_CACHE = Lazy.ofEmptyArray(64);
 *
 *         public User user(int id) {
 *             Connection c = getDatabaseConnection();
 *             return USER_CACHE.computeIfEmpty(id, i -> findUserById(c, i));
 *         }
 *     }
 * }
 *
 * {@code LazyReferenceArray<T>} implements {@code IntFunction<T>} allowing simple interoperability
 * with existing code and with less specific type declarations as shown hereunder:
 * {@snippet lang = java:
 *     class DemoIntFunction {
 *
 *         // Eager IntFunction<Value>
 *         private static final IntFunction<Value> EAGER_VALUE =
 *                 index -> new Value(index);
 *
 *         // Turns an eager IntFunction into a caching lazy IntFunction
 *         private static final IntFunction<Value> LAZILY_CACHED_VALUES =
 *                 Lazy.ofArray(64, EAGER_VALUE);
 *
 *         public static void main(String[] args) {
 *             Value value42 = LAZILY_CACHED_VALUES.apply(42);
 *         }
 *     }
 * }
 *
 * Todo: Describe IntKeyMapper
 *
 * <h3 id="lazymapper">LazyMapper</h3>
 * When several lazy values are to be held and accessible via arbitrary keys of
 * type {@code K}, general mappers can be obtained for any pre-given collection
 * of keys.  Even though this could be modeled by users via a second level of a
 * regular Java Map, special constructs are available providing equivalent
 * functionality but with potentially better performance and lower memory usage.
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
 * Individual key mapping can also be provided via a collection of
 * {@linkplain java.util.concurrent.lazy.KeyMapper key mappers} as shown in this example:
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
 * <h3 id="general">General Properties of the Lazy Constructs</h3>
 *
 * All methods of the classes in this package will throw a {@link NullPointerException}
 * if a reference parameter is {@code null}.
 *
 * All lazy constructs are "nullofobic" meaning a provider can never return {@code null}.  If nullablilty
 * for values stored are desired, the values have to be modeled using a construct that can express
 * {@code null} values in an explicit way such as {@link java.util.Optional#empty()}:
 * {@snippet lang = java:
 * import java.util.Optional;class NullDemo {
 *   private Supplier<Optional<Color>> backgroundColor =
 *           Lazy.of(() -> Optional.ofNullable(calculateBgColor()));
 *
 *   Color backgroundColor() {
 *       return backgroundColor.get()
 *                  .orElse("<unknown>");
 *    }
 *
 *   private Color calculateBgColor() {
 *       // Read background color from file returning "null" if it fails.
 *   }
 * }
 *}
 *
 * @since 22
 */
package java.util.concurrent.lazy;
