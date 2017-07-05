/*
 * Copyright (c) 2006, 2012, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.ec;

import java.io.IOException;
import java.math.BigInteger;

import java.security.*;
import java.security.spec.*;

import sun.security.util.*;

/**
 * This class implements encoding and decoding of Elliptic Curve parameters
 * as specified in RFC 3279.
 *
 * However, only named curves are currently supported.
 *
 * ASN.1 from RFC 3279 follows. Note that X9.62 (2005) has added some additional
 * options.
 *
 * <pre>
 *    EcpkParameters ::= CHOICE {
 *      ecParameters  ECParameters,
 *      namedCurve    OBJECT IDENTIFIER,
 *      implicitlyCA  NULL }
 *
 *    ECParameters ::= SEQUENCE {
 *       version   ECPVer,          -- version is always 1
 *       fieldID   FieldID,         -- identifies the finite field over
 *                                  -- which the curve is defined
 *       curve     Curve,           -- coefficients a and b of the
 *                                  -- elliptic curve
 *       base      ECPoint,         -- specifies the base point P
 *                                  -- on the elliptic curve
 *       order     INTEGER,         -- the order n of the base point
 *       cofactor  INTEGER OPTIONAL -- The integer h = #E(Fq)/n
 *       }
 *
 *    ECPVer ::= INTEGER {ecpVer1(1)}
 *
 *    Curve ::= SEQUENCE {
 *       a         FieldElement,
 *       b         FieldElement,
 *       seed      BIT STRING OPTIONAL }
 *
 *    FieldElement ::= OCTET STRING
 *
 *    ECPoint ::= OCTET STRING
 * </pre>
 *
 * @since   1.6
 * @author  Andreas Sterbenz
 */
public final class ECParameters extends AlgorithmParametersSpi {

    public ECParameters() {
        // empty
    }

