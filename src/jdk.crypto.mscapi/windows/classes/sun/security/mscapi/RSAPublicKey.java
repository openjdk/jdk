/*
 * Copyright (c) 2005, 2018, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.mscapi;

import java.math.BigInteger;
import java.security.KeyException;
import java.security.KeyRep;
import java.security.ProviderException;

import sun.security.rsa.RSAUtil.KeyType;
import sun.security.rsa.RSAPublicKeyImpl;

/**
 * The handle for an RSA public key using the Microsoft Crypto API.
 *
 * @since 1.6
 */
class RSAPublicKey extends Key implements java.security.interfaces.RSAPublicKey
{
    private static final long serialVersionUID = -2289561342425825391L;

    private byte[] publicKeyBlob = null;
    private byte[] encoding = null;
    private BigInteger modulus = null;
    private BigInteger exponent = null;

    /**
     * Construct an RSAPublicKey object.
     */
    RSAPublicKey(long hCryptProv, long hCryptKey, int keyLength)
    {
        super(new NativeHandles(hCryptProv, hCryptKey), keyLength);
    }

    /**
     * Construct an RSAPublicKey object.
     */
    RSAPublicKey(NativeHandles handles, int keyLength)
    {
        super(handles, keyLength);
    }

    /**
     * Returns the standard algorithm name for this key. For
     * example, "RSA" would indicate that this key is a RSA key.
     * See Appendix A in the <a href=
     * "../../../guide/security/CryptoSpec.html#AppA">
     * Java Cryptography Architecture API Specification &amp; Reference </a>
     * for information about standard algorithm names.
     *
     * @return the name of the algorithm associated with this key.
     */
    public String getAlgorithm()
    {
        return "RSA";
    }

    /**
     * Returns a printable description of the key.
     */
    public String toString()
    {
        StringBuffer sb = new StringBuffer();

        sb.append("RSAPublicKey [size=").append(keyLength)
            .append(" bits, type=").append(getKeyType(handles.hCryptKey))
            .append(", container=").append(getContainerName(handles.hCryptProv))
            .append("]\n  modulus: ").append(getModulus())
            .append("\n  public exponent: ").append(getPublicExponent());

        return sb.toString();
    }

    /**
     * Returns the public exponent.
     */
    public BigInteger getPublicExponent() {

        if (exponent == null) {

            try {
                publicKeyBlob = getPublicKeyBlob(handles.hCryptKey);
                exponent = new BigInteger(1, getExponent(publicKeyBlob));

            } catch (KeyException e) {
                throw new ProviderException(e);
            }
        }

        return exponent;
    }

    /**
     * Returns the modulus.
     */
    public BigInteger getModulus() {

        if (modulus == null) {

            try {
                publicKeyBlob = getPublicKeyBlob(handles.hCryptKey);
                modulus = new BigInteger(1, getModulus(publicKeyBlob));

            } catch (KeyException e) {
                throw new ProviderException(e);
            }
        }

        return modulus;
    }

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
        return "X.509";
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
        if (encoding == null) {

            try {
                encoding = RSAPublicKeyImpl.newKey(KeyType.RSA, null,
                    getModulus(), getPublicExponent()).getEncoded();

            } catch (KeyException e) {
                // ignore
            }
        }
        return encoding;
    }

    protected Object writeReplace() throws java.io.ObjectStreamException {
        return new KeyRep(KeyRep.Type.PUBLIC,
                        getAlgorithm(),
                        getFormat(),
                        getEncoded());
    }

    /*
     * Returns the Microsoft CryptoAPI representation of the key.
     */
    private native byte[] getPublicKeyBlob(long hCryptKey) throws KeyException;

    /*
     * Returns the key's public exponent (in big-endian 2's complement format).
     */
    private native byte[] getExponent(byte[] keyBlob) throws KeyException;

    /*
     * Returns the key's modulus (in big-endian 2's complement format).
     */
    private native byte[] getModulus(byte[] keyBlob) throws KeyException;
}
