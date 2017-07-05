/*
 * Copyright 2000-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.math.BigInteger;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Iterator;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.*;
import java.security.cert.CertPathValidatorException.BasicReason;
import java.security.interfaces.DSAPublicKey;
import javax.security.auth.x500.X500Principal;
import sun.security.util.Debug;
import sun.security.x509.AccessDescription;
import sun.security.x509.AuthorityInfoAccessExtension;
import sun.security.x509.CRLDistributionPointsExtension;
import sun.security.x509.DistributionPoint;
import sun.security.x509.GeneralName;
import sun.security.x509.GeneralNames;
import sun.security.x509.PKIXExtensions;
import sun.security.x509.X500Name;
import sun.security.x509.X509CertImpl;
import sun.security.x509.X509CRLEntryImpl;

/**
 * CrlRevocationChecker is a <code>PKIXCertPathChecker</code> that checks
 * revocation status information on a PKIX certificate using CRLs obtained
 * from one or more <code>CertStores</code>. This is based on section 6.3
 * of RFC 3280 (http://www.ietf.org/rfc/rfc3280.txt).
 *
 * @since       1.4
 * @author      Seth Proctor
 * @author      Steve Hanna
 */
class CrlRevocationChecker extends PKIXCertPathChecker {

    private static final Debug debug = Debug.getInstance("certpath");
    private final TrustAnchor mAnchor;
    private final List<CertStore> mStores;
    private final String mSigProvider;
    private final Date mCurrentTime;
    private PublicKey mPrevPubKey;
    private boolean mCRLSignFlag;
    private HashSet<X509CRL> mPossibleCRLs;
    private HashSet<X509CRL> mApprovedCRLs;
    private final PKIXParameters mParams;
    private static final boolean [] mCrlSignUsage =
        { false, false, false, false, false, false, true };
    private static final boolean[] ALL_REASONS =
        {true, true, true, true, true, true, true, true, true};
    private boolean mOnlyEECert = false;

    // Maximum clock skew in milliseconds (15 minutes) allowed when checking
    // validity of CRLs
    private static final long MAX_CLOCK_SKEW = 900000;

    /**
     * Creates a <code>CrlRevocationChecker</code>.
     *
     * @param anchor anchor selected to validate the target certificate
     * @param params <code>PKIXParameters</code> to be used for
     *               finding certificates and CRLs, etc.
     */
    CrlRevocationChecker(TrustAnchor anchor, PKIXParameters params)
        throws CertPathValidatorException
    {
        this(anchor, params, null);
    }

    /**
     * Creates a <code>CrlRevocationChecker</code>, allowing
     * extra certificates to be supplied beyond those contained
     * in the <code>PKIXParameters</code>.
     *
     * @param anchor anchor selected to validate the target certificate
     * @param params <code>PKIXParameters</code> to be used for
     *               finding certificates and CRLs, etc.
     * @param certs a <code>Collection</code> of certificates
     *              that may be useful, beyond those available
     *              through <code>params</code> (<code>null</code>
     *              if none)
     */
    CrlRevocationChecker(TrustAnchor anchor, PKIXParameters params,
        Collection<X509Certificate> certs) throws CertPathValidatorException
    {
        this(anchor, params, certs, false);
    }

    CrlRevocationChecker(TrustAnchor anchor, PKIXParameters params,
        Collection<X509Certificate> certs, boolean onlyEECert)
        throws CertPathValidatorException {
        mAnchor = anchor;
        mParams = params;
        mStores = new ArrayList<CertStore>(params.getCertStores());
        mSigProvider = params.getSigProvider();
        if (certs != null) {
            try {
                mStores.add(CertStore.getInstance("Collection",
                    new CollectionCertStoreParameters(certs)));
            } catch (Exception e) {
                // should never occur but not necessarily fatal, so log it,
                // ignore and continue
                if (debug != null) {
                    debug.println("CrlRevocationChecker: " +
                        "error creating Collection CertStore: " + e);
                }
            }
        }
        Date testDate = params.getDate();
        mCurrentTime = (testDate != null ? testDate : new Date());
        mOnlyEECert = onlyEECert;
        init(false);
    }

