/*
 * Copyright 1996-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.security.acl;

import java.util.Enumeration;
import java.security.Principal;

/**
 * This is the interface used for representing one entry in an Access
 * Control List (ACL).<p>
 *
 * An ACL can be thought of as a data structure with multiple ACL entry
 * objects. Each ACL entry object contains a set of permissions associated
 * with a particular principal. (A principal represents an entity such as
 * an individual user or a group). Additionally, each ACL entry is specified
 * as being either positive or negative. If positive, the permissions are
 * to be granted to the associated principal. If negative, the permissions
 * are to be denied. Each principal can have at most one positive ACL entry
 * and one negative entry; that is, multiple positive or negative ACL
 * entries are not allowed for any principal.
 *
 * Note: ACL entries are by default positive. An entry becomes a
 * negative entry only if the
 * {@link #setNegativePermissions() setNegativePermissions}
 * method is called on it.
 *
 * @see java.security.acl.Acl
 *
 * @author      Satish Dharmaraj
 */
public interface AclEntry extends Cloneable {

    /**
     * Specifies the principal for which permissions are granted or denied
     * by this ACL entry. If a principal was already set for this ACL entry,
     * false is returned, otherwise true is returned.
     *
     * @param user the principal to be set for this entry.
     *
     * @return true if the principal is set, false if there was
     * already a principal set for this entry.
     *
     * @see #getPrincipal
     */
    public boolean setPrincipal(Principal user);

    /**
     * Returns the principal for which permissions are granted or denied by
     * this ACL entry. Returns null if there is no principal set for this
     * entry yet.
     *
     * @return the principal associated with this entry.
     *
     * @see #setPrincipal
     */
    public Principal getPrincipal();

    /**
     * Sets this ACL entry to be a negative one. That is, the associated
     * principal (e.g., a user or a group) will be denied the permission set
     * specified in the entry.
     *
     * Note: ACL entries are by default positive. An entry becomes a
     * negative entry only if this <code>setNegativePermissions</code>
     * method is called on it.
     */
    public void setNegativePermissions();

    /**
     * Returns true if this is a negative ACL entry (one denying the
     * associated principal the set of permissions in the entry), false
     * otherwise.
     *
     * @return true if this is a negative ACL entry, false if it's not.
     */
    public boolean isNegative();

    /**
     * Adds the specified permission to this ACL entry. Note: An entry can
     * have multiple permissions.
     *
     * @param permission the permission to be associated with
     * the principal in this entry.
     *
     * @return true if the permission was added, false if the
     * permission was already part of this entry's permission set.
     */
    public boolean addPermission(Permission permission);

    /**
     * Removes the specified permission from this ACL entry.
     *
     * @param permission the permission to be removed from this entry.
     *
     * @return true if the permission is removed, false if the
     * permission was not part of this entry's permission set.
     */
    public boolean removePermission(Permission permission);

    /**
     * Checks if the specified permission is part of the
     * permission set in this entry.
     *
     * @param permission the permission to be checked for.
     *
     * @return true if the permission is part of the
     * permission set in this entry, false otherwise.
     */
    public boolean checkPermission(Permission permission);

    /**
     * Returns an enumeration of the permissions in this ACL entry.
     *
     * @return an enumeration of the permissions in this ACL entry.
     */
    public Enumeration<Permission> permissions();

    /**
     * Returns a string representation of the contents of this ACL entry.
     *
     * @return a string representation of the contents.
     */
    public String toString();

    /**
     * Clones this ACL entry.
     *
     * @return a clone of this ACL entry.
     */
    public Object clone();
}
