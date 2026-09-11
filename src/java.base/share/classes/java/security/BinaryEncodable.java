/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package java.security;

import javax.crypto.EncryptedPrivateKeyInfo;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import jdk.internal.javac.PreviewFeature;
import sun.security.internal.InternalBinaryEncodable;


/**
 * This interface identifies the cryptographic objects that can be converted
 * to and from binary data, and thereby encoded and decoded as PEM text.
 *
 * <p> The APIs for cryptographic objects such as public keys, private keys,
 * certificates, and certificate revocation lists all provide the means to
 * convert their instances to and from standardized binary representations.
 * Other kinds of cryptographic objects, such as certificate requests, have
 * no corresponding API but can still be expressed as standardized binary
 * representations. The {@code BinaryEncodable} interface allows the
 * {@link PEMEncoder} and {@link PEMDecoder} classes to operate uniformly on
 * binary representations of key or certificate material.
 *
 * <p> The permitted subtype {@code PEM} is notable for supporting the encoding
 * and decoding of PEM text that represents cryptographic objects for which no
 * API exists. In future releases, other permitted subtypes may be added to
 * support the encoding and decoding of such cryptographic objects.
 *
 * <p> The list of permitted subtypes shown after {@code permits} is not
 * exhaustive. This means if application code switches over a
 * {@code BinaryEncodable} value, the {@code switch} cannot be made exhaustive
 * simply by providing a {@code case} label for every permitted subtype shown
 * in the list; there also must be a {@code default} or
 * {@code case BinaryEncodable} label to handle additional subtypes. This
 * allows the list of permitted subtypes to change over time without causing
 * pre-existing switches to fail because of an unrecognized subtype.
 *
 * @see AsymmetricKey
 * @see KeyPair
 * @see PKCS8EncodedKeySpec
 * @see X509EncodedKeySpec
 * @see EncryptedPrivateKeyInfo
 * @see X509Certificate
 * @see X509CRL
 * @see PEM
 *
 * @since 27
 */

@PreviewFeature(feature = PreviewFeature.Feature.PEM_API)
public sealed interface BinaryEncodable permits AsymmetricKey, KeyPair,
    PKCS8EncodedKeySpec, X509EncodedKeySpec, EncryptedPrivateKeyInfo,
    X509Certificate, X509CRL, PEM, InternalBinaryEncodable {
}
