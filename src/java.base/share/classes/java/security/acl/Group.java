/*
 * Copyright (c) 1996, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * This interface is used to represent a group of principals. (A principal
 * represents an entity such as an individual user or a company). <p>
 *
 * Note that Group extends Principal. Thus, either a Principal or a Group can
 * be passed as an argument to methods containing a Principal parameter. For
 * example, you can add either a Principal or a Group to a Group object by
 * calling the object's {@code addMember} method, passing it the
 * Principal or Group.
 *
 * @author      Satish Dharmaraj
 * @since 1.1
 *
 * @deprecated This class is deprecated and subject to removal in a future
 *     version of Java SE. It has been replaced by {@code java.security.Policy}
 *     and related classes since 1.2.
 */
@Deprecated(since="9", forRemoval=true)
public interface Group extends Principal {

    /**
     * Adds the specified member to the group.
     *
     * @param user the principal to add to this group.
     *
     * @return true if the member was successfully added,
     * false if the principal was already a member.
     */
    public boolean addMember(Principal user);

    /**
     * Removes the specified member from the group.
     *
     * @param user the principal to remove from this group.
     *
     * @return true if the principal was removed, or
     * false if the principal was not a member.
     */
    public boolean removeMember(Principal user);

    /**
     * Returns true if the passed principal is a member of the group.
     * This method does a recursive search, so if a principal belongs to a
     * group which is a member of this group, true is returned.
     *
     * @param member the principal whose membership is to be checked.
     *
     * @return true if the principal is a member of this group,
     * false otherwise.
     */
    public boolean isMember(Principal member);


    /**
     * Returns an enumeration of the members in the group.
     * The returned objects can be instances of either Principal
     * or Group (which is a subclass of Principal).
     *
     * @return an enumeration of the group members.
     */
    public Enumeration<? extends Principal> members();

}
