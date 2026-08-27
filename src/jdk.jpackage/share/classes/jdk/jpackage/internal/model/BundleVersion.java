/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Application version.
 */
public interface BundleVersion {

    Optional<DottedVersion> asDottedVersion();

    /**
     * Creates a {@code BundleVersion} whose {@link #asDottedVersion()} method
     * returns a non-empty {@code Optional} instance wrapping the source version.
     * <p>
     * Use {@link #toString()} to get the string representation of the source version.
     *
     * @see #of(String)
     *
     * @param v the source version
     * @return a {@code BundleVersion} wrapping {@code v} parameter
     */
    static BundleVersion of(DottedVersion v) {
        Objects.requireNonNull(v);

        return new BundleVersion() {

            @Override
            public Optional<DottedVersion> asDottedVersion() {
                return Optional.of(v);
            }

            @Override
            public String toString() {
                return v.toString();
            }
        };
    }

    /**
     * Creates a {@code BundleVersion} whose {@link #asDottedVersion()} method
     * returns a non-empty {@code Optional} instance wrapping a
     * {@code DottedVersion} created from the source version using
     * {@link DottedVersion#lazy(String)} method.
     * <p>
     * Use {@link #toString()} to get the string representation of the source
     * version.
     *
     * @see #of(DottedVersion)
     *
     * @param v the source version
     * @return a {@code BundleVersion} wrapping a {@code DottedVersion} created from
     *         the {@code v} parameter
     */
    static BundleVersion of(String v) {
        return of(DottedVersion.lazy(v));
    }
}
