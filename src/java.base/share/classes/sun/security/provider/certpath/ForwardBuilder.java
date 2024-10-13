/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.PKIXReason;
import java.security.cert.CertStore;
import java.security.cert.CertStoreException;
import java.security.cert.PKIXCertPathChecker;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.security.cert.X509CertSelector;
import java.util.*;
import javax.security.auth.x500.X500Principal;

import jdk.internal.misc.ThreadTracker;
import sun.security.provider.certpath.PKIX.BuilderParams;
import sun.security.util.Debug;
import sun.security.util.ObjectIdentifier;
import sun.security.x509.AccessDescription;
import sun.security.x509.AuthorityInfoAccessExtension;
import sun.security.x509.AuthorityKeyIdentifierExtension;
import sun.security.x509.AVA;
import static sun.security.x509.PKIXExtensions.*;
import sun.security.x509.RDN;
import sun.security.x509.X500Name;
import sun.security.x509.X509CertImpl;

/**
 * This class represents a forward builder, which is able to retrieve
 * matching certificates from CertStores and verify a particular certificate
 * against a ForwardState.
 *
 * @since       1.4
 * @author      Yassir Elley
 * @author      Sean Mullan
 */
public final class ForwardBuilder extends Builder {

    private static final Debug debug = Debug.getInstance("certpath");
    private final Set<X509Certificate> trustedCerts;
    private final Set<X500Principal> trustedSubjectDNs;
    private final Set<TrustAnchor> trustAnchors;
    private X509CertSelector eeSelector;
    private AdaptableX509CertSelector caSelector;
    private X509CertSelector caTargetSelector;
    TrustAnchor trustAnchor;
    private final boolean searchAllCertStores;

    private static class ThreadTrackerHolder {
        static final ThreadTracker AIA_TRACKER = new ThreadTracker();
    }

    /**
     * Initialize the builder with the input parameters.
     *
     * @param params the parameter set used to build a certification path
     */
    ForwardBuilder(BuilderParams buildParams, boolean searchAllCertStores) {
        super(buildParams);

        // populate sets of trusted certificates and subject DNs
        trustAnchors = buildParams.trustAnchors();
        trustedCerts = HashSet.newHashSet(trustAnchors.size());
        trustedSubjectDNs = HashSet.newHashSet(trustAnchors.size());
        for (TrustAnchor anchor : trustAnchors) {
            X509Certificate trustedCert = anchor.getTrustedCert();
            if (trustedCert != null) {
                trustedCerts.add(trustedCert);
                trustedSubjectDNs.add(trustedCert.getSubjectX500Principal());
            } else {
                trustedSubjectDNs.add(anchor.getCA());
            }
        }
        this.searchAllCertStores = searchAllCertStores;
    }

    /**
     * Retrieves all certs from the specified CertStores that satisfy the
     * requirements specified in the parameters and the current
     * PKIX state (name constraints, policy constraints, etc.).
     *
     * @param currentState the current state.
     *        Must be an instance of <code>ForwardState</code>
     * @param certStores list of CertStores
     */
    @Override
    Collection<X509Certificate> getMatchingCerts(State currentState,
                                                 List<CertStore> certStores)
        throws IOException
    {
        if (debug != null) {
            debug.println("ForwardBuilder.getMatchingCerts()...");
        }

        ForwardState currState = (ForwardState) currentState;

        /*
         * We store certs in a Set because we don't want duplicates.
         * As each cert is added, it is sorted based on the PKIXCertComparator
         * algorithm.
         */
        Comparator<X509Certificate> comparator =
            new PKIXCertComparator(trustedSubjectDNs, currState.cert);
        Set<X509Certificate> certs = new TreeSet<>(comparator);

        /*
         * Only look for EE certs if search has just started.
         */
        if (currState.isInitial()) {
            getMatchingEECerts(currState, certStores, certs);
        }
        getMatchingCACerts(currState, certStores, certs);

        return certs;
    }

