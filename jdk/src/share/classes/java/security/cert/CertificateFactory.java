/*
 * Copyright 1998-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.security.cert;

import java.io.InputStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.security.Provider;
import java.security.Security;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

import sun.security.jca.*;
import sun.security.jca.GetInstance.Instance;

/**
 * This class defines the functionality of a certificate factory, which is
 * used to generate certificate, certification path (<code>CertPath</code>)
 * and certificate revocation list (CRL) objects from their encodings.
 *
 * <p>For encodings consisting of multiple certificates, use
 * <code>generateCertificates</code> when you want to
 * parse a collection of possibly unrelated certificates. Otherwise,
 * use <code>generateCertPath</code> when you want to generate
 * a <code>CertPath</code> (a certificate chain) and subsequently
 * validate it with a <code>CertPathValidator</code>.
 *
 * <p>A certificate factory for X.509 must return certificates that are an
 * instance of <code>java.security.cert.X509Certificate</code>, and CRLs
 * that are an instance of <code>java.security.cert.X509CRL</code>.
 *
 * <p>The following example reads a file with Base64 encoded certificates,
 * which are each bounded at the beginning by -----BEGIN CERTIFICATE-----, and
 * bounded at the end by -----END CERTIFICATE-----. We convert the
 * <code>FileInputStream</code> (which does not support <code>mark</code>
 * and <code>reset</code>) to a <code>BufferedInputStream</code> (which
 * supports those methods), so that each call to
 * <code>generateCertificate</code> consumes only one certificate, and the
 * read position of the input stream is positioned to the next certificate in
 * the file:<p>
 *
 * <pre>
 * FileInputStream fis = new FileInputStream(filename);
 * BufferedInputStream bis = new BufferedInputStream(fis);
 *
 * CertificateFactory cf = CertificateFactory.getInstance("X.509");
 *
 * while (bis.available() > 0) {
 *    Certificate cert = cf.generateCertificate(bis);
 *    System.out.println(cert.toString());
 * }
 * </pre>
 *
 * <p>The following example parses a PKCS#7-formatted certificate reply stored
 * in a file and extracts all the certificates from it:<p>
 *
 * <pre>
 * FileInputStream fis = new FileInputStream(filename);
 * CertificateFactory cf = CertificateFactory.getInstance("X.509");
 * Collection c = cf.generateCertificates(fis);
 * Iterator i = c.iterator();
 * while (i.hasNext()) {
 *    Certificate cert = (Certificate)i.next();
 *    System.out.println(cert);
 * }
 * </pre>
 *
 * @author Hemma Prafullchandra
 * @author Jan Luehe
 * @author Sean Mullan
 *
 *
 * @see Certificate
 * @see X509Certificate
 * @see CertPath
 * @see CRL
 * @see X509CRL
 *
 * @since 1.2
 */

public class CertificateFactory {

    // The certificate type
    private String type;

    // The provider
    private Provider provider;

    // The provider implementation
    private CertificateFactorySpi certFacSpi;

    /**
     * Creates a CertificateFactory object of the given type, and encapsulates
     * the given provider implementation (SPI object) in it.
     *
     * @param certFacSpi the provider implementation.
     * @param provider the provider.
     * @param type the certificate type.
     */
    protected CertificateFactory(CertificateFactorySpi certFacSpi,
                                 Provider provider, String type)
    {
        this.certFacSpi = certFacSpi;
        this.provider = provider;
        this.type = type;
    }

    /**
     * Returns a certificate factory object that implements the
     * specified certificate type.
     *
     * <p> This method traverses the list of registered security Providers,
     * starting with the most preferred Provider.
     * A new CertificateFactory object encapsulating the
     * CertificateFactorySpi implementation from the first
     * Provider that supports the specified type is returned.
     *
     * <p> Note that the list of registered providers may be retrieved via
     * the {@link Security#getProviders() Security.getProviders()} method.
     *
     * @param type the name of the requested certificate type.
     * See Appendix A in the <a href=
     * "../../../../technotes/guides/security/crypto/CryptoSpec.html#AppA">
     * Java Cryptography Architecture API Specification &amp; Reference </a>
     * for information about standard certificate types.
     *
     * @return a certificate factory object for the specified type.
     *
     * @exception CertificateException if no Provider supports a
     *          CertificateFactorySpi implementation for the
     *          specified type.
     *
     * @see java.security.Provider
     */
    public static final CertificateFactory getInstance(String type)
            throws CertificateException {
        try {
            Instance instance = GetInstance.getInstance("CertificateFactory",
                CertificateFactorySpi.class, type);
            return new CertificateFactory((CertificateFactorySpi)instance.impl,
                instance.provider, type);
        } catch (NoSuchAlgorithmException e) {
            throw new CertificateException(type + " not found", e);
        }
    }

