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

/**
 * Represents a set of option values based on an {@link OptionDescriptor}.
 *
 * @since 1.0
 */
public interface OptionValues {

    /**
     * Returns all available options.
     *
     * @since 1.0
     */
    OptionDescriptors getDescriptors();

    /**
     * Sets the value of {@code optionKey} to {@code value}.
     *
     * @since 1.0
     */
    <T> void set(OptionKey<T> optionKey, T value);

    /**
     * Returns the value of a given option. Returns <code>null</code> if the option does not exist.
     *
     * @since 1.0
     */
    <T> T get(OptionKey<T> optionKey);

}
