/*
 * Copyright 2000-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.util.*;
import java.security.cert.*;
import java.security.cert.PKIXReason;

import sun.security.util.Debug;
import sun.security.x509.PKIXExtensions;

/**
 * KeyChecker is a <code>PKIXCertPathChecker</code> that checks that the
 * keyCertSign bit is set in the keyUsage extension in an intermediate CA
 * certificate. It also checks whether the final certificate in a
 * certification path meets the specified target constraints specified as
 * a CertSelector in the PKIXParameters passed to the CertPathValidator.
 *
 * @since       1.4
 * @author      Yassir Elley
 */
class KeyChecker extends PKIXCertPathChecker {

    private static final Debug debug = Debug.getInstance("certpath");
    // the index of keyCertSign in the boolean KeyUsage array
    private static final int keyCertSign = 5;
    private final int certPathLen;
    private CertSelector targetConstraints;
    private int remainingCerts;

    private Set<String> supportedExts;

    /**
     * Default Constructor
     *
     * @param certPathLen allowable cert path length
     * @param targetCertSel a CertSelector object specifying the constraints
     * on the target certificate
     */
    KeyChecker(int certPathLen, CertSelector targetCertSel)
        throws CertPathValidatorException
    {
        this.certPathLen = certPathLen;
        this.targetConstraints = targetCertSel;
        init(false);
    }

    /**
     * Initializes the internal state of the checker from parameters
     * specified in the constructor
     */
    public void init(boolean forward) throws CertPathValidatorException {
        if (!forward) {
            remainingCerts = certPathLen;
        } else {
            throw new CertPathValidatorException
                ("forward checking not supported");
        }
    }

    public final boolean isForwardCheckingSupported() {
        return false;
    }

    public Set<String> getSupportedExtensions() {
        if (supportedExts == null) {
            supportedExts = new HashSet<String>();
            supportedExts.add(PKIXExtensions.KeyUsage_Id.toString());
            supportedExts.add(PKIXExtensions.ExtendedKeyUsage_Id.toString());
            supportedExts.add(PKIXExtensions.SubjectAlternativeName_Id.toString());
            supportedExts = Collections.unmodifiableSet(supportedExts);
        }
        return supportedExts;
    }

    /**
     * Checks that keyUsage and target constraints are satisfied by
     * the specified certificate.
     *
     * @param cert the Certificate
     * @param unresolvedCritExts the unresolved critical extensions
     * @exception CertPathValidatorException Exception thrown if certificate
     * does not verify
     */
    public void check(Certificate cert, Collection<String> unresCritExts)
        throws CertPathValidatorException
    {
        X509Certificate currCert = (X509Certificate) cert;

        remainingCerts--;

        // if final certificate, check that target constraints are satisfied
        if (remainingCerts == 0) {
            if ((targetConstraints != null) &&
                (targetConstraints.match(currCert) == false)) {
                throw new CertPathValidatorException("target certificate " +
                    "constraints check failed");
            }
        } else {
            // otherwise, verify that keyCertSign bit is set in CA certificate
            verifyCAKeyUsage(currCert);
        }

        // remove the extensions that we have checked
        if (unresCritExts != null && !unresCritExts.isEmpty()) {
            unresCritExts.remove(PKIXExtensions.KeyUsage_Id.toString());
            unresCritExts.remove(PKIXExtensions.ExtendedKeyUsage_Id.toString());
            unresCritExts.remove(
                PKIXExtensions.SubjectAlternativeName_Id.toString());
        }
    }

    /**
     * Static method to verify that the key usage and extended key usage
     * extension in a CA cert. The key usage extension, if present, must
     * assert the keyCertSign bit. The extended key usage extension, if
     * present, must include anyExtendedKeyUsage.
     */
    static void verifyCAKeyUsage(X509Certificate cert)
            throws CertPathValidatorException {
        String msg = "CA key usage";
        if (debug != null) {
            debug.println("KeyChecker.verifyCAKeyUsage() ---checking " + msg
                + "...");
        }

        boolean[] keyUsageBits = cert.getKeyUsage();

        // getKeyUsage returns null if the KeyUsage extension is not present
        // in the certificate - in which case there is nothing to check
        if (keyUsageBits == null) {
            return;
        }

        // throw an exception if the keyCertSign bit is not set
        if (!keyUsageBits[keyCertSign]) {
            throw new CertPathValidatorException
                (msg + " check failed: keyCertSign bit is not set", null,
                 null, -1, PKIXReason.INVALID_KEY_USAGE);
        }

        if (debug != null) {
            debug.println("KeyChecker.verifyCAKeyUsage() " + msg
                + " verified.");
        }
    }
}
