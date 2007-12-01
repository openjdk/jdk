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

package sun.security.provider;

import java.io.*;
import java.util.Collection;
import java.util.*;
import java.security.cert.*;
import sun.security.x509.X509CertImpl;
import sun.security.x509.X509CRLImpl;
import sun.security.pkcs.PKCS7;
import sun.security.provider.certpath.X509CertPath;
import sun.security.provider.certpath.X509CertificatePair;
import sun.security.util.DerValue;
import sun.security.util.Cache;
import sun.misc.BASE64Decoder;

/**
 * This class defines a certificate factory for X.509 v3 certificates &
 * certification paths, and X.509 v2 certificate revocation lists (CRLs).
 *
 * @author Jan Luehe
 * @author Hemma Prafullchandra
 * @author Sean Mullan
 *
 *
 * @see java.security.cert.CertificateFactorySpi
 * @see java.security.cert.Certificate
 * @see java.security.cert.CertPath
 * @see java.security.cert.CRL
 * @see java.security.cert.X509Certificate
 * @see java.security.cert.X509CRL
 * @see sun.security.x509.X509CertImpl
 * @see sun.security.x509.X509CRLImpl
 */

public class X509Factory extends CertificateFactorySpi {

    public static final String BEGIN_CERT = "-----BEGIN CERTIFICATE-----";
    public static final String END_CERT = "-----END CERTIFICATE-----";

    private static final int defaultExpectedLineLength = 80;

    private static final char[] endBoundary = "-----END".toCharArray();

    private static final int ENC_MAX_LENGTH = 4096 * 1024; // 4 MB MAX

    private static final Cache certCache = Cache.newSoftMemoryCache(750);
    private static final Cache crlCache = Cache.newSoftMemoryCache(750);

    /**
     * Generates an X.509 certificate object and initializes it with
     * the data read from the input stream <code>is</code>.
     *
     * @param is an input stream with the certificate data.
     *
     * @return an X.509 certificate object initialized with the data
     * from the input stream.
     *
     * @exception CertificateException on parsing errors.
     */
    public Certificate engineGenerateCertificate(InputStream is)
        throws CertificateException
    {
        if (is == null) {
            // clear the caches (for debugging)
            certCache.clear();
            X509CertificatePair.clearCache();
            throw new CertificateException("Missing input stream");
        }
        try {
            if (is.markSupported() == false) {
                // consume the entire input stream
                byte[] totalBytes;
                totalBytes = getTotalBytes(new BufferedInputStream(is));
                is = new ByteArrayInputStream(totalBytes);
            }
            byte[] encoding = readSequence(is);
            if (encoding != null) {
                X509CertImpl cert = (X509CertImpl)getFromCache(certCache, encoding);
                if (cert != null) {
                    return cert;
                }
                cert = new X509CertImpl(encoding);
                addToCache(certCache, cert.getEncodedInternal(), cert);
                return cert;
            } else {
                X509CertImpl cert;
                // determine if binary or Base64 encoding. If Base64 encoding,
                // the certificate must be bounded at the beginning by
                // "-----BEGIN".
                if (isBase64(is)) {
                    // Base64
                    byte[] data = base64_to_binary(is);
                    cert = new X509CertImpl(data);
                } else {
                    // binary
                    cert = new X509CertImpl(new DerValue(is));
                }
                return intern(cert);
            }
        } catch (IOException ioe) {
            throw (CertificateException)new CertificateException
            ("Could not parse certificate: " + ioe.toString()).initCause(ioe);
        }
    }

    /**
     * Read a DER SEQUENCE from an InputStream and return the encoding.
     * If data does not represent a SEQUENCE, it uses indefinite length
     * encoding, or is longer than ENC_MAX_LENGTH, the stream is reset
     * and this method returns null.
     */
    private static byte[] readSequence(InputStream in) throws IOException {
        in.mark(ENC_MAX_LENGTH);
        byte[] b = new byte[4];
        int i = readFully(in, b, 0, b.length);
        if ((i != b.length) || (b[0] != 0x30)) { // first byte must be SEQUENCE
            in.reset();
            return null;
        }
        i = b[1] & 0xff;
        int totalLength;
        if (i < 0x80) {
            int valueLength = i;
            totalLength = valueLength + 2;
        } else if (i == 0x81) {
            int valueLength = b[2] & 0xff;
            totalLength = valueLength + 3;
        } else if (i == 0x82) {
            int valueLength = ((b[2] & 0xff) << 8) | (b[3] & 0xff);
            totalLength = valueLength + 4;
        } else { // ignore longer length forms
            in.reset();
            return null;
        }
        if (totalLength > ENC_MAX_LENGTH) {
            in.reset();
            return null;
        }
        byte[] encoding = new byte[totalLength];
        if( totalLength < b.length ) {
            in.reset();
            i = readFully(in, encoding, 0, totalLength);
            if( i != totalLength ) {
                in.reset();
                return null;
            }
        } else {
            System.arraycopy(b, 0, encoding, 0, b.length);
            int n = totalLength - b.length;
            i = readFully(in, encoding, b.length, n);
            if (i != n) {
                in.reset();
                return null;
            }
        }
        return encoding;
    }

