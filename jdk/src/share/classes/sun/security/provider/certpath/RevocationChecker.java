/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.AccessController;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedAction;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.CertPathValidatorException.BasicReason;
import java.security.cert.Extension;
import java.security.cert.*;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.security.auth.x500.X500Principal;

import static sun.security.provider.certpath.OCSP.*;
import static sun.security.provider.certpath.PKIX.*;
import sun.security.action.GetPropertyAction;
import sun.security.x509.*;
import static sun.security.x509.PKIXExtensions.*;
import sun.security.util.Debug;

class RevocationChecker extends PKIXRevocationChecker {

    private static final Debug debug = Debug.getInstance("certpath");

    private TrustAnchor anchor;
    private ValidatorParams params;
    private boolean onlyEE;
    private boolean softFail;
    private boolean crlDP;
    private URI responderURI;
    private X509Certificate responderCert;
    private List<CertStore> certStores;
    private Map<X509Certificate, byte[]> ocspResponses;
    private List<Extension> ocspExtensions;
    private boolean legacy;

    // state variables
    private X509Certificate issuerCert;
    private PublicKey prevPubKey;
    private boolean crlSignFlag;

    private enum Mode { PREFER_OCSP, PREFER_CRLS, ONLY_CRLS };
    private Mode mode = Mode.PREFER_OCSP;

    private static class RevocationProperties {
        boolean onlyEE;
        boolean ocspEnabled;
        boolean crlDPEnabled;
        String ocspUrl;
        String ocspSubject;
        String ocspIssuer;
        String ocspSerial;
    }

    RevocationChecker() {
        legacy = false;
    }

    RevocationChecker(TrustAnchor anchor, ValidatorParams params)
        throws CertPathValidatorException
    {
        legacy = true;
        init(anchor, params);
    }

    void init(TrustAnchor anchor, ValidatorParams params)
        throws CertPathValidatorException
    {
        RevocationProperties rp = getRevocationProperties();
        URI uri = getOCSPResponder();
        responderURI = (uri == null) ? toURI(rp.ocspUrl) : uri;
        X509Certificate cert = getOCSPResponderCert();
        responderCert = (cert == null)
                        ? getResponderCert(rp, params.trustAnchors(),
                                           params.certStores())
                        : cert;
        Set<Option> options = getOptions();
        for (Option option : options) {
            switch (option) {
            case ONLY_END_ENTITY:
            case PREFER_CRLS:
            case SOFT_FAIL:
                break;
            default:
                throw new CertPathValidatorException(
                    "Unrecognized revocation parameter option: " + option);
            }
        }

        // set mode, only end entity flag
        if (legacy) {
            mode = (rp.ocspEnabled) ? Mode.PREFER_OCSP : Mode.ONLY_CRLS;
            onlyEE = rp.onlyEE;
        } else {
            if (options.contains(Option.PREFER_CRLS)) {
                mode = Mode.PREFER_CRLS;
            }
            onlyEE = options.contains(Option.ONLY_END_ENTITY);
        }
        softFail = options.contains(Option.SOFT_FAIL);
        if (legacy) {
            crlDP = rp.crlDPEnabled;
        } else {
            crlDP = true;
        }
        ocspResponses = getOCSPResponses();
        ocspExtensions = getOCSPExtensions();

        this.anchor = anchor;
        this.params = params;
        this.certStores = new ArrayList<>(params.certStores());
        try {
            this.certStores.add(CertStore.getInstance("Collection",
                new CollectionCertStoreParameters(params.certificates())));
        } catch (InvalidAlgorithmParameterException |
                 NoSuchAlgorithmException e) {
            // should never occur but not necessarily fatal, so log it,
            // ignore and continue
            if (debug != null) {
                debug.println("RevocationChecker: " +
                              "error creating Collection CertStore: " + e);
            }
        }
    }

    private static URI toURI(String uriString)
        throws CertPathValidatorException
    {
        try {
            if (uriString != null) {
                return new URI(uriString);
            }
            return null;
        } catch (URISyntaxException e) {
            throw new CertPathValidatorException(
                "cannot parse ocsp.responderURL property", e);
        }
    }

