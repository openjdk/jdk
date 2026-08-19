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
import java.util.List;
import java.util.Locale;

import jdk.incubator.json.JsonArray;
import jdk.incubator.json.JsonValue;

/**
 * JsonArray implementation class
 */
public final class JsonArrayImpl implements JsonArray, JsonValueSupport {

    private final List<JsonValue> theValues;
    private final int offset;
    private final char[] doc;

    public JsonArrayImpl(List<JsonValue> from) {
        this(from, -1, null);
    }

    public JsonArrayImpl(List<JsonValue> from, int o, char[] d) {
        theValues = from;
        offset = o;
        doc = d;
    }

    // Conversion override
    @Override
    public List<JsonValue> asList() {
        return Collections.unmodifiableList(theValues);
    }

    // Navigation overrides (on default) -> bypass the unmodifiable wrap
    @Override
    public JsonValue get(int index) {
        try {
            return theValues.get(index);
        } catch (IndexOutOfBoundsException _) {
            throw Utils.composeError(this, String.format(Locale.ROOT,
                "JsonArray index %d out of bounds for length %d.",
                index, theValues.size()));
        }
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
        var s = new StringBuilder("[");
        var list = asList();
        for (var v: list) {
            s.append(v.toString()).append(',');
        }
        if (!list.isEmpty()) {
            s.setLength(s.length() - 1); // trim final comma
        }
        return s.append(']').toString();
    }
}