    /**
     * Read from the stream until length bytes have been read or EOF has
     * been reached. Return the number of bytes actually read.
     */
    private static int readFully(InputStream in, byte[] buffer, int offset,
            int length) throws IOException {
        int read = 0;
        while (length > 0) {
            int n = in.read(buffer, offset, length);
            if (n <= 0) {
                break;
            }
            read += n;
            length -= n;
            offset += n;
        }
        return read;
    }

    /**
     * Return an interned X509CertImpl for the given certificate.
     * If the given X509Certificate or X509CertImpl is already present
     * in the cert cache, the cached object is returned. Otherwise,
     * if it is a X509Certificate, it is first converted to a X509CertImpl.
     * Then the X509CertImpl is added to the cache and returned.
     *
     * Note that all certificates created via generateCertificate(InputStream)
     * are already interned and this method does not need to be called.
     * It is useful for certificates that cannot be created via
     * generateCertificate() and for converting other X509Certificate
     * implementations to an X509CertImpl.
     */
    public static synchronized X509CertImpl intern(X509Certificate c)
            throws CertificateException {
        if (c == null) {
            return null;
        }
        boolean isImpl = c instanceof X509CertImpl;
        byte[] encoding;
        if (isImpl) {
            encoding = ((X509CertImpl)c).getEncodedInternal();
        } else {
            encoding = c.getEncoded();
        }
        X509CertImpl newC = (X509CertImpl)getFromCache(certCache, encoding);
        if (newC != null) {
            return newC;
        }
        if (isImpl) {
            newC = (X509CertImpl)c;
        } else {
            newC = new X509CertImpl(encoding);
            encoding = newC.getEncodedInternal();
        }
        addToCache(certCache, encoding, newC);
        return newC;
    }

    /**
     * Return an interned X509CRLImpl for the given certificate.
     * For more information, see intern(X509Certificate).
     */
    public static synchronized X509CRLImpl intern(X509CRL c)
            throws CRLException {
        if (c == null) {
            return null;
        }
        boolean isImpl = c instanceof X509CRLImpl;
        byte[] encoding;
        if (isImpl) {
            encoding = ((X509CRLImpl)c).getEncodedInternal();
        } else {
            encoding = c.getEncoded();
        }
        X509CRLImpl newC = (X509CRLImpl)getFromCache(crlCache, encoding);
        if (newC != null) {
            return newC;
        }
        if (isImpl) {
            newC = (X509CRLImpl)c;
        } else {
            newC = new X509CRLImpl(encoding);
            encoding = newC.getEncodedInternal();
        }
        addToCache(crlCache, encoding, newC);
        return newC;
    }

    /**
     * Get the X509CertImpl or X509CRLImpl from the cache.
     */
    private static synchronized Object getFromCache(Cache cache,
            byte[] encoding) {
        Object key = new Cache.EqualByteArray(encoding);
        Object value = cache.get(key);
        return value;
    }

    /**
     * Add the X509CertImpl or X509CRLImpl to the cache.
     */
    private static synchronized void addToCache(Cache cache, byte[] encoding,
            Object value) {
        if (encoding.length > ENC_MAX_LENGTH) {
            return;
        }
        Object key = new Cache.EqualByteArray(encoding);
        cache.put(key, value);
    }

