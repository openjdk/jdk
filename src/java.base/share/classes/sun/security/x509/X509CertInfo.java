/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.x509;

import java.io.IOException;

import java.security.cert.*;
import java.util.*;

import sun.security.util.*;
import sun.security.util.HexDumpEncoder;


/**
 * The X509CertInfo class represents X.509 certificate information.
 *
 * <P>X.509 certificates have several base data elements, including:
 *
 * <UL>
 * <LI>The <em>Subject Name</em>, an X.500 Distinguished Name for
 *      the entity (subject) for which the certificate was issued.
 *
 * <LI>The <em>Subject Public Key</em>, the public key of the subject.
 *      This is one of the most important parts of the certificate.
 *
 * <LI>The <em>Validity Period</em>, a time period (e.g. six months)
 *      within which the certificate is valid (unless revoked).
 *
 * <LI>The <em>Issuer Name</em>, an X.500 Distinguished Name for the
 *      Certificate Authority (CA) which issued the certificate.
 *
 * <LI>A <em>Serial Number</em> assigned by the CA, for use in
 *      certificate revocation and other applications.
 * </UL>
 *
 * @author Amit Kapoor
 * @author Hemma Prafullchandra
 * @see DerEncoder
 * @see X509CertImpl
 */
public class X509CertInfo {

    // Certificate attribute names
    public static final String NAME = "info";
    public static final String DN_NAME = "dname";
    public static final String VERSION = CertificateVersion.NAME;
    public static final String SERIAL_NUMBER = CertificateSerialNumber.NAME;
    public static final String ALGORITHM_ID = CertificateAlgorithmId.NAME;
    public static final String ISSUER = "issuer";
    public static final String SUBJECT = "subject";
    public static final String VALIDITY = CertificateValidity.NAME;
    public static final String KEY = CertificateX509Key.NAME;
    public static final String ISSUER_ID = "issuerID";
    public static final String SUBJECT_ID = "subjectID";
    public static final String EXTENSIONS = CertificateExtensions.NAME;

    // X509.v1 data
    protected CertificateVersion version = new CertificateVersion();
    protected CertificateSerialNumber   serialNum = null;
    protected CertificateAlgorithmId    algId = null;
    protected X500Name                  issuer = null;
    protected X500Name                  subject = null;
    protected CertificateValidity       interval = null;
    protected CertificateX509Key        pubKey = null;

    // X509.v2 & v3 extensions
    protected UniqueIdentity   issuerUniqueId = null;
    protected UniqueIdentity  subjectUniqueId = null;

    // X509.v3 extensions
    protected CertificateExtensions     extensions = null;

    // DER encoded CertificateInfo data
    private byte[]      rawCertInfo = null;

    /**
     * Construct an uninitialized X509CertInfo on which <a href="#decode">
     * decode</a> must later be called (or which may be deserialized).
     */
    public X509CertInfo() { }

    /**
     * Unmarshals a certificate from its encoded form, parsing the
     * encoded bytes.  This form of constructor is used by agents which
     * need to examine and use certificate contents.  That is, this is
     * one of the more commonly used constructors.  Note that the buffer
     * must include only a certificate, and no "garbage" may be left at
     * the end.  If you need to ignore data at the end of a certificate,
     * use another constructor.
     *
     * @param cert the encoded bytes, with no trailing data.
     * @exception CertificateParsingException on parsing errors.
     */
    public X509CertInfo(byte[] cert) throws CertificateParsingException {
        try {
            DerValue    in = new DerValue(cert);

            parse(in);
        } catch (IOException e) {
            throw new CertificateParsingException(e);
        }
    }

    /**
     * Unmarshal a certificate from its encoded form, parsing a DER value.
     * This form of constructor is used by agents which need to examine
     * and use certificate contents.
     *
     * @param derVal the der value containing the encoded cert.
     * @exception CertificateParsingException on parsing errors.
     */
    public X509CertInfo(DerValue derVal) throws CertificateParsingException {
        try {
            parse(derVal);
        } catch (IOException e) {
            throw new CertificateParsingException(e);
        }
    }

    /**
     * Appends the certificate to an output stream.
     *
     * @param out an output stream to which the certificate is appended.
     * @exception CertificateException on encoding errors.
     */
    public void encode(DerOutputStream out)
            throws CertificateException {
        if (rawCertInfo == null) {
            emit(out);
            rawCertInfo = out.toByteArray();
        } else {
            out.writeBytes(rawCertInfo.clone());
        }
    }

