/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * An operation which accepts a single integer argument and returns no result.
 * This is the primitive type specialization of {@link Consumer} for {@code int}.
 * Unlike most other functional interfaces, {@code IntConsumer} is expected to
 * operate via side-effects.
 *
 * @see Consumer
 * @since 1.8
 */
@FunctionalInterface
public interface IntConsumer {

    /**
     * Accept an input value.
     *
     * @param value the input value
     */
    void accept(int value);

    /**
     * Returns an {@code IntConsumer} which performs, in sequence, the operation
     * represented by this object followed by the operation represented by
     * another {@code IntConsumer}.
     *
     * <p>Any exceptions thrown by either {@code accept} method are relayed
     * to the caller; if performing this operation throws an exception, the
     * other operation will not be performed.
     *
     * @param other an IntConsumer which will be chained after this
     * IntConsumer
     * @return an IntConsumer which performs in sequence the {@code accept} method
     * of this IntConsumer and the {@code accept} method of the specified IntConsumer
     * operation
     * @throws NullPointerException if other is null
     */
    default IntConsumer chain(IntConsumer other) {
        Objects.requireNonNull(other);
        return (int t) -> { accept(t); other.accept(t); };
    }
}
