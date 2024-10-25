/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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

import sun.security.util.SecurityConstants;

import java.util.Enumeration;

/**
 * The {@code AllPermission} is a permission that implies all other permissions.
 *
 * @apiNote
 * This permission cannot be used for controlling access to resources
 * as the Security Manager is no longer supported.
 *
 * @see java.security.Permission
 * @see java.security.Permissions
 * @see java.security.PermissionCollection
 *
 * @author Roland Schemers
 * @since 1.2
 *
 * @serial exclude
 */

public final class AllPermission extends Permission {

    @java.io.Serial
    private static final long serialVersionUID = -2916474571451318075L;

    /**
     * Creates a new {@code AllPermission} object.
     */
    public AllPermission() {
        super("<all permissions>");
    }


    /**
     * Creates a new {@code AllPermission} object. This
     * constructor exists for use by the {@code Policy} object
     * to instantiate new {@code Permission} objects.
     *
     * @param name ignored
     * @param actions ignored.
     */
    public AllPermission(String name, String actions) {
        this();
    }

    /**
     * Checks if the specified permission is "implied" by
     * this object. This method always returns {@code true}.
     *
     * @param p the permission to check against.
     *
     * @return return
     */
    public boolean implies(Permission p) {
         return true;
    }

    /**
     * Checks two {@code AllPermission} objects for equality.
     * Two {@code AllPermission} objects are always equal.
     *
     * @param obj the object we are testing for equality with this object.
     * @return true if {@code obj} is an {@code AllPermission}, false otherwise.
     */
    @Override
    public boolean equals(Object obj) {
        return (obj instanceof AllPermission);
    }

    /**
     * {@return the hash code value for this object}
     */
    @Override
    public int hashCode() {
        return 1;
    }

    /**
     * Returns the canonical string representation of the actions.
     *
     * @return the actions.
     */
    public String getActions() {
        return "<all actions>";
    }

    /**
     * Returns a new {@code PermissionCollection} for storing
     * {@code AllPermission} objects.
     *
     * @return a new {@code PermissionCollection} suitable for
     * storing {@code AllPermission} objects.
     */
    public PermissionCollection newPermissionCollection() {
        return new AllPermissionCollection();
    }

}

/**
 * An {@code AllPermissionCollection} stores a collection
 * of {@code AllPermission} permissions. {@code AllPermission} objects
 * must be stored in a manner that allows them to be inserted in any
 * order, but enable the implies function to evaluate the implies
 * method in an efficient (and consistent) manner.
 *
 * @see java.security.Permission
 * @see java.security.Permissions
 *
 *
 * @author Roland Schemers
 *
 * @serial include
 */

final class AllPermissionCollection
    extends PermissionCollection
    implements java.io.Serializable
{

    // use serialVersionUID from JDK 1.2.2 for interoperability
    @java.io.Serial
    private static final long serialVersionUID = -4023755556366636806L;

    /**
     * True if any {@code AllPermissionCollection} objects have been added.
     */
    private boolean all_allowed;

    /**
     * Create an empty {@code AllPermissionCollection} object.
     *
     */

    public AllPermissionCollection() {
        all_allowed = false;
    }

    /**
     * Adds a permission to the {@code AllPermissionCollection} object.
     * The key for the hash is {@code permission.path}.
     *
     * @param permission the {@code Permission} object to add.
     *
     * @throws    IllegalArgumentException   if the permission is not an
     *                                       {@code AllPermission}
     *
     * @throws    SecurityException   if this {@code AllPermissionCollection}
     *                                object has been marked readonly
     */

    public void add(Permission permission) {
        if (! (permission instanceof AllPermission))
            throw new IllegalArgumentException("invalid permission: "+
                                               permission);
        if (isReadOnly())
            throw new SecurityException("attempt to add a Permission to a readonly PermissionCollection");

        all_allowed = true; // No sync; staleness OK
    }

    /**
     * Check and see if this set of permissions implies the permissions
     * expressed in "permission".
     *
     * @param permission the {@code Permission} object to compare
     *
     * @return always returns {@code true}.
     */

    public boolean implies(Permission permission) {
        return all_allowed; // No sync; staleness OK
    }

    /**
     * Returns an enumeration of all the {@code AllPermission} objects in the
     * container.
     *
     * @return an enumeration of all the {@code AllPermission} objects.
     */
    public Enumeration<Permission> elements() {
        return new Enumeration<>() {
            private boolean hasMore = all_allowed;

            public boolean hasMoreElements() {
                return hasMore;
            }

            public Permission nextElement() {
                hasMore = false;
                return SecurityConstants.ALL_PERMISSION;
            }
        };
    }
}
