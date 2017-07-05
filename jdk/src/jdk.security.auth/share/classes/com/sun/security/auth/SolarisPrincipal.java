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
 * and represents a Solaris user.
 *
 * <p> Principals such as this {@code SolarisPrincipal}
 * may be associated with a particular {@code Subject}
 * to augment that {@code Subject} with an additional
 * identity.  Refer to the {@code Subject} class for more information
 * on how to achieve this.  Authorization decisions can then be based upon
 * the Principals associated with a {@code Subject}.
 *
 * @deprecated As of JDK&nbsp;1.4, replaced by
 *             {@link UnixPrincipal}.
 *             This class is entirely deprecated.
 * @see java.security.Principal
 * @see javax.security.auth.Subject
 */
@Deprecated
public class SolarisPrincipal implements Principal, java.io.Serializable {

    private static final long serialVersionUID = -7840670002439379038L;

    private static final java.util.ResourceBundle rb =
          java.security.AccessController.doPrivileged
          (new java.security.PrivilegedAction<java.util.ResourceBundle>() {
              public java.util.ResourceBundle run() {
                  return (java.util.ResourceBundle.getBundle
                                ("sun.security.util.AuthResources"));
              }
          });


    /**
     * @serial
     */
    private String name;

    /**
     * Create a SolarisPrincipal with a Solaris username.
     *
     * @param name the Unix username for this user.
     *
     * @exception NullPointerException if the {@code name}
     *                  is {@code null}.
     */
    public SolarisPrincipal(String name) {
        if (name == null)
            throw new NullPointerException(rb.getString("provided.null.name"));

        this.name = name;
    }

    /**
     * Return the Unix username for this {@code SolarisPrincipal}.
     *
     * @return the Unix username for this {@code SolarisPrincipal}
     */
    public String getName() {
        return name;
    }

    /**
     * Return a string representation of this {@code SolarisPrincipal}.
     *
     * @return a string representation of this {@code SolarisPrincipal}.
     */
    public String toString() {
        return(rb.getString("SolarisPrincipal.") + name);
    }

    /**
     * Compares the specified Object with this {@code SolarisPrincipal}
     * for equality.  Returns true if the given object is also a
     * {@code SolarisPrincipal} and the two SolarisPrincipals
     * have the same username.
     *
     * @param o Object to be compared for equality with this
     *          {@code SolarisPrincipal}.
     *
     * @return true if the specified Object is equal to this
     *          {@code SolarisPrincipal}.
     */
    public boolean equals(Object o) {
        if (o == null)
            return false;

        if (this == o)
            return true;

        if (!(o instanceof SolarisPrincipal))
            return false;
        SolarisPrincipal that = (SolarisPrincipal)o;

        if (this.getName().equals(that.getName()))
            return true;
        return false;
    }

    /**
     * Return a hash code for this {@code SolarisPrincipal}.
     *
     * @return a hash code for this {@code SolarisPrincipal}.
     */
    public int hashCode() {
        return name.hashCode();
    }
}
