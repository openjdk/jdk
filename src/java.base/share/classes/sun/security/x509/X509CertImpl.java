/*
 * Copyright (c) 1996, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import javax.security.auth.x500.X500Principal;

import sun.security.jca.JCAUtil;
import sun.security.util.*;
import sun.security.provider.X509Factory;

import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * The X509CertImpl class represents an X.509 certificate. These certificates
 * are widely used to support authentication and other functionality in
 * Internet security systems.  Common applications include Privacy Enhanced
 * Mail (PEM), Transport Layer Security (SSL), code signing for trusted
 * software distribution, and Secure Electronic Transactions (SET).  There
 * is a commercial infrastructure ready to manage large scale deployments
 * of X.509 identity certificates.
 *
 * <P>These certificates are managed and vouched for by <em>Certificate
 * Authorities</em> (CAs).  CAs are services which create certificates by
 * placing data in the X.509 standard format and then digitally signing
 * that data.  Such signatures are quite difficult to forge.  CAs act as
 * trusted third parties, making introductions between agents who have no
 * direct knowledge of each other.  CA certificates are either signed by
 * themselves, or by some other CA such as a "root" CA.
 *
 * <P> Standards relating to X.509 Public Key Infrastructure for the Internet
 * can be referenced in RFC 5280.
 *
 * @author Dave Brownell
 * @author Amit Kapoor
 * @author Hemma Prafullchandra
 * @see X509CertInfo
 */
@SuppressWarnings("serial") // See writeReplace method in Certificate
public class X509CertImpl extends X509Certificate implements DerEncoder {

    @java.io.Serial
    private static final long serialVersionUID = -3457612960190864406L;

    public static final String NAME = "x509";

    // Certificate data, and its envelope
    private byte[]              signedCert = null;
    protected X509CertInfo      info = null;
    protected AlgorithmId       algId = null;
    protected byte[]            signature = null;

    // number of standard key usage bits.
    private static final int NUM_STANDARD_KEY_USAGE = 9;

    // SubjectAlternativeNames cache
    private Collection<List<?>> subjectAlternativeNames;

    // IssuerAlternativeNames cache
    private Collection<List<?>> issuerAlternativeNames;

    // ExtendedKeyUsage cache
    private List<String> extKeyUsage;

    // AuthorityInformationAccess cache
    private Set<AccessDescription> authInfoAccess;

    /**
     * PublicKey that has previously been used to verify
     * the signature of this certificate. Null if the certificate has not
     * yet been verified.
     */
    private PublicKey verifiedPublicKey;
    /**
     * If verifiedPublicKey is not null, name of the provider used to
     * successfully verify the signature of this certificate, or the
     * empty String if no provider was explicitly specified.
     */
    private String verifiedProvider;
    /**
     * If verifiedPublicKey is not null, result of the verification using
     * verifiedPublicKey and verifiedProvider. If true, verification was
     * successful, if false, it failed.
     */
    private boolean verificationResult;

    /**
     * Constructor simply setting all (non-cache) fields. Only used in
     * {@link #newSigned}.
     */
    public X509CertImpl(X509CertInfo info, AlgorithmId algId, byte[] signature,
                        byte[] signedCert) {
        this.info = Objects.requireNonNull(info);
        this.algId = algId;
        this.signature = signature;
        this.signedCert = Objects.requireNonNull(signedCert);
    }

    /**
     * Unmarshals a certificate from its encoded form, parsing the
     * encoded bytes.  This form of constructor is used by agents which
     * need to examine and use certificate contents.  That is, this is
     * one of the more commonly used constructors.  Note that the buffer
     * must include only a certificate, and no "garbage" may be left at
     * the end.  If you need to ignore data at the end of a certificate,
     * use another constructor.
     *
     * @param certData the encoded bytes, with no trailing padding.
     * @exception CertificateException on parsing and initialization errors.
     */
    public X509CertImpl(byte[] certData) throws CertificateException {
        try {
            parse(new DerValue(certData));
        } catch (IOException e) {
            throw new CertificateException("Unable to initialize, " + e, e);
        }
    }

    /**
     * Unmarshals a certificate from its encoded form, parsing a DER value.
     * This form of constructor is used by agents which need to examine
     * and use certificate contents.
     *
     * @param derVal the der value containing the encoded cert.
     * @exception CertificateException on parsing and initialization errors.
     */
    public X509CertImpl(DerValue derVal) throws CertificateException {
        try {
            parse(derVal);
        } catch (IOException e) {
            throw new CertificateException("Unable to initialize, " + e, e);
        }
    }

    /**
     * Unmarshals an X.509 certificate from an input stream.  If the
     * certificate is RFC1421 hex-encoded, then it must begin with
     * the line X509Factory.BEGIN_CERT and end with the line
     * X509Factory.END_CERT.
     *
     * @param in an input stream holding at least one certificate that may
     *        be either DER-encoded or RFC1421 hex-encoded version of the
     *        DER-encoded certificate.
     * @exception CertificateException on parsing and initialization errors.
     */
    public X509CertImpl(InputStream in) throws CertificateException {

        DerValue der;

        BufferedInputStream inBuffered = new BufferedInputStream(in);

        // First try reading stream as HEX-encoded DER-encoded bytes,
        // since not mistakable for raw DER
        try {
            inBuffered.mark(Integer.MAX_VALUE);
            der = readRFC1421Cert(inBuffered);
        } catch (IOException ioe) {
            try {
                // Next, try reading stream as raw DER-encoded bytes
                inBuffered.reset();
                der = new DerValue(inBuffered);
            } catch (IOException ioe1) {
                throw new CertificateException("Input stream must be " +
                                               "either DER-encoded bytes " +
                                               "or RFC1421 hex-encoded " +
                                               "DER-encoded bytes: " +
                                               ioe1.getMessage(), ioe1);
            }
        }
        try {
            parse(der);
        } catch (IOException ioe) {
            signedCert = null;
            throw new CertificateException("Unable to parse DER value of " +
                                           "certificate, " + ioe, ioe);
        }
    }