    /**
     * Returns the encoded certificate info.
     *
     * @exception CertificateEncodingException on encoding information errors.
     */
    public byte[] getEncodedInfo() throws CertificateEncodingException {
        try {
            if (rawCertInfo == null) {
                DerOutputStream tmp = new DerOutputStream();
                emit(tmp);
                rawCertInfo = tmp.toByteArray();
            }
            return rawCertInfo.clone();
        } catch (CertificateException e) {
            throw new CertificateEncodingException(e.toString());
        }
    }

    /**
     * Compares two X509CertInfo objects.  This is false if the
     * certificates are not both X.509 certs, otherwise it
     * compares them as binary data.
     *
     * @param obj the object being compared with this one
     * @return true iff the certificates are equivalent
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        return obj instanceof X509CertInfo other
                && rawCertInfo != null
                && other.rawCertInfo != null
                && Arrays.equals(rawCertInfo, other.rawCertInfo);
    }

    /**
     * Calculates a hash code value for the object.  Objects
     * which are equal will also have the same hashcode.
     */
    @Override
    public int hashCode() {
        return Arrays.hashCode(rawCertInfo);
    }

    /**
     * Returns a printable representation of the certificate.
     */
    public String toString() {

        if (subject == null || pubKey == null || interval == null
            || issuer == null || algId == null || serialNum == null) {
                throw new NullPointerException("X.509 cert is incomplete");
        }
        StringBuilder sb = new StringBuilder();

        sb.append("[\n")
            .append("  ").append(version).append('\n')
            .append("  Subject: ").append(subject).append('\n')
            .append("  Signature Algorithm: ").append(algId).append('\n')
            .append("  Key:  ").append(pubKey).append('\n')
            .append("  ").append(interval).append('\n')
            .append("  Issuer: ").append(issuer).append('\n')
            .append("  ").append(serialNum).append('\n');

        // optional v2, v3 extras
        if (issuerUniqueId != null) {
            sb.append("  Issuer Id:\n").append(issuerUniqueId).append('\n');
        }
        if (subjectUniqueId != null) {
            sb.append("  Subject Id:\n").append(subjectUniqueId).append('\n');
        }
        if (extensions != null) {
            Collection<Extension> allExts = extensions.getAllExtensions();
            Extension[] exts = allExts.toArray(new Extension[0]);
            sb.append("\nCertificate Extensions: ").append(exts.length);
            for (int i = 0; i < exts.length; i++) {
                sb.append("\n[").append(i+1).append("]: ");
                Extension ext = exts[i];
                try {
                    if (OIDMap.getClass(ext.getExtensionId()) == null) {
                        sb.append(ext);
                        byte[] extValue = ext.getExtensionValue();
                        if (extValue != null) {
                            DerOutputStream out = new DerOutputStream();
                            out.putOctetString(extValue);
                            extValue = out.toByteArray();
                            HexDumpEncoder enc = new HexDumpEncoder();
                            sb.append("Extension unknown: ")
                                .append("DER encoded OCTET string =\n")
                                .append(enc.encodeBuffer(extValue))
                                .append('\n');
                        }
                    } else {
                        sb.append(ext); //sub-class exists
                    }
                } catch (Exception e) {
                    sb.append(", Error parsing this extension");
                }
            }
            Map<String,Extension> invalid = extensions.getUnparseableExtensions();
            if (!invalid.isEmpty()) {
                sb.append("\nUnparseable certificate extensions: ")
                    .append(invalid.size());
                int i = 1;
                for (Extension ext : invalid.values()) {
                    sb.append("\n[")
                        .append(i++)
                        .append("]: ")
                        .append(ext);
                }
            }
        }
        sb.append("\n]");
        return sb.toString();
    }

    public CertificateExtensions getExtensions() {
        return extensions;
    }

    public UniqueIdentity getIssuerUniqueId() {
        return issuerUniqueId;
    }

    public UniqueIdentity getSubjectUniqueId() {
        return subjectUniqueId;
    }

    public X500Name getIssuer() {
        return issuer;
    }

    public X500Name getSubject() {
        return subject;
    }

    /*
     * Get the Issuer or Subject name
     */
    private Object getX500Name(String name, boolean getIssuer)
        throws IOException {
        if (name.equalsIgnoreCase(X509CertInfo.DN_NAME)) {
            return getIssuer ? issuer : subject;
        } else if (name.equalsIgnoreCase("x500principal")) {
            return getIssuer ? issuer.asX500Principal()
                             : subject.asX500Principal();
        } else {
            throw new IOException("Attribute name not recognized.");
        }
    }

