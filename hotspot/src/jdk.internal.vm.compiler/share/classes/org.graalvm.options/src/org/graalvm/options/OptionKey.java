/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.options;

import java.util.Objects;

/**
 * Represents the option key for a option specification.
 *
 * @since 1.0
 */
public final class OptionKey<T> {

    private final OptionType<T> type;
    private final T defaultValue;

    /**
     * Constructs a new option key given a default value. Throws {@link IllegalArgumentException} if
     * no default {@link OptionType} could be {@link OptionType#defaultType(Object) resolved} for
     * the given type. The default value must not be <code>null</code>.
     *
     * @since 1.0
     */
    public OptionKey(T defaultValue) {
        Objects.requireNonNull(defaultValue);
        this.defaultValue = defaultValue;
        this.type = OptionType.defaultType(defaultValue);
        if (type == null) {
            throw new IllegalArgumentException("No default type specified for type " + defaultValue.getClass().getName() + ". Specify the option type explicitely to resolve this.");
        }
    }

    /**
     * Contructs a new option key givena default value and option key. The default value and the
     * type must not be <code>null</code>.
     *
     * @since 1.0
     */
    public OptionKey(T defaultValue, OptionType<T> type) {
        Objects.requireNonNull(defaultValue);
        Objects.requireNonNull(type);
        this.defaultValue = defaultValue;
        this.type = type;
    }

    /**
     * Returns the option type of this key.
     *
     * @since 1.0
     */
    public OptionType<T> getType() {
        return type;
    }

    /**
     * Returns the default value for this option.
     *
     * @since 1.0
     */
    public T getDefaultValue() {
        return defaultValue;
    }

    /**
     * Returns the value of this key given the {@link OptionValues values}.
     *
     * @since 1.0
     */
    public T getValue(OptionValues values) {
        return values.get(this);
    }

}
