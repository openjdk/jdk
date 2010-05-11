/*
 * Copyright 2003-2010 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.security;

import java.io.Serializable;
import java.security.cert.CRL;
import java.security.cert.CertPath;
import sun.misc.JavaSecurityCodeSignerAccess;
import sun.misc.SharedSecrets;

/**
 * This class encapsulates information about a code signer.
 * It is immutable.
 *
 * @since 1.5
 * @author Vincent Ryan
 */

public final class CodeSigner implements Serializable {

    private static final long serialVersionUID = 6819288105193937581L;

    /**
     * The signer's certificate path.
     *
     * @serial
     */
    private CertPath signerCertPath;

    /*
     * The signature timestamp.
     *
     * @serial
     */
    private Timestamp timestamp;

    /*
     * Hash code for this code signer.
     */
    private transient int myhash = -1;

    /**
     * Constructs a CodeSigner object.
     *
     * @param signerCertPath The signer's certificate path.
     *                       It must not be <code>null</code>.
     * @param timestamp A signature timestamp.
     *                  If <code>null</code> then no timestamp was generated
     *                  for the signature.
     * @throws NullPointerException if <code>signerCertPath</code> is
     *                              <code>null</code>.
     */
    public CodeSigner(CertPath signerCertPath, Timestamp timestamp) {
        if (signerCertPath == null) {
            throw new NullPointerException();
        }
        this.signerCertPath = signerCertPath;
        this.timestamp = timestamp;
    }

    /**
     * Returns the signer's certificate path.
     *
     * @return A certificate path.
     */
    public CertPath getSignerCertPath() {
        return signerCertPath;
    }

    /**
     * Returns the signature timestamp.
     *
     * @return The timestamp or <code>null</code> if none is present.
     */
    public Timestamp getTimestamp() {
        return timestamp;
    }

    /**
     * Returns the hash code value for this code signer.
     * The hash code is generated using the signer's certificate path and the
     * timestamp, if present.
     *
     * @return a hash code value for this code signer.
     */
    public int hashCode() {
        if (myhash == -1) {
            if (timestamp == null) {
                myhash = signerCertPath.hashCode();
            } else {
                myhash = signerCertPath.hashCode() + timestamp.hashCode();
            }
        }
        return myhash;
    }

    /**
     * Tests for equality between the specified object and this
     * code signer. Two code signers are considered equal if their
     * signer certificate paths are equal and if their timestamps are equal,
     * if present in both.
     *
     * @param obj the object to test for equality with this object.
     *
     * @return true if the objects are considered equal, false otherwise.
     */
    public boolean equals(Object obj) {
        if (obj == null || (!(obj instanceof CodeSigner))) {
            return false;
        }
        CodeSigner that = (CodeSigner)obj;

        if (this == that) {
            return true;
        }
        Timestamp thatTimestamp = that.getTimestamp();
        if (timestamp == null) {
            if (thatTimestamp != null) {
                return false;
            }
        } else {
            if (thatTimestamp == null ||
                (! timestamp.equals(thatTimestamp))) {
                return false;
            }
        }
        return signerCertPath.equals(that.getSignerCertPath());
    }

    /**
     * Returns a string describing this code signer.
     *
     * @return A string comprising the signer's certificate and a timestamp,
     *         if present.
     */
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("(");
        sb.append("Signer: " + signerCertPath.getCertificates().get(0));
        if (timestamp != null) {
            sb.append("timestamp: " + timestamp);
        }
        sb.append(")");
        return sb.toString();
    }

    // A private attribute attached to this CodeSigner object. Can be accessed
    // through SharedSecrets.getJavaSecurityCodeSignerAccess().[g|s]etCRLs
    //
    // Currently called in SignatureFileVerifier.getSigners
    private transient CRL[] crls;

    /**
     * Sets the CRLs attached
     * @param crls, null to clear
     */
    void setCRLs(CRL[] crls) {
        this.crls = crls;
    }

    /**
     * Returns the CRLs attached
     * @return the crls, initially null
     */
    CRL[] getCRLs() {
        return crls;
    }

    // Set up JavaSecurityCodeSignerAccess in SharedSecrets
    static {
        SharedSecrets.setJavaSecurityCodeSignerAccess(
                new JavaSecurityCodeSignerAccess() {
            @Override
            public void setCRLs(CodeSigner signer, CRL[] crls) {
                signer.setCRLs(crls);
            }

            @Override
            public CRL[] getCRLs(CodeSigner signer) {
                return signer.getCRLs();
            }
        });
    }

}
