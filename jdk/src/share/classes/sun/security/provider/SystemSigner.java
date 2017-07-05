/*
 * Copyright 1996-2000 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.security.provider;

import java.util.*;
import java.security.*;

/**
 * SunSecurity signer. Like SystemIdentity, it has a trust bit, which
 * can be set by SunSecurity classes, and a set of accessors for other
 * classes in sun.security.*.
 *
 * @author Benjamin Renaud
 */

public class SystemSigner extends Signer {

    /** use serialVersionUID from JDK 1.1. for interoperability */
    private static final long serialVersionUID = -2127743304301557711L;

    /* Is this signer trusted */
    private boolean trusted = false;

    /**
     * Construct a signer with a given name.
     */
    public SystemSigner(String name) {
        super(name);
    }

    /**
     * Construct a signer with a name and a scope.
     *
     * @param name the signer's name.
     *
     * @param scope the scope for this signer.
     */
    public SystemSigner(String name, IdentityScope scope)
     throws KeyManagementException {

        super(name, scope);
    }

    /* Set the trust status of this signer */
    void setTrusted(boolean trusted) {
        this.trusted = trusted;
    }

    /**
     * Returns true if this signer is trusted.
     */
    public boolean isTrusted() {
        return trusted;
    }

    /* friendly callback for set keys */
    void setSignerKeyPair(KeyPair pair)
    throws InvalidParameterException, KeyException {
        setKeyPair(pair);
    }

    /* friendly callback for getting private keys */
    PrivateKey getSignerPrivateKey() {
        return getPrivateKey();
    }

    void setSignerInfo(String s) {
        setInfo(s);
    }

    /**
     * Call back method into a protected method for package friends.
     */
    void addSignerCertificate(Certificate cert) throws KeyManagementException {
        addCertificate(cert);
    }

    void clearCertificates() throws KeyManagementException {
        Certificate[] certs = certificates();
        for (int i = 0; i < certs.length; i++) {
            removeCertificate(certs[i]);
        }
    }

    public String toString() {
        String trustedString = "not trusted";
        if (trusted) {
            trustedString = "trusted";
        }
        return super.toString() + "[" + trustedString + "]";
    }
}
