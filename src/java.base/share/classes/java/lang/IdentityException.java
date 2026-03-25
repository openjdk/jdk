/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 * Identity objects are required for synchronization and locking.
 * <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">Value-based</a>
 * objects do not have identity and cannot be used for synchronization, locking,
 * or any type of {@link java.lang.ref.Reference}.
 *
 * @since Valhalla
 */
@PreviewFeature(feature = PreviewFeature.Feature.VALUE_OBJECTS)
public class IdentityException extends RuntimeException {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    /**
     * Create an {@code IdentityException} with no message.
     */
    public IdentityException() {
    }

    /**
     * Create an {@code IdentityException} with the class name and default message.
     *
     * @param clazz the class of the object
     */
    public IdentityException(Class<?> clazz) {
        super(clazz.getName() + " is not an identity class");
    }

    /**
     * Create an {@code IdentityException} with a message.
     *
     * @param  message the detail message; can be {@code null}
     */
    public IdentityException(String message) {
        super(message);
    }

    /**
     * Create an {@code IdentityException} with a cause.
     *
     * @param  cause the cause; {@code null} is permitted, and indicates
     *               that the cause is nonexistent or unknown.
     */
    public IdentityException(Throwable cause) {
        super(cause);
    }

    /**
     * Create an {@code IdentityException} with a message and cause.
     *
     * @param  message the detail message; can be {@code null}
     * @param  cause the cause; {@code null} is permitted, and indicates
     *               that the cause is nonexistent or unknown.
     */
    public IdentityException(String message, Throwable cause) {
        super(message, cause);
    }
}