    /*
     * Retrieves all end-entity certificates which satisfy constraints
     * and requirements specified in the parameters and PKIX state.
     */
    private void getMatchingEECerts(ForwardState currentState,
                                    List<CertStore> certStores,
                                    Collection<X509Certificate> eeCerts)
        throws IOException
    {
        if (debug != null) {
            debug.println("ForwardBuilder.getMatchingEECerts()...");
        }
        /*
         * Compose a certificate matching rule to filter out
         * certs which don't satisfy constraints
         *
         * First, retrieve clone of current target cert constraints,
         * and then add more selection criteria based on current validation
         * state. Since selector never changes, cache local copy & reuse.
         */
        if (eeSelector == null) {
            eeSelector = (X509CertSelector) targetCertConstraints.clone();

            /*
             * Match on certificate validity date
             */
            eeSelector.setCertificateValid(buildParams.date());

            /*
             * Policy processing optimizations
             */
            if (buildParams.explicitPolicyRequired()) {
                eeSelector.setPolicy(getMatchingPolicies());
            }
            /*
             * Require EE certs
             */
            eeSelector.setBasicConstraints(-2);
        }

        /* Retrieve matching EE certs from CertStores */
        addMatchingCerts(eeSelector, certStores, eeCerts, searchAllCertStores);
    }

    /**
     * Retrieves all CA certificates which satisfy constraints
     * and requirements specified in the parameters and PKIX state.
     */
    private void getMatchingCACerts(ForwardState currentState,
                                    List<CertStore> certStores,
                                    Collection<X509Certificate> caCerts)
        throws IOException
    {
        if (debug != null) {
            debug.println("ForwardBuilder.getMatchingCACerts()...");
        }
        int initialSize = caCerts.size();

        /*
         * Compose a CertSelector to filter out
         * certs which do not satisfy requirements.
         */
        X509CertSelector sel;

        if (currentState.isInitial()) {
            if (targetCertConstraints.getBasicConstraints() == -2) {
                // no need to continue: this means we never can match a CA cert
                return;
            }

            /* This means a CA is the target, so match on same stuff as
             * getMatchingEECerts
             */
            if (debug != null) {
                debug.println("ForwardBuilder.getMatchingCACerts(): " +
                              "the target is a CA");
            }

            if (caTargetSelector == null) {
                caTargetSelector =
                    (X509CertSelector) targetCertConstraints.clone();

                /*
                 * Since we don't check the validity period of trusted
                 * certificates, please don't set the certificate valid
                 * criterion unless the trusted certificate matching is
                 * completed.
                 */

                /*
                 * Policy processing optimizations
                 */
                if (buildParams.explicitPolicyRequired())
                    caTargetSelector.setPolicy(getMatchingPolicies());
            }

            sel = caTargetSelector;
        } else {

            if (caSelector == null) {
                caSelector = new AdaptableX509CertSelector();

                /*
                 * Since we don't check the validity period of trusted
                 * certificates, please don't set the certificate valid
                 * criterion unless the trusted certificate matching is
                 * completed.
                 */

                /*
                 * Policy processing optimizations
                 */
                if (buildParams.explicitPolicyRequired())
                    caSelector.setPolicy(getMatchingPolicies());
            }

            /*
             * Match on subject (issuer of previous cert)
             */
            caSelector.setSubject(currentState.issuerDN);

            /*
             * check the validity period
             */
            caSelector.setValidityPeriod(currentState.cert.getNotBefore(),
                                         currentState.cert.getNotAfter());

            sel = caSelector;
        }

        /*
         * For compatibility, conservatively, we don't check the path
         * length constraint of trusted anchors.  Please don't set the
         * basic constraints criterion unless the trusted certificate
         * matching is completed.
         */
        sel.setBasicConstraints(-1);

        for (X509Certificate trustedCert : trustedCerts) {
            if (sel.match(trustedCert)) {
                if (debug != null) {
                    debug.println("ForwardBuilder.getMatchingCACerts: " +
                        "found matching trust anchor." +
                        "\n  SN: " +
                            Debug.toString(trustedCert.getSerialNumber()) +
                        "\n  Subject: " +
                            trustedCert.getSubjectX500Principal() +
                        "\n  Issuer: " +
                            trustedCert.getIssuerX500Principal());
                }
                caCerts.add(trustedCert);
            }
        }

        /*
         * The trusted certificate matching is completed. We need to match
         * on certificate validity date.
         */
        sel.setCertificateValid(buildParams.date());

        /*
         * Require CA certs with a pathLenConstraint that allows
         * at least as many CA certs that have already been traversed
         */
        sel.setBasicConstraints(currentState.traversedCACerts);

        /*
         * If we have already traversed as many CA certs as the maxPathLength
         * will allow us to, then we don't bother looking through these
         * certificate pairs. If maxPathLength has a value of -1, this
         * means it is unconstrained, so we always look through the
         * certificate pairs.
         */
        if (currentState.isInitial() ||
           (buildParams.maxPathLength() == -1) ||
           (buildParams.maxPathLength() > currentState.traversedCACerts))
        {
            if (addMatchingCerts(sel, certStores,
                                 caCerts, searchAllCertStores)
                && !searchAllCertStores) {
                return;
            }
        }

        if (!currentState.isInitial() && Builder.USE_AIA) {
            // check for AuthorityInformationAccess extension
            AuthorityInfoAccessExtension aiaExt =
                currentState.cert.getAuthorityInfoAccessExtension();
            if (aiaExt != null) {
                getCerts(aiaExt, caCerts);
            }
        }

        if (debug != null) {
            int numCerts = caCerts.size() - initialSize;
            debug.println("ForwardBuilder.getMatchingCACerts: found " +
                numCerts + " CA certs");
        }
    }

