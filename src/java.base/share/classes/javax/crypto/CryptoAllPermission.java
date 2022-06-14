/*
 * Copyright (c) 1999, 2022, Oracle and/or its affiliates. All rights reserved.
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

package javax.crypto;

import java.security.*;
import java.util.Enumeration;
import java.util.Vector;

/**
 * The CryptoAllPermission is a permission that implies
 * any other crypto permissions.
 *
 * @see java.security.Permission
 * @see java.security.AllPermission
 *
 * @author Sharon Liu
 * @since 1.4
 */

final class CryptoAllPermission extends CryptoPermission {

    @java.io.Serial
    private static final long serialVersionUID = -5066513634293192112L;

    // This class is similar to java.security.AllPermission.
    static final String ALG_NAME = "CryptoAllPermission";
    static final CryptoAllPermission INSTANCE =
        new CryptoAllPermission();

    private CryptoAllPermission() {
        super(ALG_NAME);
    }

    /**
     * Checks if the specified permission is implied by
     * this object.
     *
     * @param p the permission to check against.
     *
     * @return true if the specified permission is an
     * instance of <code>CryptoPermission</code>.
     */
    public boolean implies(Permission p) {
         return (p instanceof CryptoPermission);
    }

    /**
     * Checks two <code>CryptoAllPermission</code> objects for equality.
     * Two <code>CryptoAllPermission</code> objects are always equal.
     *
     * @param obj the object to test for equality with this object.
     *
     * @return true if <i>obj</i> is a <code>CryptoAllPermission</code> object.
     */
    public boolean equals(Object obj) {
        return (obj == INSTANCE);
    }

    /**
     *
     * Returns the hash code value for this object.
     *
     * @return a hash code value for this object.
     */
    public int hashCode() {
        return 1;
    }

    /**
     * Returns a new <code>PermissionCollection</code> object for storing
     * <code>CryptoAllPermission</code> objects.
     *
     * @return a new <code>PermissionCollection</code> object suitable for
     * storing <code>CryptoAllPermissions</code> objects.
     */
    public PermissionCollection newPermissionCollection() {
        return new CryptoAllPermissionCollection();
    }
}

/**
 * A <code>CryptoAllPermissionCollection</code> stores a collection
 * of <code>CryptoAllPermission</code> permissions.
 *
 * @see java.security.Permission
 * @see java.security.Permissions
 * @see javax.crypto.CryptoPermission
 *
 * @author Sharon Liu
 */
final class CryptoAllPermissionCollection extends PermissionCollection
    implements java.io.Serializable
{

    @java.io.Serial
    private static final long serialVersionUID = 7450076868380144072L;

    // true if a CryptoAllPermission has been added
    private boolean all_allowed;

    /**
     * Create an empty <code>CryptoAllPermission/code> object.
     */
    CryptoAllPermissionCollection() {
        all_allowed = false;
    }

    /**
     * Adds a permission to <code>CryptoAllPermission</code> object.
     *
     * @param permission the <code>Permission</code> object to add.
     *
     * @exception SecurityException - if this <code>CryptoAllPermissionCollection</code>
     * object has been marked readonly
     */
    public void add(Permission permission) {
        if (isReadOnly())
            throw new SecurityException("attempt to add a Permission to " +
                                        "a readonly PermissionCollection");

        if (permission != CryptoAllPermission.INSTANCE)
            return;

        all_allowed = true;
    }

    /**
     * Check and see if this set of permissions implies the permissions
     * expressed in "permission".
     *
     * @param permission the <code>Permission</code> object to compare
     *
     * @return true if the given permission is implied by this
     * CryptoAllPermissionCollection.
     */
    public boolean implies(Permission permission) {
        if (!(permission instanceof CryptoPermission)) {
            return false;
        }
        return all_allowed;
    }

    /**
     * Returns an enumeration of all the <code>CryptoAllPermission</code>
     * objects in the container.
     *
     * @return an enumeration of all <code>CryptoAllPermission</code> objects.
     */
    public Enumeration<Permission> elements() {
        Vector<Permission> v = new Vector<>(1);
        if (all_allowed) v.add(CryptoAllPermission.INSTANCE);
        return v.elements();
    }
}