    /**
     * Generates a <code>CertPath</code> object and initializes it with
     * the data read from the <code>InputStream</code> inStream. The data
     * is assumed to be in the default encoding.
     *
     * @param inStream an <code>InputStream</code> containing the data
     * @return a <code>CertPath</code> initialized with the data from the
     *   <code>InputStream</code>
     * @exception CertificateException if an exception occurs while decoding
     * @since 1.4
     */
    public CertPath engineGenerateCertPath(InputStream inStream)
        throws CertificateException
    {
        if (inStream == null) {
            throw new CertificateException("Missing input stream");
        }
        try {
            if (inStream.markSupported() == false) {
                // consume the entire input stream
                byte[] totalBytes;
                totalBytes = getTotalBytes(new BufferedInputStream(inStream));
                inStream = new ByteArrayInputStream(totalBytes);
            }
            // determine if binary or Base64 encoding. If Base64 encoding,
            // each certificate must be bounded at the beginning by
            // "-----BEGIN".
            if (isBase64(inStream)) {
                // Base64
                byte[] data = base64_to_binary(inStream);
                return new X509CertPath(new ByteArrayInputStream(data));
            } else {
                return new X509CertPath(inStream);
            }
        } catch (IOException ioe) {
            throw new CertificateException(ioe.getMessage());
        }
    }