    /**
     * Initializes the internal state of the checker from parameters
     * specified in the constructor
     */
    public void init(boolean forward) throws CertPathValidatorException
    {
        if (!forward) {
            if (mAnchor != null) {
                if (mAnchor.getCAPublicKey() != null) {
                    mPrevPubKey = mAnchor.getCAPublicKey();
                } else {
                    mPrevPubKey = mAnchor.getTrustedCert().getPublicKey();
                }
            } else {
                mPrevPubKey = null;
            }
            mCRLSignFlag = true;
        } else {
            throw new CertPathValidatorException("forward checking "
                                + "not supported");
        }
    }

    public boolean isForwardCheckingSupported() {
        return false;
    }

    public Set<String> getSupportedExtensions() {
        return null;
    }

    /**
     * Performs the revocation status check on the certificate using
     * its internal state.
     *
     * @param cert the Certificate
     * @param unresolvedCritExts a Collection of the unresolved critical
     * extensions
     * @exception CertPathValidatorException Exception thrown if
     * certificate does not verify
     */
    public void check(Certificate cert, Collection<String> unresolvedCritExts)
        throws CertPathValidatorException
    {
        X509Certificate currCert = (X509Certificate) cert;
        verifyRevocationStatus(currCert, mPrevPubKey, mCRLSignFlag, true);

        // Make new public key if parameters are missing
        PublicKey cKey = currCert.getPublicKey();
        if (cKey instanceof DSAPublicKey &&
            ((DSAPublicKey)cKey).getParams() == null) {
            // cKey needs to inherit DSA parameters from prev key
            cKey = BasicChecker.makeInheritedParamsKey(cKey, mPrevPubKey);
        }
        mPrevPubKey = cKey;
        mCRLSignFlag = certCanSignCrl(currCert);
    }

    /**
     * Performs the revocation status check on the certificate using
     * the provided state variables, as well as the constant internal
     * data.
     *
     * @param currCert the Certificate
     * @param prevKey the previous PublicKey in the chain
     * @param signFlag a boolean as returned from the last call, or true
     * if this is the first cert in the chain
     * @return a boolean specifying if the cert is allowed to vouch for the
     * validity of a CRL for the next iteration
     * @exception CertPathValidatorException Exception thrown if
     *            certificate does not verify.
     */
    public boolean check(X509Certificate currCert, PublicKey prevKey,
        boolean signFlag) throws CertPathValidatorException
    {
        verifyRevocationStatus(currCert, prevKey, signFlag, true);
        return certCanSignCrl(currCert);
    }

    /**
     * Checks that a cert can be used to verify a CRL.
     *
     * @param currCert an X509Certificate to check
     * @return a boolean specifying if the cert is allowed to vouch for the
     * validity of a CRL
     */
    static boolean certCanSignCrl(X509Certificate currCert) {
        // if the cert doesn't include the key usage ext, or
        // the key usage ext asserts cRLSigning, return true,
        // otherwise return false.
        boolean[] kbools = currCert.getKeyUsage();
        if (kbools != null) {
            return kbools[6];
        }
        return false;
    }

    /**
     * Internal method to start the verification of a cert
     */
    private void verifyRevocationStatus(X509Certificate currCert,
        PublicKey prevKey, boolean signFlag, boolean allowSeparateKey)
        throws CertPathValidatorException
    {
        verifyRevocationStatus(currCert, prevKey, signFlag,
                               allowSeparateKey, null);
    }