    /**
     * Download certificates from the given AIA and add them to the
     * specified Collection.
     */
    // cs.getCertificates(caSelector) returns a collection of X509Certificate's
    // because of the selector, so the cast is safe
    @SuppressWarnings("unchecked")
    private boolean getCerts(AuthorityInfoAccessExtension aiaExt,
                             Collection<X509Certificate> certs)
    {
        if (!Builder.USE_AIA) {
            return false;
        }

        List<AccessDescription> adList = aiaExt.getAccessDescriptions();
        if (adList == null || adList.isEmpty()) {
            return false;
        }

        Object key = ThreadTrackerHolder.AIA_TRACKER.tryBegin();
        if (key == null) {
            // Avoid recursive fetching of certificates
            if (debug != null) {
                debug.println("Recursive fetching of certs via the AIA " +
                    "extension detected");
            }
            return false;
        }

        try {
            boolean add = false;
            for (AccessDescription ad : adList) {
                CertStore cs = URICertStore.getInstance(ad);
                if (cs != null) {
                    try {
                        if (certs.addAll((Collection<X509Certificate>)
                            cs.getCertificates(caSelector))) {
                            add = true;
                            if (!searchAllCertStores) {
                                return true;
                            }
                        }
                    } catch (CertStoreException cse) {
                        if (debug != null) {
                            debug.println("exception getting certs from CertStore:");
                            cse.printStackTrace();
                        }
                    }
                }
            }
            return add;
        } finally {
            ThreadTrackerHolder.AIA_TRACKER.end(key);
        }
    }

    /**
     * This inner class compares 2 PKIX certificates according to which
     * should be tried first when building a path from the target.
     * The preference order is as follows:
     *
     * Given trusted certificate(s):
     *    Subject:ou=D,ou=C,o=B,c=A
     *
     * Preference order for current cert:
     *
     * 1) The key identifier of an AKID extension (if present) in the
     *    previous certificate matches the key identifier in the SKID extension
     *
     * 2) Issuer matches a trusted subject
     *    Issuer: ou=D,ou=C,o=B,c=A
     *
     * 3) Issuer is in the same namespace as a trusted subject (in order of
     *    number of links to the trusted subject). If the last RDN of the
     *    common ancestor is geographical, then it is skipped and the next
     *    trusted certificate is checked.
     *    a) Issuer: ou=G,ou=C,o=B,c=A  [links=2]
     *    b) Issuer: ou=H,o=B,c=A       [links=3]
     *    c) Issuer: ou=H,o=D,c=A       [skipped, only geographical c=A is same]
     *
     * 4) Any other certificates
     */
    static class PKIXCertComparator implements Comparator<X509Certificate> {

        static final String METHOD_NME = "PKIXCertComparator.compare()";

        private final Set<X500Principal> trustedSubjectDNs;
        private final X509CertSelector certSkidSelector;

        PKIXCertComparator(Set<X500Principal> trustedSubjectDNs,
                           X509CertImpl previousCert) throws IOException {
            this.trustedSubjectDNs = trustedSubjectDNs;
            this.certSkidSelector = getSelector(previousCert);
        }

        /**
         * Returns an X509CertSelector for matching on the authority key
         * identifier, or null if not applicable.
         */
        private X509CertSelector getSelector(X509CertImpl previousCert)
            throws IOException {
            if (previousCert != null) {
                AuthorityKeyIdentifierExtension akidExt =
                    previousCert.getAuthorityKeyIdentifierExtension();
                if (akidExt != null) {
                    byte[] skid = akidExt.getEncodedKeyIdentifier();
                    if (skid != null) {
                        X509CertSelector selector = new X509CertSelector();
                        selector.setSubjectKeyIdentifier(skid);
                        return selector;
                    }
                }
            }
            return null;
        }