    private static RevocationProperties getRevocationProperties() {
        return AccessController.doPrivileged(
            new PrivilegedAction<RevocationProperties>() {
                public RevocationProperties run() {
                    RevocationProperties rp = new RevocationProperties();
                    String onlyEE = Security.getProperty(
                        "com.sun.security.onlyCheckRevocationOfEECert");
                    rp.onlyEE = onlyEE != null
                                && onlyEE.equalsIgnoreCase("true");
                    String ocspEnabled = Security.getProperty("ocsp.enable");
                    rp.ocspEnabled = ocspEnabled != null
                                     && ocspEnabled.equalsIgnoreCase("true");
                    rp.ocspUrl = Security.getProperty("ocsp.responderURL");
                    rp.ocspSubject
                        = Security.getProperty("ocsp.responderCertSubjectName");
                    rp.ocspIssuer
                        = Security.getProperty("ocsp.responderCertIssuerName");
                    rp.ocspSerial
                        = Security.getProperty("ocsp.responderCertSerialNumber");
                    rp.crlDPEnabled
                        = Boolean.getBoolean("com.sun.security.enableCRLDP");
                    return rp;
                }
            }
        );
    }

    private static X509Certificate getResponderCert(RevocationProperties rp,
                                                    Set<TrustAnchor> anchors,
                                                    List<CertStore> stores)
        throws CertPathValidatorException
    {
        if (rp.ocspSubject != null) {
            return getResponderCert(rp.ocspSubject, anchors, stores);
        } else if (rp.ocspIssuer != null && rp.ocspSerial != null) {
            return getResponderCert(rp.ocspIssuer, rp.ocspSerial,
                                    anchors, stores);
        } else if (rp.ocspIssuer != null || rp.ocspSerial != null) {
            throw new CertPathValidatorException(
                "Must specify both ocsp.responderCertIssuerName and " +
                "ocsp.responderCertSerialNumber properties");
        }
        return null;
    }

    private static X509Certificate getResponderCert(String subject,
                                                    Set<TrustAnchor> anchors,
                                                    List<CertStore> stores)
        throws CertPathValidatorException
    {
        X509CertSelector sel = new X509CertSelector();
        try {
            sel.setSubject(new X500Principal(subject));
        } catch (IllegalArgumentException e) {
            throw new CertPathValidatorException(
                "cannot parse ocsp.responderCertSubjectName property", e);
        }
        return getResponderCert(sel, anchors, stores);
    }

    private static X509Certificate getResponderCert(String issuer,
                                                    String serial,
                                                    Set<TrustAnchor> anchors,
                                                    List<CertStore> stores)
        throws CertPathValidatorException
    {
        X509CertSelector sel = new X509CertSelector();
        try {
            sel.setIssuer(new X500Principal(issuer));
        } catch (IllegalArgumentException e) {
            throw new CertPathValidatorException(
                "cannot parse ocsp.responderCertIssuerName property", e);
        }
        try {
            sel.setSerialNumber(new BigInteger(stripOutSeparators(serial), 16));
        } catch (NumberFormatException e) {
            throw new CertPathValidatorException(
                "cannot parse ocsp.responderCertSerialNumber property", e);
        }
        return getResponderCert(sel, anchors, stores);
    }

    private static X509Certificate getResponderCert(X509CertSelector sel,
                                                    Set<TrustAnchor> anchors,
                                                    List<CertStore> stores)
        throws CertPathValidatorException
    {
        // first check TrustAnchors
        for (TrustAnchor anchor : anchors) {
            X509Certificate cert = anchor.getTrustedCert();
            if (cert == null) {
                continue;
            }
            if (sel.match(cert)) {
                return cert;
            }
        }
        // now check CertStores
        for (CertStore store : stores) {
            try {
                Collection<? extends Certificate> certs =
                    store.getCertificates(sel);
                if (!certs.isEmpty()) {
                    return (X509Certificate)certs.iterator().next();
                }
            } catch (CertStoreException e) {
                // ignore and try next CertStore
                if (debug != null) {
                    debug.println("CertStore exception:" + e);
                }
                continue;
            }
        }
        throw new CertPathValidatorException(
            "Cannot find the responder's certificate " +
            "(set using the OCSP security properties).");
    }

    @Override
    public void init(boolean forward) throws CertPathValidatorException {
        if (forward) {
            throw new
                CertPathValidatorException("forward checking not supported");
        } else {
            if (anchor != null) {
                issuerCert = anchor.getTrustedCert();
                prevPubKey = (issuerCert != null) ? issuerCert.getPublicKey()
                                                  : anchor.getCAPublicKey();
            }
            crlSignFlag = true;
        }
    }

