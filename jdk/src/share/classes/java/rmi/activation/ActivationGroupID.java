/*
 * Copyright (c) 1997, 1999, Oracle and/or its affiliates. All rights reserved.
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

package java.rmi.activation;

import java.rmi.server.UID;

/**
 * The identifier for a registered activation group serves several
 * purposes: <ul>
 * <li>identifies the group uniquely within the activation system, and
 * <li>contains a reference to the group's activation system so that the
 * group can contact its activation system when necessary.</ul><p>
 *
 * The <code>ActivationGroupID</code> is returned from the call to
 * <code>ActivationSystem.registerGroup</code> and is used to identify
 * the group within the activation system. This group id is passed
 * as one of the arguments to the activation group's special constructor
 * when an activation group is created/recreated.
 *
 * @author      Ann Wollrath
 * @see         ActivationGroup
 * @see         ActivationGroupDesc
 * @since       1.2
 */
public class ActivationGroupID implements java.io.Serializable {
    /**
     * @serial The group's activation system.
     */
    private ActivationSystem system;

    /**
     * @serial The group's unique id.
     */
    private UID uid = new UID();

    /** indicate compatibility with the Java 2 SDK v1.2 version of class */
    private  static final long serialVersionUID = -1648432278909740833L;

    /**
     * Constructs a unique group id.
     *
     * @param system the group's activation system
     * @since 1.2
     */
    public ActivationGroupID(ActivationSystem system) {
        this.system = system;
    }

    /**
     * Returns the group's activation system.
     * @return the group's activation system
     * @since 1.2
     */
    public ActivationSystem getSystem() {
        return system;
    }

    /**
     * Returns a hashcode for the group's identifier.  Two group
     * identifiers that refer to the same remote group will have the
     * same hash code.
     *
     * @see java.util.Hashtable
     * @since 1.2
     */
    public int hashCode() {
        return uid.hashCode();
    }

    /**
     * Compares two group identifiers for content equality.
     * Returns true if both of the following conditions are true:
     * 1) the unique identifiers are equivalent (by content), and
     * 2) the activation system specified in each
     *    refers to the same remote object.
     *
     * @param   obj     the Object to compare with
     * @return  true if these Objects are equal; false otherwise.
     * @see             java.util.Hashtable
     * @since 1.2
     */
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj instanceof ActivationGroupID) {
            ActivationGroupID id = (ActivationGroupID)obj;
            return (uid.equals(id.uid) && system.equals(id.system));
        } else {
            return false;
        }
    }
}
