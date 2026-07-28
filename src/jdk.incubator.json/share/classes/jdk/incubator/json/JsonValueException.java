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

import java.io.Serial;

/**
 * Indicates that an error has been detected while traversing the {@code JsonValue}.
 * This exception is thrown under the following conditions:
 * <ul>
 *   <li>
 *     An {@link JsonValue##access access} or a
 *     {@link JsonValue##conversion conversion} method is invoked on a
 *     {@code JsonValue} of an incompatible type. For example, calling
 *     {@code asBoolean()} on a {@code JsonString}.
 *   </li>
 *   <li>
 *     An access method is invoked for a non-existent value, such as
 *     {@code get(String)} for a missing member in a {@code JsonObject}, or
 *     {@code get(int)} for an out-of-bounds index in a {@code JsonArray}.
 *   </li>
 *   <li>
 *     {@code asInt()} or {@code asLong()} is invoked on a {@code JsonNumber}
 *     that cannot be represented without loss of information by the target type.
 *   </li>
 *   <li>
 *     {@code asDouble()} is invoked on a {@code JsonNumber} whose string
 *     representation cannot be converted to a finite {@code double}.
 *   </li>
 * </ul>
 * @since 99
 */
public final class JsonValueException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 2040280066622450939L;

    /**
     * Constructs a {@code JsonValueException} with the specified detail message.
     * @param message the detail message
     */
    public JsonValueException(String message) {
        super(message);
    }
}