    @Override
    public boolean isForwardCheckingSupported() {
        return false;
    }

    @Override
    public Set<String> getSupportedExtensions() {
        return null;
    }

    @Override
    public void check(Certificate cert, Collection<String> unresolvedCritExts)
        throws CertPathValidatorException
    {
        X509Certificate xcert = (X509Certificate)cert;
        if (onlyEE && xcert.getBasicConstraints() != -1) {
            if (debug != null) {
                debug.println("Skipping revocation check, not end entity cert");
            }
        } else {
            check(xcert, unresolvedCritExts, prevPubKey, crlSignFlag);
        }
        updateState(xcert);
    }

    void check(X509Certificate xcert, Collection<String> unresolvedCritExts,
               PublicKey pubKey, boolean crlSignFlag)
        throws CertPathValidatorException
    {
        try {
            switch (mode) {
                case PREFER_OCSP:
                    checkOCSP(xcert, unresolvedCritExts);
                    break;
                case PREFER_CRLS:
                case ONLY_CRLS:
                    checkCRLs(xcert, unresolvedCritExts, null,
                              pubKey, crlSignFlag);
                    break;
            }
        } catch (CertPathValidatorException e) {
            if (e.getReason() == BasicReason.REVOKED) {
                throw e;
            }
            CertPathValidatorException cause = e;
            if (softFail && e instanceof NetworkFailureException) {
                if (mode == Mode.ONLY_CRLS) return;
            }
            // Rethrow the exception if ONLY_CRLS
            if (mode == Mode.ONLY_CRLS) {
                throw e;
            }
            // Otherwise, failover
            if (debug != null) {
                debug.println("RevocationChecker.check() " + e.getMessage());
                debug.println("RevocationChecker.check() preparing to failover");
            }
            try {
                switch (mode) {
                    case PREFER_OCSP:
                        checkCRLs(xcert, unresolvedCritExts, null,
                                  pubKey, crlSignFlag);
                        break;
                    case PREFER_CRLS:
                        checkOCSP(xcert, unresolvedCritExts);
                        break;
                }
            } catch (CertPathValidatorException x) {
                if (debug != null) {
                    debug.println("RevocationChecker.check() failover failed");
                    debug.println("RevocationChecker.check() " + x.getMessage());
                }
                if (x.getReason() == BasicReason.REVOKED) {
                    throw x;
                }
                if (cause != null) {
                    if (softFail && cause instanceof NetworkFailureException) {
                        return;
                    } else {
                        cause.addSuppressed(x);
                        throw cause;
                    }
                }
                if (softFail && x instanceof NetworkFailureException) {
                    return;
                }
                throw x;
            }
        }
    }

    private void updateState(X509Certificate cert)
        throws CertPathValidatorException
    {
        issuerCert = cert;

        // Make new public key if parameters are missing
        PublicKey pubKey = cert.getPublicKey();
        if (PKIX.isDSAPublicKeyWithoutParams(pubKey)) {
            // pubKey needs to inherit DSA parameters from prev key
            pubKey = BasicChecker.makeInheritedParamsKey(pubKey, prevPubKey);
        }
        prevPubKey = pubKey;
        crlSignFlag = certCanSignCrl(cert);
    }

    // Maximum clock skew in milliseconds (15 minutes) allowed when checking
    // validity of CRLs
    private static final long MAX_CLOCK_SKEW = 900000;
    private void checkCRLs(X509Certificate cert,
                           Collection<String> unresolvedCritExts,
                           Set<X509Certificate> stackedCerts,
                           PublicKey pubKey, boolean signFlag)
        throws CertPathValidatorException
    {
        checkCRLs(cert, pubKey, signFlag, true,
                  stackedCerts, params.trustAnchors());
    }