    // Used by SunPKCS11 and SunJSSE.
    public static ECPoint decodePoint(byte[] data, EllipticCurve curve)
            throws IOException {
        if ((data.length == 0) || (data[0] != 4)) {
            throw new IOException("Only uncompressed point format supported");
        }
        int n = data.length / 2;
        if (n > ((curve.getField().getFieldSize() + 7 ) >> 3)) {
            throw new IOException("Point does not match field size");
        }
        byte[] xb = new byte[n];
        byte[] yb = new byte[n];
        System.arraycopy(data, 1, xb, 0, n);
        System.arraycopy(data, n + 1, yb, 0, n);
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

    // Copied from the SunPKCS11 code - should be moved to a common location.
    // trim leading (most significant) zeroes from the result
    static byte[] trimZeroes(byte[] b) {
        int i = 0;
        while ((i < b.length - 1) && (b[i] == 0)) {
            i++;
        }
        if (i == 0) {
            return b;
        }
        byte[] t = new byte[b.length - i];
        System.arraycopy(b, i, t, 0, t.length);
        return t;
    }

    // Convert the given ECParameterSpec object to a NamedCurve object.
    // If params does not represent a known named curve, return null.
    // Used by SunPKCS11.
    public static NamedCurve getNamedCurve(ECParameterSpec params) {
        if ((params instanceof NamedCurve) || (params == null)) {
            return (NamedCurve)params;
        }
        // This is a hack to allow SunJSSE to work with 3rd party crypto
        // providers for ECC and not just SunPKCS11.
        // This can go away once we decide how to expose curve names in the
        // public API.
        // Note that it assumes that the 3rd party provider encodes named
        // curves using the short form, not explicitly. If it did that, then
        // the SunJSSE TLS ECC extensions are wrong, which could lead to
        // interoperability problems.
        int fieldSize = params.getCurve().getField().getFieldSize();
        for (ECParameterSpec namedCurve : NamedCurve.knownECParameterSpecs()) {
            // ECParameterSpec does not define equals, so check all the
            // components ourselves.
            // Quick field size check first
            if (namedCurve.getCurve().getField().getFieldSize() != fieldSize) {
                continue;
            }
            if (namedCurve.getCurve().equals(params.getCurve()) == false) {
                continue;
            }
            if (namedCurve.getGenerator().equals(params.getGenerator()) == false) {
                continue;
            }
            if (namedCurve.getOrder().equals(params.getOrder()) == false) {
                continue;
            }
            if (namedCurve.getCofactor() != params.getCofactor()) {
                continue;
            }
            // everything matches our named curve, return it
            return (NamedCurve)namedCurve;
        }
        // no match found
        return null;
    }

    // Used by SunJSSE.
    public static String getCurveName(ECParameterSpec params) {
        NamedCurve curve = getNamedCurve(params);
        return (curve == null) ? null : curve.getObjectIdentifier().toString();
    }

    // Used by SunPKCS11.
    public static byte[] encodeParameters(ECParameterSpec params) {
        NamedCurve curve = getNamedCurve(params);
        if (curve == null) {
            throw new RuntimeException("Not a known named curve: " + params);
        }
        return curve.getEncoded();
    }

    // Used by SunPKCS11.
    public static ECParameterSpec decodeParameters(byte[] params) throws IOException {
        DerValue encodedParams = new DerValue(params);
        if (encodedParams.tag == DerValue.tag_ObjectId) {
            ObjectIdentifier oid = encodedParams.getOID();
            ECParameterSpec spec = NamedCurve.getECParameterSpec(oid);
            if (spec == null) {
                throw new IOException("Unknown named curve: " + oid);
            }
            return spec;
        }

        throw new IOException("Only named ECParameters supported");

        // The code below is incomplete.
        // It is left as a starting point for a complete parsing implementation.

/*
        if (encodedParams.tag != DerValue.tag_Sequence) {
            throw new IOException("Unsupported EC parameters, tag: " + encodedParams.tag);
        }

        encodedParams.data.reset();

        DerInputStream in = encodedParams.data;

        int version = in.getInteger();
        if (version != 1) {
            throw new IOException("Unsupported EC parameters version: " + version);
        }
        ECField field = parseField(in);
        EllipticCurve curve = parseCurve(in, field);
        ECPoint point = parsePoint(in, curve);

        BigInteger order = in.getBigInteger();
        int cofactor = 0;

        if (in.available() != 0) {
            cofactor = in.getInteger();
        }

        // XXX HashAlgorithm optional

        if (encodedParams.data.available() != 0) {
            throw new IOException("encoded params have " +
                                  encodedParams.data.available() +
                                  " extra bytes");
        }

        return new ECParameterSpec(curve, point, order, cofactor);
*/
    }

/*
    private static final ObjectIdentifier fieldTypePrime =
        ObjectIdentifier.newInternal(new int[] {1, 2, 840, 10045, 1, 1});

    private static final ObjectIdentifier fieldTypeChar2 =
        ObjectIdentifier.newInternal(new int[] {1, 2, 840, 10045, 1, 2});

    private static ECField parseField(DerInputStream in) throws IOException {
        DerValue v = in.getDerValue();
        ObjectIdentifier oid = v.data.getOID();
        if (oid.equals(fieldTypePrime) == false) {
            throw new IOException("Only prime fields supported: " + oid);
        }
        BigInteger fieldSize = v.data.getBigInteger();
        return new ECFieldFp(fieldSize);
    }

    private static EllipticCurve parseCurve(DerInputStream in, ECField field)
            throws IOException {
        DerValue v = in.getDerValue();
        byte[] ab = v.data.getOctetString();
        byte[] bb = v.data.getOctetString();
        return new EllipticCurve(field, new BigInteger(1, ab), new BigInteger(1, bb));
    }

    private static ECPoint parsePoint(DerInputStream in, EllipticCurve curve)
            throws IOException {
        byte[] data = in.getOctetString();
        return decodePoint(data, curve);
    }
*/

    // used by ECPublicKeyImpl and ECPrivateKeyImpl
    static AlgorithmParameters getAlgorithmParameters(ECParameterSpec spec)
            throws InvalidKeyException {
        try {
            AlgorithmParameters params =
                AlgorithmParameters.getInstance("EC", "SunEC");
            params.init(spec);
            return params;
        } catch (GeneralSecurityException e) {
            throw new InvalidKeyException("EC parameters error", e);
        }
    }

    // AlgorithmParameterSpi methods

    // The parameters these AlgorithmParameters object represents.
    // Currently, it is always an instance of NamedCurve.
    private ECParameterSpec paramSpec;

    protected void engineInit(AlgorithmParameterSpec paramSpec)
            throws InvalidParameterSpecException {
        if (paramSpec instanceof ECParameterSpec) {
            this.paramSpec = getNamedCurve((ECParameterSpec)paramSpec);
            if (this.paramSpec == null) {
                throw new InvalidParameterSpecException
                    ("Not a supported named curve: " + paramSpec);
            }
        } else if (paramSpec instanceof ECGenParameterSpec) {
            String name = ((ECGenParameterSpec)paramSpec).getName();
            ECParameterSpec spec = NamedCurve.getECParameterSpec(name);
            if (spec == null) {
                throw new InvalidParameterSpecException("Unknown curve: " + name);
            }
            this.paramSpec = spec;
        } else if (paramSpec == null) {
            throw new InvalidParameterSpecException
                ("paramSpec must not be null");
        } else {
            throw new InvalidParameterSpecException
                ("Only ECParameterSpec and ECGenParameterSpec supported");
        }
    }

    protected void engineInit(byte[] params) throws IOException {
        paramSpec = decodeParameters(params);
    }

    protected void engineInit(byte[] params, String decodingMethod) throws IOException {
        engineInit(params);
    }

    protected <T extends AlgorithmParameterSpec> T engineGetParameterSpec(Class<T> spec)
            throws InvalidParameterSpecException {
        if (spec.isAssignableFrom(ECParameterSpec.class)) {
            return spec.cast(paramSpec);
        } else if (spec.isAssignableFrom(ECGenParameterSpec.class)) {
            return spec.cast(new ECGenParameterSpec(getCurveName(paramSpec)));
        } else {
            throw new InvalidParameterSpecException
                ("Only ECParameterSpec and ECGenParameterSpec supported");
        }
    }

    protected byte[] engineGetEncoded() throws IOException {
        return encodeParameters(paramSpec);
    }

    protected byte[] engineGetEncoded(String encodingMethod) throws IOException {
        return engineGetEncoded();
    }

    protected String engineToString() {
        return paramSpec.toString();
    }
}