    /**
     * Internal method to start the verification of a cert
     * @param stackedCerts a <code>Set</code> of <code>X509Certificate</code>s>
     *                     whose revocation status depends on the
     *                     non-revoked status of this cert. To avoid
     *                     circular dependencies, we assume they're
     *                     revoked while checking the revocation
     *                     status of this cert.
     */
    private void verifyRevocationStatus(X509Certificate currCert,
        PublicKey prevKey, boolean signFlag, boolean allowSeparateKey,
        Set<X509Certificate> stackedCerts) throws CertPathValidatorException
    {

        String msg = "revocation status";
        if (debug != null) {
            debug.println("CrlRevocationChecker.verifyRevocationStatus()" +
                " ---checking " + msg + "...");
        }

        if (mOnlyEECert && currCert.getBasicConstraints() != -1) {
            if (debug != null) {
                debug.println("Skipping revocation check, not end entity cert");
            }
            return;
        }

        // reject circular dependencies - RFC 3280 is not explicit on how
        // to handle this, so we feel it is safest to reject them until
        // the issue is resolved in the PKIX WG.
        if ((stackedCerts != null) && stackedCerts.contains(currCert)) {
            if (debug != null) {
                debug.println("CrlRevocationChecker.verifyRevocationStatus()" +
                    " circular dependency");
            }
            throw new CertPathValidatorException
                ("Could not determine revocation status", null, null, -1,
                 BasicReason.UNDETERMINED_REVOCATION_STATUS);
        }

        // init the state for this run
        mPossibleCRLs = new HashSet<X509CRL>();
        mApprovedCRLs = new HashSet<X509CRL>();
        boolean[] reasonsMask = new boolean[9];

        try {
            X509CRLSelector sel = new X509CRLSelector();
            sel.setCertificateChecking(currCert);
            CertPathHelper.setDateAndTime(sel, mCurrentTime, MAX_CLOCK_SKEW);

            for (CertStore mStore : mStores) {
                for (java.security.cert.CRL crl : mStore.getCRLs(sel)) {
                    mPossibleCRLs.add((X509CRL)crl);
                }
            }
            DistributionPointFetcher store =
                DistributionPointFetcher.getInstance();
            // all CRLs returned by the DP Fetcher have also been verified
            mApprovedCRLs.addAll(store.getCRLs(sel, signFlag, prevKey,
                mSigProvider, mStores, reasonsMask, mAnchor));
        } catch (Exception e) {
            if (debug != null) {
                debug.println("CrlRevocationChecker.verifyRevocationStatus() "
                    + "unexpected exception: " + e.getMessage());
            }
            throw new CertPathValidatorException(e);
        }

        if (debug != null) {
            debug.println("CrlRevocationChecker.verifyRevocationStatus() " +
                "crls.size() = " + mPossibleCRLs.size());
        }
        if (!mPossibleCRLs.isEmpty()) {
            // Now that we have a list of possible CRLs, see which ones can
            // be approved
            mApprovedCRLs.addAll(verifyPossibleCRLs(mPossibleCRLs, currCert,
                signFlag, prevKey, reasonsMask));
        }
        if (debug != null) {
            debug.println("CrlRevocationChecker.verifyRevocationStatus() " +
                "approved crls.size() = " + mApprovedCRLs.size());
        }

        // make sure that we have at least one CRL that _could_ cover
        // the certificate in question and all reasons are covered
        if (mApprovedCRLs.isEmpty() ||
            !Arrays.equals(reasonsMask, ALL_REASONS)) {
            if (allowSeparateKey) {
                verifyWithSeparateSigningKey(currCert, prevKey, signFlag,
                                             stackedCerts);
                return;
            } else {
                throw new CertPathValidatorException
                ("Could not determine revocation status", null, null, -1,
                 BasicReason.UNDETERMINED_REVOCATION_STATUS);
            }
        }

        // See if the cert is in the set of approved crls.
        if (debug != null) {
            BigInteger sn = currCert.getSerialNumber();
            debug.println("starting the final sweep...");
            debug.println("CrlRevocationChecker.verifyRevocationStatus" +
                          " cert SN: " + sn.toString());
        }

        CRLReason reasonCode = CRLReason.UNSPECIFIED;
        X509CRLEntryImpl entry = null;
        for (X509CRL crl : mApprovedCRLs) {
            X509CRLEntry e = crl.getRevokedCertificate(currCert);
            if (e != null) {
                try {
                    entry = X509CRLEntryImpl.toImpl(e);
                } catch (CRLException ce) {
                    throw new CertPathValidatorException(ce);
                }
                if (debug != null) {
                    debug.println("CrlRevocationChecker.verifyRevocationStatus"
                        + " CRL entry: " + entry.toString());
                }

                /*
                 * Abort CRL validation and throw exception if there are any
                 * unrecognized critical CRL entry extensions (see section
                 * 5.3 of RFC 3280).
                 */
                Set<String> unresCritExts = entry.getCriticalExtensionOIDs();
                if (unresCritExts != null && !unresCritExts.isEmpty()) {
                    /* remove any that we will process */
                    unresCritExts.remove
                        (PKIXExtensions.ReasonCode_Id.toString());
                    unresCritExts.remove
                        (PKIXExtensions.CertificateIssuer_Id.toString());
                    if (!unresCritExts.isEmpty()) {
                        if (debug != null) {
                            debug.println("Unrecognized "
                            + "critical extension(s) in revoked CRL entry: "
                            + unresCritExts);
                        }
                        throw new CertPathValidatorException
                        ("Could not determine revocation status", null, null,
                         -1, BasicReason.UNDETERMINED_REVOCATION_STATUS);
                    }
                }

                reasonCode = entry.getRevocationReason();
                if (reasonCode == null) {
                    reasonCode = CRLReason.UNSPECIFIED;
                }
                Throwable t = new CertificateRevokedException
                    (entry.getRevocationDate(), reasonCode,
                     crl.getIssuerX500Principal(), entry.getExtensions());
                throw new CertPathValidatorException(t.getMessage(), t,
                    null, -1, BasicReason.REVOKED);
            }
        }
    }

