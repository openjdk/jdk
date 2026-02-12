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

package java.security;

/**
 * Thrown to indicate a failure during cryptographic processing at runtime.
 *
 * <p>This exception represents a general cryptographic error that occurs during
 * processing, typically used for unrecoverable failures related to
 * {@link GeneralSecurityException}, but in contexts where checked exceptions
 * are not desired.
 *
 * <p>This exception is not intended to represent internal
 * provider errors, which should be reported using {@link ProviderException}.
 */
public final class CryptoException extends RuntimeException {

    @java.io.Serial
    private static final long serialVersionUID = -6824337376392797817L;

    /**
     * Constructs a new CryptoException with {@code null} as its detail message.
     * The cause is not initialized and may subsequently be initialized by a
     * call to {@link #initCause(Throwable)}.
     */
    public CryptoException() {
        super();
    }

    /**
     * Constructs a new CryptoException with the specified detail message.
     * The cause is not initialized and may subsequently be initialized by a
     * call to {@link #initCause(Throwable)}.
     *
     * @param message the detail message. The detail message is saved for later
     *                retrieval by the {@link #getMessage()} method.
     */
    public CryptoException(String message) {
        super(message);
    }

    /**
     * Constructs a new CryptoException with the specified detail message and cause.
     *
     * <p> Note that the detail message associated with {@code cause} is not
     * automatically incorporated in this exception's detail message.
     *
     * @param message the detail message. The detail message is saved for later
     *                retrieval by the {@link #getMessage()} method.
     * @param cause the cause. The cause is saved for later retrieval by the
     *              {@link #getCause()} method. A {@code null} value is permitted
     *              and indicates that the cause is nonexistent or unknown.
     */
    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new CryptoException with the specified cause and a detail
     * message of {@code (cause == null ? null : cause.toString())}, which
     * typically contains the class and detail message of {@code cause}.
     *
     * @param cause the cause. The cause is saved for later retrieval by the
     *              {@link #getCause()} method. A {@code null} value is permitted
     *              and indicates that the cause is nonexistent or unknown.
     */
    public CryptoException(Throwable cause) {
        super(cause);
    }
}
