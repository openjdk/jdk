/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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


package com.sun.jmx.snmp.IPAcl;



import java.security.Principal;
import java.security.acl.Acl;
import java.security.acl.AclEntry;
import java.security.acl.NotOwnerException;

import java.io.Serializable;
import java.security.acl.Permission;
import java.util.Vector;
import java.util.Enumeration;


/**
 * Represent an Access Control List (ACL) which is used to guard access to http adaptor.
 * <P>
 * It is a data structure with multiple ACL entries. Each ACL entry, of interface type
 * AclEntry, contains a set of permissions and a set of communities associated with a
 * particular principal. (A principal represents an entity such as a host or a group of host).
 * Additionally, each ACL entry is specified as being either positive or negative.
 * If positive, the permissions are to be granted to the associated principal.
 * If negative, the permissions are to be denied.
 *
 * @see java.security.acl.Acl
 */

class AclImpl extends OwnerImpl implements Acl, Serializable {
  private static final long serialVersionUID = -2250957591085270029L;

  private Vector<AclEntry> entryList = null;
  private String aclName = null;

  /**
   * Constructs the ACL with a specified owner
   *
   * @param owner owner of the ACL.
   * @param name  name of this ACL.
   */
  public AclImpl (PrincipalImpl owner, String name) {
        super(owner);
        entryList = new Vector<>();
        aclName = name;
  }

  /**
   * Sets the name of this ACL.
   *
   * @param caller the principal invoking this method. It must be an owner
   *        of this ACL.
   * @param name the name to be given to this ACL.
   *
   * @exception NotOwnerException if the caller principal is not an owner
   *            of this ACL.
   * @see java.security.Principal
   */
  @Override
  public void setName(Principal caller, String name)
        throws NotOwnerException {
          if (!isOwner(caller))
                throw new NotOwnerException();
          aclName = name;
  }

  /**
   * Returns the name of this ACL.
   *
   * @return the name of this ACL.
   */
  @Override
  public String getName(){
        return aclName;
  }

  /**
   * Adds an ACL entry to this ACL. An entry associates a principal (e.g., an individual or a group)
   * with a set of permissions. Each principal can have at most one positive ACL entry
   * (specifying permissions to be granted to the principal) and one negative ACL entry
   * (specifying permissions to be denied). If there is already an ACL entry
   * of the same type (negative or positive) already in the ACL, false is returned.
   *
   * @param caller the principal invoking this method. It must be an owner
   *        of this ACL.
   * @param entry the ACL entry to be added to this ACL.
   * @return true on success, false if an entry of the same type (positive
   *       or negative) for the same principal is already present in this ACL.
   * @exception NotOwnerException if the caller principal is not an owner of
   *       this ACL.
   * @see java.security.Principal
   */
  @Override
  public boolean addEntry(Principal caller, AclEntry entry)
        throws NotOwnerException {
          if (!isOwner(caller))
                throw new NotOwnerException();

          if (entryList.contains(entry))
                return false;
          /*
                 for (Enumeration e = entryList.elements();e.hasMoreElements();){
                 AclEntry ent = (AclEntry) e.nextElement();
                 if (ent.getPrincipal().equals(entry.getPrincipal()))
                 return false;
                 }
                 */

          entryList.addElement(entry);
          return true;
  }

  /**
   * Removes an ACL entry from this ACL.
   *
   * @param caller the principal invoking this method. It must be an owner
   *        of this ACL.
   * @param entry the ACL entry to be removed from this ACL.
   * @return true on success, false if the entry is not part of this ACL.
   * @exception NotOwnerException if the caller principal is not an owner
   *   of this Acl.
   * @see java.security.Principal
   * @see java.security.acl.AclEntry
   */
  @Override
  public boolean removeEntry(Principal caller, AclEntry entry)
        throws NotOwnerException {
          if (!isOwner(caller))
                throw new NotOwnerException();

          return (entryList.removeElement(entry));
  }

  /**
   * Removes all ACL entries from this ACL.
   *
   * @param caller the principal invoking this method. It must be an owner
   *        of this ACL.
   * @exception NotOwnerException if the caller principal is not an owner of
   *        this Acl.
   * @see java.security.Principal
   */
  public void removeAll(Principal caller)
        throws NotOwnerException {
          if (!isOwner(caller))
                throw new NotOwnerException();
        entryList.removeAllElements();
  }

