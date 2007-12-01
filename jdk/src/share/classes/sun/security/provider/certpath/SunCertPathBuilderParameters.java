/*
 * Copyright 2000-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.security.provider.certpath;

import java.util.Set;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.*;

/**
 * This class specifies the set of parameters used as input for the Sun
 * certification path build algorithm. It is identical to PKIXBuilderParameters
 * with the addition of a <code>buildForward</code> parameter which allows
 * the caller to specify whether or not the path should be constructed in
 * the forward direction.
 *
 * The default for the <code>buildForward</code> parameter is
 * true, which means that the build algorithm should construct paths
 * from the target subject back to the trusted anchor.
 *
 * @since       1.4
 * @author      Sean Mullan
 * @author      Yassir Elley
 */
public class SunCertPathBuilderParameters extends PKIXBuilderParameters {

    private boolean buildForward = true;

    /**
     * Creates an instance of <code>SunCertPathBuilderParameters</code> with the
     * specified parameter values.
     *
     * @param trustAnchors a <code>Set</code> of <code>TrustAnchor</code>s
     * @param targetConstraints a <code>CertSelector</code> specifying the
     * constraints on the target certificate
     * @throws InvalidAlgorithmParameterException if the specified
     * <code>Set</code> is empty <code>(trustAnchors.isEmpty() == true)</code>
     * @throws NullPointerException if the specified <code>Set</code> is
     * <code>null</code>
     * @throws ClassCastException if any of the elements in the <code>Set</code>
     * are not of type <code>java.security.cert.TrustAnchor</code>
     */
    public SunCertPathBuilderParameters(Set<TrustAnchor> trustAnchors,
        CertSelector targetConstraints) throws InvalidAlgorithmParameterException
    {
        super(trustAnchors, targetConstraints);
        setBuildForward(true);
    }

    /**
     * Creates an instance of <code>SunCertPathBuilderParameters</code> that
     * uses the specified <code>KeyStore</code> to populate the set
     * of most-trusted CA certificates.
     *
     * @param keystore A keystore from which the set of most-trusted
     * CA certificates will be populated.
     * @param targetConstraints a <code>CertSelector</code> specifying the
     * constraints on the target certificate
     * @throws KeyStoreException if the keystore has not been initialized.
     * @throws InvalidAlgorithmParameterException if the keystore does
     * not contain at least one trusted certificate entry
     * @throws NullPointerException if the keystore is <code>null</code>
     */
    public SunCertPathBuilderParameters(KeyStore keystore,
        CertSelector targetConstraints)
        throws KeyStoreException, InvalidAlgorithmParameterException
    {
        super(keystore, targetConstraints);
        setBuildForward(true);
    }

    /**
     * Returns the value of the buildForward flag.
     *
     * @return the value of the buildForward flag
     */
    public boolean getBuildForward() {
        return this.buildForward;
    }

    /**
     * Sets the value of the buildForward flag. If true, paths
     * are built from the target subject to the trusted anchor.
     * If false, paths are built from the trusted anchor to the
     * target subject. The default value if not specified is true.
     *
     * @param buildForward the value of the buildForward flag
     */
    public void setBuildForward(boolean buildForward) {
        this.buildForward = buildForward;
    }

    /**
     * Returns a formatted string describing the parameters.
     *
     * @return a formatted string describing the parameters.
     */
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("[\n");
        sb.append(super.toString());
        sb.append("  Build Forward Flag: " + String.valueOf(buildForward) + "\n");
        sb.append("]\n");
        return sb.toString();
    }
}
