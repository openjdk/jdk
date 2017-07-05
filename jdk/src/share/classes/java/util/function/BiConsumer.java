/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package java.util.function;

import java.util.Objects;

/**
 * An operation which accepts two input arguments and returns no result. This is
 * the two-arity specialization of {@link Consumer}. Unlike most other
 * functional interfaces, {@code BiConsumer} is expected to operate via
 * side-effects.
 *
 * @param <T> the type of the first argument to the {@code accept} operation
 * @param <U> the type of the second argument to the {@code accept} operation
 *
 * @see Consumer
 * @since 1.8
 */
@FunctionalInterface
public interface BiConsumer<T, U> {

    /**
     * Performs operations upon the provided objects which may modify those
     * objects and/or external state.
     *
     * @param t an input object
     * @param u an input object
     */
    void accept(T t, U u);

    /**
     * Returns a {@code BiConsumer} which performs, in sequence, the operation
     * represented by this object followed by the operation represented by
     * the other {@code BiConsumer}.
     *
     * <p>Any exceptions thrown by either {@code accept} method are relayed
     * to the caller; if performing this operation throws an exception, the
     * other operation will not be performed.
     *
     * @param other a BiConsumer which will be chained after this BiConsumer
     * @return a BiConsumer which performs in sequence the {@code accept} method
     * of this BiConsumer and the {@code accept} method of the specified
     * BiConsumer operation
     * @throws NullPointerException if other is null
     */
    default BiConsumer<T, U> chain(BiConsumer<? super T, ? super U> other) {
        Objects.requireNonNull(other);

        return (l, r) -> {
            accept(l, r);
            other.accept(l, r);
        };
    }
}