    /**
     * Returns a certificate factory object for the specified
     * certificate type.
     *
     * <p> A new CertificateFactory object encapsulating the
     * CertificateFactorySpi implementation from the specified provider
     * is returned.  The specified provider must be registered
     * in the security provider list.
     *
     * <p> Note that the list of registered providers may be retrieved via
     * the {@link Security#getProviders() Security.getProviders()} method.
     *
     * @param type the certificate type.
     * See Appendix A in the <a href=
     * "../../../../technotes/guides/security/crypto/CryptoSpec.html#AppA">
     * Java Cryptography Architecture API Specification &amp; Reference </a>
     * for information about standard certificate types.
     *
     * @param provider the name of the provider.
     *
     * @return a certificate factory object for the specified type.
     *
     * @exception CertificateException if a CertificateFactorySpi
     *          implementation for the specified algorithm is not
     *          available from the specified provider.
     *
     * @exception NoSuchProviderException if the specified provider is not
     *          registered in the security provider list.
     *
     * @exception IllegalArgumentException if the provider name is null
     *          or empty.
     *
     * @see java.security.Provider
     */
    public static final CertificateFactory getInstance(String type,
            String provider) throws CertificateException,
            NoSuchProviderException {
        try {
            Instance instance = GetInstance.getInstance("CertificateFactory",
                CertificateFactorySpi.class, type, provider);
            return new CertificateFactory((CertificateFactorySpi)instance.impl,
                instance.provider, type);
        } catch (NoSuchAlgorithmException e) {
            throw new CertificateException(type + " not found", e);
        }
    }

    /**
     * Returns a certificate factory object for the specified
     * certificate type.
     *
     * <p> A new CertificateFactory object encapsulating the
     * CertificateFactorySpi implementation from the specified Provider
     * object is returned.  Note that the specified Provider object
     * does not have to be registered in the provider list.
     *
     * @param type the certificate type.
     * See Appendix A in the <a href=
     * "../../../../technotes/guides/security/crypto/CryptoSpec.html#AppA">
     * Java Cryptography Architecture API Specification &amp; Reference </a>
     * for information about standard certificate types.

     * @param provider the provider.
     *
     * @return a certificate factory object for the specified type.
     *
     * @exception CertificateException if a CertificateFactorySpi
     *          implementation for the specified algorithm is not available
     *          from the specified Provider object.
     *
     * @exception IllegalArgumentException if the <code>provider</code> is
     *          null.
     *
     * @see java.security.Provider
     *
     * @since 1.4
     */
    public static final CertificateFactory getInstance(String type,
            Provider provider) throws CertificateException {
        try {
            Instance instance = GetInstance.getInstance("CertificateFactory",
                CertificateFactorySpi.class, type, provider);
            return new CertificateFactory((CertificateFactorySpi)instance.impl,
                instance.provider, type);
        } catch (NoSuchAlgorithmException e) {
            throw new CertificateException(type + " not found", e);
        }
    }

    /**
     * Returns the provider of this certificate factory.
     *
     * @return the provider of this certificate factory.
     */
    public final Provider getProvider() {
        return this.provider;
    }

    /**
     * Returns the name of the certificate type associated with this
     * certificate factory.
     *
     * @return the name of the certificate type associated with this
     * certificate factory.
     */
    public final String getType() {
        return this.type;
    }

