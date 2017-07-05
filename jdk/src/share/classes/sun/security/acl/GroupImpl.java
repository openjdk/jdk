/*
 * Copyright (c) 1996, 2006, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.acl;

import java.util.*;
import java.security.*;
import java.security.acl.*;

/**
 * This class implements a group of principals.
 * @author      Satish Dharmaraj
 */
public class GroupImpl implements Group {
    private Vector<Principal> groupMembers = new Vector<Principal>(50, 100);
    private String group;

    /**
     * Constructs a Group object with no members.
     * @param groupName the name of the group
     */
    public GroupImpl(String groupName) {
        this.group = groupName;
    }

    /**
     * adds the specified member to the group.
     * @param user The principal to add to the group.
     * @return true if the member was added - false if the
     * member could not be added.
     */
    public boolean addMember(Principal user) {
        if (groupMembers.contains(user))
          return false;

        // do not allow groups to be added to itself.
        if (group.equals(user.toString()))
            throw new IllegalArgumentException();

        groupMembers.addElement(user);
        return true;
    }

    /**
     * removes the specified member from the group.
     * @param user The principal to remove from the group.
     * @param true if the principal was removed false if
     * the principal was not a member
     */
    public boolean removeMember(Principal user) {
        return groupMembers.removeElement(user);
    }

    /**
     * returns the enumeration of the members in the group.
     */
    public Enumeration<? extends Principal> members() {
        return groupMembers.elements();
    }

    /**
     * This function returns true if the group passed matches
     * the group represented in this interface.
     * @param another The group to compare this group to.
     */
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Group == false) {
            return false;
        }
        Group another = (Group)obj;
        return group.equals(another.toString());
    }

    // equals(Group) for compatibility
    public boolean equals(Group another) {
        return equals((Object)another);
    }

    /**
     * Prints a stringified version of the group.
     */
    public String toString() {
        return group;
    }

    /**
     * return a hashcode for the principal.
     */
    public int hashCode() {
        return group.hashCode();
    }

    /**
     * returns true if the passed principal is a member of the group.
     * @param member The principal whose membership must be checked for.
     * @return true if the principal is a member of this group,
     * false otherwise
     */
    public boolean isMember(Principal member) {

        //
        // if the member is part of the group (common case), return true.
        // if not, recursively search depth first in the group looking for the
        // principal.
        //
        if (groupMembers.contains(member)) {
            return true;
        } else {
            Vector<Group> alreadySeen = new Vector<Group>(10);
            return isMemberRecurse(member, alreadySeen);
        }
    }

    /**
     * return the name of the principal.
     */
    public String getName() {
        return group;
    }

    //
    // This function is the recursive search of groups for this
    // implementation of the Group. The search proceeds building up
    // a vector of already seen groups. Only new groups are considered,
    // thereby avoiding loops.
    //
    boolean isMemberRecurse(Principal member, Vector<Group> alreadySeen) {
        Enumeration<? extends Principal> e = members();
        while (e.hasMoreElements()) {
            boolean mem = false;
            Principal p = (Principal) e.nextElement();

            // if the member is in this collection, return true
            if (p.equals(member)) {
                return true;
            } else if (p instanceof GroupImpl) {
                //
                // if not recurse if the group has not been checked already.
                // Can call method in this package only if the object is an
                // instance of this class. Otherwise call the method defined
                // in the interface. (This can lead to a loop if a mixture of
                // implementations form a loop, but we live with this improbable
                // case rather than clutter the interface by forcing the
                // implementation of this method.)
                //
                GroupImpl g = (GroupImpl) p;
                alreadySeen.addElement(this);
                if (!alreadySeen.contains(g))
                  mem =  g.isMemberRecurse(member, alreadySeen);
            } else if (p instanceof Group) {
                Group g = (Group) p;
                if (!alreadySeen.contains(g))
                  mem = g.isMember(member);
            }

            if (mem)
              return mem;
        }
        return false;
    }
}
