/*
 * Copyright (c) 2009, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.math.BigInteger;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.security.cert.CertPathValidatorException.BasicReason;
import java.security.interfaces.DSAParams;
import java.security.interfaces.DSAPublicKey;
import java.security.spec.DSAPublicKeySpec;
import java.util.*;

import sun.security.util.Debug;
import sun.security.util.DisabledAlgorithmConstraints;
import sun.security.util.KeyUtil;
import sun.security.validator.Validator;
import sun.security.x509.AlgorithmId;
import sun.security.x509.X509CertImpl;

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
    private final Date date;
    private final String variant;
    private PublicKey trustedPubKey;
    private PublicKey prevPubKey;
    private TrustAnchor anchor;

    private static final Set<CryptoPrimitive> SIGNATURE_PRIMITIVE_SET =
        Collections.unmodifiableSet(EnumSet.of(CryptoPrimitive.SIGNATURE));

    private static final Set<CryptoPrimitive> KU_PRIMITIVE_SET =
        Collections.unmodifiableSet(EnumSet.of(
            CryptoPrimitive.SIGNATURE,
            CryptoPrimitive.KEY_ENCAPSULATION,
            CryptoPrimitive.PUBLIC_KEY_ENCRYPTION,
            CryptoPrimitive.KEY_AGREEMENT));

    /**
     * Create a new {@code AlgorithmChecker} with the given
     * {@code TrustAnchor} and {@code String} variant.
     *
     * @param anchor the trust anchor selected to validate the target
     *     certificate
     * @param variant the Validator variant of the operation. A null value
     *                passed will set it to Validator.GENERIC.
     */
    public AlgorithmChecker(TrustAnchor anchor, String variant) {
        this(anchor, null, null, variant);
    }

    /**
     * Create a new {@code AlgorithmChecker} with the given
     * {@code AlgorithmConstraints} and {@code String} variant.
     *
     * Note that this constructor can initialize a variation of situations where
     * the AlgorithmConstraints or Variant maybe known.
     *
     * @param constraints the algorithm constraints (or null)
     * @param variant the Validator variant of the operation. A null value
     *                passed will set it to Validator.GENERIC.
     */
    public AlgorithmChecker(AlgorithmConstraints constraints, String variant) {
        this(null, constraints, null, variant);
    }

    /**
     * Create a new {@code AlgorithmChecker} with the
     * given {@code TrustAnchor}, {@code AlgorithmConstraints}, {@code Date},
     * and {@code String} variant.
     *
     * @param anchor the trust anchor selected to validate the target
     *     certificate
     * @param constraints the algorithm constraints (or null)
     * @param date the date specified by the PKIXParameters date, or the
     *             timestamp if JAR files are being validated and the
     *             JAR is timestamped. May be null if no timestamp or
     *             PKIXParameter date is set.
     * @param variant the Validator variant of the operation. A null value
     *                passed will set it to Validator.GENERIC.
     */
    public AlgorithmChecker(TrustAnchor anchor,
            AlgorithmConstraints constraints, Date date, String variant) {

        if (anchor != null) {
            setTrustAnchorAndKeys(anchor);
        }

        this.constraints = constraints == null ?
            DisabledAlgorithmConstraints.certPathConstraints() : constraints;
        this.date = date;
        this.variant = (variant == null ? Validator.VAR_GENERIC : variant);
    }

    /**
     * Create a new {@code AlgorithmChecker} with the given {@code TrustAnchor},
     * {@code PKIXParameter} date, and {@code variant}.
     *
     * @param anchor the trust anchor selected to validate the target
     *     certificate
     * @param date the date specified by the PKIXParameters date, or the
     *             timestamp if JAR files are being validated and the
     *             JAR is timestamped. May be null if no timestamp or
     *             PKIXParameter date is set.
     * @param variant the Validator variant of the operation. A null value
     *                passed will set it to Validator.GENERIC.
     */
    public AlgorithmChecker(TrustAnchor anchor, Date date, String variant) {
        this(anchor, null, date, variant);
    }

    @Override
    public void init(boolean forward) throws CertPathValidatorException {
        //  Note that this class does not support forward mode.
        if (!forward) {
            prevPubKey = trustedPubKey;
        } else {
            throw new
                CertPathValidatorException("forward checking not supported");
        }
    }

    @Override
    public boolean isForwardCheckingSupported() {
        //  Note that as this class does not support forward mode, the method
        //  will always return false.
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

        if (!(cert instanceof X509Certificate)) {
            // ignore the check for non-x.509 certificate
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
            algorithmId = x509Cert.getSigAlg();
        } catch (CertificateException ce) {
            throw new CertPathValidatorException(ce);
        }

        AlgorithmParameters currSigAlgParams = algorithmId.getParameters();
        PublicKey currPubKey = cert.getPublicKey();
        String currSigAlg = x509Cert.getSigAlgName();

        if (constraints instanceof DisabledAlgorithmConstraints dac) {
            if (prevPubKey != null && prevPubKey == trustedPubKey) {
                // check constraints of trusted public key (make sure
                // algorithm and size is not restricted)
                CertPathConstraintsParameters cp =
                    new CertPathConstraintsParameters(trustedPubKey, variant,
                        anchor, date);
                dac.permits(KeyUtil.getAlgorithm(trustedPubKey),
                    cp, true);
            }
            // Check the signature algorithm and parameters against constraints
            CertPathConstraintsParameters cp =
                new CertPathConstraintsParameters(x509Cert, variant,
                    anchor, date);
            dac.permits(currSigAlg, currSigAlgParams, cp, true);
        } else {
            if (prevPubKey != null) {
                if (!constraints.permits(SIGNATURE_PRIMITIVE_SET,
                    currSigAlg, prevPubKey, currSigAlgParams)) {
                    throw new CertPathValidatorException(
                        "Algorithm constraints check failed on " +
                            currSigAlg + "signature and " +
                            currPubKey.getAlgorithm() + " key with size of " +
                            sun.security.util.KeyUtil.getKeySize(currPubKey) +
                            "bits",
                        null, null, -1, BasicReason.ALGORITHM_CONSTRAINED);
                }
            } else {
                if (!constraints.permits(SIGNATURE_PRIMITIVE_SET,
                    currSigAlg, currSigAlgParams)) {
                    throw new CertPathValidatorException(
                        "Algorithm constraints check failed on " +
                            "signature algorithm: " + currSigAlg,
                        null, null, -1, BasicReason.ALGORITHM_CONSTRAINED);
                }
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
            if (!constraints.permits(primitives, currPubKey)) {
                throw new CertPathValidatorException(
                    "Algorithm constraints check failed on " +
                        currPubKey.getAlgorithm() + " key with size of " +
                        sun.security.util.KeyUtil.getKeySize(currPubKey) +
                        "bits",
                    null, null, -1, BasicReason.ALGORITHM_CONSTRAINED);
            }
        }

        if (prevPubKey != null) {
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
                            "key with inherited parameters: " +
                            e.getMessage(), e);
                }
            }
        }

        // reset the previous public key
        prevPubKey = currPubKey;
    }

    /**
     * Sets the anchor, trustedPubKey and prevPubKey fields based on the
     * specified trust anchor.
     */
    private void setTrustAnchorAndKeys(TrustAnchor anchor) {
        if (anchor.getTrustedCert() != null) {
            this.trustedPubKey = anchor.getTrustedCert().getPublicKey();
        } else {
            this.trustedPubKey = anchor.getCAPublicKey();
        }
        this.anchor = anchor;
        this.prevPubKey = this.trustedPubKey;
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
        // Only set if trust anchor has not already been set.
        if (this.trustedPubKey == null) {
            setTrustAnchorAndKeys(anchor);
        }
    }

    /**
     * Check the signature algorithm with the specified public key.
     *
     * @param key the public key to verify the CRL signature
     * @param algorithmId signature algorithm Algorithm ID
     * @param variant the Validator variant of the operation. A null
     *                value passed will set it to Validator.GENERIC.
     * @param anchor the trust anchor selected to validate the public key
     */
    static void check(PublicKey key, AlgorithmId algorithmId, String variant,
                      TrustAnchor anchor) throws CertPathValidatorException {

        DisabledAlgorithmConstraints.certPathConstraints().permits(
            algorithmId.getName(), algorithmId.getParameters(),
            new CertPathConstraintsParameters(key, variant, anchor, null), true);
    }
}

