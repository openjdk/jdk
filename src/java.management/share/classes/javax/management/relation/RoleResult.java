/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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

package javax.management.relation;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.io.Serializable;

/**
 * Represents the result of a multiple access to several roles of a relation
 * (either for reading or writing).
 *
 * <p>The <b>serialVersionUID</b> of this class is <code>-6304063118040985512L</code>.
 *
 * @since 1.5
 */
public class RoleResult implements Serializable {

    private static final long serialVersionUID = -6304063118040985512L;
    /**
     * @serialField roleList RoleList List of roles successfully accessed
     * @serialField unresolvedRoleList RoleUnresolvedList List of roles unsuccessfully accessed
     */
    private static final ObjectStreamField[] serialPersistentFields =
    {
      new ObjectStreamField("roleList", RoleList.class),
      new ObjectStreamField("unresolvedRoleList", RoleUnresolvedList.class)
    };

    //
    // Private members
    //

    /**
     * @serial List of roles successfully accessed
     */
    private RoleList roleList = null;

    /**
     * @serial List of roles unsuccessfully accessed
     */
    private RoleUnresolvedList unresolvedRoleList = null;

    //
    // Constructor
    //

    /**
     * Constructor.
     *
     * @param list  list of roles successfully accessed.
     * @param unresolvedList  list of roles not accessed (with problem
     * descriptions).
     */
    public RoleResult(RoleList list,
                      RoleUnresolvedList unresolvedList) {

        setRoles(list);
        setRolesUnresolved(unresolvedList);
    }

    //
    // Accessors
    //

    /**
     * Retrieves list of roles successfully accessed.
     *
     * @return a RoleList
     *
     * @see #setRoles
     */
    public RoleList getRoles() {
        return roleList;
    }

    /**
     * Retrieves list of roles unsuccessfully accessed.
     *
     * @return a RoleUnresolvedList.
     *
     * @see #setRolesUnresolved
     */
    public RoleUnresolvedList getRolesUnresolved() {
        return unresolvedRoleList;
    }

    /**
     * Sets list of roles successfully accessed.
     *
     * @param list  list of roles successfully accessed
     *
     * @see #getRoles
     */
    public void setRoles(RoleList list) {
        if (list != null) {

            roleList = new RoleList();

            for (Object o : list) {
                Role currRole = (Role)o;
                roleList.add((Role)currRole.clone());
            }
        } else {
            roleList = null;
        }
    }

    /**
     * Sets list of roles unsuccessfully accessed.
     *
     * @param unresolvedList  list of roles unsuccessfully accessed
     *
     * @see #getRolesUnresolved
     */
    public void setRolesUnresolved(RoleUnresolvedList unresolvedList) {
        if (unresolvedList != null) {

            unresolvedRoleList = new RoleUnresolvedList();

            for (Object o : unresolvedList) {
                RoleUnresolved currRoleUnres = (RoleUnresolved)o;
                unresolvedRoleList.add((RoleUnresolved)currRoleUnres.clone());
            }
        } else {
            unresolvedRoleList = null;
        }
    }

    /**
     * Deserializes a {@link RoleResult} from an {@link ObjectInputStream}.
     */
    private void readObject(ObjectInputStream in)
            throws IOException, ClassNotFoundException {
      in.defaultReadObject();
    }


    /**
     * Serializes a {@link RoleResult} to an {@link ObjectOutputStream}.
     */
    private void writeObject(ObjectOutputStream out)
            throws IOException {
      out.defaultWriteObject();
    }
}
