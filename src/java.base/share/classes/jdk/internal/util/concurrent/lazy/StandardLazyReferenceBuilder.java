/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.util.concurrent.lazy;

import java.util.Objects;
import java.util.concurrent.lazy.Lazy;
import java.util.concurrent.lazy.LazyReference;
import java.util.function.Supplier;

public record StandardLazyReferenceBuilder<V>(Supplier<? extends V> s,
                                              V v,
                                              Lazy.Evaluation e)
        implements LazyReference.Builder<V> {

    public StandardLazyReferenceBuilder(Supplier<? extends V> s) {
        this(s, null, Lazy.Evaluation.AT_USE);
    }

    @Override
    public LazyReference.Builder<V> withValue(V v) {
        Objects.requireNonNull(v);
        return new StandardLazyReferenceBuilder<>(s, v, e);
    }

    @Override
    public LazyReference.Builder<V> withEarliestEvaluation(Lazy.Evaluation e) {
        return new StandardLazyReferenceBuilder<>(s, v, e);
    }

    @Override
    public LazyReference<V> build() {
        return (v != null)
                ? new PreComputedLazyReference<>(v)
                : new StandardLazyReference<>(s);
    }

}