    /**
     * read input stream as HEX-encoded DER-encoded bytes
     *
     * @param in InputStream to read
     * @return DerValue corresponding to decoded HEX-encoded bytes
     * @throws IOException if stream can not be interpreted as RFC1421
     *                     encoded bytes
     */
    private DerValue readRFC1421Cert(InputStream in) throws IOException {
        DerValue der = null;
        String line;
        BufferedReader certBufferedReader =
            new BufferedReader(new InputStreamReader(in, US_ASCII));
        try {
            line = certBufferedReader.readLine();
        } catch (IOException ioe1) {
            throw new IOException("Unable to read InputStream: " +
                                  ioe1.getMessage());
        }
        if (line.equals(X509Factory.BEGIN_CERT)) {
            /* stream appears to be hex-encoded bytes */
            ByteArrayOutputStream decstream = new ByteArrayOutputStream();
            try {
                while ((line = certBufferedReader.readLine()) != null) {
                    if (line.equals(X509Factory.END_CERT)) {
                        der = new DerValue(decstream.toByteArray());
                        break;
                    } else {
                        decstream.write(Pem.decode(line));
                    }
                }
            } catch (IOException ioe2) {
                throw new IOException("Unable to read InputStream: "
                                      + ioe2.getMessage());
            }
        } else {
            throw new IOException("InputStream is not RFC1421 hex-encoded " +
                                  "DER bytes");
        }
        return der;
    }

    // helper method to record certificate, if necessary, after construction
    public static X509CertImpl newX509CertImpl(byte[] certData) throws CertificateException {
        var cert = new X509CertImpl(certData);
        JCAUtil.tryCommitCertEvent(cert);
        return cert;
    }

    /**
     * DER encode this object onto an output stream.
     * Implements the <code>DerEncoder</code> interface.
     *
     * @param out the output stream on which to write the DER encoding.
     */
    @Override
    public void encode(DerOutputStream out) {
        out.writeBytes(signedCert);
    }

    /**
     * Returns the encoded form of this certificate. It is
     * assumed that each certificate type would have only a single
     * form of encoding; for example, X.509 certificates would
     * be encoded as ASN.1 DER.
     *
     * @exception CertificateEncodingException if an encoding error occurs.
     */
    public byte[] getEncoded() throws CertificateEncodingException {
        return getEncodedInternal().clone();
    }

    /**
     * Returned the encoding as an uncloned byte array. Callers must
     * guarantee that they neither modify it nor expose it to untrusted
     * code.
     */
    public byte[] getEncodedInternal() throws CertificateEncodingException {
        return signedCert;
    }

    /**
     * Throws an exception if the certificate was not signed using the
     * verification key provided.  Successfully verifying a certificate
     * does <em>not</em> indicate that one should trust the entity which
     * it represents.
     *
     * @param key the public key used for verification.
     *
     * @exception InvalidKeyException on incorrect key.
     * @exception NoSuchAlgorithmException on unsupported signature
     * algorithms.
     * @exception NoSuchProviderException if there's no default provider.
     * @exception SignatureException on signature errors.
     * @exception CertificateException on encoding errors.
     */
    public void verify(PublicKey key)
    throws CertificateException, NoSuchAlgorithmException,
        InvalidKeyException, NoSuchProviderException, SignatureException {
        verify(key, "");
    }

    /**
     * Throws an exception if the certificate was not signed using the
     * verification key provided.  Successfully verifying a certificate
     * does <em>not</em> indicate that one should trust the entity which
     * it represents.
     *
     * @param key the public key used for verification.
     * @param sigProvider the name of the provider.
     *
     * @exception NoSuchAlgorithmException on unsupported signature
     * algorithms.
     * @exception InvalidKeyException on incorrect key.
     * @exception NoSuchProviderException on incorrect provider.
     * @exception SignatureException on signature errors.
     * @exception CertificateException on encoding errors.
     */
    public synchronized void verify(PublicKey key, String sigProvider)
            throws CertificateException, NoSuchAlgorithmException,
            InvalidKeyException, NoSuchProviderException, SignatureException {
        if (sigProvider == null) {
            sigProvider = "";
        }
        if ((verifiedPublicKey != null) && verifiedPublicKey.equals(key)) {
            // this certificate has already been verified using
            // this public key. Make sure providers match, too.
            if (sigProvider.equals(verifiedProvider)) {
                if (verificationResult) {
                    return;
                } else {
                    throw new SignatureException("Signature does not match.");
                }
            }
        }
        // Verify the signature ...
        Signature sigVerf;
        String sigName = algId.getName();
        if (sigProvider.isEmpty()) {
            sigVerf = Signature.getInstance(sigName);
        } else {
            sigVerf = Signature.getInstance(sigName, sigProvider);
        }

        try {
            SignatureUtil.initVerifyWithParam(sigVerf, key,
                SignatureUtil.getParamSpec(sigName, getSigAlgParams()));
        } catch (ProviderException e) {
            throw new CertificateException(e.getMessage(), e.getCause());
        } catch (InvalidAlgorithmParameterException e) {
            throw new CertificateException(e);
        }

        byte[] rawCert = info.getEncodedInfo();
        sigVerf.update(rawCert, 0, rawCert.length);

        // verify may throw SignatureException for invalid encodings, etc.
        verificationResult = sigVerf.verify(signature);
        verifiedPublicKey = key;
        verifiedProvider = sigProvider;

        if (!verificationResult) {
            throw new SignatureException("Signature does not match.");
        }
    }

