/*
 * Copyright (c) 2005, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.security.PrivateKey;

/**
 * The handle for an RSA private key using the Microsoft Crypto API.
 *
 * @author Stanley Man-Kit Ho
 * @since 1.6
 */
class RSAPrivateKey extends Key implements PrivateKey
{
    private static final long serialVersionUID = 8113152807912338063L;

    /**
     * Construct an RSAPrivateKey object.
     */
    RSAPrivateKey(long hCryptProv, long hCryptKey, int keyLength)
    {
        super(new NativeHandles(hCryptProv, hCryptKey), keyLength);
    }

    /**
     * Construct an RSAPrivateKey object.
     */
    RSAPrivateKey(NativeHandles handles, int keyLength)
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

    public String toString()
    {
        return "RSAPrivateKey [size=" + keyLength + " bits, type=" +
            getKeyType(handles.hCryptKey) + ", container=" +
            getContainerName(handles.hCryptProv) + "]";
    }

    // This class is not serializable
    private void writeObject(java.io.ObjectOutputStream out)
        throws java.io.IOException {

        throw new java.io.NotSerializableException();
    }
}