        /**
         * @param oCert1 first X509Certificate to be compared
         * @param oCert2 second X509Certificate to be compared
         * @return -1 if oCert1 is preferable to oCert2, or
         *            if oCert1 and oCert2 are equally preferable (in this
         *            case it doesn't matter which is preferable, but we don't
         *            return 0 because the comparator would behave strangely
         *            when used in a SortedSet).
         *          1 if oCert2 is preferable to oCert1
         *          0 if oCert1.equals(oCert2). We only return 0 if the
         *          certs are equal so that this comparator behaves
         *          correctly when used in a SortedSet.
         */
        @Override
        public int compare(X509Certificate oCert1, X509Certificate oCert2) {

            // if certs are the same, return 0
            if (oCert1.equals(oCert2)) return 0;

            // If akid/skid match then it is preferable
            if (certSkidSelector != null) {
                if (certSkidSelector.match(oCert1)) {
                    return -1;
                }
                if (certSkidSelector.match(oCert2)) {
                    return 1;
                }
            }

            X500Principal cIssuer1 = oCert1.getIssuerX500Principal();
            X500Principal cIssuer2 = oCert2.getIssuerX500Principal();

            /* If one cert's issuer matches a trusted subject, then it is
             * preferable.
             */
            if (debug != null) {
                debug.println(METHOD_NME + " cert1 Issuer:  " + cIssuer1);
                debug.println(METHOD_NME + " cert2 Issuer:  " + cIssuer2);
                debug.println(METHOD_NME + " MATCH TRUSTED SUBJECT TEST...");
            }

            if (trustedSubjectDNs.contains(cIssuer1)) {
                return -1;
            }
            if (trustedSubjectDNs.contains(cIssuer2)) {
                return 1;
            }

            /* If one cert's issuer is in the same namespace as a trusted
             * subject, then it is preferable, in order of increasing naming
             * distance.
             */
            String debugMsg = null;
            if (debug != null) {
                debug.println(METHOD_NME + " SAME NAMESPACE AS TRUSTED TEST...");
                debugMsg = METHOD_NME + " distance (number of " +
                    "RDNs) from cert%1$s issuer to trusted subject %2$s: %3$d";
            }

            X500Name cIssuer1Name = X500Name.asX500Name(cIssuer1);
            X500Name cIssuer2Name = X500Name.asX500Name(cIssuer2);
            // Note that we stop searching if we find a trust anchor that
            // has a common non-geographical ancestor on the basis that there
            // is a good chance that this path is the one we want.
            for (X500Principal tSubject : trustedSubjectDNs) {
                X500Name tSubjectName = X500Name.asX500Name(tSubject);
                int d1 = distanceToCommonAncestor(tSubjectName, cIssuer1Name);
                int d2 = distanceToCommonAncestor(tSubjectName, cIssuer2Name);
                if (debug != null) {
                    if (d1 != -1) {
                        debug.println(String.format(debugMsg, "1", tSubject, d1));
                    }
                    if (d2 != -1) {
                        debug.println(String.format(debugMsg, "2", tSubject, d2));
                    }
                }
                if (d1 == -1 && d2 == -1) {
                    // neither cert has a common non-geographical ancestor with
                    // trust anchor, so continue checking other trust anchors
                    continue;
                }
                if (d1 != -1) {
                    if (d2 != -1) {
                        // both certs share a common non-geographical ancestor
                        // with trust anchor. Prefer the one that is closer
                        // to the trust anchor.
                        return (d1 > d2) ? 1 : -1;
                    } else {
                        // cert1 shares a common non-geographical ancestor with
                        // trust anchor, so it is preferred.
                        return -1;
                    }
                } else if (d2 != -1) {
                    // cert2 shares a common non-geographical ancestor with
                    // trust anchor, so it is preferred.
                    return 1;
                }
            }

            /* Otherwise, certs are equally preferable.
             */
            if (debug != null) {
                debug.println(METHOD_NME + " no tests matched; RETURN -1");
            }
            return -1;
        }
    }