    /*
     * This routine unmarshals the certificate information.
     */
    private void parse(DerValue val)
    throws CertificateParsingException, IOException {
        DerInputStream  in;
        DerValue        tmp;

        if (val.tag != DerValue.tag_Sequence) {
            throw new CertificateParsingException("signed fields invalid");
        }
        rawCertInfo = val.toByteArray();

        in = val.data;

        // Version
        tmp = in.getDerValue();
        if (tmp.isContextSpecific((byte)0)) {
            version = new CertificateVersion(tmp);
            tmp = in.getDerValue();
        }

        // Serial number ... an integer
        serialNum = new CertificateSerialNumber(tmp);

        // Algorithm Identifier
        algId = new CertificateAlgorithmId(in);

        // Issuer name
        issuer = new X500Name(in);
        if (issuer.isEmpty()) {
            throw new CertificateParsingException(
                "Empty issuer DN not allowed in X509Certificates");
        }

        // validity:  SEQUENCE { start date, end date }
        interval = new CertificateValidity(in);

        // subject name
        subject = new X500Name(in);
        if ((version.compare(CertificateVersion.V1) == 0) &&
                subject.isEmpty()) {
            throw new CertificateParsingException(
                      "Empty subject DN not allowed in v1 certificate");
        }

        // public key
        pubKey = new CertificateX509Key(in);

        // If more data available, make sure version is not v1.
        if (in.available() != 0) {
            if (version.compare(CertificateVersion.V1) == 0) {
                throw new CertificateParsingException(
                          "no more data allowed for version 1 certificate");
            }
        } else {
            return;
        }

        // Get the issuerUniqueId if present
        tmp = in.getDerValue();
        if (tmp.isContextSpecific((byte)1)) {
            issuerUniqueId = new UniqueIdentity(tmp);
            if (in.available() == 0)
                return;
            tmp = in.getDerValue();
        }

        // Get the subjectUniqueId if present.
        if (tmp.isContextSpecific((byte)2)) {
            subjectUniqueId = new UniqueIdentity(tmp);
            if (in.available() == 0)
                return;
            tmp = in.getDerValue();
        }

        // Get the extensions.
        if (version.compare(CertificateVersion.V3) != 0) {
            throw new CertificateParsingException(
                      "Extensions not allowed in v2 certificate");
        }
        if (tmp.isConstructed() && tmp.isContextSpecific((byte)3)) {
            extensions = new CertificateExtensions(tmp.data);
        }

        // verify X.509 V3 Certificate
        verifyCert(subject, extensions);

    }

    /*
     * Verify if X.509 V3 Certificate is compliant with RFC 5280.
     */
    private void verifyCert(X500Name subject,
        CertificateExtensions extensions)
        throws CertificateParsingException {

        // if SubjectName is empty, check for SubjectAlternativeNameExtension
        if (subject.isEmpty()) {
            if (extensions == null) {
                throw new CertificateParsingException("X.509 Certificate is " +
                        "incomplete: subject field is empty, and certificate " +
                        "has no extensions");
            }
            SubjectAlternativeNameExtension subjectAltNameExt =
                    (SubjectAlternativeNameExtension)
                    extensions.getExtension(SubjectAlternativeNameExtension.NAME);
            if (subjectAltNameExt == null) {
                throw new CertificateParsingException("X.509 Certificate is " +
                        "incomplete: subject field is empty, and " +
                        "SubjectAlternativeName extension is absent");
            }
            GeneralNames names = subjectAltNameExt.getNames();

            // SubjectAlternativeName extension is empty or not marked critical
            if (names == null || names.isEmpty()) {
                throw new CertificateParsingException("X.509 Certificate is " +
                        "incomplete: subject field is empty, and " +
                        "SubjectAlternativeName extension is empty");
            } else if (!subjectAltNameExt.isCritical()) {
                throw new CertificateParsingException("X.509 Certificate is " +
                        "incomplete: SubjectAlternativeName extension MUST " +
                        "be marked critical when subject field is empty");
            }
        }
    }

    /*
     * Marshal the contents of a "raw" certificate into a DER sequence.
     */
    private void emit(DerOutputStream out) throws CertificateException {
        DerOutputStream tmp = new DerOutputStream();

        // version number, iff not V1
        version.encode(tmp);

        // Encode serial number, issuer signing algorithm, issuer name
        // and validity
        serialNum.encode(tmp);
        algId.encode(tmp);

        if ((version.compare(CertificateVersion.V1) == 0) &&
            (issuer.toString() == null))
            throw new CertificateParsingException(
                      "Null issuer DN not allowed in v1 certificate");

        issuer.encode(tmp);
        interval.encode(tmp);

        // Encode subject (principal) and associated key
        if ((version.compare(CertificateVersion.V1) == 0) &&
            (subject.toString() == null))
            throw new CertificateParsingException(
                      "Null subject DN not allowed in v1 certificate");
        subject.encode(tmp);
        pubKey.encode(tmp);

        // Encode issuerUniqueId & subjectUniqueId.
        if (issuerUniqueId != null) {
            issuerUniqueId.encode(tmp, DerValue.createTag(DerValue.TAG_CONTEXT,
                                                          false,(byte)1));
        }
        if (subjectUniqueId != null) {
            subjectUniqueId.encode(tmp, DerValue.createTag(DerValue.TAG_CONTEXT,
                                                           false,(byte)2));
        }

        // Write all the extensions.
        if (extensions != null) {
            extensions.encode(tmp);
        }

        // Wrap the data; encoding of the "raw" cert is now complete.
        out.write(DerValue.tag_Sequence, tmp);
    }