    /**
     * Generates a certificate object and initializes it with
     * the data read from the input stream <code>inStream</code>.
     *
     * <p>In order to take advantage of the specialized certificate format
     * supported by this certificate factory,
     * the returned certificate object can be typecast to the corresponding
     * certificate class. For example, if this certificate
     * factory implements X.509 certificates, the returned certificate object
     * can be typecast to the <code>X509Certificate</code> class.
     *
     * <p>In the case of a certificate factory for X.509 certificates, the
     * certificate provided in <code>inStream</code> must be DER-encoded and
     * may be supplied in binary or printable (Base64) encoding. If the
     * certificate is provided in Base64 encoding, it must be bounded at
     * the beginning by -----BEGIN CERTIFICATE-----, and must be bounded at
     * the end by -----END CERTIFICATE-----.
     *
     * <p>Note that if the given input stream does not support
     * {@link java.io.InputStream#mark(int) mark} and
     * {@link java.io.InputStream#reset() reset}, this method will
     * consume the entire input stream. Otherwise, each call to this
     * method consumes one certificate and the read position of the
     * input stream is positioned to the next available byte after
     * the inherent end-of-certificate marker. If the data in the input stream
     * does not contain an inherent end-of-certificate marker (other
     * than EOF) and there is trailing data after the certificate is parsed, a
     * <code>CertificateException</code> is thrown.
     *
     * @param inStream an input stream with the certificate data.
     *
     * @return a certificate object initialized with the data
     * from the input stream.
     *
     * @exception CertificateException on parsing errors.
     */
    public final Certificate generateCertificate(InputStream inStream)
        throws CertificateException
    {
        return certFacSpi.engineGenerateCertificate(inStream);
    }

    /**
     * Returns an iteration of the <code>CertPath</code> encodings supported
     * by this certificate factory, with the default encoding first. See
     * Appendix A in the
     * <a href="../../../../technotes/guides/security/certpath/CertPathProgGuide.html#AppA">
     * Java Certification Path API Programmer's Guide</a> for information about
     * standard encoding names and their formats.
     * <p>
     * Attempts to modify the returned <code>Iterator</code> via its
     * <code>remove</code> method result in an
     * <code>UnsupportedOperationException</code>.
     *
     * @return an <code>Iterator</code> over the names of the supported
     *         <code>CertPath</code> encodings (as <code>String</code>s)
     * @since 1.4
     */
    public final Iterator<String> getCertPathEncodings() {
        return(certFacSpi.engineGetCertPathEncodings());
    }

    /**
     * Generates a <code>CertPath</code> object and initializes it with
     * the data read from the <code>InputStream</code> inStream. The data
     * is assumed to be in the default encoding. The name of the default
     * encoding is the first element of the <code>Iterator</code> returned by
     * the {@link #getCertPathEncodings getCertPathEncodings} method.
     *
     * @param inStream an <code>InputStream</code> containing the data
     * @return a <code>CertPath</code> initialized with the data from the
     *   <code>InputStream</code>
     * @exception CertificateException if an exception occurs while decoding
     * @since 1.4
     */
    public final CertPath generateCertPath(InputStream inStream)
        throws CertificateException
    {
        return(certFacSpi.engineGenerateCertPath(inStream));
    }

    /**
     * Generates a <code>CertPath</code> object and initializes it with
     * the data read from the <code>InputStream</code> inStream. The data
     * is assumed to be in the specified encoding. See Appendix A in the
     * <a href="../../../../technotes/guides/security/certpath/CertPathProgGuide.html#AppA">
     * Java Certification Path API Programmer's Guide</a>
     * for information about standard encoding names and their formats.
     *
     * @param inStream an <code>InputStream</code> containing the data
     * @param encoding the encoding used for the data
     * @return a <code>CertPath</code> initialized with the data from the
     *   <code>InputStream</code>
     * @exception CertificateException if an exception occurs while decoding or
     *   the encoding requested is not supported
     * @since 1.4
     */
    public final CertPath generateCertPath(InputStream inStream,
        String encoding) throws CertificateException
    {
        return(certFacSpi.engineGenerateCertPath(inStream, encoding));
    }

    /**
     * Generates a <code>CertPath</code> object and initializes it with
     * a <code>List</code> of <code>Certificate</code>s.
     * <p>
     * The certificates supplied must be of a type supported by the
     * <code>CertificateFactory</code>. They will be copied out of the supplied
     * <code>List</code> object.
     *
     * @param certificates a <code>List</code> of <code>Certificate</code>s
     * @return a <code>CertPath</code> initialized with the supplied list of
     *   certificates
     * @exception CertificateException if an exception occurs
     * @since 1.4
     */
    public final CertPath
        generateCertPath(List<? extends Certificate> certificates)
        throws CertificateException
    {
        return(certFacSpi.engineGenerateCertPath(certificates));
    }