    private void checkCRLs(X509Certificate cert, PublicKey prevKey,
                           boolean signFlag, boolean allowSeparateKey,
                           Set<X509Certificate> stackedCerts,
                           Set<TrustAnchor> anchors)
        throws CertPathValidatorException
    {
        if (debug != null) {
            debug.println("RevocationChecker.checkCRLs()" +
                          " ---checking revocation status ...");
        }

        // reject circular dependencies - RFC 3280 is not explicit on how
        // to handle this, so we feel it is safest to reject them until
        // the issue is resolved in the PKIX WG.
        if (stackedCerts != null && stackedCerts.contains(cert)) {
            if (debug != null) {
                debug.println("RevocationChecker.checkCRLs()" +
                              " circular dependency");
            }
            throw new CertPathValidatorException
                ("Could not determine revocation status", null, null, -1,
                 BasicReason.UNDETERMINED_REVOCATION_STATUS);
        }

        Set<X509CRL> possibleCRLs = new HashSet<>();
        Set<X509CRL> approvedCRLs = new HashSet<>();
        X509CRLSelector sel = new X509CRLSelector();
        sel.setCertificateChecking(cert);
        CertPathHelper.setDateAndTime(sel, params.date(), MAX_CLOCK_SKEW);

        // First, check user-specified CertStores
        NetworkFailureException nfe = null;
        for (CertStore store : certStores) {
            try {
                for (CRL crl : store.getCRLs(sel)) {
                    possibleCRLs.add((X509CRL)crl);
                }
            } catch (CertStoreException e) {
                if (debug != null) {
                    debug.println("RevocationChecker.checkCRLs() " +
                                  "CertStoreException: " + e.getMessage());
                }
                if (softFail && nfe == null &&
                    CertStoreHelper.isCausedByNetworkIssue(store.getType(),e)) {
                    // save this exception, we may need to throw it later
                    nfe = new NetworkFailureException(e);
                }
            }
        }

        if (debug != null) {
            debug.println("RevocationChecker.checkCRLs() " +
                          "possible crls.size() = " + possibleCRLs.size());
        }
        boolean[] reasonsMask = new boolean[9];
        if (!possibleCRLs.isEmpty()) {
            // Now that we have a list of possible CRLs, see which ones can
            // be approved
            approvedCRLs.addAll(verifyPossibleCRLs(possibleCRLs, cert, prevKey,
                                                   signFlag, reasonsMask,
                                                   anchors));
        }

        if (debug != null) {
            debug.println("RevocationChecker.checkCRLs() " +
                          "approved crls.size() = " + approvedCRLs.size());
        }

        // make sure that we have at least one CRL that _could_ cover
        // the certificate in question and all reasons are covered
        if (!approvedCRLs.isEmpty() &&
            Arrays.equals(reasonsMask, ALL_REASONS))
        {
            checkApprovedCRLs(cert, approvedCRLs);
        } else {
            // Check Distribution Points
            // all CRLs returned by the DP Fetcher have also been verified
            try {
                if (crlDP) {
                    approvedCRLs.addAll(DistributionPointFetcher.getCRLs(
                                        sel, signFlag, prevKey,
                                        params.sigProvider(), certStores,
                                        reasonsMask, anchors, params.date()));
                }
            } catch (CertStoreException e) {
                if (softFail && e instanceof CertStoreTypeException) {
                    CertStoreTypeException cste = (CertStoreTypeException)e;
                    if (CertStoreHelper.isCausedByNetworkIssue(cste.getType(),
                                                               e)) {
                        throw new NetworkFailureException(e);
                    }
                }
                throw new CertPathValidatorException(e);
            }
            if (!approvedCRLs.isEmpty() &&
                Arrays.equals(reasonsMask, ALL_REASONS))
            {
                checkApprovedCRLs(cert, approvedCRLs);
            } else {
                if (allowSeparateKey) {
                    try {
                        verifyWithSeparateSigningKey(cert, prevKey, signFlag,
                                                     stackedCerts);
                        return;
                    } catch (CertPathValidatorException cpve) {
                        if (nfe != null) {
                            // if a network issue previously prevented us from
                            // retrieving a CRL from one of the user-specified
                            // CertStores and SOFT_FAIL is enabled, throw it now
                            // so it can be handled appropriately
                            throw nfe;
                        }
                        throw cpve;
                    }
                } else {
                    if (nfe != null) {
                        // if a network issue previously prevented us from
                        // retrieving a CRL from one of the user-specified
                        // CertStores and SOFT_FAIL is enabled, throw it now
                        // so it can be handled appropriately
                        throw nfe;
                    }
                    throw new CertPathValidatorException
                    ("Could not determine revocation status", null, null, -1,
                     BasicReason.UNDETERMINED_REVOCATION_STATUS);
                }
            }
        }
    }

