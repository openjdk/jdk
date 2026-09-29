/*
 * Copyright (c) 2004, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.jdi;

/**
 * A {@code JDIPermission} object contains a name (also referred
 * to as a "target name") but no actions list; you either have the
 * named permission or you don't.
 *
 * @apiNote
 * This permission cannot be used for controlling access to resources
 * as the Security Manager is no longer supported.
 * Consequently this class is deprecated for removal in a future release.
 *
 * @author  Tim Bell
 * @since   1.5
 *
 * @see Bootstrap
 * @see java.security.BasicPermission
 * @see java.security.Permission
 * @see java.security.Permissions
 * @see java.security.PermissionCollection
 * @see java.lang.SecurityManager
 *
 * @deprecated This class was only useful in conjunction with the Security Manager,
 * which is no longer supported. There is no replacement for this class.
 */

@Deprecated(since="25", forRemoval=true)
public final class JDIPermission extends java.security.BasicPermission {

    private static final long serialVersionUID = -6988461416938786271L;

    /**
     * The {@code JDIPermission} class represents access rights to the
     * {@code VirtualMachineManager}
     * @param name Permission name. Must be "virtualMachineManager".
     * @throws IllegalArgumentException if the name argument is invalid.
     */
    public JDIPermission(String name) {
        super(name);
        if (!name.equals("virtualMachineManager")) {
            throw new IllegalArgumentException("name: " + name);
        }
    }

    /**
     * Constructs a new JDIPermission object.
     *
     * @param name Permission name. Must be "virtualMachineManager".
     * @param actions Must be either null or the empty string.
     * @throws IllegalArgumentException if arguments are invalid.
     */
    public JDIPermission(String name, String actions)
        throws IllegalArgumentException {
        super(name);
        if (!name.equals("virtualMachineManager")) {
            throw new IllegalArgumentException("name: " + name);
        }
        if (actions != null && actions.length() > 0) {
            throw new IllegalArgumentException("actions: " + actions);
        }
    }
}
