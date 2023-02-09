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
import jdk.internal.classfile.impl.PackageDescImpl;
import static jdk.internal.classfile.impl.PackageDescImpl.*;

/**
 * A nominal descriptor for a {@link Package} constant.
 *
 * <p>To create a {@linkplain PackageDesc} for a package, use {@link #of} or
 * {@link #ofInternalName(String)}.
 *
 */
public sealed interface PackageDesc
        permits PackageDescImpl {

    /**
     * Returns a {@linkplain PackageDesc} for a package,
     * given the name of the package, such as {@code "java.lang"}.
     * <p>
     * {@jls 13.1}
     *
     * @param name the fully qualified (dot-separated) binary package name
     * @return a {@linkplain PackageDesc} describing the desired package
     * @throws NullPointerException if the argument is {@code null}
     * @throws IllegalArgumentException if the name string is not in the
     * correct format
     */
    static PackageDesc of(String name) {
        validateBinaryPackageName(requireNonNull(name));
        return new PackageDescImpl(binaryToInternal(name));
    }

    /**
     * Returns a {@linkplain PackageDesc} for a package,
     * given the name of the package in internal form,
     * such as {@code "java/lang"}.
     * <p>
     * {@jvms 4.2.1} In this internal form, the ASCII periods (.) that normally separate the identifiers
     * which make up the binary name are replaced by ASCII forward slashes (/).
     * @param name the fully qualified class name, in internal (slash-separated) form
     * @return a {@linkplain PackageDesc} describing the desired package
     * @throws NullPointerException if the argument is {@code null}
     * @throws IllegalArgumentException if the name string is not in the
     * correct format
     */
    static PackageDesc ofInternalName(String name) {
        validateInternalPackageName(requireNonNull(name));
        return new PackageDescImpl(name);
    }

    /**
     * Returns the fully qualified (slash-separated) internal package name
     * of this {@linkplain PackageDesc}.
     *
     * @return the package name, or the empty string for the
     * default package
     */
    String packageInternalName();

    /**
     * Returns the fully qualified (dot-separated) binary package name
     * of this {@linkplain PackageDesc}.
     *
     * @return the package name, or the empty string for the
     * default package
     */
    default String packageName() {
        return internalToBinary(packageInternalName());
    }

    /**
     * Compare the specified object with this descriptor for equality.  Returns
     * {@code true} if and only if the specified object is also a
     * {@linkplain PackageDesc} and both describe the same package.
     *
     * @param o the other object
     * @return whether this descriptor is equal to the other object
     */
    @Override
    boolean equals(Object o);
}
