/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
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
 * <p> This class implements the <code>Principal</code> interface
 * and represents information about a Windows NT user, group or realm.
 *
 * <p> Windows NT chooses to represent users, groups and realms (or domains)
 * with not only common names, but also relatively unique numbers.  These
 * numbers are called Security IDentifiers, or SIDs.  Windows NT
 * also provides services that render these SIDs into string forms.
 * This class represents these string forms.
 *
 * <p> Principals such as this <code>NTSid</code>
 * may be associated with a particular <code>Subject</code>
 * to augment that <code>Subject</code> with an additional
 * identity.  Refer to the <code>Subject</code> class for more information
 * on how to achieve this.  Authorization decisions can then be based upon
 * the Principals associated with a <code>Subject</code>.
 *
 * @see java.security.Principal
 * @see javax.security.auth.Subject
 */
public class NTSid implements Principal, java.io.Serializable {

    private static final long serialVersionUID = 4412290580770249885L;

    /**
     * @serial
     */
    private String sid;

    /**
     * Create an <code>NTSid</code> with a Windows NT SID.
     *
     * <p>
     *
     * @param stringSid the Windows NT SID. <p>
     *
     * @exception NullPointerException if the <code>String</code>
     *                  is <code>null</code>.
     *
     * @exception IllegalArgumentException if the <code>String</code>
     *                  has zero length.
     */
    public NTSid (String stringSid) {
        if (stringSid == null) {
            java.text.MessageFormat form = new java.text.MessageFormat
                (sun.security.util.ResourcesMgr.getString
                        ("invalid.null.input.value",
                        "sun.security.util.AuthResources"));
            Object[] source = {"stringSid"};
            throw new NullPointerException(form.format(source));
        }
        if (stringSid.length() == 0) {
            throw new IllegalArgumentException
                (sun.security.util.ResourcesMgr.getString
                        ("Invalid.NTSid.value",
                        "sun.security.util.AuthResources"));
        }
        sid = new String(stringSid);
    }

    /**
     * Return a string version of this <code>NTSid</code>.
     *
     * <p>
     *
     * @return a string version of this <code>NTSid</code>
     */
    public String getName() {
        return sid;
    }

    /**
     * Return a string representation of this <code>NTSid</code>.
     *
     * <p>
     *
     * @return a string representation of this <code>NTSid</code>.
     */
    public String toString() {
        java.text.MessageFormat form = new java.text.MessageFormat
                (sun.security.util.ResourcesMgr.getString
                        ("NTSid.name",
                        "sun.security.util.AuthResources"));
        Object[] source = {sid};
        return form.format(source);
    }

    /**
     * Compares the specified Object with this <code>NTSid</code>
     * for equality.  Returns true if the given object is also a
     * <code>NTSid</code> and the two NTSids have the same String
     * representation.
     *
     * <p>
     *
     * @param o Object to be compared for equality with this
     *          <code>NTSid</code>.
     *
     * @return true if the specified Object is equal to this
     *          <code>NTSid</code>.
     */
    public boolean equals(Object o) {
        if (o == null)
            return false;

        if (this == o)
            return true;

        if (!(o instanceof NTSid))
            return false;
        NTSid that = (NTSid)o;

        if (sid.equals(that.sid)) {
            return true;
        }
        return false;
    }

    /**
     * Return a hash code for this <code>NTSid</code>.
     *
     * <p>
     *
     * @return a hash code for this <code>NTSid</code>.
     */
    public int hashCode() {
        return sid.hashCode();
    }
}
