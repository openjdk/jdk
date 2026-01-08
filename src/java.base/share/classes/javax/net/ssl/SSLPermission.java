/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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

package javax.net.ssl;

import java.security.*;

/**
 * This class is for various network permissions.
 * An SSLPermission contains a name (also referred to as a "target name") but
 * no actions list; you either have the named permission
 * or you don't.
 * <P>
 * The target name is the name of the network permission. The naming
 * convention follows the  hierarchical property naming convention.
 * Also, an asterisk
 * may appear at the end of the name, following a ".", or by itself, to
 * signify a wildcard match. For example: "foo.*" and "*" signify a wildcard
 * match, while "*foo" and "a*b" do not.
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
 * @since 1.4
 * @author Marianne Mueller
 * @author Roland Schemers
 */

@Deprecated(since="25", forRemoval=true)
public final class SSLPermission extends BasicPermission {

    @java.io.Serial
    private static final long serialVersionUID = -3456898025505876775L;

    /**
     * Creates a new SSLPermission with the specified name.
     * The name is the symbolic name of the SSLPermission, such as
     * "setDefaultAuthenticator", etc. An asterisk
     * may appear at the end of the name, following a ".", or by itself, to
     * signify a wildcard match.
     *
     * @param name the name of the SSLPermission.
     *
     * @throws NullPointerException if <code>name</code> is null.
     * @throws IllegalArgumentException if <code>name</code> is empty.
     */

    public SSLPermission(String name)
    {
        super(name);
    }

    /**
     * Creates a new SSLPermission object with the specified name.
     * The name is the symbolic name of the SSLPermission, and the
     * actions String is currently unused and should be null.
     *
     * @param name the name of the SSLPermission.
     * @param actions ignored.
     *
     * @throws NullPointerException if <code>name</code> is null.
     * @throws IllegalArgumentException if <code>name</code> is empty.
     */

    public SSLPermission(String name, String actions)
    {
        super(name, actions);
    }
}
