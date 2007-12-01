/*
 * Copyright 1996-1999 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.security.acl;

import java.security.Principal;
import java.security.acl.*;

/**
 * The PermissionImpl class implements the permission
 * interface for permissions that are strings.
 * @author Satish Dharmaraj
 */
public class PermissionImpl implements Permission {

    private String permission;

    /**
     * Construct a permission object using a string.
     * @param permission the stringified version of the permission.
     */
    public PermissionImpl(String permission) {
        this.permission = permission;
    }

    /**
     * This function returns true if the object passed matches the permission
     * represented in this interface.
     * @param another The Permission object to compare with.
     * @return true if the Permission objects are equal, false otherwise
     */
    public boolean equals(Object another) {
        if (another instanceof Permission) {
            Permission p = (Permission) another;
            return permission.equals(p.toString());
        } else {
            return false;
        }
    }

    /**
     * Prints a stringified version of the permission.
     * @return the string representation of the Permission.
     */
    public String toString() {
        return permission;
    }

    /**
     * Returns a hashcode for this PermissionImpl.
     *
     * @return a hashcode for this PermissionImpl.
     */
    public int hashCode() {
        return toString().hashCode();
    }

}
