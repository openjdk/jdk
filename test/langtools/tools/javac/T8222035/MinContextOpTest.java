/*
 * Copyright (c) 2019, Google LLC. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @bug 8222035
 * @summary minimal inference context optimization is forcing resolution with incomplete constraints
 * @compile/fail/ref=MinContextOpTest.out -XDrawDiagnostics MinContextOpTest.java
 */

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Stream;

public class MinContextOpTest {
    abstract class A {
        abstract static class T<K> {
            abstract String f();
        }

        abstract <E> Function<E, E> id();

        abstract static class ImmutableMap<K, V> implements Map<K, V> {}

        abstract <T, K, V> Collector<T, ?, ImmutableMap<K, V>> toImmutableMap(
                Function<? super T, ? extends K> k, Function<? super T, ? extends V> v);

        ImmutableMap<String, T<?>> test(Stream<T> stream) {
            return stream.collect(toImmutableMap(T::f, id()));
        }
    }
}
