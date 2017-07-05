/*
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.provider.certpath;

import java.security.AlgorithmConstraints;
import java.security.CryptoPrimitive;
import java.security.Timestamp;
import java.security.cert.CertPathValidator;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Set;
import java.util.EnumSet;
import java.math.BigInteger;
import java.security.PublicKey;
import java.security.KeyFactory;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.cert.Certificate;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.security.cert.PKIXCertPathChecker;
import java.security.cert.TrustAnchor;
import java.security.cert.CRLException;
import java.security.cert.CertificateException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertPathValidatorException.BasicReason;
import java.security.cert.PKIXReason;
import java.security.interfaces.DSAParams;
import java.security.interfaces.DSAPublicKey;
import java.security.spec.DSAPublicKeySpec;

import sun.security.util.AnchorCertificates;
import sun.security.util.ConstraintsParameters;
import sun.security.util.Debug;
import sun.security.util.DisabledAlgorithmConstraints;
import sun.security.validator.Validator;
import sun.security.x509.X509CertImpl;
import sun.security.x509.X509CRLImpl;
import sun.security.x509.AlgorithmId;

/**
 * A {@code PKIXCertPathChecker} implementation to check whether a
 * specified certificate contains the required algorithm constraints.
 * <p>
 * Certificate fields such as the subject public key, the signature
 * algorithm, key usage, extended key usage, etc. need to conform to
 * the specified algorithm constraints.
 *
 * @see PKIXCertPathChecker
 * @see PKIXParameters
 */
public final class AlgorithmChecker extends PKIXCertPathChecker {
    private static final Debug debug = Debug.getInstance("certpath");

    private final AlgorithmConstraints constraints;
    private final PublicKey trustedPubKey;
    private final Date pkixdate;
    private PublicKey prevPubKey;
    private final Timestamp jarTimestamp;
    private final String variant;

    private static final Set<CryptoPrimitive> SIGNATURE_PRIMITIVE_SET =
        Collections.unmodifiableSet(EnumSet.of(CryptoPrimitive.SIGNATURE));

    private static final Set<CryptoPrimitive> KU_PRIMITIVE_SET =
        Collections.unmodifiableSet(EnumSet.of(
            CryptoPrimitive.SIGNATURE,
            CryptoPrimitive.KEY_ENCAPSULATION,
            CryptoPrimitive.PUBLIC_KEY_ENCRYPTION,
            CryptoPrimitive.KEY_AGREEMENT));

    private static final DisabledAlgorithmConstraints
        certPathDefaultConstraints = new DisabledAlgorithmConstraints(
            DisabledAlgorithmConstraints.PROPERTY_CERTPATH_DISABLED_ALGS);

    // If there is no "cacerts" keyword, then disable anchor checking
    private static final boolean publicCALimits =
            certPathDefaultConstraints.checkProperty("jdkCA");

    // If anchor checking enabled, this will be true if the trust anchor
    // has a match in the cacerts file
    private boolean trustedMatch = false;

    /**
     * Create a new {@code AlgorithmChecker} with the given algorithm
     * given {@code TrustAnchor} and {@code String} variant.
     *
     * @param anchor the trust anchor selected to validate the target
     *     certificate
     * @param variant is the Validator variants of the operation. A null value
     *                passed will set it to Validator.GENERIC.
     */
    public AlgorithmChecker(TrustAnchor anchor, String variant) {
        this(anchor, certPathDefaultConstraints, null, null, variant);
    }

    /**
     * Create a new {@code AlgorithmChecker} with the given
     * {@code AlgorithmConstraints}, {@code Timestamp}, and {@code String}
     * variant.
     *
     * Note that this constructor can initialize a variation of situations where
     * the AlgorithmConstraints, Timestamp, or Variant maybe known.
     *
     * @param constraints the algorithm constraints (or null)
     * @param jarTimestamp Timestamp passed for JAR timestamp constraint
     *                     checking. Set to null if not applicable.
     * @param variant is the Validator variants of the operation. A null value
     *                passed will set it to Validator.GENERIC.
     */
    public AlgorithmChecker(AlgorithmConstraints constraints,
            Timestamp jarTimestamp, String variant) {
        this(null, constraints, null, jarTimestamp, variant);
    }

