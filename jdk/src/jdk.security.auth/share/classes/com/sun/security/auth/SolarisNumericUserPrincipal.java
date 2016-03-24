/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.security.auth;

import java.security.Principal;

/**
 * This class implements the {@code Principal} interface
 * and represents a user's Solaris identification number (UID).
 *
 * <p> Principals such as this {@code SolarisNumericUserPrincipal}
 * may be associated with a particular {@code Subject}
 * to augment that {@code Subject} with an additional
 * identity.  Refer to the {@code Subject} class for more information
 * on how to achieve this.  Authorization decisions can then be based upon
 * the Principals associated with a {@code Subject}.
 * @deprecated As of JDK&nbsp;1.4, replaced by
 *             {@link UnixNumericUserPrincipal}.
 *             This class is entirely deprecated.
 *
 * @see java.security.Principal
 * @see javax.security.auth.Subject
 */
@Deprecated
public class SolarisNumericUserPrincipal implements
                                        Principal,
                                        java.io.Serializable {

    private static final long serialVersionUID = -3178578484679887104L;

    private static final java.util.ResourceBundle rb =
          java.security.AccessController.doPrivileged
          (new java.security.PrivilegedAction<java.util.ResourceBundle>() {
              public java.util.ResourceBundle run() {
                  return (java.util.ResourceBundle.getBundle
                                ("sun.security.util.AuthResources",
                                 sun.security.util.ResourcesMgr.class.getModule()));
              }
           });


    /**
     * @serial
     */
    private String name;

    /**
     * Create a {@code SolarisNumericUserPrincipal} using a
     * {@code String} representation of the
     * user's identification number (UID).
     *
     * @param name the user identification number (UID) for this user.
     *
     * @exception NullPointerException if the {@code name}
     *                  is {@code null}.
     */
    public SolarisNumericUserPrincipal(String name) {
        if (name == null)
            throw new NullPointerException(rb.getString("provided.null.name"));

        this.name = name;
    }

    /**
     * Create a {@code SolarisNumericUserPrincipal} using a
     * long representation of the user's identification number (UID).
     *
     * @param name the user identification number (UID) for this user
     *                  represented as a long.
     */
    public SolarisNumericUserPrincipal(long name) {
        this.name = Long.toString(name);
    }

    /**
     * Return the user identification number (UID) for this
     * {@code SolarisNumericUserPrincipal}.
     *
     * @return the user identification number (UID) for this
     *          {@code SolarisNumericUserPrincipal}
     */
    public String getName() {
        return name;
    }

    /**
     * Return the user identification number (UID) for this
     * {@code SolarisNumericUserPrincipal} as a long.
     *
     * @return the user identification number (UID) for this
     *          {@code SolarisNumericUserPrincipal} as a long.
     */
    public long longValue() {
        return Long.parseLong(name);
    }

    /**
     * Return a string representation of this
     * {@code SolarisNumericUserPrincipal}.
     *
     * @return a string representation of this
     *          {@code SolarisNumericUserPrincipal}.
     */
    public String toString() {
        return(rb.getString("SolarisNumericUserPrincipal.") + name);
    }

    /**
     * Compares the specified Object with this
     * {@code SolarisNumericUserPrincipal}
     * for equality.  Returns true if the given object is also a
     * {@code SolarisNumericUserPrincipal} and the two
     * SolarisNumericUserPrincipals
     * have the same user identification number (UID).
     *
     * @param o Object to be compared for equality with this
     *          {@code SolarisNumericUserPrincipal}.
     *
     * @return true if the specified Object is equal to this
     *          {@code SolarisNumericUserPrincipal}.
     */
    public boolean equals(Object o) {
        if (o == null)
            return false;

        if (this == o)
            return true;

        if (!(o instanceof SolarisNumericUserPrincipal))
            return false;
        SolarisNumericUserPrincipal that = (SolarisNumericUserPrincipal)o;

        if (this.getName().equals(that.getName()))
            return true;

       return false;
    }

    /**
     * Return a hash code for this {@code SolarisNumericUserPrincipal}.
     *
     * @return a hash code for this {@code SolarisNumericUserPrincipal}.
     */
    public int hashCode() {
        return name.hashCode();
    }
}
