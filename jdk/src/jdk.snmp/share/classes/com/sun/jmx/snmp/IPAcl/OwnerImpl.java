/*
 * Copyright (c) 1997, 2007, Oracle and/or its affiliates. All rights reserved.
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



import java.util.Vector;
import java.io.Serializable;

import java.security.Principal;
import java.security.acl.Owner;
import java.security.acl.LastOwnerException;
import java.security.acl.NotOwnerException;


/**
 * Owner of Access Control Lists (ACLs).
 * The initial owner Principal should be specified as an
 * argument to the constructor of the class AclImpl.
 *
 * @see java.security.acl.Owner
 */

class OwnerImpl implements Owner, Serializable {
  private static final long serialVersionUID = -576066072046319874L;

  private Vector<Principal> ownerList = null;

  /**
   * Constructs an empty list of owner.
   */
  public OwnerImpl (){
        ownerList = new Vector<Principal>();
  }

  /**
   * Constructs a list of owner with the specified principal as first element.
   *
   * @param owner the principal added to the owner list.
   */
  public OwnerImpl (PrincipalImpl owner){
        ownerList = new Vector<Principal>();
        ownerList.addElement(owner);
  }

  /**
   * Adds an owner. Only owners can modify ACL contents. The caller principal
   * must be an owner of the ACL in order to invoke this method. That is, only
   * an owner can add another owner. The initial owner is configured at
   * ACL construction time.
   *
   * @param caller the principal invoking this method.
   *        It must be an owner of the ACL.
   * @param owner the owner that should be added to the list of owners.
   * @return true if successful, false if owner is already an owner.
   * @exception NotOwnerException if the caller principal is not an owner
   *    of the ACL.
   */
  public boolean addOwner(Principal caller, Principal owner)
        throws NotOwnerException {
        if (!ownerList.contains(caller))
          throw new NotOwnerException();

        if (ownerList.contains(owner)) {
          return false;
        } else {
          ownerList.addElement(owner);
          return true;
        }
  }

  /**
   * Deletes an owner. If this is the last owner in the ACL, an exception is raised.
   *<P>
   * The caller principal must be an owner of the ACL in order to invoke this method.
   *
   * @param caller the principal invoking this method. It must be an owner
   *   of the ACL.
   * @param owner the owner to be removed from the list of owners.
   * @return true if successful, false if owner is already an owner.
   * @exception NotOwnerException if the caller principal is not an owner
   *   of the ACL.
   * @exception LastOwnerException if there is only one owner left, so that
   *   deleteOwner would leave the ACL owner-less.
   */
  public boolean deleteOwner(Principal caller, Principal owner)
                throws NotOwnerException,LastOwnerException {

        if (!ownerList.contains(caller))
          throw new NotOwnerException();

        if (!ownerList.contains(owner)){
          return false;
        } else {
          if (ownerList.size() == 1)
                throw new LastOwnerException();

          ownerList.removeElement(owner);
          return true;
        }
  }

  /**
   * Returns true if the given principal is an owner of the ACL.
   *
   * @param owner the principal to be checked to determine whether or
   *        not it is an owner.
   * @return true if the given principal is an owner of the ACL.
   */
  public boolean isOwner(Principal owner){
        return ownerList.contains(owner);
  }
}