    /**
     * Create a new {@code AlgorithmChecker} with the
     * given {@code TrustAnchor}, {@code AlgorithmConstraints},
     * {@code Timestamp}, and {@code String} variant.
     *
     * @param anchor the trust anchor selected to validate the target
     *     certificate
     * @param constraints the algorithm constraints (or null)
     * @param pkixdate The date specified by the PKIXParameters date.  If the
     *                 PKIXParameters is null, the current date is used.  This
     *                 should be null when jar files are being checked.
     * @param jarTimestamp Timestamp passed for JAR timestamp constraint
     *                     checking. Set to null if not applicable.
     * @param variant is the Validator variants of the operation. A null value
     *                passed will set it to Validator.GENERIC.
     */
    public AlgorithmChecker(TrustAnchor anchor,
            AlgorithmConstraints constraints, Date pkixdate,
            Timestamp jarTimestamp, String variant) {

        if (anchor != null) {
            if (anchor.getTrustedCert() != null) {
                this.trustedPubKey = anchor.getTrustedCert().getPublicKey();
                // Check for anchor certificate restrictions
                trustedMatch = checkFingerprint(anchor.getTrustedCert());
                if (trustedMatch && debug != null) {
                    debug.println("trustedMatch = true");
                }
            } else {
                this.trustedPubKey = anchor.getCAPublicKey();
            }
        } else {
            this.trustedPubKey = null;
            if (debug != null) {
                debug.println("TrustAnchor is null, trustedMatch is false.");
            }
        }

        this.prevPubKey = this.trustedPubKey;
        this.constraints = (constraints == null ? certPathDefaultConstraints :
                constraints);
        // If we are checking jar files, set pkixdate the same as the timestamp
        // for certificate checking
        this.pkixdate = (jarTimestamp != null ? jarTimestamp.getTimestamp() :
                pkixdate);
        this.jarTimestamp = jarTimestamp;
        this.variant = (variant == null ? Validator.VAR_GENERIC : variant);
    }

    /**
     * Create a new {@code AlgorithmChecker} with the given {@code TrustAnchor},
     * {@code PKIXParameter} date, and {@code varient}
     *
     * @param anchor the trust anchor selected to validate the target
     *     certificate
     * @param pkixdate Date the constraints are checked against. The value is
     *             either the PKIXParameters date or null for the current date.
     * @param variant is the Validator variants of the operation. A null value
     *                passed will set it to Validator.GENERIC.
     */
    public AlgorithmChecker(TrustAnchor anchor, Date pkixdate, String variant) {
        this(anchor, certPathDefaultConstraints, pkixdate, null, variant);
    }

    // Check this 'cert' for restrictions in the AnchorCertificates
    // trusted certificates list
    private static boolean checkFingerprint(X509Certificate cert) {
        if (!publicCALimits) {
            return false;
        }

        if (debug != null) {
            debug.println("AlgorithmChecker.contains: " + cert.getSigAlgName());
        }
        return AnchorCertificates.contains(cert);
    }

    @Override
    public void init(boolean forward) throws CertPathValidatorException {
        //  Note that this class does not support forward mode.
        if (!forward) {
            if (trustedPubKey != null) {
                prevPubKey = trustedPubKey;
            } else {
                prevPubKey = null;
            }
        } else {
            throw new
                CertPathValidatorException("forward checking not supported");
        }
    }

    @Override
    public boolean isForwardCheckingSupported() {
        //  Note that as this class does not support forward mode, the method
        //  will always returns false.
        return false;
    }

    @Override
    public Set<String> getSupportedExtensions() {
        return null;
    }