    /**
     * Returns the distance (number of RDNs) from the issuer's DN to the
     * common non-geographical ancestor of the trust anchor and issuer's DN.
     *
     * @param anchor the anchor's DN
     * @param issuer the issuer's DN
     * @return the distance or -1 if no common ancestor or an attribute of the
     *    last RDN of the common ancestor is geographical
     */
    private static int distanceToCommonAncestor(X500Name anchor, X500Name issuer) {
        List<RDN> anchorRdns = anchor.rdns();
        List<RDN> issuerRdns = issuer.rdns();
        int minLen = Math.min(anchorRdns.size(), issuerRdns.size());
        if (minLen == 0) {
            return -1;
        }

        // Compare names from highest RDN down the naming tree.
        int i = 0;
        for (; i < minLen; i++) {
            RDN rdn = anchorRdns.get(i);
            if (!rdn.equals(issuerRdns.get(i))) {
                if (i == 0) {
                    return -1;
                } else {
                    break;
                }
            }
        }

        // check if last RDN is geographical
        RDN lastRDN = anchorRdns.get(i - 1);
        for (AVA ava : lastRDN.avas()) {
            ObjectIdentifier oid = ava.getObjectIdentifier();
            if (oid.equals(X500Name.countryName_oid) ||
                oid.equals(X500Name.stateName_oid) ||
                oid.equals(X500Name.localityName_oid) ||
                oid.equals(X500Name.streetAddress_oid)) {
                return -1;
           }
        }

        return issuer.size() - i;
    }

    /**
     * Verifies a matching certificate.
     *
     * This method executes the validation steps in the PKIX path
     * validation algorithm, RFC 5280, which were
     * not satisfied by the selection criteria used by getCertificates()
     * to find the certs and only the steps that can be executed in a
     * forward direction (target to trust anchor). Those steps that can
     * only be executed in a reverse direction are deferred until the
     * complete path has been built.
     *
     * Trust anchor certs are not validated.
     *
     * If the last certificate is being verified (the one whose subject
     * matches the target subject) then steps in 6.1.4 of the PKIX
     * Certification Path Validation algorithm are NOT executed,
     * regardless of whether the last cert is an end-entity
     * cert or not. This allows callers to certify CA certs as
     * well as EE certs.
     *
     * @param cert the certificate to be verified
     * @param currentState the current state against which the cert is verified
     * @param certPathList the certPathList generated thus far
     */
    @Override
    void verifyCert(X509Certificate cert, State currentState,
                    List<X509Certificate> certPathList)
        throws GeneralSecurityException
    {
        if (debug != null) {
            debug.println("ForwardBuilder.verifyCert(SN: "
                + Debug.toString(cert.getSerialNumber())
                + "\n  Issuer: " + cert.getIssuerX500Principal() + ")"
                + "\n  Subject: " + cert.getSubjectX500Principal() + ")");
        }

        ForwardState currState = (ForwardState)currentState;

        // Don't bother to verify untrusted certificate more.
        currState.untrustedChecker.check(cert, Collections.emptySet());

        /* check if trusted cert */
        boolean isTrustedCert = trustedCerts.contains(cert);

        /* we don't perform any validation of the trusted cert */
        if (!isTrustedCert) {
            /*
             * Check CRITICAL private extensions for user checkers that
             * support forward checking (forwardCheckers) and remove
             * ones we know how to check.
             */
            Set<String> unresCritExts = cert.getCriticalExtensionOIDs();
            if (unresCritExts == null) {
                unresCritExts = Collections.emptySet();
            }
            for (PKIXCertPathChecker checker : currState.forwardCheckers) {
                checker.check(cert, unresCritExts);
            }

            /*
             * Remove extensions from user checkers that don't support
             * forward checking. After this step, we will have removed
             * all extensions that all user checkers are capable of
             * processing.
             */
            for (PKIXCertPathChecker checker : buildParams.certPathCheckers()) {
                if (!checker.isForwardCheckingSupported()) {
                    Set<String> supportedExts = checker.getSupportedExtensions();
                    if (supportedExts != null) {
                        unresCritExts.removeAll(supportedExts);
                    }
                }
            }

            /*
             * Look at the remaining extensions and remove any ones we know how
             * to check. If there are any left, throw an exception!
             */
            if (!unresCritExts.isEmpty()) {
                unresCritExts.remove(BasicConstraints_Id.toString());
                unresCritExts.remove(NameConstraints_Id.toString());
                unresCritExts.remove(CertificatePolicies_Id.toString());
                unresCritExts.remove(PolicyMappings_Id.toString());
                unresCritExts.remove(PolicyConstraints_Id.toString());
                unresCritExts.remove(InhibitAnyPolicy_Id.toString());
                unresCritExts.remove(SubjectAlternativeName_Id.toString());
                unresCritExts.remove(KeyUsage_Id.toString());
                unresCritExts.remove(ExtendedKeyUsage_Id.toString());

                if (!unresCritExts.isEmpty())
                    throw new CertPathValidatorException
                        ("Unrecognized critical extension(s)", null, null, -1,
                         PKIXReason.UNRECOGNIZED_CRIT_EXT);
            }
        }

        /*
         * if this is the target certificate (init=true), then we are
         * not able to do any more verification, so just return
         */
        if (currState.isInitial()) {
            return;
        }

        /* we don't perform any validation of the trusted cert */
        if (!isTrustedCert) {
            /* Make sure this is a CA cert */
            if (cert.getBasicConstraints() == -1) {
                throw new CertificateException("cert is NOT a CA cert");
            }

            /*
             * Check keyUsage extension
             */
            KeyChecker.verifyCAKeyUsage(cert);
        }
    }

