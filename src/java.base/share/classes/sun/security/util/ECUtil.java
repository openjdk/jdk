/*
 * Copyright (c) 2006, 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.util;

import java.io.IOException;

import java.math.BigInteger;

import java.security.*;

import java.security.interfaces.*;

import java.security.spec.*;

import java.util.Arrays;

import sun.security.x509.X509Key;

public class ECUtil {

    // Used by SunPKCS11 and SunJSSE.
    public static ECPoint decodePoint(byte[] data, EllipticCurve curve)
            throws IOException {
        if ((data.length == 0) || (data[0] != 4)) {
            throw new IOException("Only uncompressed point format supported");
        }
        // Per ANSI X9.62, an encoded point is a 1 byte type followed by
        // ceiling(log base 2 field-size / 8) bytes of x and the same of y.
        int n = (data.length - 1) / 2;
        if (n != ((curve.getField().getFieldSize() + 7 ) >> 3)) {
            throw new IOException("Point does not match field size");
        }

        byte[] xb = Arrays.copyOfRange(data, 1, 1 + n);
        byte[] yb = Arrays.copyOfRange(data, n + 1, n + 1 + n);

        return new ECPoint(new BigInteger(1, xb), new BigInteger(1, yb));
    }

    // Used by SunPKCS11 and SunJSSE.
    public static byte[] encodePoint(ECPoint point, EllipticCurve curve) {
        // get field size in bytes (rounding up)
        int n = (curve.getField().getFieldSize() + 7) >> 3;
        byte[] xb = trimZeroes(point.getAffineX().toByteArray());
        byte[] yb = trimZeroes(point.getAffineY().toByteArray());
        if ((xb.length > n) || (yb.length > n)) {
            throw new RuntimeException
                ("Point coordinates do not match field size");
        }
        byte[] b = new byte[1 + (n << 1)];
        b[0] = 4; // uncompressed
        System.arraycopy(xb, 0, b, n - xb.length + 1, xb.length);
        System.arraycopy(yb, 0, b, b.length - yb.length, yb.length);
        return b;
    }

    public static byte[] trimZeroes(byte[] b) {
        int i = 0;
        while ((i < b.length - 1) && (b[i] == 0)) {
            i++;
        }
        if (i == 0) {
            return b;
        }

        return Arrays.copyOfRange(b, i, b.length);
    }

    private static KeyFactory getKeyFactory() {
        try {
            return KeyFactory.getInstance("EC", "SunEC");
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new RuntimeException(e);
        }
    }

    public static ECPublicKey decodeX509ECPublicKey(byte[] encoded)
            throws InvalidKeySpecException {
        KeyFactory keyFactory = getKeyFactory();
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);

        return (ECPublicKey)keyFactory.generatePublic(keySpec);
    }

    public static byte[] x509EncodeECPublicKey(ECPoint w,
            ECParameterSpec params) throws InvalidKeySpecException {
        KeyFactory keyFactory = getKeyFactory();
        ECPublicKeySpec keySpec = new ECPublicKeySpec(w, params);
        X509Key key = (X509Key)keyFactory.generatePublic(keySpec);

        return key.getEncoded();
    }

    public static ECPrivateKey decodePKCS8ECPrivateKey(byte[] encoded)
            throws InvalidKeySpecException {
        KeyFactory keyFactory = getKeyFactory();
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);

        return (ECPrivateKey)keyFactory.generatePrivate(keySpec);
    }

    public static ECPrivateKey generateECPrivateKey(BigInteger s,
            ECParameterSpec params) throws InvalidKeySpecException {
        KeyFactory keyFactory = getKeyFactory();
        ECPrivateKeySpec keySpec = new ECPrivateKeySpec(s, params);

        return (ECPrivateKey)keyFactory.generatePrivate(keySpec);
    }

    public static AlgorithmParameters getECParameters(Provider p) {
        try {
            if (p != null) {
                return AlgorithmParameters.getInstance("EC", p);
            }

            return AlgorithmParameters.getInstance("EC");
        } catch (NoSuchAlgorithmException nsae) {
            throw new RuntimeException(nsae);
        }
    }

    public static byte[] encodeECParameterSpec(Provider p,
                                               ECParameterSpec spec) {
        AlgorithmParameters parameters = getECParameters(p);

        try {
            parameters.init(spec);
        } catch (InvalidParameterSpecException ipse) {
            throw new RuntimeException("Not a known named curve: " + spec);
        }

        try {
            return parameters.getEncoded();
        } catch (IOException ioe) {
            // it is a bug if this should happen
            throw new RuntimeException(ioe);
        }
    }

    public static ECParameterSpec getECParameterSpec(Provider p,
                                                     ECParameterSpec spec) {
        AlgorithmParameters parameters = getECParameters(p);

        try {
            parameters.init(spec);
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (InvalidParameterSpecException ipse) {
            return null;
        }
    }

    public static ECParameterSpec getECParameterSpec(Provider p,
                                                     byte[] params)
            throws IOException {
        AlgorithmParameters parameters = getECParameters(p);

        parameters.init(params);

        try {
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (InvalidParameterSpecException ipse) {
            return null;
        }
    }

    public static ECParameterSpec getECParameterSpec(Provider p, String name) {
        AlgorithmParameters parameters = getECParameters(p);

        try {
            parameters.init(new ECGenParameterSpec(name));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (InvalidParameterSpecException ipse) {
            return null;
        }
    }

    public static ECParameterSpec getECParameterSpec(Provider p, int keySize) {
        AlgorithmParameters parameters = getECParameters(p);

        try {
            parameters.init(new ECKeySizeParameterSpec(keySize));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (InvalidParameterSpecException ipse) {
            return null;
        }

    }

    public static String getCurveName(Provider p, ECParameterSpec spec) {
        ECGenParameterSpec nameSpec;
        AlgorithmParameters parameters = getECParameters(p);

        try {
            parameters.init(spec);
            nameSpec = parameters.getParameterSpec(ECGenParameterSpec.class);
        } catch (InvalidParameterSpecException ipse) {
            return null;
        }

        if (nameSpec == null) {
            return null;
        }

        return nameSpec.getName();
    }

    private ECUtil() {}
}