    /**
     * We have a cert whose revocation status couldn't be verified by
     * a CRL issued by the cert that issued the CRL. See if we can
     * find a valid CRL issued by a separate key that can verify the
     * revocation status of this certificate.
     * <p>
     * Note that this does not provide support for indirect CRLs,
     * only CRLs signed with a different key (but the same issuer
     * name) as the certificate being checked.
     *
     * @param currCert the <code>X509Certificate</code> to be checked
     * @param prevKey the <code>PublicKey</code> that failed
     * @param signFlag <code>true</code> if that key was trusted to sign CRLs
     * @param stackedCerts a <code>Set</code> of <code>X509Certificate</code>s>
     *                     whose revocation status depends on the
     *                     non-revoked status of this cert. To avoid
     *                     circular dependencies, we assume they're
     *                     revoked while checking the revocation
     *                     status of this cert.
     * @throws CertPathValidatorException if the cert's revocation status
     *         cannot be verified successfully with another key
     */
    private void verifyWithSeparateSigningKey(X509Certificate currCert,
        PublicKey prevKey, boolean signFlag, Set<X509Certificate> stackedCerts)
        throws CertPathValidatorException {
        String msg = "revocation status";
        if (debug != null) {
            debug.println(
                "CrlRevocationChecker.verifyWithSeparateSigningKey()" +
                " ---checking " + msg + "...");
        }

        // reject circular dependencies - RFC 3280 is not explicit on how
        // to handle this, so we feel it is safest to reject them until
        // the issue is resolved in the PKIX WG.
        if ((stackedCerts != null) && stackedCerts.contains(currCert)) {
            if (debug != null) {
                debug.println(
                    "CrlRevocationChecker.verifyWithSeparateSigningKey()" +
                    " circular dependency");
            }
            throw new CertPathValidatorException
                ("Could not determine revocation status", null, null,
                 -1, BasicReason.UNDETERMINED_REVOCATION_STATUS);
        }

        // If prevKey wasn't trusted, maybe we just didn't have the right
        // path to it. Don't rule that key out.
        if (!signFlag) {
            prevKey = null;
        }

        // Try to find another key that might be able to sign
        // CRLs vouching for this cert.
        buildToNewKey(currCert, prevKey, stackedCerts);
    }

