/*
 * Copyright 2003-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.io.IOException;
import java.security.cert.CertPathValidatorException;
import sun.misc.HexDumpEncoder;
import sun.security.x509.*;
import sun.security.util.*;

/**
 * This class can be used to generate an OCSP request and send it over
 * an outputstream. Currently we do not support signing requests
 * The OCSP Request is specified in RFC 2560 and
 * the ASN.1 definition is as follows:
 * <pre>
 *
 * OCSPRequest     ::=     SEQUENCE {
 *      tbsRequest                  TBSRequest,
 *      optionalSignature   [0]     EXPLICIT Signature OPTIONAL }
 *
 *   TBSRequest      ::=     SEQUENCE {
 *      version             [0]     EXPLICIT Version DEFAULT v1,
 *      requestorName       [1]     EXPLICIT GeneralName OPTIONAL,
 *      requestList                 SEQUENCE OF Request,
 *      requestExtensions   [2]     EXPLICIT Extensions OPTIONAL }
 *
 *  Signature       ::=     SEQUENCE {
 *      signatureAlgorithm      AlgorithmIdentifier,
 *      signature               BIT STRING,
 *      certs               [0] EXPLICIT SEQUENCE OF Certificate OPTIONAL
 *   }
 *
 *  Version         ::=             INTEGER  {  v1(0) }
 *
 *  Request         ::=     SEQUENCE {
 *      reqCert                     CertID,
 *      singleRequestExtensions     [0] EXPLICIT Extensions OPTIONAL }
 *
 *  CertID          ::= SEQUENCE {
 *       hashAlgorithm  AlgorithmIdentifier,
 *       issuerNameHash OCTET STRING, -- Hash of Issuer's DN
 *       issuerKeyHash  OCTET STRING, -- Hash of Issuers public key
 *       serialNumber   CertificateSerialNumber
 * }
 *
 * </pre>
 *
 * @author      Ram Marti
 */

class OCSPRequest {

    private static final Debug debug = Debug.getInstance("certpath");
    private static final boolean dump = false;

    // Serial number of the certificates to be checked for revocation
    private SerialNumber serialNumber;

    // Issuer's certificate (for computing certId hash values)
    private X509CertImpl issuerCert;

    // CertId of the certificate to be checked
    private CertId certId = null;

    /*
     * Constructs an OCSPRequest. This constructor is used
     * to construct an unsigned OCSP Request for a single user cert.
     */
    // used by OCSPChecker
    OCSPRequest(X509CertImpl userCert, X509CertImpl issuerCert)
        throws CertPathValidatorException {

        if (issuerCert == null) {
            throw new CertPathValidatorException("Null IssuerCertificate");
        }
        this.issuerCert = issuerCert;
        serialNumber = userCert.getSerialNumberObject();
    }

    // used by OCSPChecker
    byte[] encodeBytes() throws IOException {

        // encode tbsRequest
        DerOutputStream tmp = new DerOutputStream();
        DerOutputStream derSingleReqList  = new DerOutputStream();
        SingleRequest singleRequest = null;

        try {
            singleRequest = new SingleRequest(issuerCert, serialNumber);
        } catch (Exception e) {
            throw new IOException("Error encoding OCSP request");
        }

        certId = singleRequest.getCertId();
        singleRequest.encode(derSingleReqList);
        tmp.write(DerValue.tag_Sequence, derSingleReqList);
        // No extensions supported
        DerOutputStream tbsRequest = new DerOutputStream();
        tbsRequest.write(DerValue.tag_Sequence, tmp);

        // OCSPRequest without the signature
        DerOutputStream ocspRequest = new DerOutputStream();
        ocspRequest.write(DerValue.tag_Sequence, tbsRequest);

        byte[] bytes = ocspRequest.toByteArray();

        if (dump) {
            HexDumpEncoder hexEnc = new HexDumpEncoder();
            System.out.println ("OCSPRequest bytes are... ");
            System.out.println(hexEnc.encode(bytes));
        }

        return(bytes);
    }

    // used by OCSPChecker
    CertId getCertId() {
        return certId;
    }

    private static class SingleRequest {
        private CertId certId;

        // No extensions are set

        private SingleRequest(X509CertImpl cert, SerialNumber serialNo) throws Exception {
            certId = new CertId(cert, serialNo);
        }

        private void encode(DerOutputStream out) throws IOException {
            DerOutputStream tmp = new DerOutputStream();
            certId.encode(tmp);
            out.write(DerValue.tag_Sequence, tmp);
        }

        private CertId getCertId() {
            return certId;
        }
    }
}
