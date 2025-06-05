/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * This exception was originally thrown by the {@link AccessController} to
 * indicate that a requested access was denied.
 *
 * @author Li Gong
 * @author Roland Schemers
 * @since 1.2
 * @deprecated This exception was only useful in conjunction with
 *       {@linkplain SecurityManager the Security Manager}, which is no
 *       longer supported. There is no replacement for the Security Manager
 *       or this class.
 */

@Deprecated(since="17", forRemoval=true)
public class AccessControlException extends SecurityException {

    @java.io.Serial
    private static final long serialVersionUID = 5138225684096988535L;

    /**
     * @serial The permission that caused the exception to be thrown.
     */
    private Permission perm;

    /**
     * Constructs an {@code AccessControlException} with the
     * specified, detailed message.
     *
     * @param   s   the detail message.
     */
    public AccessControlException(String s) {
        super(s);
    }

    /**
     * Constructs an {@code AccessControlException} with the
     * specified, detailed message, and the requested permission that caused
     * the exception.
     *
     * @param   s   the detail message.
     * @param   p   the permission that caused the exception.
     */
    public AccessControlException(String s, Permission p) {
        super(s);
        perm = p;
    }

    /**
     * Gets the {@code Permission} object associated with this exception, or
     * {@code null} if there was no corresponding {@code Permission} object.
     *
     * @return the Permission object.
     */
    public Permission getPermission() {
        return perm;
    }
}