    /**
     * Set the version number of the certificate.
     *
     * @param val the Object class value for the Extensions
     * @exception CertificateException on invalid data.
     */
    public void setVersion(CertificateVersion val) {
        // set rawCertInfo to null, so that we are forced to re-encode
        rawCertInfo = null;
        version = val;
    }

    public CertificateVersion getVersion() {
        return version;
    }

    /**
     * Set the serial number of the certificate.
     *
     * @param val the Object class value for the CertificateSerialNumber
     * @exception CertificateException on invalid data.
     */
    public void setSerialNumber(CertificateSerialNumber val) {
        // set rawCertInfo to null, so that we are forced to re-encode
        rawCertInfo = null;
        serialNum = val;
    }

    public CertificateSerialNumber getSerialNumber() {
        return serialNum;
    }

    /**
     * Set the algorithm id of the certificate.
     *
     * @param val the Object class value for the AlgorithmId
     * @exception CertificateException on invalid data.
     */
    public void setAlgorithmId(CertificateAlgorithmId val) {
        // set rawCertInfo to null, so that we are forced to re-encode
        rawCertInfo = null;
        algId = val;
    }

    public CertificateAlgorithmId getAlgorithmId() {
        return algId;
    }

    /**
     * Set the issuer name of the certificate.
     *
     * @param val the Object class value for the issuer
     * @exception CertificateException on invalid data.
     */
    public void setIssuer(X500Name val) {
        // set rawCertInfo to null, so that we are forced to re-encode
        rawCertInfo = null;
        issuer = val;
    }

    /**
     * Set the validity interval of the certificate.
     *
     * @param val the Object class value for the CertificateValidity
     * @exception CertificateException on invalid data.
     */
    public void setValidity(CertificateValidity val) {
        // set rawCertInfo to null, so that we are forced to re-encode
        rawCertInfo = null;
        interval = val;
    }

    public CertificateValidity getValidity() {
        return interval;
    }

    /**
     * Set the subject name of the certificate.
     *
     * @param val the Object class value for the Subject
     * @exception CertificateException on invalid data.
     */
    public void setSubject(X500Name val) throws CertificateException {
        // set rawCertInfo to null, so that we are forced to re-encode
        rawCertInfo = null;
        subject = val;
    }

    /**
     * Set the public key in the certificate.
     *
     * @param val the Object class value for the PublicKey
     * @exception CertificateException on invalid data.
     */
    public void setKey(CertificateX509Key val) {
        // set rawCertInfo to null, so that we are forced to re-encode
        rawCertInfo = null;
        pubKey = val;
    }

    public CertificateX509Key getKey() {
        return pubKey;
    }

    /**
     * Set the Issuer Unique Identity in the certificate.
     *
     * @param val the Object class value for the IssuerUniqueId
     * @exception CertificateException
     */
    public void setIssuerUniqueId(UniqueIdentity val) throws CertificateException {
        // set rawCertInfo to null, so that we are forced to re-encode
        rawCertInfo = null;
        if (version.compare(CertificateVersion.V2) < 0) {
            throw new CertificateException("Invalid version");
        }
        issuerUniqueId = val;
    }

    /**
     * Set the Subject Unique Identity in the certificate.
     *
     * @param val the Object class value for the SubjectUniqueId
     * @exception CertificateException
     */
    public void setSubjectUniqueId(UniqueIdentity val) throws CertificateException {
        // set rawCertInfo to null, so that we are forced to re-encode
        rawCertInfo = null;
        if (version.compare(CertificateVersion.V2) < 0) {
            throw new CertificateException("Invalid version");
        }
        subjectUniqueId = val;
    }

    /**
     * Set the extensions in the certificate.
     *
     * @param val the Object class value for the Extensions
     * @exception CertificateException
     */
    public void setExtensions(CertificateExtensions val) throws CertificateException {
        // set rawCertInfo to null, so that we are forced to re-encode
        rawCertInfo = null;
        if (version.compare(CertificateVersion.V3) < 0) {
            throw new CertificateException("Invalid version");
        }
        extensions = val;
    }
}
