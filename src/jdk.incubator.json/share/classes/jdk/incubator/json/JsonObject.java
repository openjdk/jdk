/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.json;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import jdk.incubator.json.impl.JsonObjectImpl;

/**
 * The interface that represents JSON object.
 * <p>
 * A {@code JsonObject} can be produced by {@link Json#parse(String)}.
 * <p>
 * Alternatively, {@link #of(Map)} can be used to obtain a {@code JsonObject}.
 * <p>
 * Implementations of {@code JsonObject} cannot be created from sources that
 * contain duplicate member names. If duplicate names appear while parsing with
 * {@link Json#parse(String)}, a {@code JsonParseException} is thrown. If duplicate
 * member names are detected while creating a {@code JsonObject} with {@link #of(Map)},
 * an {@code IllegalArgumentException} is thrown.
 *
 * @spec https://datatracker.ietf.org/doc/html/rfc8259#section-4 RFC 8259:
 *      The JavaScript Object Notation (JSON) Data Interchange Format - Objects
 * @since 28
 */
public non-sealed interface JsonObject extends JsonValue {

    /**
     * {@inheritDoc}
     *
     * @implNote {@inheritDoc}
     */
    @Override
    Map<String, JsonValue> asMap();

    /**
     * {@inheritDoc}
     *
     * @param name {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     * @throws JsonValueException if there is no association with the member name
     */
    @Override
    default JsonValue get(String name) {
        // Overridden to specify
        return JsonValue.super.get(name);
    }

    /**
     * {@inheritDoc}
     *
     * @param name {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    @Override
    default Optional<JsonValue> tryGet(String name) {
        // Overridden to specify
        return JsonValue.super.tryGet(name);
    }

    /**
     * {@return the {@code JsonObject} created from the given
     * map of {@code String} to {@code JsonValue}s}
     *
     * @param map the map of {@code JsonValue}s. Non-null.
     * @throws IllegalArgumentException if duplicate member names are given in
     *      {@code map}, including when they are encountered while iterating over
     *      the mappings of an {@link java.util.IdentityHashMap}.
     * @throws NullPointerException if {@code map} is {@code null}, contains
     *      any keys that are {@code null}, or contains any values that are {@code null}.
     */
    static JsonObject of(Map<String, ? extends JsonValue> map) {
        Objects.requireNonNull(map);

        if (map.isEmpty()) {
            return new JsonObjectImpl(Collections.emptyMap());
        } else {
            var m = new LinkedHashMap<String, JsonValue>();
            for (var e : map.entrySet()) {
                var key = Objects.requireNonNull(e.getKey());
                var value = Objects.requireNonNull(e.getValue());
                if (m.putIfAbsent(key, value) != null) {
                    throw new IllegalArgumentException("Duplicate member name: " + key);
                }
            }
            return new JsonObjectImpl(m);
        }
    }
}
