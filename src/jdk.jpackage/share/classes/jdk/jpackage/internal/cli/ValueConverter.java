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
package jdk.jpackage.internal.cli;

import java.util.Objects;
import java.util.function.Function;

interface ValueConverter<T> {

    /**
     * Converts the given string value into a Java type.
     *
     * @param value the string to convert
     * @return the converted value
     * @throws IllegalArgumentException if the given string value can not be
     *                                  converted to an object of type {@link T}
     */
    T convert(String value) throws IllegalArgumentException;

    /**
     * Gives the class of the type of values this converter converts to.
     *
     * @return the target class for conversion
     */
    Class<? extends T> valueType();

    static <T> ValueConverter<T> create(Function<String, T> mapper, Class<? extends T> type) {
        Objects.requireNonNull(mapper);
        Objects.requireNonNull(type);

        return new ValueConverter<>() {

            @Override
            public T convert(String value) {
                Objects.requireNonNull(value);
                return Objects.requireNonNull(mapper.apply(value));
            }

            @Override
            public Class<? extends T> valueType() {
                return type;
            }

        };
    }
}
