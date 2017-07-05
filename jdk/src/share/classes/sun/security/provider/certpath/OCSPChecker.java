/*
 * Copyright (c) 2003, 2009, Oracle and/or its affiliates. All rights reserved.
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
import java.util.*;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.Security;
import java.security.cert.*;
import java.security.cert.CertPathValidatorException.BasicReason;
import java.net.URI;
import java.net.URISyntaxException;
import javax.security.auth.x500.X500Principal;

import static sun.security.provider.certpath.OCSP.*;
import sun.security.util.Debug;
import sun.security.x509.*;

/**
 * OCSPChecker is a <code>PKIXCertPathChecker</code> that uses the
 * Online Certificate Status Protocol (OCSP) as specified in RFC 2560
 * <a href="http://www.ietf.org/rfc/rfc2560.txt">
 * http://www.ietf.org/rfc/rfc2560.txt</a>.
 *
 * @author      Ram Marti
 */
class OCSPChecker extends PKIXCertPathChecker {

    static final String OCSP_ENABLE_PROP = "ocsp.enable";
    static final String OCSP_URL_PROP = "ocsp.responderURL";
    static final String OCSP_CERT_SUBJECT_PROP =
        "ocsp.responderCertSubjectName";
    static final String OCSP_CERT_ISSUER_PROP = "ocsp.responderCertIssuerName";
    static final String OCSP_CERT_NUMBER_PROP =
        "ocsp.responderCertSerialNumber";

    private static final String HEX_DIGITS = "0123456789ABCDEFabcdef";
    private static final Debug DEBUG = Debug.getInstance("certpath");
    private static final boolean dump = false;

    private int remainingCerts;

    private X509Certificate[] certs;

    private CertPath cp;

    private PKIXParameters pkixParams;

    private boolean onlyEECert = false;

    /**
     * Default Constructor
     *
     * @param certPath the X509 certification path
     * @param pkixParams the input PKIX parameter set
     * @throws CertPathValidatorException if OCSPChecker can not be created
     */
    OCSPChecker(CertPath certPath, PKIXParameters pkixParams)
        throws CertPathValidatorException {
        this(certPath, pkixParams, false);
    }

    OCSPChecker(CertPath certPath, PKIXParameters pkixParams, boolean onlyEECert)
        throws CertPathValidatorException {

        this.cp = certPath;
        this.pkixParams = pkixParams;
        this.onlyEECert = onlyEECert;
        List<? extends Certificate> tmp = cp.getCertificates();
        certs = tmp.toArray(new X509Certificate[tmp.size()]);
        init(false);
    }

    /**
     * Initializes the internal state of the checker from parameters
     * specified in the constructor
     */
    @Override
    public void init(boolean forward) throws CertPathValidatorException {
        if (!forward) {
            remainingCerts = certs.length + 1;
        } else {
            throw new CertPathValidatorException(
                "Forward checking not supported");
        }
    }

    @Override public boolean isForwardCheckingSupported() {
        return false;
    }

    @Override public Set<String> getSupportedExtensions() {
        return Collections.<String>emptySet();
    }

