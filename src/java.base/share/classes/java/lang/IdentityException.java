/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
package java.lang;

import jdk.internal.javac.PreviewFeature;

/**
 * Thrown when an identity object is required but a value object is supplied.
 * <p>
 * Identity objects are required for synchronization, locking, or any type
 * of {@link java.lang.ref.Reference} that can track object liveness.
 * Value objects are barred from these operations to avoid erroneous attempts at
 * {@linkplain Object##Indistinguishability distinguishing}
 * between copies of the same value.
 * To test if an object is an identity object, use {@link java.util.Objects#hasIdentity}.
 *
 * @since 28
 * @see Object##Indistinguishability object distinguishability
 */
@PreviewFeature(feature = PreviewFeature.Feature.VALUE_OBJECTS)
public final class IdentityException extends RuntimeException {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    /**
     * Create an {@code IdentityException} with no message.
     */
    public IdentityException() {
    }

    /**
     * Create an {@code IdentityException} with a message.
     *
     * @param  message the detail message; can be {@code null}
     */
    public IdentityException(String message) {
        super(message);
    }
}
