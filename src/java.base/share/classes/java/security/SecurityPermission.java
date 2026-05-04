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
 * This class is for security permissions. A {@code SecurityPermission}
 * contains a name (also referred to as a "target name") but no actions list;
 * you either have the named permission or you don't.
 * <p>
 * The target name is the name of a security configuration parameter.
 *
 * @deprecated
 * This permission cannot be used for controlling access to resources
 * as the Security Manager is no longer supported.
 *
 * @see java.security.BasicPermission
 * @see java.security.Permission
 * @see java.security.Permissions
 * @see java.security.PermissionCollection
 * @see java.lang.SecurityManager
 *
 * @author Marianne Mueller
 * @author Roland Schemers
 * @since 1.2
 */

@Deprecated(since="25", forRemoval=true)
public final class SecurityPermission extends BasicPermission {

    @java.io.Serial
    private static final long serialVersionUID = 5236109936224050470L;

    /**
     * Creates a new {@code SecurityPermission} with the specified name.
     * The name is the symbolic name of the {@code SecurityPermission}.
     * An asterisk may appear at the end of the name, following a ".",
     * or by itself, to signify a wildcard match.
     *
     * @param name the name of the {@code SecurityPermission}
     *
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    public SecurityPermission(String name)
    {
        super(name);
    }

    /**
     * Creates a new {@code SecurityPermission} object with the specified name.
     * The name is the symbolic name of the {@code SecurityPermission}, and the
     * actions {@code String} is currently unused and should be {@code null}.
     *
     * @param name the name of the {@code SecurityPermission}
     * @param actions should be {@code null}.
     *
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    public SecurityPermission(String name, String actions)
    {
        super(name, actions);
    }
}