    private void checkApprovedCRLs(X509Certificate cert,
                                   Set<X509CRL> approvedCRLs)
        throws CertPathValidatorException
    {
        // See if the cert is in the set of approved crls.
        if (debug != null) {
            BigInteger sn = cert.getSerialNumber();
            debug.println("RevocationChecker.checkApprovedCRLs() " +
                          "starting the final sweep...");
            debug.println("RevocationChecker.checkApprovedCRLs()" +
                          " cert SN: " + sn.toString());
        }

        CRLReason reasonCode = CRLReason.UNSPECIFIED;
        X509CRLEntryImpl entry = null;
        for (X509CRL crl : approvedCRLs) {
            X509CRLEntry e = crl.getRevokedCertificate(cert);
            if (e != null) {
                try {
                    entry = X509CRLEntryImpl.toImpl(e);
                } catch (CRLException ce) {
                    throw new CertPathValidatorException(ce);
                }
                if (debug != null) {
                    debug.println("RevocationChecker.checkApprovedCRLs()"
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
                    unresCritExts.remove(ReasonCode_Id.toString());
                    unresCritExts.remove(CertificateIssuer_Id.toString());
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

    private void checkOCSP(X509Certificate cert,
                           Collection<String> unresolvedCritExts)
        throws CertPathValidatorException
    {
        X509CertImpl currCert = null;
        try {
            currCert = X509CertImpl.toImpl(cert);
        } catch (CertificateException ce) {
            throw new CertPathValidatorException(ce);
        }

        URI responderURI = (this.responderURI != null)
                           ? this.responderURI : getOCSPServerURI(currCert);

        X509Certificate respCert = (responderCert == null) ? issuerCert
                                                           : responderCert;

        // The algorithm constraints of the OCSP trusted responder certificate
        // does not need to be checked in this code. The constraints will be
        // checked when the responder's certificate is validated.

        OCSPResponse response = null;
        CertId certId = null;
        try {
            if (issuerCert != null) {
                certId = new CertId(issuerCert,
                                    currCert.getSerialNumberObject());
            } else {
                // must be an anchor name and key
                certId = new CertId(anchor.getCA(), anchor.getCAPublicKey(),
                                    currCert.getSerialNumberObject());
            }

            // check if there is a cached OCSP response available
            byte[] responseBytes = ocspResponses.get(cert);
            if (responseBytes != null) {
                if (debug != null) {
                    debug.println("Found cached OCSP response");
                }
                response = new OCSPResponse(responseBytes);

                // verify the response
                byte[] nonce = null;
                for (Extension ext : ocspExtensions) {
                    if (ext.getId().equals("1.3.6.1.5.5.7.48.1.2")) {
                        nonce = ext.getValue();
                    }
                }
                response.verify(Collections.singletonList(certId), respCert,
                                params.date(), nonce);

            } else {
                response = OCSP.check(Collections.singletonList(certId),
                                      responderURI, respCert, params.date(),
                                      ocspExtensions);
            }
        } catch (IOException e) {
            throw new CertPathValidatorException(e);
        }

        RevocationStatus rs =
            (RevocationStatus)response.getSingleResponse(certId);
        RevocationStatus.CertStatus certStatus = rs.getCertStatus();
        if (certStatus == RevocationStatus.CertStatus.REVOKED) {
            Throwable t = new CertificateRevokedException(
                rs.getRevocationTime(), rs.getRevocationReason(),
                respCert.getSubjectX500Principal(), rs.getSingleExtensions());
            throw new CertPathValidatorException(t.getMessage(), t, null,
                                                 -1, BasicReason.REVOKED);
        } else if (certStatus == RevocationStatus.CertStatus.UNKNOWN) {
            throw new CertPathValidatorException(
                "Certificate's revocation status is unknown", null,
                params.certPath(), -1,
                BasicReason.UNDETERMINED_REVOCATION_STATUS);
        }
    }

    /*
     * Removes any non-hexadecimal characters from a string.
     */
    private static final String HEX_DIGITS = "0123456789ABCDEFabcdef";
    private static String stripOutSeparators(String value) {
        char[] chars = value.toCharArray();
        StringBuilder hexNumber = new StringBuilder();
        for (int i = 0; i < chars.length; i++) {
            if (HEX_DIGITS.indexOf(chars[i]) != -1) {
                hexNumber.append(chars[i]);
            }
        }
        return hexNumber.toString();
    }

    private static URI getOCSPServerURI(X509CertImpl cert)
        throws CertPathValidatorException
    {
        // Examine the certificate's AuthorityInfoAccess extension
        AuthorityInfoAccessExtension aia =
            cert.getAuthorityInfoAccessExtension();
        if (aia == null) {
            throw new CertPathValidatorException(
                "Must specify the location of an OCSP Responder");
        }

        List<AccessDescription> descriptions = aia.getAccessDescriptions();
        for (AccessDescription description : descriptions) {
            if (description.getAccessMethod().equals((Object)
                AccessDescription.Ad_OCSP_Id)) {

                GeneralName generalName = description.getAccessLocation();
                if (generalName.getType() == GeneralNameInterface.NAME_URI) {
                    URIName uri = (URIName)generalName.getName();
                    return uri.getURI();
                }
            }
        }

        throw new CertPathValidatorException(
            "Cannot find the location of the OCSP Responder");
    }

    /**
     * Checks that a cert can be used to verify a CRL.
     *
     * @param cert an X509Certificate to check
     * @return a boolean specifying if the cert is allowed to vouch for the
     *         validity of a CRL
     */
    static boolean certCanSignCrl(X509Certificate cert) {
        // if the cert doesn't include the key usage ext, or
        // the key usage ext asserts cRLSigning, return true,
        // otherwise return false.
        boolean[] keyUsage = cert.getKeyUsage();
        if (keyUsage != null) {
            return keyUsage[6];
        }
        return false;
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
     * @param trustAnchors a <code>Set</code> of <code>TrustAnchor</code>s>
     * @return a collection of approved crls (or an empty collection)
     */
    private static final boolean[] ALL_REASONS =
        {true, true, true, true, true, true, true, true, true};
    private Collection<X509CRL> verifyPossibleCRLs(Set<X509CRL> crls,
                                                   X509Certificate cert,
                                                   PublicKey prevKey,
                                                   boolean signFlag,
                                                   boolean[] reasonsMask,
                                                   Set<TrustAnchor> anchors)
        throws CertPathValidatorException
    {
        try {
            X509CertImpl certImpl = X509CertImpl.toImpl(cert);
            if (debug != null) {
                debug.println("RevocationChecker.verifyPossibleCRLs: " +
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
                DistributionPoint point = new DistributionPoint(
                     new GeneralNames().add(new GeneralName(certIssuer)),
                     null, null);
                points = Collections.singletonList(point);
            } else {
                points = ext.get(CRLDistributionPointsExtension.POINTS);
            }
            Set<X509CRL> results = new HashSet<>();
            for (DistributionPoint point : points) {
                for (X509CRL crl : crls) {
                    if (DistributionPointFetcher.verifyCRL(
                            certImpl, point, crl, reasonsMask, signFlag,
                            prevKey, params.sigProvider(), anchors,
                            certStores, params.date()))
                    {
                        results.add(crl);
                    }
                }
                if (Arrays.equals(reasonsMask, ALL_REASONS))
                    break;
            }
            return results;
        } catch (CertificateException | CRLException | IOException e) {
            if (debug != null) {
                debug.println("Exception while verifying CRL: "+e.getMessage());
                e.printStackTrace();
            }
            return Collections.emptySet();
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
    private void verifyWithSeparateSigningKey(X509Certificate cert,
                                              PublicKey prevKey,
                                              boolean signFlag,
                                              Set<X509Certificate> stackedCerts)
        throws CertPathValidatorException
    {
        String msg = "revocation status";
        if (debug != null) {
            debug.println(
                "RevocationChecker.verifyWithSeparateSigningKey()" +
                " ---checking " + msg + "...");
        }

        // reject circular dependencies - RFC 3280 is not explicit on how
        // to handle this, so we feel it is safest to reject them until
        // the issue is resolved in the PKIX WG.
        if ((stackedCerts != null) && stackedCerts.contains(cert)) {
            if (debug != null) {
                debug.println(
                    "RevocationChecker.verifyWithSeparateSigningKey()" +
                    " circular dependency");
            }
            throw new CertPathValidatorException
                ("Could not determine revocation status", null, null,
                 -1, BasicReason.UNDETERMINED_REVOCATION_STATUS);
        }

        // Try to find another key that might be able to sign
        // CRLs vouching for this cert.
        // If prevKey wasn't trusted, maybe we just didn't have the right
        // path to it. Don't rule that key out.
        if (!signFlag) {
            buildToNewKey(cert, null, stackedCerts);
        } else {
            buildToNewKey(cert, prevKey, stackedCerts);
        }
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
    private static final boolean [] CRL_SIGN_USAGE =
        { false, false, false, false, false, false, true };
    private void buildToNewKey(X509Certificate currCert,
                               PublicKey prevKey,
                               Set<X509Certificate> stackedCerts)
        throws CertPathValidatorException
    {

        if (debug != null) {
            debug.println("RevocationChecker.buildToNewKey()" +
                          " starting work");
        }
        Set<PublicKey> badKeys = new HashSet<>();
        if (prevKey != null) {
            badKeys.add(prevKey);
        }
        X509CertSelector certSel = new RejectKeySelector(badKeys);
        certSel.setSubject(currCert.getIssuerX500Principal());
        certSel.setKeyUsage(CRL_SIGN_USAGE);

        Set<TrustAnchor> newAnchors = anchor == null ?
                                      params.trustAnchors() :
                                      Collections.singleton(anchor);

        PKIXBuilderParameters builderParams;
        try {
            builderParams = new PKIXBuilderParameters(newAnchors, certSel);
        } catch (InvalidAlgorithmParameterException iape) {
            throw new RuntimeException(iape); // should never occur
        }
        builderParams.setInitialPolicies(params.initialPolicies());
        builderParams.setCertStores(certStores);
        builderParams.setExplicitPolicyRequired
            (params.explicitPolicyRequired());
        builderParams.setPolicyMappingInhibited
            (params.policyMappingInhibited());
        builderParams.setAnyPolicyInhibited(params.anyPolicyInhibited());
        // Policy qualifiers must be rejected, since we don't have
        // any way to convey them back to the application.
        // That's the default, so no need to write code.
        builderParams.setDate(params.date());
        // CertPathCheckers need to be cloned to start from fresh state
        builderParams.setCertPathCheckers(
            params.getPKIXParameters().getCertPathCheckers());
        builderParams.setSigProvider(params.sigProvider());

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
                    debug.println("RevocationChecker.buildToNewKey: " +
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
                    debug.println("RevocationChecker.buildToNewKey()" +
                                  " about to try build ...");
                }
                PKIXCertPathBuilderResult cpbr =
                    (PKIXCertPathBuilderResult)builder.build(builderParams);

                if (debug != null) {
                    debug.println("RevocationChecker.buildToNewKey()" +
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
                        X509Certificate cert = (X509Certificate)cpList.get(i);

                        if (debug != null) {
                            debug.println("RevocationChecker.buildToNewKey()"
                                          + " index " + i + " checking "
                                          + cert);
                        }
                        checkCRLs(cert, prevKey2, signFlag, true,
                                  stackedCerts, newAnchors);
                        signFlag = certCanSignCrl(cert);
                        prevKey2 = cert.getPublicKey();
                    }
                } catch (CertPathValidatorException cpve) {
                    // ignore it and try to get another key
                    badKeys.add(cpbr.getPublicKey());
                    continue;
                }

                if (debug != null) {
                    debug.println("RevocationChecker.buildToNewKey()" +
                                  " got key " + cpbr.getPublicKey());
                }
                // Now check revocation on the current cert using that key.
                // If it doesn't check out, try to find a different key.
                // And if we can't find a key, then return false.
                PublicKey newKey = cpbr.getPublicKey();
                try {
                    checkCRLs(currCert, newKey, true, false, null,
                              params.trustAnchors());
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
        @Override
        public boolean match(Certificate cert) {
            if (!super.match(cert))
                return(false);

            if (badKeySet.contains(cert.getPublicKey())) {
                if (debug != null)
                    debug.println("RejectKeySelector.match: bad key");
                return false;
            }

            if (debug != null)
                debug.println("RejectKeySelector.match: returning true");
            return true;
        }

        /**
         * Return a printable representation of the <code>CertSelector</code>.
         *
         * @return a <code>String</code> describing the contents of the
         *         <code>CertSelector</code>
         */
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("RejectKeySelector: [\n");
            sb.append(super.toString());
            sb.append(badKeySet);
            sb.append("]");
            return sb.toString();
        }
    }
}
