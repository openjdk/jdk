/*
 * Copyright (c) 1996, 2004, Oracle and/or its affiliates. All rights reserved.
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

package java.security.acl;

import java.util.Enumeration;
import java.security.Principal;

/**
 * Interface representing an Access Control List (ACL).  An Access
 * Control List is a data structure used to guard access to
 * resources.<p>
 *
 * An ACL can be thought of as a data structure with multiple ACL
 * entries.  Each ACL entry, of interface type AclEntry, contains a
 * set of permissions associated with a particular principal. (A
 * principal represents an entity such as an individual user or a
 * group). Additionally, each ACL entry is specified as being either
 * positive or negative. If positive, the permissions are to be
 * granted to the associated principal. If negative, the permissions
 * are to be denied.<p>
 *
 * The ACL Entries in each ACL observe the following rules:<p>
 *
 * <ul> <li>Each principal can have at most one positive ACL entry and
 * one negative entry; that is, multiple positive or negative ACL
 * entries are not allowed for any principal.  Each entry specifies
 * the set of permissions that are to be granted (if positive) or
 * denied (if negative). <p>
 *
 * <li>If there is no entry for a particular principal, then the
 * principal is considered to have a null (empty) permission set.<p>
 *
 * <li>If there is a positive entry that grants a principal a
 * particular permission, and a negative entry that denies the
 * principal the same permission, the result is as though the
 * permission was never granted or denied. <p>
 *
 * <li>Individual permissions always override permissions of the
 * group(s) to which the individual belongs. That is, individual
 * negative permissions (specific denial of permissions) override the
 * groups' positive permissions. And individual positive permissions
 * override the groups' negative permissions.<p>
 *
 * </ul>
 *
 * The <code> java.security.acl </code> package provides the
 * interfaces to the ACL and related data structures (ACL entries,
 * groups, permissions, etc.), and the <code> sun.security.acl </code>
 * classes provide a default implementation of the interfaces. For
 * example, <code> java.security.acl.Acl </code> provides the
 * interface to an ACL and the <code> sun.security.acl.AclImpl </code>
 * class provides the default implementation of the interface.<p>
 *
 * The <code> java.security.acl.Acl </code> interface extends the
 * <code> java.security.acl.Owner </code> interface. The Owner
 * interface is used to maintain a list of owners for each ACL.  Only
 * owners are allowed to modify an ACL. For example, only an owner can
 * call the ACL's <code>addEntry</code> method to add a new ACL entry
 * to the ACL.
 *
 * @see java.security.acl.AclEntry
 * @see java.security.acl.Owner
 * @see java.security.acl.Acl#getPermissions
 *
 * @author Satish Dharmaraj
 */

public interface Acl extends Owner {

    /**
     * Sets the name of this ACL.
     *
     * @param caller the principal invoking this method. It must be an
     * owner of this ACL.
     *
     * @param name the name to be given to this ACL.
     *
     * @exception NotOwnerException if the caller principal
     * is not an owner of this ACL.
     *
     * @see #getName
     */
    public void setName(Principal caller, String name)
      throws NotOwnerException;

    /**
     * Returns the name of this ACL.
     *
     * @return the name of this ACL.
     *
     * @see #setName
     */
    public String getName();

    /**
     * Adds an ACL entry to this ACL. An entry associates a principal
     * (e.g., an individual or a group) with a set of
     * permissions. Each principal can have at most one positive ACL
     * entry (specifying permissions to be granted to the principal)
     * and one negative ACL entry (specifying permissions to be
     * denied). If there is already an ACL entry of the same type
     * (negative or positive) already in the ACL, false is returned.
     *
     * @param caller the principal invoking this method. It must be an
     * owner of this ACL.
     *
     * @param entry the ACL entry to be added to this ACL.
     *
     * @return true on success, false if an entry of the same type
     * (positive or negative) for the same principal is already
     * present in this ACL.
     *
     * @exception NotOwnerException if the caller principal
     *  is not an owner of this ACL.
     */
    public boolean addEntry(Principal caller, AclEntry entry)
      throws NotOwnerException;

    /**
     * Removes an ACL entry from this ACL.
     *
     * @param caller the principal invoking this method. It must be an
     * owner of this ACL.
     *
     * @param entry the ACL entry to be removed from this ACL.
     *
     * @return true on success, false if the entry is not part of this ACL.
     *
     * @exception NotOwnerException if the caller principal is not
     * an owner of this Acl.
     */
    public boolean removeEntry(Principal caller, AclEntry entry)
          throws NotOwnerException;

    /**
     * Returns an enumeration for the set of allowed permissions for the
     * specified principal (representing an entity such as an individual or
     * a group). This set of allowed permissions is calculated as
     * follows:<p>
     *
     * <ul>
     *
     * <li>If there is no entry in this Access Control List for the
     * specified principal, an empty permission set is returned.<p>
     *
     * <li>Otherwise, the principal's group permission sets are determined.
     * (A principal can belong to one or more groups, where a group is a
     * group of principals, represented by the Group interface.)
     * The group positive permission set is the union of all
     * the positive permissions of each group that the principal belongs to.
     * The group negative permission set is the union of all
     * the negative permissions of each group that the principal belongs to.
     * If there is a specific permission that occurs in both
     * the positive permission set and the negative permission set,
     * it is removed from both.<p>
     *
     * The individual positive and negative permission sets are also
     * determined. The positive permission set contains the permissions
     * specified in the positive ACL entry (if any) for the principal.
     * Similarly, the negative permission set contains the permissions
     * specified in the negative ACL entry (if any) for the principal.
     * The individual positive (or negative) permission set is considered
     * to be null if there is not a positive (negative) ACL entry for the
     * principal in this ACL.<p>
     *
     * The set of permissions granted to the principal is then calculated
     * using the simple rule that individual permissions always override
     * the group permissions. That is, the principal's individual negative
     * permission set (specific denial of permissions) overrides the group
     * positive permission set, and the principal's individual positive
     * permission set overrides the group negative permission set.
     *
     * </ul>
     *
     * @param user the principal whose permission set is to be returned.
     *
     * @return the permission set specifying the permissions the principal
     * is allowed.
     */
    public Enumeration<Permission> getPermissions(Principal user);

    /**
     * Returns an enumeration of the entries in this ACL. Each element in
     * the enumeration is of type AclEntry.
     *
     * @return an enumeration of the entries in this ACL.
     */
    public Enumeration<AclEntry> entries();

    /**
     * Checks whether or not the specified principal has the specified
     * permission. If it does, true is returned, otherwise false is returned.
     *
     * More specifically, this method checks whether the passed permission
     * is a member of the allowed permission set of the specified principal.
     * The allowed permission set is determined by the same algorithm as is
     * used by the <code>getPermissions</code> method.
     *
     * @param principal the principal, assumed to be a valid authenticated
     * Principal.
     *
     * @param permission the permission to be checked for.
     *
     * @return true if the principal has the specified permission, false
     * otherwise.
     *
     * @see #getPermissions
     */
    public boolean checkPermission(Principal principal, Permission permission);

    /**
     * Returns a string representation of the
     * ACL contents.
     *
     * @return a string representation of the ACL contents.
     */
    public String toString();
}