    /**
     * Tries to find a CertPath that establishes a key that can be
     * used to verify the revocation status of a given certificate.
     * Ignores keys that have previously been tried. Throws a
     * CertPathValidatorException if no such key could be found.
     *
     * @param currCert the <code>X509Certificate</code> to be checked
     * @param prevKey the <code>PublicKey</code> of the certificate whose key
     *    cannot be used to vouch for the CRL and should be ignored
     * @param stackedCerts a <code>Set</code> of <code>X509Certificate</code>s>
     *                     whose revocation status depends on the
     *                     establishment of this path.
     * @throws CertPathValidatorException on failure
     */
    private void buildToNewKey(X509Certificate currCert,
        PublicKey prevKey, Set<X509Certificate> stackedCerts)
        throws CertPathValidatorException {

        if (debug != null) {
            debug.println("CrlRevocationChecker.buildToNewKey()" +
                          " starting work");
        }
        Set<PublicKey> badKeys = new HashSet<PublicKey>();
        if (prevKey != null) {
            badKeys.add(prevKey);
        }
        X509CertSelector certSel = new RejectKeySelector(badKeys);
        certSel.setSubject(currCert.getIssuerX500Principal());
        certSel.setKeyUsage(mCrlSignUsage);

        Set<TrustAnchor> newAnchors = mAnchor == null
            ? mParams.getTrustAnchors()
            : Collections.singleton(mAnchor);

        PKIXBuilderParameters builderParams;
        if (mParams instanceof PKIXBuilderParameters) {
            builderParams = (PKIXBuilderParameters) mParams.clone();
            builderParams.setTargetCertConstraints(certSel);
            // Policy qualifiers must be rejected, since we don't have
            // any way to convey them back to the application.
            builderParams.setPolicyQualifiersRejected(true);
            try {
                builderParams.setTrustAnchors(newAnchors);
            } catch (InvalidAlgorithmParameterException iape) {
                throw new RuntimeException(iape); // should never occur
            }
        } else {
            // It's unfortunate that there's no easy way to make a
            // PKIXBuilderParameters object from a PKIXParameters
            // object. This might miss some things if parameters
            // are added in the future or the validatorParams object
            // is a custom class derived from PKIXValidatorParameters.
            try {
                builderParams = new PKIXBuilderParameters(newAnchors, certSel);
            } catch (InvalidAlgorithmParameterException iape) {
                throw new RuntimeException(iape); // should never occur
            }
            builderParams.setInitialPolicies(mParams.getInitialPolicies());
            builderParams.setCertStores(mStores);
            builderParams.setExplicitPolicyRequired
                (mParams.isExplicitPolicyRequired());
            builderParams.setPolicyMappingInhibited
                (mParams.isPolicyMappingInhibited());
            builderParams.setAnyPolicyInhibited(mParams.isAnyPolicyInhibited());
            // Policy qualifiers must be rejected, since we don't have
            // any way to convey them back to the application.
            // That's the default, so no need to write code.
            builderParams.setDate(mParams.getDate());
            builderParams.setCertPathCheckers(mParams.getCertPathCheckers());
            builderParams.setSigProvider(mParams.getSigProvider());
        }

        // Skip revocation during this build to detect circular
        // references. But check revocation afterwards, using the
        // key (or any other that works).
        builderParams.setRevocationEnabled(false);

        // check for AuthorityInformationAccess extension
        if (Builder.USE_AIA == true) {
            X509CertImpl currCertImpl = null;
            try {
                currCertImpl = X509CertImpl.toImpl(currCert);
            } catch (CertificateException ce) {
                // ignore but log it
                if (debug != null) {
                    debug.println("CrlRevocationChecker.buildToNewKey: " +
                        "error decoding cert: " + ce);
                }
            }
            AuthorityInfoAccessExtension aiaExt = null;
            if (currCertImpl != null) {
                aiaExt = currCertImpl.getAuthorityInfoAccessExtension();
            }
            if (aiaExt != null) {
                List<AccessDescription> adList = aiaExt.getAccessDescriptions();
                if (adList != null) {
                    for (AccessDescription ad : adList) {
                        CertStore cs = URICertStore.getInstance(ad);
                        if (cs != null) {
                            if (debug != null) {
                                debug.println("adding AIAext CertStore");
                            }
                            builderParams.addCertStore(cs);
                        }
                    }
                }
            }
        }

        CertPathBuilder builder = null;
        try {
            builder = CertPathBuilder.getInstance("PKIX");
        } catch (NoSuchAlgorithmException nsae) {
            throw new CertPathValidatorException(nsae);
        }
        while (true) {
            try {
                if (debug != null) {
                    debug.println("CrlRevocationChecker.buildToNewKey()" +
                                  " about to try build ...");
                }
                PKIXCertPathBuilderResult cpbr =
                    (PKIXCertPathBuilderResult) builder.build(builderParams);

                if (debug != null) {
                    debug.println("CrlRevocationChecker.buildToNewKey()" +
                                  " about to check revocation ...");
                }
                // Now check revocation of all certs in path, assuming that
                // the stackedCerts are revoked.
                if (stackedCerts == null) {
                    stackedCerts = new HashSet<X509Certificate>();
                }
                stackedCerts.add(currCert);
                TrustAnchor ta = cpbr.getTrustAnchor();
                PublicKey prevKey2 = ta.getCAPublicKey();
                if (prevKey2 == null) {
                    prevKey2 = ta.getTrustedCert().getPublicKey();
                }
                boolean signFlag = true;
                List<? extends Certificate> cpList =
                    cpbr.getCertPath().getCertificates();
                try {
                    for (int i = cpList.size()-1; i >= 0; i-- ) {
                        X509Certificate cert = (X509Certificate) cpList.get(i);

                        if (debug != null) {
                            debug.println("CrlRevocationChecker.buildToNewKey()"
                                + " index " + i + " checking " + cert);
                        }
                        verifyRevocationStatus(cert, prevKey2, signFlag,
                                               true, stackedCerts);
                        signFlag = certCanSignCrl(cert);
                        prevKey2 = cert.getPublicKey();
                    }
                } catch (CertPathValidatorException cpve) {
                    // ignore it and try to get another key
                    badKeys.add(cpbr.getPublicKey());
                    continue;
                }

                if (debug != null) {
                    debug.println("CrlRevocationChecker.buildToNewKey()" +
                                  " got key " + cpbr.getPublicKey());
                }
                // Now check revocation on the current cert using that key.
                // If it doesn't check out, try to find a different key.
                // And if we can't find a key, then return false.
                PublicKey newKey = cpbr.getPublicKey();
                try {
                    verifyRevocationStatus(currCert, newKey, true, false);
                    // If that passed, the cert is OK!
                    return;
                } catch (CertPathValidatorException cpve) {
                    // If it is revoked, rethrow exception
                    if (cpve.getReason() == BasicReason.REVOKED) {
                        throw cpve;
                    }
                    // Otherwise, ignore the exception and
                    // try to get another key.
                }
                badKeys.add(newKey);
            } catch (InvalidAlgorithmParameterException iape) {
                throw new CertPathValidatorException(iape);
            } catch (CertPathBuilderException cpbe) {
                throw new CertPathValidatorException
                    ("Could not determine revocation status", null, null,
                     -1, BasicReason.UNDETERMINED_REVOCATION_STATUS);
            }
        }
    }

