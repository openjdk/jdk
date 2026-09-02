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

package jdk.incubator.json.impl;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedMap;

import jdk.incubator.json.JsonObject;
import jdk.incubator.json.JsonValue;

/**
 * JsonObject implementation class. Instances of this class are immutable.
 *
 * <p>For a parsed instance, {@code doc} is the backing input JSON
 * text and {@code offset} indicates the starting offset in {@code doc}.
 * For a factory-created instance, {@code doc} and {@code offset} are
 * {@code null} and {@code -1}, respectively.
 *
 * <p>{@code theMembers} is a {@code SequencedMap} that preserves member
 * order. For a parsed instance, the member order follows the input JSON text.
 * For a factory-created instance, it retains the iteration order of the given
 * {@code Map}. {@code asMap()} returns an unmodifiable view of this map.
 */
public final class JsonObjectImpl implements JsonObject, JsonValueSupport {

    private final SequencedMap<String, JsonValue> theMembers;
    private final int offset;
    private final char[] doc;

    public JsonObjectImpl(SequencedMap<String, JsonValue> map) {
        this(map, -1, null);
    }

    public JsonObjectImpl(SequencedMap<String, JsonValue> map, int o, char[] d) {
        theMembers = map;
        offset = o;
        doc = d;
    }

    // Conversion override
    @Override
    public Map<String, JsonValue> asMap() {
        return Collections.unmodifiableMap(theMembers);
    }

    // Navigation overrides (on default) -> bypass the unmodifiable wrap
    @Override
    public JsonValue get(String name) {
        Objects.requireNonNull(name);
        return switch (theMembers.get(name)) {
            case JsonValue jv -> jv;
            case null -> throw Utils.composeError(this,
                    "JsonObject member \"%s\" does not exist.".formatted(name));
        };
    }

    @Override
    public Optional<JsonValue> tryGet(String name) {
        Objects.requireNonNull(name);
        return Optional.ofNullable(theMembers.get(name));
    }

    @Override
    public char[] doc() {
        return doc;
    }

    @Override
    public int offset() {
        return offset;
    }

    @Override
    public String toString() {
        return JsonGenerator.toCompactString(this);
    }
}
