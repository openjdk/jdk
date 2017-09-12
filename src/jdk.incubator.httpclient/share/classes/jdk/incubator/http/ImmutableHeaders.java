/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.http;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.function.Predicate;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;

final class ImmutableHeaders implements HttpHeaders {

    private final Map<String, List<String>> map;

    public static ImmutableHeaders empty() {
        return of(emptyMap());
    }

    public static ImmutableHeaders of(Map<String, List<String>> src) {
        return of(src, x -> true);
    }

    public static ImmutableHeaders of(Map<String, List<String>> src,
                                      Predicate<? super String> keyAllowed) {
        requireNonNull(src, "src");
        requireNonNull(keyAllowed, "keyAllowed");
        return new ImmutableHeaders(src, keyAllowed);
    }

    private ImmutableHeaders(Map<String, List<String>> src,
                             Predicate<? super String> keyAllowed) {
        Map<String, List<String>> m = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        src.entrySet().stream()
                .filter(e -> keyAllowed.test(e.getKey()))
                .forEach(e ->
                        {
                            List<String> values = new ArrayList<>(e.getValue());
                            m.put(e.getKey(), unmodifiableList(values));
                        }
                );
        this.map = unmodifiableMap(m);
    }

    @Override
    public Optional<String> firstValue(String name) {
        return allValues(name).stream().findFirst();
    }

    @Override
    public OptionalLong firstValueAsLong(String name) {
        return allValues(name).stream().mapToLong(Long::valueOf).findFirst();
    }

    @Override
    public List<String> allValues(String name) {
        requireNonNull(name);
        List<String> values = map.get(name);
        // Making unmodifiable list out of empty in order to make a list which
        // throws UOE unconditionally
        return values != null ? values : unmodifiableList(emptyList());
    }

    @Override
    public Map<String, List<String>> map() {
        return map;
    }
}