    /*
     * This inner class extends the X509CertSelector to add an additional
     * check to make sure the subject public key isn't on a particular list.
     * This class is used by buildToNewKey() to make sure the builder doesn't
     * end up with a CertPath to a public key that has already been rejected.
     */
    private static class RejectKeySelector extends X509CertSelector {
        private final Set<PublicKey> badKeySet;

        /**
         * Creates a new <code>RejectKeySelector</code>.
         *
         * @param badPublicKeys a <code>Set</code> of
         *                      <code>PublicKey</code>s that
         *                      should be rejected (or <code>null</code>
         *                      if no such check should be done)
         */
        RejectKeySelector(Set<PublicKey> badPublicKeys) {
            this.badKeySet = badPublicKeys;
        }

        /**
         * Decides whether a <code>Certificate</code> should be selected.
         *
         * @param cert the <code>Certificate</code> to be checked
         * @return <code>true</code> if the <code>Certificate</code> should be
         *         selected, <code>false</code> otherwise
         */
        public boolean match(Certificate cert) {
            if (!super.match(cert))
                return(false);

            if (badKeySet.contains(cert.getPublicKey())) {
                if (debug != null)
                    debug.println("RejectCertSelector.match: bad key");
                return false;
            }

            if (debug != null)
                debug.println("RejectCertSelector.match: returning true");
            return true;
        }

