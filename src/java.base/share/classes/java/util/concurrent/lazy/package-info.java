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
 * A small toolkit of classes that support lock-free, thread-safe
 * use of lazy initialized values with superior performance.
 *
 * <h2 id="lazy">Lazy</h2>
 *
 * Instances of the many lazy types are obtained form the {@link java.util.concurrent.lazy.Lazy} class
 * using several factories and builders.
 *
 * <h3 id="lazyreference">LazyReference</h3>
 *
 * For example, Lazy can provide atomic
 * lazy evaluation using a <em>preset-supplier</em>:
 *
 * {@snippet lang = java :
 * class Foo {
 *   private static final LazyReference<Bar> BAR = Lazy.of(Bar::new);
 *   public Bar bar() {
 *     return BAR.get(); // Bar is computed here on first invocation
 *   }
 * }
 *}
 *
 * On some occations, a preset-suppler might not be known a priori or the
 * lazy value to compute might depend on values not know at declaration time. In
 * such cases, an empty LazyReference can be obtained and used as exemplified below:
 * {@snippet lang = java:
 *     LazyReference<String> lazy = Lazy.ofEmpty();
 *     ...
 *     String color = "brown";
 *     ...
 *     String fox = lazy.supplyIfEmpty(() -> "The quck " + color + " fox");
 *}
 *
 * A custom configurable LazyReference can be obtained via the
 * {@linkplain java.util.concurrent.lazy.Lazy#builder() builder} method.
 * Here is how a lazy value can be computed in the background and may already be computed
 * when first referenced from user code:
 * {@snippet lang = java:
 *        LazyReference<Foo> lazy = Lazy.<Foo>builder()
 *                 .withSupplier(Foo::new)
 *                 .withEarliestEvaluation(Lazy.Evaluation.CREATION_BACKGROUND)
 *                 .build();
 *
 *         Thread.sleep(1000);
 *
 *         // lazy is likely already pre-computed here
 *         System.out.println("lazy.get() = " + lazy.get());
 * }
 *
 * {@code LazyReference<T>} implements {@code Supplier<T>} allowing simple interoperability with legacy code
 * and less specific type declaration as shown hereunder:
 * {@snippet lang = java:
 *    Suppler<Foo> eagerFoo = new Foo();
 *    ...
 *    Supplier<Foo> fooLazyCache = Lazy.of(eagerFoo);
 *    ...
 *    Foo theFoo = fooLazyCache.get();
 * }
 *
 * <h3 id="lazyarray">LazyArray</h3>
 *
 * Arrays of lazy values (i.e. {@link java.util.concurrent.lazy.LazyReferenceArray}) can also be
 * obtained via {@link java.util.concurrent.lazy.Lazy} factory methods in the same way as for LazyReference instance but with
 * an extra initial arity, indicating the desired length of the array:
 * {@snippet lang = java:
 *    LazyReferenceArray<Value> lazy = Lazy.ofArray(32, index -> new Value(1L << index));
 *    // ...
 *    Value value = lazy.get(16);
 * }
 * As can be seen above, an array takes an IntFunction rather than a Supplier, allowing custom values to be
 * computed and entered into the array depending on the current index being used.
 *
 * As was the case for LazyReference, empty LazyReference arrays can also be constructed, allowing
 * lazy mappers known at a later stage to be used:
 * {@snippet lang = java:
 *    // Cache the first 64 users
 *    private static final LazyReferenceArray<User> lazy = Lazy.ofEmptyArray(64);
 *    ...
 *    Connection c = ...
 *    User value = lazy.computeIfEmpty(42, i -> findUserById(c, i));
 *    assertNotNull(value); // Value is non-null
 * }
 *
 * {@code LazyReferenceArray<T>} implements {@code IntFunction<T>} allowing simple interoperability with legacy code
 * and less specific type declaration as shown hereunder:
 * {@snippet lang = java:
 *    IntFunction<Foo> eagerFoo = index -> new Foo(index);
 *    ...
 *    IntSupplier<Foo> fooLazyCache = Lazy.ofArray(64, eagerFoo);
 *    ...
 *    Foo foo42 = fooLazyCache.apply(42);
 * }
 *
 * Todo: Describe IntKeyMapper and
 *
 * <h3 id="lazymapper">LazyMapper</h3>
 * When several lazy values are to be held and accessible via arbitary keys of type K, general mappers can be
 * obtained for any pre-given collection of keys. Even though this could be modelled
 * directly by users via a second level of regular Java Maps, there are special constructs available that
 * provide equivialent functionality but with potentially better performance and lower memory usage.
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
 * Individual key mappers can also be provided via a collection of
 * {@linkplain java.util.concurrent.lazy.KeyMapper key mappers} as shown in this example:
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
 * <h3 id="general">General Properties of the Lazy Constructs</h3>
 *
 * All methods of the classes in this package will throw a {@link NullPointerException}
 * if a reference parameter is {@code null}.
 *
 * All lazy constucts are nullofobic meaning if nullablilty for values stores is desired,
 * the value has to be modelled using a construct that can express a null value in an explicit way such as
 * {@link java.util.Optional#empty()}:
 * {@snippet lang = java:
 *   Supplier<Optional<Color>> backgroundColor = Lazy.of(this::calculateBgColor);
 *   ...
 *   var bg = backgroundColor.get()
 *                .orElse("<none>");
 *   ...
 *   private Optional<Color> calculateBgColor() {
 *       ...
 *   }
 * }
 *
 * @since 22
 */
package java.util.concurrent.lazy;
