/*
 * Copyright 1996-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.util.*;
import java.security.*;
import java.security.acl.*;

/**
 * Class implementing the Owner interface. The
 * initial owner principal is configured as
 * part of the constructor.
 * @author      Satish Dharmaraj
 */
public class OwnerImpl implements Owner {
    private Group ownerGroup;

    public OwnerImpl(Principal owner) {
        ownerGroup = new GroupImpl("AclOwners");
        ownerGroup.addMember(owner);
    }

    /**
     * Adds an owner. Owners can modify ACL contents and can disassociate
     * ACLs from the objects they protect in the AclConfig interface.
     * The caller principal must be a part of the owners list of the ACL in
     * order to invoke this method. The initial owner is configured
     * at ACL construction time.
     * @param caller the principal who is invoking this method.
     * @param owner The owner that should be added to the owners list.
     * @return true if success, false if already an owner.
     * @exception NotOwnerException if the caller principal is not on
     * the owners list of the Acl.
     */
    public synchronized boolean addOwner(Principal caller, Principal owner)
      throws NotOwnerException
    {
        if (!isOwner(caller))
            throw new NotOwnerException();

        ownerGroup.addMember(owner);
        return false;
    }

    /**
     * Delete owner. If this is the last owner in the ACL, an exception is
     * raised.
     * The caller principal must be a part of the owners list of the ACL in
     * order to invoke this method.
     * @param caller the principal who is invoking this method.
     * @param owner The owner to be removed from the owners list.
     * @return true if the owner is removed, false if the owner is not part
     * of the owners list.
     * @exception NotOwnerException if the caller principal is not on
     * the owners list of the Acl.
     * @exception LastOwnerException if there is only one owner left in the group, then
     * deleteOwner would leave the ACL owner-less. This exception is raised in such a case.
     */
    public synchronized boolean deleteOwner(Principal caller, Principal owner)
      throws NotOwnerException, LastOwnerException
    {
        if (!isOwner(caller))
            throw new NotOwnerException();

        Enumeration<? extends Principal> e = ownerGroup.members();
        //
        // check if there is atleast 2 members left.
        //
        Object o = e.nextElement();
        if (e.hasMoreElements())
            return ownerGroup.removeMember(owner);
        else
            throw new LastOwnerException();

    }

    /**
     * returns if the given principal belongs to the owner list.
     * @param owner The owner to check if part of the owners list
     * @return true if the passed principal is in the owner list, false if not.
     */
    public synchronized boolean isOwner(Principal owner) {
        return ownerGroup.isMember(owner);
    }
}