    /**
     * Throws an exception if the certificate was not signed using the
     * verification key provided.  This method uses the signature verification
     * engine supplied by the specified provider. Note that the specified
     * Provider object does not have to be registered in the provider list.
     * Successfully verifying a certificate does <em>not</em> indicate that one
     * should trust the entity which it represents.
     *
     * @param key the public key used for verification.
     * @param sigProvider the provider.
     *
     * @exception NoSuchAlgorithmException on unsupported signature
     * algorithms.
     * @exception InvalidKeyException on incorrect key.
     * @exception SignatureException on signature errors.
     * @exception CertificateException on encoding errors.
     */
    public synchronized void verify(PublicKey key, Provider sigProvider)
            throws CertificateException, NoSuchAlgorithmException,
            InvalidKeyException, SignatureException {
        // Verify the signature ...
        Signature sigVerf;
        String sigName = algId.getName();
        if (sigProvider == null) {
            sigVerf = Signature.getInstance(sigName);
        } else {
            sigVerf = Signature.getInstance(sigName, sigProvider);
        }

        try {
            SignatureUtil.initVerifyWithParam(sigVerf, key,
                SignatureUtil.getParamSpec(sigName, getSigAlgParams()));
        } catch (ProviderException e) {
            throw new CertificateException(e.getMessage(), e.getCause());
        } catch (InvalidAlgorithmParameterException e) {
            throw new CertificateException(e);
        }

        byte[] rawCert = info.getEncodedInfo();
        sigVerf.update(rawCert, 0, rawCert.length);

        // verify may throw SignatureException for invalid encodings, etc.
        verificationResult = sigVerf.verify(signature);
        verifiedPublicKey = key;

        if (!verificationResult) {
            throw new SignatureException("Signature does not match.");
        }
    }

    /**
     * Creates a new X.509 certificate, which is signed using the given key
     * (associating a signature algorithm and an X.500 name).
     * This operation is used to implement the certificate generation
     * functionality of a certificate authority.
     *
     * @param info the X509CertInfo to sign
     * @param key the private key used for signing.
     * @param algorithm the name of the signature algorithm used.
     * @return the newly signed certificate
     *
     * @exception InvalidKeyException on incorrect key.
     * @exception NoSuchAlgorithmException on unsupported signature algorithms.
     * @exception NoSuchProviderException if there's no default provider.
     * @exception SignatureException on signature errors.
     * @exception CertificateException on encoding errors.
     */
    public static X509CertImpl newSigned(X509CertInfo info, PrivateKey key, String algorithm)
            throws CertificateException, NoSuchAlgorithmException,
            InvalidKeyException, NoSuchProviderException, SignatureException {
        return newSigned(info, key, algorithm, null);
    }

    /**
     * Creates a new X.509 certificate, which is signed using the given key
     * (associating a signature algorithm and an X.500 name).
     * This operation is used to implement the certificate generation
     * functionality of a certificate authority.
     *
     * @param info the X509CertInfo to sign
     * @param key the private key used for signing.
     * @param algorithm the name of the signature algorithm used.
     * @param provider (optional) the name of the provider.
     * @return the newly signed certificate
     *
     * @exception NoSuchAlgorithmException on unsupported signature algorithms.
     * @exception InvalidKeyException on incorrect key.
     * @exception NoSuchProviderException on incorrect provider.
     * @exception SignatureException on signature errors.
     * @exception CertificateException on encoding errors.
     */
    public static X509CertImpl newSigned(X509CertInfo info, PrivateKey key, String algorithm, String provider)
            throws CertificateException, NoSuchAlgorithmException,
            InvalidKeyException, NoSuchProviderException, SignatureException {
        Signature sigEngine = SignatureUtil.fromKey(
                algorithm, key, provider);
        AlgorithmId algId = SignatureUtil.fromSignature(sigEngine, key);

        DerOutputStream out = new DerOutputStream();
        DerOutputStream tmp = new DerOutputStream();

        // encode certificate info
        info.setAlgorithmId(new CertificateAlgorithmId(algId));
        info.encode(tmp);
        byte[] rawCert = tmp.toByteArray();

        // encode algorithm identifier
        algId.encode(tmp);

        // Create and encode the signature itself.
        sigEngine.update(rawCert, 0, rawCert.length);
        byte[] signature = sigEngine.sign();
        tmp.putBitString(signature);

        // Wrap the signed data in a SEQUENCE { data, algorithm, sig }
        out.write(DerValue.tag_Sequence, tmp);
        byte[] signedCert = out.toByteArray();

        return new X509CertImpl(info, algId, signature, signedCert);
    }

    /**
     * Checks that the certificate is currently valid, i.e. the current
     * time is within the specified validity period.
     *
     * @exception CertificateExpiredException if the certificate has expired.
     * @exception CertificateNotYetValidException if the certificate is not
     * yet valid.
     */
    public void checkValidity()
    throws CertificateExpiredException, CertificateNotYetValidException {
        Date date = new Date();
        checkValidity(date);
    }

    /**
     * Checks that the specified date is within the certificate's
     * validity period, or basically if the certificate would be
     * valid at the specified date/time.
     *
     * @param date the Date to check against to see if this certificate
     *        is valid at that date/time.
     *
     * @exception CertificateExpiredException if the certificate has expired
     * with respect to the <code>date</code> supplied.
     * @exception CertificateNotYetValidException if the certificate is not
     * yet valid with respect to the <code>date</code> supplied.
     */
    public void checkValidity(Date date)
    throws CertificateExpiredException, CertificateNotYetValidException {

        CertificateValidity interval;
        try {
            interval = info.getValidity();
        } catch (Exception e) {
            throw new CertificateNotYetValidException("Incorrect validity period");
        }
        if (interval == null)
            throw new CertificateNotYetValidException("Null validity period");
        interval.valid(date);
    }

    /**
     * Return the requested attribute from the certificate.
     * <p>
     * Note that the X509CertInfo is not cloned for performance reasons.
     * Callers must ensure that they do not modify it. All other
     * attributes are cloned.
     */

    public X509CertInfo getInfo() {
        return info;
    }

    /**
     * Returns a printable representation of the certificate.  This does not
     * contain all the information available to distinguish this from any
     * other certificate.  The certificate must be fully constructed
     * before this function may be called.
     */
    public String toString() {
        if (algId == null || signature == null)
            return "";

        HexDumpEncoder encoder = new HexDumpEncoder();
        return "[\n" + info + '\n' +
            "  Algorithm: [" + algId + "]\n" +
            "  Signature:\n" + encoder.encodeBuffer(signature) + "\n]";
    }

    // the strongly typed gets, as per java.security.cert.X509Certificate

    /**
     * Gets the publickey from this certificate.
     *
     * @return the publickey.
     */
    public PublicKey getPublicKey() {
        return info.getKey().getKey();
    }