    /**
     * Sends an OCSPRequest for the certificate to the OCSP Server and
     * processes the response back from the OCSP Server.
     *
     * @param cert the Certificate
     * @param unresolvedCritExts the unresolved critical extensions
     * @exception CertPathValidatorException Exception is thrown if the
     *            certificate has been revoked.
     */
    @Override
    public void check(Certificate cert, Collection<String> unresolvedCritExts)
        throws CertPathValidatorException {

        // Decrement the certificate counter
        remainingCerts--;

        X509CertImpl currCertImpl = null;
        try {
            currCertImpl = X509CertImpl.toImpl((X509Certificate)cert);
        } catch (CertificateException ce) {
            throw new CertPathValidatorException(ce);
        }

        if (onlyEECert && currCertImpl.getBasicConstraints() != -1) {
            if (DEBUG != null) {
                DEBUG.println("Skipping revocation check, not end entity cert");
            }
            return;
        }

        /*
         * OCSP security property values, in the following order:
         *   1. ocsp.responderURL
         *   2. ocsp.responderCertSubjectName
         *   3. ocsp.responderCertIssuerName
         *   4. ocsp.responderCertSerialNumber
         */
        // should cache these properties to avoid calling every time?
        String[] properties = getOCSPProperties();

        // Check whether OCSP is feasible before seeking cert information
        URI uri = getOCSPServerURI(currCertImpl, properties[0]);

        // When responder's subject name is set then the issuer/serial
        // properties are ignored
        X500Principal responderSubjectName = null;
        X500Principal responderIssuerName = null;
        BigInteger responderSerialNumber = null;
        if (properties[1] != null) {
            responderSubjectName = new X500Principal(properties[1]);
        } else if (properties[2] != null && properties[3] != null) {
            responderIssuerName = new X500Principal(properties[2]);
            // remove colon or space separators
            String value = stripOutSeparators(properties[3]);
            responderSerialNumber = new BigInteger(value, 16);
        } else if (properties[2] != null || properties[3] != null) {
            throw new CertPathValidatorException(
                "Must specify both ocsp.responderCertIssuerName and " +
                "ocsp.responderCertSerialNumber properties");
        }

        // If the OCSP responder cert properties are set then the
        // identified cert must be located in the trust anchors or
        // in the cert stores.
        boolean seekResponderCert = false;
        if (responderSubjectName != null || responderIssuerName != null) {
            seekResponderCert = true;
        }

        // Set the issuer certificate to the next cert in the chain
        // (unless we're processing the final cert).
        X509Certificate issuerCert = null;
        boolean seekIssuerCert = true;
        X509Certificate responderCert = null;
        if (remainingCerts < certs.length) {
            issuerCert = certs[remainingCerts];
            seekIssuerCert = false; // done

            // By default, the OCSP responder's cert is the same as the
            // issuer of the cert being validated.
            if (!seekResponderCert) {
                responderCert = issuerCert;
                if (DEBUG != null) {
                    DEBUG.println("Responder's certificate is the same " +
                        "as the issuer of the certificate being validated");
                }
            }
        }

        // Check anchor certs for:
        //    - the issuer cert (of the cert being validated)
        //    - the OCSP responder's cert
        if (seekIssuerCert || seekResponderCert) {

            if (DEBUG != null && seekResponderCert) {
                DEBUG.println("Searching trust anchors for responder's " +
                    "certificate");
            }

            // Extract the anchor certs
            Iterator<TrustAnchor> anchors
                = pkixParams.getTrustAnchors().iterator();
            if (!anchors.hasNext()) {
                throw new CertPathValidatorException(
                    "Must specify at least one trust anchor");
            }

            X500Principal certIssuerName =
                currCertImpl.getIssuerX500Principal();
            while (anchors.hasNext() && (seekIssuerCert || seekResponderCert)) {

                TrustAnchor anchor = anchors.next();
                X509Certificate anchorCert = anchor.getTrustedCert();
                X500Principal anchorSubjectName =
                    anchorCert.getSubjectX500Principal();

                if (dump) {
                    System.out.println("Issuer DN is " + certIssuerName);
                    System.out.println("Subject DN is " + anchorSubjectName);
                }

                // Check if anchor cert is the issuer cert
                if (seekIssuerCert &&
                    certIssuerName.equals(anchorSubjectName)) {

                    issuerCert = anchorCert;
                    seekIssuerCert = false; // done

                    // By default, the OCSP responder's cert is the same as
                    // the issuer of the cert being validated.
                    if (!seekResponderCert && responderCert == null) {
                        responderCert = anchorCert;
                        if (DEBUG != null) {
                            DEBUG.println("Responder's certificate is the" +
                                " same as the issuer of the certificate " +
                                "being validated");
                        }
                    }
                }

                // Check if anchor cert is the responder cert
                if (seekResponderCert) {
                    // Satisfy the responder subject name property only, or
                    // satisfy the responder issuer name and serial number
                    // properties only
                    if ((responderSubjectName != null &&
                         responderSubjectName.equals(anchorSubjectName)) ||
                        (responderIssuerName != null &&
                         responderSerialNumber != null &&
                         responderIssuerName.equals(
                         anchorCert.getIssuerX500Principal()) &&
                         responderSerialNumber.equals(
                         anchorCert.getSerialNumber()))) {

                        responderCert = anchorCert;
                        seekResponderCert = false; // done
                    }
                }
            }
            if (issuerCert == null) {
                throw new CertPathValidatorException(
                    "No trusted certificate for " + currCertImpl.getIssuerDN());
            }

            // Check cert stores if responder cert has not yet been found
            if (seekResponderCert) {
                if (DEBUG != null) {
                    DEBUG.println("Searching cert stores for responder's " +
                        "certificate");
                }
                X509CertSelector filter = null;
                if (responderSubjectName != null) {
                    filter = new X509CertSelector();
                    filter.setSubject(responderSubjectName);
                } else if (responderIssuerName != null &&
                    responderSerialNumber != null) {
                    filter = new X509CertSelector();
                    filter.setIssuer(responderIssuerName);
                    filter.setSerialNumber(responderSerialNumber);
                }
                if (filter != null) {
                    List<CertStore> certStores = pkixParams.getCertStores();
                    for (CertStore certStore : certStores) {
                        Iterator i = null;
                        try {
                            i = certStore.getCertificates(filter).iterator();
                        } catch (CertStoreException cse) {
                            // ignore and try next certStore
                            if (DEBUG != null) {
                                DEBUG.println("CertStore exception:" + cse);
                            }
                            continue;
                        }
                        if (i.hasNext()) {
                            responderCert = (X509Certificate) i.next();
                            seekResponderCert = false; // done
                            break;
                        }
                    }
                }
            }
        }

        // Could not find the certificate identified in the OCSP properties
        if (seekResponderCert) {
            throw new CertPathValidatorException(
                "Cannot find the responder's certificate " +
                "(set using the OCSP security properties).");
        }

        CertId certId = null;
        OCSPResponse response = null;
        try {
            certId = new CertId
                (issuerCert, currCertImpl.getSerialNumberObject());
            response = OCSP.check(Collections.singletonList(certId), uri,
                responderCert, pkixParams.getDate());
        } catch (Exception e) {
            if (e instanceof CertPathValidatorException) {
                throw (CertPathValidatorException) e;
            } else {
                // Wrap exceptions in CertPathValidatorException so that
                // we can fallback to CRLs, if enabled.
                throw new CertPathValidatorException(e);
            }
        }

        RevocationStatus rs = (RevocationStatus) response.getSingleResponse(certId);
        RevocationStatus.CertStatus certStatus = rs.getCertStatus();
        if (certStatus == RevocationStatus.CertStatus.REVOKED) {
            Throwable t = new CertificateRevokedException(
                rs.getRevocationTime(), rs.getRevocationReason(),
                responderCert.getSubjectX500Principal(),
                rs.getSingleExtensions());
            throw new CertPathValidatorException(t.getMessage(), t,
                null, -1, BasicReason.REVOKED);
        } else if (certStatus == RevocationStatus.CertStatus.UNKNOWN) {
            throw new CertPathValidatorException(
                "Certificate's revocation status is unknown", null, cp,
                remainingCerts, BasicReason.UNDETERMINED_REVOCATION_STATUS);
        }
    }

