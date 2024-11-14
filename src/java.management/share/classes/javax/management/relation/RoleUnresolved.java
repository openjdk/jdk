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

import static com.sun.jmx.mbeanserver.Util.cast;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.io.Serializable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.management.ObjectName;

/**
 * Represents an unresolved role: a role not retrieved from a relation due
 * to a problem. It provides the role name, value (if problem when trying to
 * set the role) and an integer defining the problem (constants defined in
 * RoleStatus).
 *
 * <p>The <b>serialVersionUID</b> of this class is <code>-48350262537070138L</code>.
 *
 * @since 1.5
 */
public class RoleUnresolved implements Serializable {

    private static final long serialVersionUID = -48350262537070138L;
    /** @serialField roleName String Role name
     *  @serialField roleValue List Role value ({@link List} of {@link ObjectName} objects)
     *  @serialField problemType int Problem type
     */
    private static final ObjectStreamField[] serialPersistentFields =
    {
      new ObjectStreamField("roleName", String.class),
      new ObjectStreamField("roleValue", List.class),
      new ObjectStreamField("problemType", int.class)
    };

    //
    // Private members
    //

    /**
     * @serial Role name
     */
    private String roleName = null;

    /**
     * @serial Role value ({@link List} of {@link ObjectName} objects)
     */
    private List<ObjectName> roleValue = null;

    /**
     * @serial Problem type
     */
    private int problemType;

    //
    // Constructor
    //

    /**
     * Constructor.
     *
     * @param name  name of the role
     * @param value  value of the role (if problem when setting the
     * role)
     * @param pbType  type of problem (according to known problem types,
     * listed as static final members).
     *
     * @exception IllegalArgumentException  if null parameter or incorrect
     * problem type
     */
    public RoleUnresolved(String name,
                          List<ObjectName> value,
                          int pbType)
        throws IllegalArgumentException {

        if (name == null) {
            String excMsg = "Invalid parameter.";
            throw new IllegalArgumentException(excMsg);
        }

        setRoleName(name);
        setRoleValue(value);
        // Can throw IllegalArgumentException
        setProblemType(pbType);
        return;
    }

    //
    // Accessors
    //

    /**
     * Retrieves role name.
     *
     * @return the role name.
     *
     * @see #setRoleName
     */
    public String getRoleName() {
        return roleName;
    }

    /**
     * Retrieves role value.
     *
     * @return an ArrayList of ObjectName objects, the one provided to be set
     * in given role. Null if the unresolved role is returned for a read
     * access.
     *
     * @see #setRoleValue
     */
    public List<ObjectName> getRoleValue() {
        return roleValue;
    }

    /**
     * Retrieves problem type.
     *
     * @return an integer corresponding to a problem, those being described as
     * static final members of current class.
     *
     * @see #setProblemType
     */
    public int getProblemType() {
        return problemType;
    }

    /**
     * Sets role name.
     *
     * @param name the new role name.
     *
     * @exception IllegalArgumentException  if null parameter
     *
     * @see #getRoleName
     */
    public void setRoleName(String name)
        throws IllegalArgumentException {

        if (name == null) {
            String excMsg = "Invalid parameter.";
            throw new IllegalArgumentException(excMsg);
        }

        roleName = name;
        return;
    }

    /**
     * Sets role value.
     *
     * @param value  List of ObjectName objects for referenced
     * MBeans not set in role.
     *
     * @see #getRoleValue
     */
    public void setRoleValue(List<ObjectName> value) {

        if (value != null) {
            roleValue = new ArrayList<>(value);
        } else {
            roleValue = null;
        }
        return;
    }

    /**
     * Sets problem type.
     *
     * @param pbType  integer corresponding to a problem. Must be one of
     * those described as static final members of current class.
     *
     * @exception IllegalArgumentException  if incorrect problem type
     *
     * @see #getProblemType
     */
    public void setProblemType(int pbType)
        throws IllegalArgumentException {

        if (!(RoleStatus.isRoleStatus(pbType))) {
            String excMsg = "Incorrect problem type.";
            throw new IllegalArgumentException(excMsg);
        }
        problemType = pbType;
        return;
    }

    /**
     * Clone this object.
     *
     * @return an independent clone.
     */
    public Object clone() {
        try {
            return new RoleUnresolved(roleName, roleValue, problemType);
        } catch (IllegalArgumentException exc) {
            return null; // :)
        }
    }

    /**
     * Return a string describing this object.
     *
     * @return a description of this RoleUnresolved object.
     */
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("role name: " + roleName);
        if (roleValue != null) {
            result.append("; value: ");
            for (Iterator<ObjectName> objNameIter = roleValue.iterator();
                 objNameIter.hasNext();) {
                ObjectName currObjName = objNameIter.next();
                result.append(currObjName.toString());
                if (objNameIter.hasNext()) {
                    result.append(", ");
                }
            }
        }
        result.append("; problem type: " + problemType);
        return result.toString();
    }

    /**
     * Deserializes a {@link RoleUnresolved} from an {@link ObjectInputStream}.
     */
    private void readObject(ObjectInputStream in)
            throws IOException, ClassNotFoundException {
      in.defaultReadObject();
    }


    /**
     * Serializes a {@link RoleUnresolved} to an {@link ObjectOutputStream}.
     */
    private void writeObject(ObjectOutputStream out)
            throws IOException {
      out.defaultWriteObject();
    }
}