    @Override
    public void check(Certificate cert,
            Collection<String> unresolvedCritExts)
            throws CertPathValidatorException {

        if (!(cert instanceof X509Certificate) || constraints == null) {
            // ignore the check for non-x.509 certificate or null constraints
            return;
        }

        // check the key usage and key size
        boolean[] keyUsage = ((X509Certificate) cert).getKeyUsage();
        if (keyUsage != null && keyUsage.length < 9) {
            throw new CertPathValidatorException(
                "incorrect KeyUsage extension",
                null, null, -1, PKIXReason.INVALID_KEY_USAGE);
        }

        X509CertImpl x509Cert;
        AlgorithmId algorithmId;
        try {
            x509Cert = X509CertImpl.toImpl((X509Certificate)cert);
            algorithmId = (AlgorithmId)x509Cert.get(X509CertImpl.SIG_ALG);
        } catch (CertificateException ce) {
            throw new CertPathValidatorException(ce);
        }

        AlgorithmParameters currSigAlgParams = algorithmId.getParameters();
        PublicKey currPubKey = cert.getPublicKey();
        String currSigAlg = ((X509Certificate)cert).getSigAlgName();

        // Check the signature algorithm and parameters against constraints.
        if (!constraints.permits(SIGNATURE_PRIMITIVE_SET, currSigAlg,
                currSigAlgParams)) {
            throw new CertPathValidatorException(
                    "Algorithm constraints check failed on signature " +
                            "algorithm: " + currSigAlg, null, null, -1,
                    BasicReason.ALGORITHM_CONSTRAINED);
        }

        // Assume all key usage bits are set if key usage is not present
        Set<CryptoPrimitive> primitives = KU_PRIMITIVE_SET;

        if (keyUsage != null) {
                primitives = EnumSet.noneOf(CryptoPrimitive.class);

            if (keyUsage[0] || keyUsage[1] || keyUsage[5] || keyUsage[6]) {
                // keyUsage[0]: KeyUsage.digitalSignature
                // keyUsage[1]: KeyUsage.nonRepudiation
                // keyUsage[5]: KeyUsage.keyCertSign
                // keyUsage[6]: KeyUsage.cRLSign
                primitives.add(CryptoPrimitive.SIGNATURE);
            }

            if (keyUsage[2]) {      // KeyUsage.keyEncipherment
                primitives.add(CryptoPrimitive.KEY_ENCAPSULATION);
            }

            if (keyUsage[3]) {      // KeyUsage.dataEncipherment
                primitives.add(CryptoPrimitive.PUBLIC_KEY_ENCRYPTION);
            }

            if (keyUsage[4]) {      // KeyUsage.keyAgreement
                primitives.add(CryptoPrimitive.KEY_AGREEMENT);
            }

            // KeyUsage.encipherOnly and KeyUsage.decipherOnly are
            // undefined in the absence of the keyAgreement bit.

            if (primitives.isEmpty()) {
                throw new CertPathValidatorException(
                    "incorrect KeyUsage extension bits",
                    null, null, -1, PKIXReason.INVALID_KEY_USAGE);
            }
        }

        ConstraintsParameters cp =
                new ConstraintsParameters((X509Certificate)cert,
                        trustedMatch, pkixdate, jarTimestamp, variant);

        // Check against local constraints if it is DisabledAlgorithmConstraints
        if (constraints instanceof DisabledAlgorithmConstraints) {
            ((DisabledAlgorithmConstraints)constraints).permits(currSigAlg, cp);
            // DisabledAlgorithmsConstraints does not check primitives, so key
            // additional key check.

        } else {
            // Perform the default constraints checking anyway.
            certPathDefaultConstraints.permits(currSigAlg, cp);
            // Call locally set constraints to check key with primitives.
            if (!constraints.permits(primitives, currPubKey)) {
                throw new CertPathValidatorException(
                        "Algorithm constraints check failed on key " +
                                currPubKey.getAlgorithm() + " with size of " +
                                sun.security.util.KeyUtil.getKeySize(currPubKey) +
                                "bits",
                        null, null, -1, BasicReason.ALGORITHM_CONSTRAINED);
            }
        }

        // If there is no previous key, set one and exit
        if (prevPubKey == null) {
            prevPubKey = currPubKey;
            return;
        }

        // Check with previous cert for signature algorithm and public key
        if (!constraints.permits(
                SIGNATURE_PRIMITIVE_SET,
                currSigAlg, prevPubKey, currSigAlgParams)) {
            throw new CertPathValidatorException(
                    "Algorithm constraints check failed on " +
                            "signature algorithm: " + currSigAlg,
                    null, null, -1, BasicReason.ALGORITHM_CONSTRAINED);
        }

        // Inherit key parameters from previous key
        if (PKIX.isDSAPublicKeyWithoutParams(currPubKey)) {
            // Inherit DSA parameters from previous key
            if (!(prevPubKey instanceof DSAPublicKey)) {
                throw new CertPathValidatorException("Input key is not " +
                        "of a appropriate type for inheriting parameters");
            }

            DSAParams params = ((DSAPublicKey)prevPubKey).getParams();
            if (params == null) {
                throw new CertPathValidatorException(
                        "Key parameters missing from public key.");
            }

            try {
                BigInteger y = ((DSAPublicKey)currPubKey).getY();
                KeyFactory kf = KeyFactory.getInstance("DSA");
                DSAPublicKeySpec ks = new DSAPublicKeySpec(y, params.getP(),
                        params.getQ(), params.getG());
                currPubKey = kf.generatePublic(ks);
            } catch (GeneralSecurityException e) {
                throw new CertPathValidatorException("Unable to generate " +
                        "key with inherited parameters: " + e.getMessage(), e);
            }
        }

        // reset the previous public key
        prevPubKey = currPubKey;
    }