    /**
     * Returns a (possibly empty) collection view of the certificates read
     * from the given input stream <code>inStream</code>.
     *
     * <p>In order to take advantage of the specialized certificate format
     * supported by this certificate factory, each element in
     * the returned collection view can be typecast to the corresponding
     * certificate class. For example, if this certificate
     * factory implements X.509 certificates, the elements in the returned
     * collection can be typecast to the <code>X509Certificate</code> class.
     *
     * <p>In the case of a certificate factory for X.509 certificates,
     * <code>inStream</code> may contain a sequence of DER-encoded certificates
     * in the formats described for
     * {@link #generateCertificate(java.io.InputStream) generateCertificate}.
     * In addition, <code>inStream</code> may contain a PKCS#7 certificate
     * chain. This is a PKCS#7 <i>SignedData</i> object, with the only
     * significant field being <i>certificates</i>. In particular, the
     * signature and the contents are ignored. This format allows multiple
     * certificates to be downloaded at once. If no certificates are present,
     * an empty collection is returned.
     *
     * <p>Note that if the given input stream does not support
     * {@link java.io.InputStream#mark(int) mark} and
     * {@link java.io.InputStream#reset() reset}, this method will
     * consume the entire input stream.
     *
     * @param inStream the input stream with the certificates.
     *
     * @return a (possibly empty) collection view of
     * java.security.cert.Certificate objects
     * initialized with the data from the input stream.
     *
     * @exception CertificateException on parsing errors.
     */
    public final Collection<? extends Certificate> generateCertificates
            (InputStream inStream) throws CertificateException {
        return certFacSpi.engineGenerateCertificates(inStream);
    }

    /**
     * Generates a certificate revocation list (CRL) object and initializes it
     * with the data read from the input stream <code>inStream</code>.
     *
     * <p>In order to take advantage of the specialized CRL format
     * supported by this certificate factory,
     * the returned CRL object can be typecast to the corresponding
     * CRL class. For example, if this certificate
     * factory implements X.509 CRLs, the returned CRL object
     * can be typecast to the <code>X509CRL</code> class.
     *
     * <p>Note that if the given input stream does not support
     * {@link java.io.InputStream#mark(int) mark} and
     * {@link java.io.InputStream#reset() reset}, this method will
     * consume the entire input stream. Otherwise, each call to this
     * method consumes one CRL and the read position of the input stream
     * is positioned to the next available byte after the the inherent
     * end-of-CRL marker. If the data in the
     * input stream does not contain an inherent end-of-CRL marker (other
     * than EOF) and there is trailing data after the CRL is parsed, a
     * <code>CRLException</code> is thrown.
     *
     * @param inStream an input stream with the CRL data.
     *
     * @return a CRL object initialized with the data
     * from the input stream.
     *
     * @exception CRLException on parsing errors.
     */
    public final CRL generateCRL(InputStream inStream)
        throws CRLException
    {
        return certFacSpi.engineGenerateCRL(inStream);
    }

    /**
     * Returns a (possibly empty) collection view of the CRLs read
     * from the given input stream <code>inStream</code>.
     *
     * <p>In order to take advantage of the specialized CRL format
     * supported by this certificate factory, each element in
     * the returned collection view can be typecast to the corresponding
     * CRL class. For example, if this certificate
     * factory implements X.509 CRLs, the elements in the returned
     * collection can be typecast to the <code>X509CRL</code> class.
     *
     * <p>In the case of a certificate factory for X.509 CRLs,
     * <code>inStream</code> may contain a sequence of DER-encoded CRLs.
     * In addition, <code>inStream</code> may contain a PKCS#7 CRL
     * set. This is a PKCS#7 <i>SignedData</i> object, with the only
     * significant field being <i>crls</i>. In particular, the
     * signature and the contents are ignored. This format allows multiple
     * CRLs to be downloaded at once. If no CRLs are present,
     * an empty collection is returned.
     *
     * <p>Note that if the given input stream does not support
     * {@link java.io.InputStream#mark(int) mark} and
     * {@link java.io.InputStream#reset() reset}, this method will
     * consume the entire input stream.
     *
     * @param inStream the input stream with the CRLs.
     *
     * @return a (possibly empty) collection view of
     * java.security.cert.CRL objects initialized with the data from the input
     * stream.
     *
     * @exception CRLException on parsing errors.
     */
    public final Collection<? extends CRL> generateCRLs(InputStream inStream)
            throws CRLException {
        return certFacSpi.engineGenerateCRLs(inStream);
    }
}
