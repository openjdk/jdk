/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.HttpURLConnection;
import java.security.cert.CertificateException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CRLReason;
import java.security.cert.Extension;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static sun.security.provider.certpath.OCSPResponse.*;
import sun.security.util.Debug;
import sun.security.x509.AccessDescription;
import sun.security.x509.AuthorityInfoAccessExtension;
import sun.security.x509.GeneralName;
import sun.security.x509.GeneralNameInterface;
import sun.security.x509.URIName;
import sun.security.x509.X509CertImpl;

/**
 * This is a class that checks the revocation status of a certificate(s) using
 * OCSP. It is not a PKIXCertPathChecker and therefore can be used outside of
 * the CertPathValidator framework. It is useful when you want to
 * just check the revocation status of a certificate, and you don't want to
 * incur the overhead of validating all of the certificates in the
 * associated certificate chain.
 *
 * @author Sean Mullan
 */
public final class OCSP {

    private static final Debug debug = Debug.getInstance("certpath");

    private static final int CONNECT_TIMEOUT = 15000; // 15 seconds

    private OCSP() {}

    /**
     * Obtains the revocation status of a certificate using OCSP using the most
     * common defaults. The OCSP responder URI is retrieved from the
     * certificate's AIA extension. The OCSP responder certificate is assumed
     * to be the issuer's certificate (or issued by the issuer CA).
     *
     * @param cert the certificate to be checked
     * @param issuerCert the issuer certificate
     * @return the RevocationStatus
     * @throws IOException if there is an exception connecting to or
     *    communicating with the OCSP responder
     * @throws CertPathValidatorException if an exception occurs while
     *    encoding the OCSP Request or validating the OCSP Response
     */
    public static RevocationStatus check(X509Certificate cert,
        X509Certificate issuerCert)
        throws IOException, CertPathValidatorException {
        CertId certId = null;
        URI responderURI = null;
        try {
            X509CertImpl certImpl = X509CertImpl.toImpl(cert);
            responderURI = getResponderURI(certImpl);
            if (responderURI == null) {
                throw new CertPathValidatorException
                    ("No OCSP Responder URI in certificate");
            }
            certId = new CertId(issuerCert, certImpl.getSerialNumberObject());
        } catch (CertificateException ce) {
            throw new CertPathValidatorException
                ("Exception while encoding OCSPRequest", ce);
        } catch (IOException ioe) {
            throw new CertPathValidatorException
                ("Exception while encoding OCSPRequest", ioe);
        }
        OCSPResponse ocspResponse = check(Collections.singletonList(certId),
            responderURI, issuerCert, null);
        return (RevocationStatus) ocspResponse.getSingleResponse(certId);
    }

    /**
     * Obtains the revocation status of a certificate using OCSP.
     *
     * @param cert the certificate to be checked
     * @param issuerCert the issuer certificate
     * @param responderURI the URI of the OCSP responder
     * @param responderCert the OCSP responder's certificate
     * @param date the time the validity of the OCSP responder's certificate
     *    should be checked against. If null, the current time is used.
     * @return the RevocationStatus
     * @throws IOException if there is an exception connecting to or
     *    communicating with the OCSP responder
     * @throws CertPathValidatorException if an exception occurs while
     *    encoding the OCSP Request or validating the OCSP Response
     */
    public static RevocationStatus check(X509Certificate cert,
        X509Certificate issuerCert, URI responderURI, X509Certificate
        responderCert, Date date)
        throws IOException, CertPathValidatorException {
        CertId certId = null;
        try {
            X509CertImpl certImpl = X509CertImpl.toImpl(cert);
            certId = new CertId(issuerCert, certImpl.getSerialNumberObject());
        } catch (CertificateException ce) {
            throw new CertPathValidatorException
                ("Exception while encoding OCSPRequest", ce);
        } catch (IOException ioe) {
            throw new CertPathValidatorException
                ("Exception while encoding OCSPRequest", ioe);
        }
        OCSPResponse ocspResponse = check(Collections.singletonList(certId),
            responderURI, responderCert, date);
        return (RevocationStatus) ocspResponse.getSingleResponse(certId);
    }

