/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.incubator.json.JsonNull;

/**
 * JsonNull implementation class. Instances of this class are immutable.
 *
 * <p>For a parsed instance, {@code doc} is the backing input JSON
 * text and {@code offset} indicates the starting offset in {@code doc}.
 * For a factory-created instance, {@code doc} and {@code offset} are
 * {@code null} and {@code -1}, respectively.
 */
public final class JsonNullImpl implements JsonNull, JsonValueSupport {

    private final int offset;
    private final char[] doc;

    public static final JsonNullImpl NULL = new JsonNullImpl(null, -1);
    private static final String VALUE = "null";

    public JsonNullImpl(char[] doc, int offset) {
        this.doc = doc;
        this.offset = offset;
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
        return VALUE;
    }
}
