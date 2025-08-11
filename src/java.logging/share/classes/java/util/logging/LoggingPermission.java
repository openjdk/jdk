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


package java.util.logging;


/**
 * This class is for logging permissions. Currently there is only one
 * LoggingPermission named "control".
 *
 * @deprecated
 * This permission cannot be used for controlling access to resources
 * as the Security Manager is no longer supported.
 *
 *
 * @since 1.4
 * @see java.security.BasicPermission
 * @see java.security.Permission
 * @see java.security.Permissions
 * @see java.security.PermissionCollection
 * @see java.lang.SecurityManager
 *
 */

@Deprecated(since="25", forRemoval=true)
public final class LoggingPermission extends java.security.BasicPermission {

    private static final long serialVersionUID = 63564341580231582L;

    /**
     * Creates a new LoggingPermission object.
     *
     * @param name Permission name.  Must be "control".
     * @param actions Must be either null or the empty string.
     *
     * @throws NullPointerException if <code>name</code> is <code>null</code>.
     * @throws IllegalArgumentException if <code>name</code> is empty or if
     * arguments are invalid.
     */
    public LoggingPermission(String name, String actions) throws IllegalArgumentException {
        super(name);
        if (!name.equals("control")) {
            throw new IllegalArgumentException("name: " + name);
        }
        if (actions != null && actions.length() > 0) {
            throw new IllegalArgumentException("actions: " + actions);
        }
    }
}