    /**
     * Checks the revocation status of a list of certificates using OCSP.
     *
     * @param certs the CertIds to be checked
     * @param responderURI the URI of the OCSP responder
     * @param responderCert the OCSP responder's certificate
     * @param date the time the validity of the OCSP responder's certificate
     *    should be checked against. If null, the current time is used.
     * @return the OCSPResponse
     * @throws IOException if there is an exception connecting to or
     *    communicating with the OCSP responder
     * @throws CertPathValidatorException if an exception occurs while
     *    encoding the OCSP Request or validating the OCSP Response
     */
    static OCSPResponse check(List<CertId> certIds, URI responderURI,
        X509Certificate responderCert, Date date)
        throws IOException, CertPathValidatorException {

        byte[] bytes = null;
        try {
            OCSPRequest request = new OCSPRequest(certIds);
            bytes = request.encodeBytes();
        } catch (IOException ioe) {
            throw new CertPathValidatorException
                ("Exception while encoding OCSPRequest", ioe);
        }

        InputStream in = null;
        OutputStream out = null;
        byte[] response = null;
        try {
            URL url = responderURI.toURL();
            if (debug != null) {
                debug.println("connecting to OCSP service at: " + url);
            }
            HttpURLConnection con = (HttpURLConnection)url.openConnection();
            con.setConnectTimeout(CONNECT_TIMEOUT);
            con.setReadTimeout(CONNECT_TIMEOUT);
            con.setDoOutput(true);
            con.setDoInput(true);
            con.setRequestMethod("POST");
            con.setRequestProperty
                ("Content-type", "application/ocsp-request");
            con.setRequestProperty
                ("Content-length", String.valueOf(bytes.length));
            out = con.getOutputStream();
            out.write(bytes);
            out.flush();
            // Check the response
            if (debug != null &&
                con.getResponseCode() != HttpURLConnection.HTTP_OK) {
                debug.println("Received HTTP error: " + con.getResponseCode()
                    + " - " + con.getResponseMessage());
            }
            in = con.getInputStream();
            int contentLength = con.getContentLength();
            if (contentLength == -1) {
                contentLength = Integer.MAX_VALUE;
            }
            response = new byte[contentLength > 2048 ? 2048 : contentLength];
            int total = 0;
            while (total < contentLength) {
                int count = in.read(response, total, response.length - total);
                if (count < 0)
                    break;

                total += count;
                if (total >= response.length && total < contentLength) {
                    response = Arrays.copyOf(response, total * 2);
                }
            }
            response = Arrays.copyOf(response, total);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ioe) {
                    throw ioe;
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ioe) {
                    throw ioe;
                }
            }
        }

        OCSPResponse ocspResponse = null;
        try {
            ocspResponse = new OCSPResponse(response, date, responderCert);
        } catch (IOException ioe) {
            // response decoding exception
            throw new CertPathValidatorException(ioe);
        }
        if (ocspResponse.getResponseStatus() != ResponseStatus.SUCCESSFUL) {
            throw new CertPathValidatorException
                ("OCSP response error: " + ocspResponse.getResponseStatus());
        }

        // Check that the response includes a response for all of the
        // certs that were supplied in the request
        for (CertId certId : certIds) {
            SingleResponse sr = ocspResponse.getSingleResponse(certId);
            if (sr == null) {
                if (debug != null) {
                    debug.println("No response found for CertId: " + certId);
                }
                throw new CertPathValidatorException(
                    "OCSP response does not include a response for a " +
                    "certificate supplied in the OCSP request");
            }
            if (debug != null) {
                debug.println("Status of certificate (with serial number " +
                    certId.getSerialNumber() + ") is: " + sr.getCertStatus());
            }
        }
        return ocspResponse;
    }

    /**
     * Returns the URI of the OCSP Responder as specified in the
     * certificate's Authority Information Access extension, or null if
     * not specified.
     *
     * @param cert the certificate
     * @return the URI of the OCSP Responder, or null if not specified
     */
    public static URI getResponderURI(X509Certificate cert) {
        try {
            return getResponderURI(X509CertImpl.toImpl(cert));
        } catch (CertificateException ce) {
            // treat this case as if the cert had no extension
            return null;
        }
    }

    static URI getResponderURI(X509CertImpl certImpl) {

        // Examine the certificate's AuthorityInfoAccess extension
        AuthorityInfoAccessExtension aia =
            certImpl.getAuthorityInfoAccessExtension();
        if (aia == null) {
            return null;
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
        return null;
    }

    /**
     * The Revocation Status of a certificate.
     */
    public static interface RevocationStatus {
        public enum CertStatus { GOOD, REVOKED, UNKNOWN };

        /**
         * Returns the revocation status.
         */
        CertStatus getCertStatus();
        /**
         * Returns the time when the certificate was revoked, or null
         * if it has not been revoked.
         */
        Date getRevocationTime();
        /**
         * Returns the reason the certificate was revoked, or null if it
         * has not been revoked.
         */
        CRLReason getRevocationReason();

        /**
         * Returns a Map of additional extensions.
         */
        Map<String, Extension> getSingleExtensions();
    }
}
