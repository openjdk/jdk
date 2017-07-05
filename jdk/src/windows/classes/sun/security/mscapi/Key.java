/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.security.mscapi;

/**
 * The handle for an RSA or DSA key using the Microsoft Crypto API.
 *
 * @see DSAPrivateKey
 * @see RSAPrivateKey
 * @see RSAPublicKey
 *
 * @since 1.6
 * @author  Stanley Man-Kit Ho
 */
abstract class Key implements java.security.Key
{

    // Native handle
    protected long hCryptProv = 0;
    protected long hCryptKey = 0;

    // Key length
    protected int keyLength = 0;

    /**
     * Construct a Key object.
     */
    protected Key(long hCryptProv, long hCryptKey, int keyLength)
    {
        this.hCryptProv = hCryptProv;
        this.hCryptKey = hCryptKey;
        this.keyLength = keyLength;
    }

    /**
     * Finalization method
     */
    protected void finalize() throws Throwable
    {
        try {
            synchronized(this)
            {
                cleanUp(hCryptProv, hCryptKey);
                hCryptProv = 0;
                hCryptKey = 0;
            }

        } finally {
            super.finalize();
        }
    }

    /**
     * Native method to cleanup the key handle.
     */
    private native static void cleanUp(long hCryptProv, long hCryptKey);

    /**
     * Return bit length of the key.
     */
    public int bitLength()
    {
        return keyLength;
    }


    /**
     * Return native HCRYPTKEY handle.
     */
    public long getHCryptKey()
    {
        return hCryptKey;
    }

    /**
     * Return native HCRYPTPROV handle.
     */
    public long getHCryptProvider()
    {
        return hCryptProv;
    }

    /**
     * Returns the standard algorithm name for this key. For
     * example, "DSA" would indicate that this key is a DSA key.
     * See Appendix A in the <a href=
     * "../../../guide/security/CryptoSpec.html#AppA">
     * Java Cryptography Architecture API Specification &amp; Reference </a>
     * for information about standard algorithm names.
     *
     * @return the name of the algorithm associated with this key.
     */
    public abstract String getAlgorithm();

    /**
     * Returns the name of the primary encoding format of this key,
     * or null if this key does not support encoding.
     * The primary encoding format is
     * named in terms of the appropriate ASN.1 data format, if an
     * ASN.1 specification for this key exists.
     * For example, the name of the ASN.1 data format for public
     * keys is <I>SubjectPublicKeyInfo</I>, as
     * defined by the X.509 standard; in this case, the returned format is
     * <code>"X.509"</code>. Similarly,
     * the name of the ASN.1 data format for private keys is
     * <I>PrivateKeyInfo</I>,
     * as defined by the PKCS #8 standard; in this case, the returned format is
     * <code>"PKCS#8"</code>.
     *
     * @return the primary encoding format of the key.
     */
    public String getFormat()
    {
        return null;
    }

    /**
     * Returns the key in its primary encoding format, or null
     * if this key does not support encoding.
     *
     * @return the encoded key, or null if the key does not support
     * encoding.
     */
    public byte[] getEncoded()
    {
        return null;
    }

    protected native static String getContainerName(long hCryptProv);

    protected native static String getKeyType(long hCryptKey);
}
