/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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


/**
 * Thrown to indicate that requested entry cannot be obtained from the constant
 * pool or the bootstrap method table.  This is also thrown when the lazy
 * evaluation of constant pool or bootstrap method table entries encounter
 * format errors.
 *
 * @since 24
 */
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
     * @param message the detail message, may be {@code null} for no detail
     *                message
     */
    public ConstantPoolException(String message) {
        super(message);
    }

    /**
     * Constructs a {@code ConstantPoolException} with the specified cause and
     * a detail message of {@code cause == null ? null : cause.toString()}.
     *
     * @param cause the cause, may be {@code null} for nonexistent or unknown
     *              cause
     */
    public ConstantPoolException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a {@code ConstantPoolException} with the specified detail
     * message and cause.
     *
     * @param message the detail message, may be {@code null} for no detail
     *                message
     * @param cause the cause, may be {@code null} for nonexistent or unknown
     *              cause
     */
    public ConstantPoolException(String message, Throwable cause) {
        super(message, cause);
    }
}
