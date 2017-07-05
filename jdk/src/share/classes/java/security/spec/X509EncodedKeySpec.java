/*
 * Copyright (c) 1997, 2005, Oracle and/or its affiliates. All rights reserved.
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

package java.security.spec;

/**
 * This class represents the ASN.1 encoding of a public key,
 * encoded according to the ASN.1 type <code>SubjectPublicKeyInfo</code>.
 * The <code>SubjectPublicKeyInfo</code> syntax is defined in the X.509
 * standard as follows:
 *
 * <pre>
 * SubjectPublicKeyInfo ::= SEQUENCE {
 *   algorithm AlgorithmIdentifier,
 *   subjectPublicKey BIT STRING }
 * </pre>
 *
 * @author Jan Luehe
 *
 *
 * @see java.security.Key
 * @see java.security.KeyFactory
 * @see KeySpec
 * @see EncodedKeySpec
 * @see PKCS8EncodedKeySpec
 *
 * @since 1.2
 */

public class X509EncodedKeySpec extends EncodedKeySpec {

    /**
     * Creates a new X509EncodedKeySpec with the given encoded key.
     *
     * @param encodedKey the key, which is assumed to be
     * encoded according to the X.509 standard. The contents of the
     * array are copied to protect against subsequent modification.
     * @exception NullPointerException if <code>encodedKey</code>
     * is null.
     */
    public X509EncodedKeySpec(byte[] encodedKey) {
        super(encodedKey);
    }

    /**
     * Returns the key bytes, encoded according to the X.509 standard.
     *
     * @return the X.509 encoding of the key. Returns a new array
     * each time this method is called.
     */
    public byte[] getEncoded() {
        return super.getEncoded();
    }

    /**
     * Returns the name of the encoding format associated with this
     * key specification.
     *
     * @return the string <code>"X.509"</code>.
     */
    public final String getFormat() {
        return "X.509";
    }
}
