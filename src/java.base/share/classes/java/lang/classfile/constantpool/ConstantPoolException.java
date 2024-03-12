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
package java.lang.classfile.constantpool;

import jdk.internal.javac.PreviewFeature;

/**
 * Thrown to indicate that requested entry cannot be obtained from the constant
 * pool.
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public class ConstantPoolException extends IllegalArgumentException {

    @java.io.Serial
    private static final long serialVersionUID = 7245472922409094120L;

    /**
     * Constructs a {@code ConstantPoolException} with no detail message.
     */
    public ConstantPoolException() {
        super();
    }

    /**
     * Constructs a {@code ConstantPoolException} with the specified detail
     * message.
     *
     * @param message the detail message.
     */
    public ConstantPoolException(String message) {
        super(message);
    }

    /**
     * Constructs a {@code ConstantPoolException} with the specified cause and
     * a detail message of {@code (cause==null ? null : cause.toString())}.
     * @param cause the cause (which is saved for later retrieval by the
     *        {@link Throwable#getCause()} method).  (A {@code null} value is
     *        permitted, and indicates that the cause is nonexistent or
     *        unknown.)
     */
    public ConstantPoolException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a {@code ConstantPoolException} with the specified detail
     * message and cause.
     *
     * @param message the detail message (which is saved for later retrieval
     *        by the {@link Throwable#getMessage()} method).
     * @param cause the cause (which is saved for later retrieval by the
     *        {@link Throwable#getCause()} method).  (A {@code null} value
     *        is permitted, and indicates that the cause is nonexistent or
     *        unknown.)
     */
    public ConstantPoolException(String message, Throwable cause) {
        super(message, cause);
    }
}
