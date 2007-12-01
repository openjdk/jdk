/*
 * Copyright 2000-2001 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.security.cert;

import java.security.PublicKey;

/**
 * This class represents the successful result of the PKIX certification
 * path validation algorithm.
 *
 * <p>Instances of <code>PKIXCertPathValidatorResult</code> are returned by the
 * {@link CertPathValidator#validate validate} method of
 * <code>CertPathValidator</code> objects implementing the PKIX algorithm.
 *
 * <p> All <code>PKIXCertPathValidatorResult</code> objects contain the
 * valid policy tree and subject public key resulting from the
 * validation algorithm, as well as a <code>TrustAnchor</code> describing
 * the certification authority (CA) that served as a trust anchor for the
 * certification path.
 * <p>
 * <b>Concurrent Access</b>
 * <p>
 * Unless otherwise specified, the methods defined in this class are not
 * thread-safe. Multiple threads that need to access a single
 * object concurrently should synchronize amongst themselves and
 * provide the necessary locking. Multiple threads each manipulating
 * separate objects need not synchronize.
 *
 * @see CertPathValidatorResult
 *
 * @since       1.4
 * @author      Yassir Elley
 * @author      Sean Mullan
 */
public class PKIXCertPathValidatorResult implements CertPathValidatorResult {

    private TrustAnchor trustAnchor;
    private PolicyNode policyTree;
    private PublicKey subjectPublicKey;

    /**
     * Creates an instance of <code>PKIXCertPathValidatorResult</code>
     * containing the specified parameters.
     *
     * @param trustAnchor a <code>TrustAnchor</code> describing the CA that
     * served as a trust anchor for the certification path
     * @param policyTree the immutable valid policy tree, or <code>null</code>
     * if there are no valid policies
     * @param subjectPublicKey the public key of the subject
     * @throws NullPointerException if the <code>subjectPublicKey</code> or
     * <code>trustAnchor</code> parameters are <code>null</code>
     */
    public PKIXCertPathValidatorResult(TrustAnchor trustAnchor,
        PolicyNode policyTree, PublicKey subjectPublicKey)
    {
        if (subjectPublicKey == null)
            throw new NullPointerException("subjectPublicKey must be non-null");
        if (trustAnchor == null)
            throw new NullPointerException("trustAnchor must be non-null");
        this.trustAnchor = trustAnchor;
        this.policyTree = policyTree;
        this.subjectPublicKey = subjectPublicKey;
    }

    /**
     * Returns the <code>TrustAnchor</code> describing the CA that served
     * as a trust anchor for the certification path.
     *
     * @return the <code>TrustAnchor</code> (never <code>null</code>)
     */
    public TrustAnchor getTrustAnchor() {
        return trustAnchor;
    }

    /**
     * Returns the root node of the valid policy tree resulting from the
     * PKIX certification path validation algorithm. The
     * <code>PolicyNode</code> object that is returned and any objects that
     * it returns through public methods are immutable.
     *
     * <p>Most applications will not need to examine the valid policy tree.
     * They can achieve their policy processing goals by setting the
     * policy-related parameters in <code>PKIXParameters</code>. However, more
     * sophisticated applications, especially those that process policy
     * qualifiers, may need to traverse the valid policy tree using the
     * {@link PolicyNode#getParent PolicyNode.getParent} and
     * {@link PolicyNode#getChildren PolicyNode.getChildren} methods.
     *
     * @return the root node of the valid policy tree, or <code>null</code>
     * if there are no valid policies
     */
    public PolicyNode getPolicyTree() {
        return policyTree;
    }

    /**
     * Returns the public key of the subject (target) of the certification
     * path, including any inherited public key parameters if applicable.
     *
     * @return the public key of the subject (never <code>null</code>)
     */
    public PublicKey getPublicKey() {
        return subjectPublicKey;
    }

    /**
     * Returns a copy of this object.
     *
     * @return the copy
     */
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            /* Cannot happen */
            throw new InternalError(e.toString());
        }
    }

    /**
     * Return a printable representation of this
     * <code>PKIXCertPathValidatorResult</code>.
     *
     * @return a <code>String</code> describing the contents of this
     *         <code>PKIXCertPathValidatorResult</code>
     */
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("PKIXCertPathValidatorResult: [\n");
        sb.append("  Trust Anchor: " + trustAnchor.toString() + "\n");
        sb.append("  Policy Tree: " + String.valueOf(policyTree) + "\n");
        sb.append("  Subject Public Key: " + subjectPublicKey + "\n");
        sb.append("]");
        return sb.toString();
    }
}
