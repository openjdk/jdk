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
package jdk.jpackage.internal.util;

import java.util.Objects;

/**
 * Object wrapper implementing {@link Object#equals(Object)} such that it
 * returns {@code true} only when the argument is another instance of this class
 * wrapping the same object.
 * <p>
 * The class guarantees that {@link Object#equals(Object)} and
 * {@link Object#hashCode()} methods of the wrapped object will never be called
 * inside of the class methods.
 *
 * @param <T> the type of the wrapped value
 */
public final class IdentityWrapper<T> {

    public IdentityWrapper(T value) {
        this.value = Objects.requireNonNull(value);
    }

    public T value() {
        return value;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if ((obj == null) || (getClass() != obj.getClass())) {
            return false;
        }
        var other = (IdentityWrapper<?>) obj;
        return value == other.value;
    }

    @Override
    public String toString() {
        return String.format("Identity[%s]", value);
    }

    public static <T> IdentityWrapper<T> wrapIdentity(T v) {
        return new IdentityWrapper<>(v);
    }

    private final T value;
}