    /**
     * Verifies whether the input certificate completes the path.
     * First checks the cert against each trust anchor that was specified,
     * in order, and returns true if the cert matches the trust anchor
     * specified as a certificate or has the same key and subject of an anchor
     * specified as a trusted {pubkey, caname} pair.
     * If no match has been found, does a second check of the cert against
     * anchors specified as a trusted {pubkey, caname} pair to see if the cert
     * was issued by that anchor.
     * Returns false if none of the trust anchors are valid for this cert.
     *
     * @param cert the certificate to test
     * @return a boolean value indicating whether the cert completes the path.
     */
    @Override
    boolean isPathCompleted(X509Certificate cert) {
        List<TrustAnchor> otherAnchors = new ArrayList<>();
        // first, check if cert is already trusted
        for (TrustAnchor anchor : trustAnchors) {
            if (anchor.getTrustedCert() != null) {
                if (cert.equals(anchor.getTrustedCert())) {
                    this.trustAnchor = anchor;
                    return true;
                } else {
                    continue;
                }
            }
            X500Principal principal = anchor.getCA();
            PublicKey publicKey = anchor.getCAPublicKey();

            if (principal != null && publicKey != null &&
                    principal.equals(cert.getSubjectX500Principal())) {
                if (publicKey.equals(cert.getPublicKey())) {
                    // the cert itself is a trust anchor
                    this.trustAnchor = anchor;
                    return true;
                }
                // else, it is a self-issued certificate of the anchor
            }
            otherAnchors.add(anchor);
        }
        // next, check if cert is issued by anchor specified by key/name
        for (TrustAnchor anchor : otherAnchors) {
            X500Principal principal = anchor.getCA();
            PublicKey publicKey = anchor.getCAPublicKey();
            // Check subject/issuer name chaining
            if (principal == null ||
                    !principal.equals(cert.getIssuerX500Principal())) {
                continue;
            }

            // skip anchor if it contains a DSA key with no DSA params
            if (PKIX.isDSAPublicKeyWithoutParams(publicKey)) {
                continue;
            }

            /*
             * Check signature
             */
            try {
                cert.verify(publicKey, buildParams.sigProvider());
            } catch (InvalidKeyException ike) {
                if (debug != null) {
                    debug.println("ForwardBuilder.isPathCompleted() invalid "
                                  + "DSA key found");
                }
                continue;
            } catch (GeneralSecurityException e){
                if (debug != null) {
                    debug.println("ForwardBuilder.isPathCompleted() " +
                                  "unexpected exception");
                    e.printStackTrace();
                }
                continue;
            }

            this.trustAnchor = anchor;
            return true;
        }

        return false;
    }

    /** Adds the certificate to the certPathList
     *
     * @param cert the certificate to be added
     * @param certPathList the certification path list
     */
    @Override
    void addCertToPath(X509Certificate cert,
                       LinkedList<X509Certificate> certPathList)
    {
        certPathList.addFirst(cert);
    }

    /** Removes final certificate from the certPathList
     *
     * @param certPathList the certification path list
     */
    @Override
    void removeFinalCertFromPath(LinkedList<X509Certificate> certPathList) {
        certPathList.removeFirst();
    }
}