    /*
     * The OCSP security property values are in the following order:
     *   1. ocsp.responderURL
     *   2. ocsp.responderCertSubjectName
     *   3. ocsp.responderCertIssuerName
     *   4. ocsp.responderCertSerialNumber
     */
    private static URI getOCSPServerURI(X509CertImpl currCertImpl,
        String responderURL) throws CertPathValidatorException {

        if (responderURL != null) {
            try {
                return new URI(responderURL);
            } catch (URISyntaxException e) {
                throw new CertPathValidatorException(e);
            }
        }

        // Examine the certificate's AuthorityInfoAccess extension
        AuthorityInfoAccessExtension aia =
            currCertImpl.getAuthorityInfoAccessExtension();
        if (aia == null) {
            throw new CertPathValidatorException(
                "Must specify the location of an OCSP Responder");
        }

        List<AccessDescription> descriptions = aia.getAccessDescriptions();
        for (AccessDescription description : descriptions) {
            if (description.getAccessMethod().equals(
                AccessDescription.Ad_OCSP_Id)) {

                GeneralName generalName = description.getAccessLocation();
                if (generalName.getType() == GeneralNameInterface.NAME_URI) {
                    URIName uri = (URIName) generalName.getName();
                    return uri.getURI();
                }
            }
        }

        throw new CertPathValidatorException(
            "Cannot find the location of the OCSP Responder");
    }

    /*
     * Retrieves the values of the OCSP security properties.
     */
    private static String[] getOCSPProperties() {
        final String[] properties = new String[4];

        AccessController.doPrivileged(
            new PrivilegedAction<Void>() {
                public Void run() {
                    properties[0] = Security.getProperty(OCSP_URL_PROP);
                    properties[1] =
                        Security.getProperty(OCSP_CERT_SUBJECT_PROP);
                    properties[2] =
                        Security.getProperty(OCSP_CERT_ISSUER_PROP);
                    properties[3] =
                        Security.getProperty(OCSP_CERT_NUMBER_PROP);
                    return null;
                }
            });

        return properties;
    }

    /*
     * Removes any non-hexadecimal characters from a string.
     */
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
}