    /**
     * Generates a <code>CertPath</code> object and initializes it with
     * the data read from the <code>InputStream</code> inStream. The data
     * is assumed to be in the specified encoding.
     *
     * @param inStream an <code>InputStream</code> containing the data
     * @param encoding the encoding used for the data
     * @return a <code>CertPath</code> initialized with the data from the
     *   <code>InputStream</code>
     * @exception CertificateException if an exception occurs while decoding or
     *   the encoding requested is not supported
     * @since 1.4
     */
    public CertPath engineGenerateCertPath(InputStream inStream,
        String encoding) throws CertificateException
    {
        if (inStream == null) {
            throw new CertificateException("Missing input stream");
        }
        try {
            if (inStream.markSupported() == false) {
                // consume the entire input stream
                byte[] totalBytes;
                totalBytes = getTotalBytes(new BufferedInputStream(inStream));
                inStream = new ByteArrayInputStream(totalBytes);
            }
            // determine if binary or Base64 encoding. If Base64 encoding,
            // each certificate must be bounded at the beginning by
            // "-----BEGIN".
            if (isBase64(inStream)) {
                // Base64
                byte[] data = base64_to_binary(inStream);
                return new X509CertPath(new ByteArrayInputStream(data), encoding);
            } else {
                return(new X509CertPath(inStream, encoding));
            }
        } catch (IOException ioe) {
            throw new CertificateException(ioe.getMessage());
        }
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
    public CertPath
        engineGenerateCertPath(List<? extends Certificate> certificates)
        throws CertificateException
    {
        return(new X509CertPath(certificates));
    }

    /**
     * Returns an iteration of the <code>CertPath</code> encodings supported
     * by this certificate factory, with the default encoding first.
     * <p>
     * Attempts to modify the returned <code>Iterator</code> via its
     * <code>remove</code> method result in an
     * <code>UnsupportedOperationException</code>.
     *
     * @return an <code>Iterator</code> over the names of the supported
     *         <code>CertPath</code> encodings (as <code>String</code>s)
     * @since 1.4
     */
    public Iterator<String> engineGetCertPathEncodings() {
        return(X509CertPath.getEncodingsStatic());
    }

    /**
     * Returns a (possibly empty) collection view of X.509 certificates read
     * from the given input stream <code>is</code>.
     *
     * @param is the input stream with the certificates.
     *
     * @return a (possibly empty) collection view of X.509 certificate objects
     * initialized with the data from the input stream.
     *
     * @exception CertificateException on parsing errors.
     */
    public Collection<? extends java.security.cert.Certificate>
            engineGenerateCertificates(InputStream is)
            throws CertificateException {
        if (is == null) {
            throw new CertificateException("Missing input stream");
        }
        try {
            if (is.markSupported() == false) {
                // consume the entire input stream
                is = new ByteArrayInputStream
                     (getTotalBytes(new BufferedInputStream(is)));
            }
            return parseX509orPKCS7Cert(is);
        } catch (IOException ioe) {
            throw new CertificateException(ioe);
        }
    }

    /**
     * Generates an X.509 certificate revocation list (CRL) object and
     * initializes it with the data read from the given input stream
     * <code>is</code>.
     *
     * @param is an input stream with the CRL data.
     *
     * @return an X.509 CRL object initialized with the data
     * from the input stream.
     *
     * @exception CRLException on parsing errors.
     */
    public CRL engineGenerateCRL(InputStream is)
        throws CRLException
    {
        if (is == null) {
            // clear the cache (for debugging)
            crlCache.clear();
            throw new CRLException("Missing input stream");
        }
        try {
            if (is.markSupported() == false) {
                // consume the entire input stream
                byte[] totalBytes;
                totalBytes = getTotalBytes(new BufferedInputStream(is));
                is = new ByteArrayInputStream(totalBytes);
            }
            byte[] encoding = readSequence(is);
            if (encoding != null) {
                X509CRLImpl crl = (X509CRLImpl)getFromCache(crlCache, encoding);
                if (crl != null) {
                    return crl;
                }
                crl = new X509CRLImpl(encoding);
                addToCache(crlCache, crl.getEncodedInternal(), crl);
                return crl;
            } else {
                X509CRLImpl crl;
                // determine if binary or Base64 encoding. If Base64 encoding,
                // the CRL must be bounded at the beginning by
                // "-----BEGIN".
                if (isBase64(is)) {
                    // Base64
                    byte[] data = base64_to_binary(is);
                    crl = new X509CRLImpl(data);
                } else {
                    // binary
                    crl = new X509CRLImpl(new DerValue(is));
                }
                return intern(crl);
            }
        } catch (IOException ioe) {
            throw new CRLException(ioe.getMessage());
        }
    }

    /**
     * Returns a (possibly empty) collection view of X.509 CRLs read
     * from the given input stream <code>is</code>.
     *
     * @param is the input stream with the CRLs.
     *
     * @return a (possibly empty) collection view of X.509 CRL objects
     * initialized with the data from the input stream.
     *
     * @exception CRLException on parsing errors.
     */
    public Collection<? extends java.security.cert.CRL> engineGenerateCRLs(InputStream
is)
        throws CRLException
    {
        if (is == null) {
            throw new CRLException("Missing input stream");
        }
        try {
            if (is.markSupported() == false) {
                // consume the entire input stream
                is = new ByteArrayInputStream
                    (getTotalBytes(new BufferedInputStream(is)));
            }
            return parseX509orPKCS7CRL(is);
        } catch (IOException ioe) {
            throw new CRLException(ioe.getMessage());
        }
    }

    /*
     * Parses the data in the given input stream as a sequence of DER
     * encoded X.509 certificates (in binary or base 64 encoded format) OR
     * as a single PKCS#7 encoded blob (in binary or base64 encoded format).
     */
    private Collection<? extends java.security.cert.Certificate>
        parseX509orPKCS7Cert(InputStream is)
        throws CertificateException, IOException
    {
        Collection<X509CertImpl> coll = new ArrayList<X509CertImpl>();
        boolean first = true;
        while (is.available() != 0) {
            // determine if binary or Base64 encoding. If Base64 encoding,
            // each certificate must be bounded at the beginning by
            // "-----BEGIN".
            InputStream is2 = is;
            if (isBase64(is2)) {
                // Base64
                is2 = new ByteArrayInputStream(base64_to_binary(is2));
            }
            if (first)
                is2.mark(is2.available());
            try {
                // treat as X.509 cert
                coll.add(intern(new X509CertImpl(new DerValue(is2))));
            } catch (CertificateException e) {
                Throwable cause = e.getCause();
                // only treat as PKCS#7 if this is the first cert parsed
                // and the root cause of the decoding failure is an IOException
                if (first && cause != null && (cause instanceof IOException)) {
                    // treat as PKCS#7
                    is2.reset();
                    PKCS7 pkcs7 = new PKCS7(is2);
                    X509Certificate[] certs = pkcs7.getCertificates();
                    // certs are optional in PKCS #7
                    if (certs != null) {
                        return Arrays.asList(certs);
                    } else {
                        // no certs provided
                        return new ArrayList<X509Certificate>(0);
                    }
                } else {
                    throw e;
                }
            }
            first = false;
        }
        return coll;
    }

    /*
     * Parses the data in the given input stream as a sequence of DER encoded
     * X.509 CRLs (in binary or base 64 encoded format) OR as a single PKCS#7
     * encoded blob (in binary or base 64 encoded format).
     */
    private Collection<? extends java.security.cert.CRL>
        parseX509orPKCS7CRL(InputStream is)
        throws CRLException, IOException
    {
        Collection<X509CRLImpl> coll = new ArrayList<X509CRLImpl>();
        boolean first = true;
        while (is.available() != 0) {
            // determine if binary or Base64 encoding. If Base64 encoding,
            // the CRL must be bounded at the beginning by
            // "-----BEGIN".
            InputStream is2 = is;
            if (isBase64(is)) {
                // Base64
                is2 = new ByteArrayInputStream(base64_to_binary(is2));
            }
            if (first)
                is2.mark(is2.available());
            try {
                // treat as X.509 CRL
                coll.add(new X509CRLImpl(is2));
            } catch (CRLException e) {
                // only treat as PKCS#7 if this is the first CRL parsed
                if (first) {
                    is2.reset();
                    PKCS7 pkcs7 = new PKCS7(is2);
                    X509CRL[] crls = pkcs7.getCRLs();
                    // CRLs are optional in PKCS #7
                    if (crls != null) {
                        return Arrays.asList(crls);
                    } else {
                        // no crls provided
                        return new ArrayList<X509CRL>(0);
                    }
                }
            }
            first = false;
        }
        return coll;
    }

    /*
     * Converts a Base64-encoded X.509 certificate or X.509 CRL or PKCS#7 data
     * to binary encoding.
     * In all cases, the data must be bounded at the beginning by
     * "-----BEGIN", and must be bounded at the end by "-----END".
     */
    private byte[] base64_to_binary(InputStream is)
        throws IOException
    {
        long len = 0; // total length of base64 encoding, including boundaries

        is.mark(is.available());

        BufferedInputStream bufin = new BufferedInputStream(is);
        BufferedReader br =
            new BufferedReader(new InputStreamReader(bufin, "ASCII"));

        // First read all of the data that is found between
        // the "-----BEGIN" and "-----END" boundaries into a buffer.
        String temp;
        if ((temp=readLine(br))==null || !temp.startsWith("-----BEGIN")) {
            throw new IOException("Unsupported encoding");
        } else {
            len += temp.length();
        }
        StringBuffer strBuf = new StringBuffer();
        while ((temp=readLine(br))!=null && !temp.startsWith("-----END")) {
            strBuf.append(temp);
        }
        if (temp == null) {
            throw new IOException("Unsupported encoding");
        } else {
            len += temp.length();
        }

        // consume only as much as was needed
        len += strBuf.length();
        is.reset();
        is.skip(len);

        // Now, that data is supposed to be a single X.509 certificate or
        // X.509 CRL or PKCS#7 formatted data... Base64 encoded.
        // Decode into binary and return the result.
        BASE64Decoder decoder = new BASE64Decoder();
        return decoder.decodeBuffer(strBuf.toString());
    }

    /*
     * Reads the entire input stream into a byte array.
     */
    private byte[] getTotalBytes(InputStream is) throws IOException {
        byte[] buffer = new byte[8192];
        ByteArrayOutputStream baos = new ByteArrayOutputStream(2048);
        int n;
        baos.reset();
        while ((n = is.read(buffer, 0, buffer.length)) != -1) {
            baos.write(buffer, 0, n);
        }
        return baos.toByteArray();
    }

    /*
     * Determines if input is binary or Base64 encoded.
     */
    private boolean isBase64(InputStream is) throws IOException {
        if (is.available() >= 10) {
            is.mark(10);
            int c1 = is.read();
            int c2 = is.read();
            int c3 = is.read();
            int c4 = is.read();
            int c5 = is.read();
            int c6 = is.read();
            int c7 = is.read();
            int c8 = is.read();
            int c9 = is.read();
            int c10 = is.read();
            is.reset();
            if (c1 == '-' && c2 == '-' && c3 == '-' && c4 == '-'
                && c5 == '-' && c6 == 'B' && c7 == 'E' && c8 == 'G'
                && c9 == 'I' && c10 == 'N') {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    /*
     * Read a line of text.  A line is considered to be terminated by any one
     * of a line feed ('\n'), a carriage return ('\r'), a carriage return
     * followed immediately by a linefeed, or an end-of-certificate marker.
     *
     * @return     A String containing the contents of the line, including
     *             any line-termination characters, or null if the end of the
     *             stream has been reached.
     */
    private String readLine(BufferedReader br) throws IOException {
        int c;
        int i = 0;
        boolean isMatch = true;
        boolean matched = false;
        StringBuffer sb = new StringBuffer(defaultExpectedLineLength);
        do {
            c = br.read();
            if (isMatch && (i < endBoundary.length)) {
                isMatch = ((char)c != endBoundary[i++]) ? false : true;
            }
            if (!matched)
                matched = (isMatch && (i == endBoundary.length));
            sb.append((char)c);
        } while ((c != -1) && (c != '\n') && (c != '\r'));

        if (!matched && c == -1) {
            return null;
        }
        if (c == '\r') {
            br.mark(1);
            int c2 = br.read();
            if (c2 == '\n') {
                sb.append((char)c);
            } else {
                br.reset();
            }
        }
        return sb.toString();
    }
}