    /**
     * Try to set the trust anchor of the checker.
     * <p>
     * If there is no trust anchor specified and the checker has not started,
     * set the trust anchor.
     *
     * @param anchor the trust anchor selected to validate the target
     *     certificate
     */
    void trySetTrustAnchor(TrustAnchor anchor) {
        // Don't bother if the check has started or trust anchor has already
        // specified.
        if (prevPubKey == null) {
            if (anchor == null) {
                throw new IllegalArgumentException(
                        "The trust anchor cannot be null");
            }

            // Don't bother to change the trustedPubKey.
            if (anchor.getTrustedCert() != null) {
                prevPubKey = anchor.getTrustedCert().getPublicKey();
                // Check for anchor certificate restrictions
                trustedMatch = checkFingerprint(anchor.getTrustedCert());
                if (trustedMatch && debug != null) {
                    debug.println("trustedMatch = true");
                }
            } else {
                prevPubKey = anchor.getCAPublicKey();
            }
        }
    }

    /**
     * Check the signature algorithm with the specified public key.
     *
     * @param key the public key to verify the CRL signature
     * @param crl the target CRL
     * @param variant is the Validator variants of the operation. A null value
     *                passed will set it to Validator.GENERIC.
     */
    static void check(PublicKey key, X509CRL crl, String variant)
                        throws CertPathValidatorException {

        X509CRLImpl x509CRLImpl = null;
        try {
            x509CRLImpl = X509CRLImpl.toImpl(crl);
        } catch (CRLException ce) {
            throw new CertPathValidatorException(ce);
        }

        AlgorithmId algorithmId = x509CRLImpl.getSigAlgId();
        check(key, algorithmId, variant);
    }

    /**
     * Check the signature algorithm with the specified public key.
     *
     * @param key the public key to verify the CRL signature
     * @param algorithmId signature algorithm Algorithm ID
     * @param variant is the Validator variants of the operation. A null value
     *                passed will set it to Validator.GENERIC.
     */
    static void check(PublicKey key, AlgorithmId algorithmId, String variant)
                        throws CertPathValidatorException {
        String sigAlgName = algorithmId.getName();
        AlgorithmParameters sigAlgParams = algorithmId.getParameters();

        certPathDefaultConstraints.permits(new ConstraintsParameters(
                sigAlgName, sigAlgParams, key, variant));
    }
}