    /**
     * Gets the version number from the certificate.
     *
     * @return the version number, i.e. 1, 2 or 3.
     */
    public int getVersion() {
        try {
            int vers = info.getVersion().getVersion();
            return vers + 1;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Gets the serial number from the certificate.
     *
     * @return the serial number.
     */
    public BigInteger getSerialNumber() {
        SerialNumber ser = getSerialNumberObject();

        return ser != null ? ser.getNumber() : null;
    }

    /**
     * Gets the serial number from the certificate as
     * a SerialNumber object.
     *
     * @return the serial number.
     */
    public SerialNumber getSerialNumberObject() {
        return info.getSerialNumber().getSerial();
    }


    /**
     * Gets the subject distinguished name from the certificate.
     *
     * @return the subject name.
     */
    @SuppressWarnings("deprecation")
    public Principal getSubjectDN() {
        return info.getSubject();
    }

    /**
     * Get subject name as X500Principal. Overrides implementation in
     * X509Certificate with a slightly more efficient version that is
     * also aware of X509CertImpl mutability.
     */
    public X500Principal getSubjectX500Principal() {
        try {
            return info.getSubject().asX500Principal();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gets the issuer distinguished name from the certificate.
     *
     * @return the issuer name.
     */
    @SuppressWarnings("deprecation")
    public Principal getIssuerDN() {
        return info.getIssuer();
    }

    /**
     * Get issuer name as X500Principal. Overrides implementation in
     * X509Certificate with a slightly more efficient version that is
     * also aware of X509CertImpl mutability.
     */
    public X500Principal getIssuerX500Principal() {
        try {
            return info.getIssuer().asX500Principal();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gets the notBefore date from the validity period of the certificate.
     *
     * @return the start date of the validity period.
     */
    public Date getNotBefore() {
        return info.getValidity().getNotBefore();
    }

    /**
     * Gets the notAfter date from the validity period of the certificate.
     *
     * @return the end date of the validity period.
     */
    public Date getNotAfter() {
        return info.getValidity().getNotAfter();
    }

    /**
     * Gets the DER encoded certificate information, the
     * <code>tbsCertificate</code> from this certificate.
     * This can be used to verify the signature independently.
     *
     * @return the DER encoded certificate information.
     * @exception CertificateEncodingException if an encoding error occurs.
     */
    public byte[] getTBSCertificate() throws CertificateEncodingException {
        return info.getEncodedInfo();
    }

    /**
     * Gets the raw Signature bits from the certificate.
     *
     * @return the signature.
     */
    public byte[] getSignature() {
        if (signature == null)
            return null;
        return signature.clone();
    }

    /**
     * Gets the signature algorithm name for the certificate
     * signature algorithm.
     * For example, the string "SHA-1/DSA" or "DSS".
     *
     * @return the signature algorithm name.
     */
    public String getSigAlgName() {
        if (algId == null)
            return null;
        return algId.getName();
    }

    /**
     * Gets the signature algorithm OID string from the certificate.
     * For example, the string "1.2.840.10040.4.3"
     *
     * @return the signature algorithm oid string.
     */
    public String getSigAlgOID() {
        if (algId == null)
            return null;
        ObjectIdentifier oid = algId.getOID();
        return oid.toString();
    }

    public AlgorithmId getSigAlg() {
        return algId;
    }

    /**
     * Gets the DER encoded signature algorithm parameters from this
     * certificate's signature algorithm.
     *
     * @return the DER encoded signature algorithm parameters, or
     *         null if no parameters are present.
     */
    public byte[] getSigAlgParams() {
        return algId == null ? null : algId.getEncodedParams();
    }

    /**
     * Gets the Issuer Unique Identity from the certificate.
     *
     * @return the Issuer Unique Identity.
     */
    public boolean[] getIssuerUniqueID() {
        UniqueIdentity id = info.getIssuerUniqueId();
        if (id == null)
            return null;
        else
            return id.getId();
    }

    /**
     * Gets the Subject Unique Identity from the certificate.
     *
     * @return the Subject Unique Identity.
     */
    public boolean[] getSubjectUniqueID() {
        UniqueIdentity id = info.getSubjectUniqueId();
        if (id == null)
            return null;
        else
            return id.getId();
    }

    public KeyIdentifier getAuthKeyId() {
        AuthorityKeyIdentifierExtension aki
            = getAuthorityKeyIdentifierExtension();
        if (aki != null) {
            return aki.getKeyIdentifier();
        }
        return null;
    }

    /**
     * Returns the subject's key identifier, or null
     */
    public KeyIdentifier getSubjectKeyId() {
        SubjectKeyIdentifierExtension ski = getSubjectKeyIdentifierExtension();
        if (ski != null) {
            return ski.getKeyIdentifier();
        }
        return null;
    }

    /**
     * Get AuthorityKeyIdentifier extension
     * @return AuthorityKeyIdentifier object or null (if no such object
     * in certificate)
     */
    public AuthorityKeyIdentifierExtension getAuthorityKeyIdentifierExtension()
    {
        return (AuthorityKeyIdentifierExtension)
            getExtension(PKIXExtensions.AuthorityKey_Id);
    }

    /**
     * Get BasicConstraints extension
     * @return BasicConstraints object or null (if no such object in
     * certificate)
     */
    public BasicConstraintsExtension getBasicConstraintsExtension() {
        return (BasicConstraintsExtension)
            getExtension(PKIXExtensions.BasicConstraints_Id);
    }

    /**
     * Get CertificatePoliciesExtension
     * @return CertificatePoliciesExtension or null (if no such object in
     * certificate)
     */
    public CertificatePoliciesExtension getCertificatePoliciesExtension() {
        return (CertificatePoliciesExtension)
            getExtension(PKIXExtensions.CertificatePolicies_Id);
    }

    /**
     * Get ExtendedKeyUsage extension
     * @return ExtendedKeyUsage extension object or null (if no such object
     * in certificate)
     */
    public ExtendedKeyUsageExtension getExtendedKeyUsageExtension() {
        return (ExtendedKeyUsageExtension)
            getExtension(PKIXExtensions.ExtendedKeyUsage_Id);
    }

    /**
     * Get IssuerAlternativeName extension
     * @return IssuerAlternativeName object or null (if no such object in
     * certificate)
     */
    public IssuerAlternativeNameExtension getIssuerAlternativeNameExtension() {
        return (IssuerAlternativeNameExtension)
            getExtension(PKIXExtensions.IssuerAlternativeName_Id);
    }

    /**
     * Get NameConstraints extension
     * @return NameConstraints object or null (if no such object in certificate)
     */
    public NameConstraintsExtension getNameConstraintsExtension() {
        return (NameConstraintsExtension)
            getExtension(PKIXExtensions.NameConstraints_Id);
    }

    /**
     * Get PolicyConstraints extension
     * @return PolicyConstraints object or null (if no such object in
     * certificate)
     */
    public PolicyConstraintsExtension getPolicyConstraintsExtension() {
        return (PolicyConstraintsExtension)
            getExtension(PKIXExtensions.PolicyConstraints_Id);
    }

    /**
     * Get PolicyMappingsExtension extension
     * @return PolicyMappingsExtension object or null (if no such object
     * in certificate)
     */
    public PolicyMappingsExtension getPolicyMappingsExtension() {
        return (PolicyMappingsExtension)
            getExtension(PKIXExtensions.PolicyMappings_Id);
    }

    /**
     * Get PrivateKeyUsage extension
     * @return PrivateKeyUsage object or null (if no such object in certificate)
     */
    public PrivateKeyUsageExtension getPrivateKeyUsageExtension() {
        return (PrivateKeyUsageExtension)
            getExtension(PKIXExtensions.PrivateKeyUsage_Id);
    }

    /**
     * Get SubjectAlternativeName extension
     * @return SubjectAlternativeName object or null (if no such object in
     * certificate)
     */
    public SubjectAlternativeNameExtension getSubjectAlternativeNameExtension()
    {
        return (SubjectAlternativeNameExtension)
            getExtension(PKIXExtensions.SubjectAlternativeName_Id);
    }

    /**
     * Get SubjectKeyIdentifier extension
     * @return SubjectKeyIdentifier object or null (if no such object in
     * certificate)
     */
    public SubjectKeyIdentifierExtension getSubjectKeyIdentifierExtension() {
        return (SubjectKeyIdentifierExtension)
            getExtension(PKIXExtensions.SubjectKey_Id);
    }

    /**
     * Get CRLDistributionPoints extension
     * @return CRLDistributionPoints object or null (if no such object in
     * certificate)
     */
    public CRLDistributionPointsExtension getCRLDistributionPointsExtension() {
        return (CRLDistributionPointsExtension)
            getExtension(PKIXExtensions.CRLDistributionPoints_Id);
    }

    /**
     * Return true if a critical extension is found that is
     * not supported, otherwise return false.
     */
    public boolean hasUnsupportedCriticalExtension() {
        CertificateExtensions exts = info.getExtensions();
        if (exts == null)
            return false;
        return exts.hasUnsupportedCriticalExtension();
    }

    /**
     * Gets a Set of the extension(s) marked CRITICAL in the
     * certificate. In the returned set, each extension is
     * represented by its OID string.
     *
     * @return a set of the extension oid strings in the
     * certificate that are marked critical.
     */
    public Set<String> getCriticalExtensionOIDs() {
        try {
            CertificateExtensions exts = info.getExtensions();
            if (exts == null) {
                return null;
            }
            Set<String> extSet = new TreeSet<>();
            for (Extension ex : exts.getAllExtensions()) {
                if (ex.isCritical()) {
                    extSet.add(ex.getExtensionId().toString());
                }
            }
            return extSet;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gets a Set of the extension(s) marked NON-CRITICAL in the
     * certificate. In the returned set, each extension is
     * represented by its OID string.
     *
     * @return a set of the extension oid strings in the
     * certificate that are NOT marked critical.
     */
    public Set<String> getNonCriticalExtensionOIDs() {
        try {
            CertificateExtensions exts = info.getExtensions();
            if (exts == null) {
                return null;
            }
            Set<String> extSet = new TreeSet<>();
            for (Extension ex : exts.getAllExtensions()) {
                if (!ex.isCritical()) {
                    extSet.add(ex.getExtensionId().toString());
                }
            }
            extSet.addAll(exts.getUnparseableExtensions().keySet());
            return extSet;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gets the extension identified by the given ObjectIdentifier
     *
     * @param oid the Object Identifier value for the extension.
     * @return Extension or null if certificate does not contain this
     *         extension
     */
    public Extension getExtension(ObjectIdentifier oid) {
        CertificateExtensions extensions = info.getExtensions();
        if (extensions != null) {
            Extension ex = extensions.getExtension(oid.toString());
            if (ex != null) {
                return ex;
            }
            for (Extension ex2 : extensions.getAllExtensions()) {
                if (ex2.getExtensionId().equals(oid)) {
                    //XXXX May want to consider cloning this
                    return ex2;
                }
            }
            /* no such extension in this certificate */
        }
        return null;
    }

    public Extension getUnparseableExtension(ObjectIdentifier oid) {
        CertificateExtensions extensions = info.getExtensions();
        if (extensions == null) {
            return null;
        } else {
            return extensions.getUnparseableExtensions().get(oid.toString());
        }
    }

    /**
     * Gets the DER encoded extension identified by the given
     * oid String.
     *
     * @param oid the Object Identifier value for the extension.
     * @return the DER-encoded extension value, or {@code null} if
     *         the extensions are not present or the value is not found
     */
    public byte[] getExtensionValue(String oid) {
        try {
            ObjectIdentifier findOID = ObjectIdentifier.of(oid);
            String extAlias = OIDMap.getName(findOID);
            Extension certExt = null;
            CertificateExtensions exts = info.getExtensions();
            if (exts == null) {
                return null;
            }
            if (extAlias == null) { // may be unknown
                // get the extensions, search through' for this oid
                for (Extension ex : exts.getAllExtensions()) {
                    ObjectIdentifier inCertOID = ex.getExtensionId();
                    if (inCertOID.equals(findOID)) {
                        certExt = ex;
                        break;
                    }
                }
            } else { // there's subclass that can handle this extension
                certExt = exts.getExtension(extAlias);
            }
            if (certExt == null) {
                certExt = exts.getUnparseableExtensions().get(oid);
                if (certExt == null) {
                    return null;
                }
            }
            byte[] extData = certExt.getExtensionValue();
            if (extData == null) {
                return null;
            }
            DerOutputStream out = new DerOutputStream();
            out.putOctetString(extData);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get a boolean array representing the bits of the KeyUsage extension,
     * (oid = 2.5.29.15).
     * @return the bit values of this extension as an array of booleans.
     */
    public boolean[] getKeyUsage() {
        try {
            CertificateExtensions extensions = info.getExtensions();
            if (extensions == null) {
                return null;
            }
            KeyUsageExtension certExt = (KeyUsageExtension)
                    extensions.getExtension(KeyUsageExtension.NAME);
            if (certExt == null)
                return null;

            boolean[] ret = certExt.getBits();
            if (ret.length < NUM_STANDARD_KEY_USAGE) {
                boolean[] usageBits = new boolean[NUM_STANDARD_KEY_USAGE];
                System.arraycopy(ret, 0, usageBits, 0, ret.length);
                ret = usageBits;
            }
            return ret;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * This method is the overridden implementation of the
     * getExtendedKeyUsage method in X509Certificate in the Sun
     * provider. It is better performance-wise since it returns cached
     * values.
     */
    @Override
    public synchronized List<String> getExtendedKeyUsage()
        throws CertificateParsingException {
        if (extKeyUsage != null) {
            return extKeyUsage;
        }
        ExtendedKeyUsageExtension ext = (ExtendedKeyUsageExtension)
            getExtensionIfParseable(PKIXExtensions.ExtendedKeyUsage_Id);
        if (ext == null) {
            return null;
        }
        extKeyUsage = Collections.unmodifiableList(ext.getExtendedKeyUsage());
        return extKeyUsage;
    }

    /**
     * Returns the extension identified by OID or null if it doesn't exist
     * and is not unparseable.
     *
     * @throws CertificateParsingException if extension is unparseable
     */
    private Extension getExtensionIfParseable(ObjectIdentifier oid)
            throws CertificateParsingException {
        Extension ext = getExtension(oid);
        if (ext == null) {
            // check if unparseable
            UnparseableExtension unparseableExt =
                   (UnparseableExtension)getUnparseableExtension(oid);
            if (unparseableExt != null) {
                throw new CertificateParsingException(
                        unparseableExt.exceptionMessage());
            }
        }
        return ext;
    }

    /**
     * This static method is the default implementation of the
     * getExtendedKeyUsage method in X509Certificate. A
     * X509Certificate provider generally should overwrite this to
     * provide among other things caching for better performance.
     */
    public static List<String> getExtendedKeyUsage(X509Certificate cert)
        throws CertificateParsingException {
        try {
            byte[] ext = cert.getExtensionValue
                    (KnownOIDs.extendedKeyUsage.value());
            if (ext == null)
                return null;
            DerValue val = new DerValue(ext);
            byte[] data = val.getOctetString();

            ExtendedKeyUsageExtension ekuExt =
                new ExtendedKeyUsageExtension(Boolean.FALSE, data);
            return Collections.unmodifiableList(ekuExt.getExtendedKeyUsage());
        } catch (IOException ioe) {
            throw new CertificateParsingException(ioe);
        }
    }

    /**
     * Get the certificate constraints path length from
     * the critical BasicConstraints extension, (oid = 2.5.29.19).
     * @return the length of the constraint.
     */
    public int getBasicConstraints() {
        try {
            BasicConstraintsExtension certExt = getBasicConstraintsExtension();
            if (certExt == null)
                return -1;

            if (certExt.isCa())
                return certExt.getPathLen();
            else
                return -1;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Converts a GeneralNames structure into an immutable Collection of
     * alternative names (subject or issuer) in the form required by
     * {@link #getSubjectAlternativeNames} or
     * {@link #getIssuerAlternativeNames}.
     *
     * @param names the GeneralNames to be converted
     * @return an immutable Collection of alternative names
     */
    private static Collection<List<?>> makeAltNames(GeneralNames names) {
        if (names.isEmpty()) {
            return Collections.emptySet();
        }
        List<List<?>> newNames = new ArrayList<>();
        for (GeneralName gname : names.names()) {
            GeneralNameInterface name = gname.getName();
            List<Object> nameEntry = new ArrayList<>(2);
            nameEntry.add(name.getType());
            switch (name.getType()) {
            case GeneralNameInterface.NAME_RFC822:
                nameEntry.add(((RFC822Name) name).getName());
                break;
            case GeneralNameInterface.NAME_DNS:
                nameEntry.add(((DNSName) name).getName());
                break;
            case GeneralNameInterface.NAME_DIRECTORY:
                nameEntry.add(((X500Name) name).getRFC2253Name());
                break;
            case GeneralNameInterface.NAME_URI:
                nameEntry.add(((URIName) name).getName());
                break;
            case GeneralNameInterface.NAME_IP:
                try {
                    nameEntry.add(((IPAddressName) name).getName());
                } catch (IOException ioe) {
                    // IPAddressName in cert is bogus
                    throw new RuntimeException("IPAddress cannot be parsed",
                        ioe);
                }
                break;
            case GeneralNameInterface.NAME_OID:
                nameEntry.add(((OIDName) name).getOID().toString());
                break;
            default:
                // add DER encoded form
                DerOutputStream derOut = new DerOutputStream();
                name.encode(derOut);
                nameEntry.add(derOut.toByteArray());
                if (name.getType() == GeneralNameInterface.NAME_ANY
                        && name instanceof OtherName oname) {
                    nameEntry.add(oname.getOID().toString());
                    byte[] nameValue = oname.getNameValue();
                    try {
                        String v = new DerValue(nameValue).getAsString();
                        nameEntry.add(v == null ? nameValue : v);
                    } catch (IOException ioe) {
                        nameEntry.add(nameValue);
                    }
                }
                break;
            }
            newNames.add(Collections.unmodifiableList(nameEntry));
        }
        return Collections.unmodifiableCollection(newNames);
    }

    /**
     * Checks a Collection of altNames and clones any name entries of type
     * byte [].
     */ // only partially generified due to javac bug
    private static Collection<List<?>> cloneAltNames(Collection<List<?>> altNames) {
        boolean mustClone = false;
        for (List<?> nameEntry : altNames) {
            if (nameEntry.get(1) instanceof byte[]) {
                // must clone names
                mustClone = true;
                break;
            }
        }
        if (mustClone) {
            List<List<?>> namesCopy = new ArrayList<>();
            for (List<?> nameEntry : altNames) {
                Object nameObject = nameEntry.get(1);
                if (nameObject instanceof byte[]) {
                    List<Object> nameEntryCopy =
                                        new ArrayList<>(nameEntry);
                    nameEntryCopy.set(1, ((byte[])nameObject).clone());
                    namesCopy.add(Collections.unmodifiableList(nameEntryCopy));
                } else {
                    namesCopy.add(nameEntry);
                }
            }
            return Collections.unmodifiableCollection(namesCopy);
        } else {
            return altNames;
        }
    }

    /**
     * This method is the overridden implementation of the
     * getSubjectAlternativeNames method in X509Certificate in the Sun
     * provider. It is better performance-wise since it returns cached
     * values.
     */
    @Override
    public synchronized Collection<List<?>> getSubjectAlternativeNames()
        throws CertificateParsingException {
        // return cached value if we can
        if (subjectAlternativeNames != null) {
            return cloneAltNames(subjectAlternativeNames);
        }
        SubjectAlternativeNameExtension subjectAltNameExt =
            (SubjectAlternativeNameExtension)getExtensionIfParseable(
                PKIXExtensions.SubjectAlternativeName_Id);
        if (subjectAltNameExt == null) {
            return null;
        }
        GeneralNames names = subjectAltNameExt.getNames();
        subjectAlternativeNames = makeAltNames(names);
        return subjectAlternativeNames;
    }

    /**
     * This static method is the default implementation of the
     * getSubjectAlternativeNames method in X509Certificate. A
     * X509Certificate provider generally should overwrite this to
     * provide among other things caching for better performance.
     */
    public static Collection<List<?>> getSubjectAlternativeNames(X509Certificate cert)
        throws CertificateParsingException {
        try {
            byte[] ext = cert.getExtensionValue
                    (KnownOIDs.SubjectAlternativeName.value());
            if (ext == null) {
                return null;
            }
            DerValue val = new DerValue(ext);
            byte[] data = val.getOctetString();

            SubjectAlternativeNameExtension subjectAltNameExt =
                new SubjectAlternativeNameExtension(Boolean.FALSE,
                                                    data);

            GeneralNames names = subjectAltNameExt.getNames();
            return makeAltNames(names);
        } catch (IOException ioe) {
            throw new CertificateParsingException(ioe);
        }
    }

    /**
     * This method is the overridden implementation of the
     * getIssuerAlternativeNames method in X509Certificate in the Sun
     * provider. It is better performance-wise since it returns cached
     * values.
     */
    @Override
    public synchronized Collection<List<?>> getIssuerAlternativeNames()
        throws CertificateParsingException {
        // return cached value if we can
        if (issuerAlternativeNames != null) {
            return cloneAltNames(issuerAlternativeNames);
        }
        IssuerAlternativeNameExtension issuerAltNameExt =
            (IssuerAlternativeNameExtension)getExtensionIfParseable(
                PKIXExtensions.IssuerAlternativeName_Id);
        if (issuerAltNameExt == null) {
            return null;
        }
        GeneralNames names = issuerAltNameExt.getNames();
        issuerAlternativeNames = makeAltNames(names);
        return issuerAlternativeNames;
    }

    /**
     * This static method is the default implementation of the
     * getIssuerAlternativeNames method in X509Certificate. A
     * X509Certificate provider generally should overwrite this to
     * provide among other things caching for better performance.
     */
    public static Collection<List<?>> getIssuerAlternativeNames(X509Certificate cert)
        throws CertificateParsingException {
        try {
            byte[] ext = cert.getExtensionValue
                    (KnownOIDs.IssuerAlternativeName.value());
            if (ext == null) {
                return null;
            }

            DerValue val = new DerValue(ext);
            byte[] data = val.getOctetString();

            IssuerAlternativeNameExtension issuerAltNameExt =
                new IssuerAlternativeNameExtension(Boolean.FALSE,
                                                    data);
            GeneralNames names = issuerAltNameExt.getNames();
            return makeAltNames(names);
        } catch (IOException ioe) {
            throw new CertificateParsingException(ioe);
        }
    }

    public AuthorityInfoAccessExtension getAuthorityInfoAccessExtension() {
        return (AuthorityInfoAccessExtension)
            getExtension(PKIXExtensions.AuthInfoAccess_Id);
    }

    /************************************************************/

    /*
     * Cert is a SIGNED ASN.1 macro, a three element sequence:
     *
     *  - Data to be signed (ToBeSigned) -- the "raw" cert
     *  - Signature algorithm (SigAlgId)
     *  - The signature bits
     *
     * This routine unmarshals the certificate, saving the signature
     * parts away for later verification.
     */
    private void parse(DerValue val)
            throws CertificateException, IOException {
        // check if we can overwrite the certificate

        if (val.data == null || val.tag != DerValue.tag_Sequence)
            throw new CertificateParsingException(
                      "invalid DER-encoded certificate data");

        signedCert = val.toByteArray();
        DerValue[] seq = new DerValue[3];

        seq[0] = val.data.getDerValue();
        seq[1] = val.data.getDerValue();
        seq[2] = val.data.getDerValue();

        if (val.data.available() != 0) {
            throw new CertificateParsingException("signed overrun, bytes = "
                                     + val.data.available());
        }
        if (seq[0].tag != DerValue.tag_Sequence) {
            throw new CertificateParsingException("signed fields invalid");
        }

        algId = AlgorithmId.parse(seq[1]);
        signature = seq[2].getBitString();

        if (seq[1].data.available() != 0) {
            throw new CertificateParsingException("algid field overrun");
        }
        if (seq[2].data.available() != 0)
            throw new CertificateParsingException("signed fields overrun");

        // The CertificateInfo
        info = new X509CertInfo(seq[0]);

        // the "inner" and "outer" signature algorithms must match
        AlgorithmId infoSigAlg = info.getAlgorithmId().getAlgId();
        if (! algId.equals(infoSigAlg))
            throw new CertificateException("Signature algorithm mismatch");
    }

    /**
     * Extract the subject or issuer X500Principal from an X509Certificate.
     * Parses the encoded form of the cert to preserve the principal's
     * ASN.1 encoding.
     */
    private static X500Principal getX500Principal(X509Certificate cert,
            boolean getIssuer) throws Exception {
        byte[] encoded = cert.getEncoded();
        DerInputStream derIn = new DerInputStream(encoded);
        DerValue tbsCert = derIn.getSequence(3)[0];
        DerInputStream tbsIn = tbsCert.data;
        DerValue tmp;
        tmp = tbsIn.getDerValue();
        // skip version number if present
        if (tmp.isContextSpecific((byte)0)) {
          tmp = tbsIn.getDerValue();
        }
        // tmp always contains serial number now
        tmp = tbsIn.getDerValue();              // skip signature
        tmp = tbsIn.getDerValue();              // issuer
        if (!getIssuer) {
            tmp = tbsIn.getDerValue();          // skip validity
            tmp = tbsIn.getDerValue();          // subject
        }
        byte[] principalBytes = tmp.toByteArray();
        return new X500Principal(principalBytes);
    }

    /**
     * Extract the subject X500Principal from an X509Certificate.
     * Called from java.security.cert.X509Certificate.getSubjectX500Principal().
     */
    public static X500Principal getSubjectX500Principal(X509Certificate cert) {
        try {
            return getX500Principal(cert, false);
        } catch (Exception e) {
            throw new RuntimeException("Could not parse subject", e);
        }
    }

    /**
     * Extract the issuer X500Principal from an X509Certificate.
     * Called from java.security.cert.X509Certificate.getIssuerX500Principal().
     */
    public static X500Principal getIssuerX500Principal(X509Certificate cert) {
        try {
            return getX500Principal(cert, true);
        } catch (Exception e) {
            throw new RuntimeException("Could not parse issuer", e);
        }
    }

    /**
     * Returned the encoding of the given certificate for internal use.
     * Callers must guarantee that they neither modify it nor expose it
     * to untrusted code. Uses getEncodedInternal() if the certificate
     * is instance of X509CertImpl, getEncoded() otherwise.
     */
    public static byte[] getEncodedInternal(Certificate cert)
            throws CertificateEncodingException {
        if (cert instanceof X509CertImpl) {
            return ((X509CertImpl)cert).getEncodedInternal();
        } else {
            return cert.getEncoded();
        }
    }

    /**
     * Utility method to convert an arbitrary instance of X509Certificate
     * to a X509CertImpl. Does a cast if possible, otherwise reparses
     * the encoding.
     */
    public static X509CertImpl toImpl(X509Certificate cert)
            throws CertificateException {
        if (cert instanceof X509CertImpl) {
            return (X509CertImpl)cert;
        } else {
            return X509Factory.intern(cert);
        }
    }

    /**
     * Utility method to test if a certificate is self-issued. This is
     * the case iff the subject and issuer X500Principals are equal.
     */
    public static boolean isSelfIssued(X509Certificate cert) {
        X500Principal subject = cert.getSubjectX500Principal();
        X500Principal issuer = cert.getIssuerX500Principal();
        return subject.equals(issuer);
    }

    /**
     * Utility method to test if a certificate is self-signed. This is
     * the case iff the subject and issuer X500Principals are equal
     * AND the certificate's subject public key can be used to verify
     * the certificate. In case of exception, returns false.
     */
    public static boolean isSelfSigned(X509Certificate cert,
        String sigProvider) {
        if (isSelfIssued(cert)) {
            try {
                if (sigProvider == null) {
                    cert.verify(cert.getPublicKey());
                } else {
                    cert.verify(cert.getPublicKey(), sigProvider);
                }
                return true;
            } catch (Exception e) {
                // In case of exception, return false
            }
        }
        return false;
    }

    private final ConcurrentHashMap<String,String> fingerprints =
            new ConcurrentHashMap<>(2);

    private String getFingerprint(String algorithm, Debug debug) {
        return fingerprints.computeIfAbsent(algorithm,
            x -> {
                try {
                    return getFingerprintInternal(x, getEncodedInternal(), debug);
                } catch (CertificateEncodingException e) {
                    if (debug != null) {
                        debug.println("Cannot encode certificate: " + e);
                    }
                    return null;
                }
            });
    }

    private static String getFingerprintInternal(String algorithm,
            byte[] encodedCert, Debug debug) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] digest = md.digest(encodedCert);
            return HexFormat.of().withUpperCase().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            if (debug != null) {
                debug.println("Cannot create " + algorithm
                    + " MessageDigest: " + e);
            }
            return null;
        }
    }

    /**
     * Gets the requested fingerprint of the certificate. The result
     * only contains 0-9 and A-F. No small case, no colon.
     *
     * @param algorithm the MessageDigest algorithm
     * @param cert the X509Certificate
     * @return the fingerprint, or null if it cannot be calculated because
     *     of an exception
     */
    public static String getFingerprint(String algorithm,
            X509Certificate cert, Debug debug) {
        if (cert instanceof X509CertImpl) {
            return ((X509CertImpl)cert).getFingerprint(algorithm, debug);
        } else {
            try {
                return getFingerprintInternal(algorithm, cert.getEncoded(), debug);
            } catch (CertificateEncodingException e) {
                if (debug != null) {
                    debug.println("Cannot encode certificate: " + e);
                }
                return null;
            }
        }
    }

    /**
     * Restores the state of this object from the stream.
     * <p>
     * Deserialization of this object is not supported.
     *
     * @param  stream the {@code ObjectInputStream} from which data is read
     * @throws IOException if an I/O error occurs
     * @throws ClassNotFoundException if a serialized class cannot be loaded
     */
    @java.io.Serial
    private void readObject(ObjectInputStream stream)
            throws IOException, ClassNotFoundException {
        throw new InvalidObjectException(
                "X509CertImpls are not directly deserializable");
    }
}
