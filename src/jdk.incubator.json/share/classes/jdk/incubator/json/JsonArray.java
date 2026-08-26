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

import java.util.List;

import jdk.incubator.json.impl.JsonArrayImpl;

/**
 * The interface that represents JSON array.
 * <p>
 * A {@code JsonArray} can be produced by {@link Json#parse(String)}.
 * <p> Alternatively, {@link #of(List)} can be used to obtain a {@code JsonArray}.
 *
 * @spec https://datatracker.ietf.org/doc/html/rfc8259#section-5 RFC 8259:
 *      The JavaScript Object Notation (JSON) Data Interchange Format - Arrays
 * @since 28
 */
public non-sealed interface JsonArray extends JsonValue {

    /**
     * {@inheritDoc}
     */
    @Override
    List<JsonValue> asList();

    /**
     * {@inheritDoc}
     *
     * @param index {@inheritDoc}
     * @throws JsonValueException if the given index is out of bounds
     */
    @Override
    default JsonValue get(int index) {
        // Overridden to specify
        return JsonValue.super.get(index);
    }

    /**
     * {@return the {@code JsonArray} whose contents are copied from the given
     * list of {@code JsonValue}s}
     *
     * @param src the list of {@code JsonValue}s. Non-null.
     * @throws NullPointerException if {@code src} is {@code null}, or contains
     *      any values that are {@code null}
     */
    static JsonArray of(List<? extends JsonValue> src) {
        // Careful not to use List::contains on src for null checking which
        // throws NPE for immutable lists
        return new JsonArrayImpl(List.copyOf(src));
    }
}