  /**
   * Returns an enumeration for the set of allowed permissions for
   * the specified principal
   * (representing an entity such as an individual or a group).
   * This set of allowed permissions is calculated as follows:
   * <UL>
   * <LI>If there is no entry in this Access Control List for the specified
   * principal, an empty permission set is returned.</LI>
   * <LI>Otherwise, the principal's group permission sets are determined.
   * (A principal can belong to one or more groups, where a group is a group
   * of principals, represented by the Group interface.)</LI>
   * </UL>
   * @param user the principal whose permission set is to be returned.
   * @return the permission set specifying the permissions the principal
   *     is allowed.
   * @see java.security.Principal
   */
  @Override
  public Enumeration<Permission> getPermissions(Principal user){
        Vector<Permission> empty = new Vector<>();
        for (Enumeration<AclEntry> e = entryList.elements();e.hasMoreElements();){
          AclEntry ent = e.nextElement();
          if (ent.getPrincipal().equals(user))
                return ent.permissions();
        }
        return empty.elements();
  }

  /**
   * Returns an enumeration of the entries in this ACL. Each element in the
   * enumeration is of type AclEntry.
   *
   * @return an enumeration of the entries in this ACL.
   */
  @Override
  public Enumeration<AclEntry> entries(){
        return entryList.elements();
  }

  /**
   * Checks whether or not the specified principal has the specified
   * permission.
   * If it does, true is returned, otherwise false is returned.
   * More specifically, this method checks whether the passed permission
   * is a member of the allowed permission set of the specified principal.
   * The allowed permission set is determined by the same algorithm as is
   * used by the getPermissions method.
   *
   * @param user the principal, assumed to be a valid authenticated Principal.
   * @param perm the permission to be checked for.
   * @return true if the principal has the specified permission,
   *         false otherwise.
   * @see java.security.Principal
   * @see java.security.Permission
   */
  @Override
  public boolean checkPermission(Principal user,
                                 java.security.acl.Permission perm) {
        for (Enumeration<AclEntry> e = entryList.elements();e.hasMoreElements();){
          AclEntry ent = e.nextElement();
          if (ent.getPrincipal().equals(user))
                if (ent.checkPermission(perm)) return true;
        }
        return false;
  }

  /**
   * Checks whether or not the specified principal has the specified
   * permission.
   * If it does, true is returned, otherwise false is returned.
   * More specifically, this method checks whether the passed permission
   * is a member of the allowed permission set of the specified principal.
   * The allowed permission set is determined by the same algorithm as is
   * used by the getPermissions method.
   *
   * @param user the principal, assumed to be a valid authenticated Principal.
   * @param community the community name associated with the principal.
   * @param perm the permission to be checked for.
   * @return true if the principal has the specified permission, false
   *        otherwise.
   * @see java.security.Principal
   * @see java.security.Permission
   */
  public boolean checkPermission(Principal user, String community,
                                 java.security.acl.Permission perm) {
        for (Enumeration<AclEntry> e = entryList.elements();e.hasMoreElements();){
          AclEntryImpl ent = (AclEntryImpl) e.nextElement();
          if (ent.getPrincipal().equals(user))
                if (ent.checkPermission(perm) && ent.checkCommunity(community)) return true;
        }
        return false;
  }

  /**
   * Checks whether or not the specified community string is defined.
   *
   * @param community the community name associated with the principal.
   *
   * @return true if the specified community string is defined, false
   *      otherwise.
   * @see java.security.Principal
   * @see java.security.Permission
   */
  public boolean checkCommunity(String community) {
        for (Enumeration<AclEntry> e = entryList.elements();e.hasMoreElements();){
          AclEntryImpl ent = (AclEntryImpl) e.nextElement();
          if (ent.checkCommunity(community)) return true;
        }
        return false;
  }

  /**
   * Returns a string representation of the ACL contents.
   *
   * @return a string representation of the ACL contents.
   */
  @Override
  public String toString(){
        return ("AclImpl: "+ getName());
  }
}