        /**
         * Return a printable representation of the <code>CertSelector</code>.
         *
         * @return a <code>String</code> describing the contents of the
         *         <code>CertSelector</code>
         */
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("RejectCertSelector: [\n");
            sb.append(super.toString());
            sb.append(badKeySet);
            sb.append("]");
            return sb.toString();
        }
    }

    /**
     * Internal method that verifies a set of possible_crls,
     * and sees if each is approved, based on the cert.
     *
     * @param crls a set of possible CRLs to test for acceptability
     * @param cert the certificate whose revocation status is being checked
     * @param signFlag <code>true</code> if prevKey was trusted to sign CRLs
     * @param prevKey the public key of the issuer of cert
     * @param reasonsMask the reason code mask
     * @return a collection of approved crls (or an empty collection)
     */
    private Collection<X509CRL> verifyPossibleCRLs(Set<X509CRL> crls,
        X509Certificate cert, boolean signFlag, PublicKey prevKey,
        boolean[] reasonsMask) throws CertPathValidatorException
    {
        try {
            X509CertImpl certImpl = X509CertImpl.toImpl(cert);
            if (debug != null) {
                debug.println("CRLRevocationChecker.verifyPossibleCRLs: " +
                        "Checking CRLDPs for "
                        + certImpl.getSubjectX500Principal());
            }
            CRLDistributionPointsExtension ext =
                certImpl.getCRLDistributionPointsExtension();
            List<DistributionPoint> points = null;
            if (ext == null) {
                // assume a DP with reasons and CRLIssuer fields omitted
                // and a DP name of the cert issuer.
                // TODO add issuerAltName too
                X500Name certIssuer = (X500Name)certImpl.getIssuerDN();
                DistributionPoint point = new DistributionPoint
                    (new GeneralNames().add(new GeneralName(certIssuer)),
                     null, null);
                points = Collections.singletonList(point);
            } else {
                points = (List<DistributionPoint>)ext.get(
                                        CRLDistributionPointsExtension.POINTS);
            }
            Set<X509CRL> results = new HashSet<X509CRL>();
            DistributionPointFetcher dpf =
                DistributionPointFetcher.getInstance();
            for (Iterator<DistributionPoint> t = points.iterator();
                 t.hasNext() && !Arrays.equals(reasonsMask, ALL_REASONS); ) {
                DistributionPoint point = t.next();
                for (X509CRL crl : crls) {
                    if (dpf.verifyCRL(certImpl, point, crl, reasonsMask,
                        signFlag, prevKey, mSigProvider, mAnchor, mStores)) {
                        results.add(crl);
                    }
                }
            }
            return results;
        } catch (Exception e) {
            if (debug != null) {
                debug.println("Exception while verifying CRL: "+e.getMessage());
                e.printStackTrace();
            }
            return Collections.emptySet();
        }
    }
}
