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

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SetBuilder<T> {

    public static <T> SetBuilder<T> build() {
        return new SetBuilder<>();
    }

    public SetBuilder<T> set(Collection<? extends T> v) {
        return clear().add(v);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final SetBuilder<T> set(T... v) {
        return set(List.of(v));
    }

    public SetBuilder<T> add(Collection<? extends T> v) {
        values.addAll(v);
        return this;
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final SetBuilder<T> add(T... v) {
        return add(List.of(v));
    }

    public SetBuilder<T> remove(Collection<? extends T> v) {
        values.removeAll(v);
        return this;
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final SetBuilder<T> remove(T... v) {
        return remove(List.of(v));
    }

    public SetBuilder<T> clear() {
        values.clear();
        return this;
    }

    public SetBuilder<T> emptyAllowed(boolean v) {
        emptyAllowed = v;
        return this;
    }

    public Set<T> create() {
        if (values.isEmpty() && !emptyAllowed) {
            throw new UnsupportedOperationException();
        }
        return Set.copyOf(values);
    }

    private boolean emptyAllowed;
    private final Set<T> values = new HashSet<>();
}
