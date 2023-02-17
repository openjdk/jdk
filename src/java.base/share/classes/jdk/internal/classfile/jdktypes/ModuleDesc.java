/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.classfile.jdktypes;

import static java.util.Objects.requireNonNull;
import jdk.internal.classfile.impl.ModuleDescImpl;
import static jdk.internal.classfile.impl.ModuleDescImpl.*;

/**
 * A nominal descriptor for a {@link Module} constant.
 *
 * <p>To create a {@linkplain ModuleDesc} for a module, use {@link #of}.
 *
 */
public sealed interface ModuleDesc
        permits ModuleDescImpl {

    /**
     * Returns a {@linkplain ModuleDesc} for a module,
     * given the name of the module.
     * <p>
     * {@jvms 4.2.3} Module names are not encoded in "internal form" like class and interface names, that is,
     * the ASCII periods (.) that separate the identifiers in a module name are not replaced by ASCII forward slashes (/).
     * <p>
     * Module names may be drawn from the entire Unicode codespace, subject to the following constraints:
     * <ul>
     * <li>A module name must not contain any code point in the range '&#92;u0000' to '&#92;u001F' inclusive.
     * <li>The ASCII backslash (\) is reserved for use as an escape character in module names.
     * It must not appear in a module name unless it is followed by an ASCII backslash, an ASCII colon (:), or an ASCII at-sign (@).
     * The ASCII character sequence \\ may be used to encode a backslash in a module name.
     * <li>The ASCII colon (:) and at-sign (@) are reserved for future use in module names.
     * They must not appear in module names unless they are escaped.
     * The ASCII character sequences \: and \@ may be used to encode a colon and an at-sign in a module name.
     * </ul>
     * @param name module name
     * @return a {@linkplain ModuleDesc} describing the desired module
     * @throws NullPointerException if the argument is {@code null}
     * @throws IllegalArgumentException if the name string is not in the
     * correct format
     */
    static ModuleDesc of(String name) {
        validateModuleName(requireNonNull(name));
        return new ModuleDescImpl(name);
    }

    /**
     * Returns the module name of this {@linkplain ModuleDesc}.
     *
     * @return the module name
     */
    String moduleName();

    /**
     * Compare the specified object with this descriptor for equality.  Returns
     * {@code true} if and only if the specified object is also a
     * {@linkplain ModuleDesc} and both describe the same module.
     *
     * @param o the other object
     * @return whether this descriptor is equal to the other object
     */
    @Override
    boolean equals(Object o);
}
