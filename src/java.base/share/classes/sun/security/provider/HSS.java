/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package sun.security.provider;

import sun.security.util.*;
import sun.security.x509.AlgorithmId;
import sun.security.x509.X509Key;

import java.io.*;
import java.security.*;
import java.security.SecureRandom;
import java.security.spec.*;
import java.util.Arrays;

/**
 * Implementation of the Hierarchical Signature System using the
 * Leighton-Micali Signatures (HSS/LMS) as described in RFC 8554 and
 * NIST Special publication 800-208.
 */
public final class HSS extends SignatureSpi {
    private HSSPublicKey pubKey;
    private ByteArrayOutputStream messageStream;

    @Override
    @Deprecated
    protected void engineSetParameter(String param, Object value) {
        throw new InvalidParameterException("No settable parameters exist for HSS/LMS");
    }

    @Override
    @Deprecated
    protected AlgorithmParameters engineGetParameter(String param) {
        throw new InvalidParameterException("No parameters exist for HSS/LMS");
    }

    @Override
    protected void engineInitSign(PrivateKey privateKey)
            throws InvalidKeyException {
        throw new InvalidKeyException("HSS/LMS signing is not supported");
    }

    @Override
    protected void engineInitSign(PrivateKey prk, SecureRandom sr)
            throws InvalidKeyException {
        throw new InvalidKeyException("HSS/LMS signing is not supported");
    }

    // This will never be called because engineInitSign unconditionally
    // throws an exception
    @Override
    protected byte[] engineSign() throws SignatureException {
        throw new SignatureException("HSS/LMS signing is not supported");
    }

    @Override
    protected void engineInitVerify(PublicKey publicKey)
            throws InvalidKeyException {
        HSSPublicKey pub;
        if (publicKey instanceof HSSPublicKey p) {
            pub = p;
        } else {
            KeyFactoryImpl factory = new HSS.KeyFactoryImpl();
            Key pk = factory.engineTranslateKey(publicKey);
            pub = (HSSPublicKey) pk;
        }
        pubKey = pub;
        messageStream = new ByteArrayOutputStream();
    }

    @Override
    protected void engineUpdate(byte data) {
        messageStream.write(data);
    }

    @Override
    protected void engineUpdate(byte[] data, int off, int len) {
        messageStream.write(data, off, len);
    }

    @Override
    protected boolean engineVerify(byte[] signature)
            throws SignatureException {

        boolean result = true;
        try {
            HSSSignature sig = new HSSSignature(signature, pubKey);
            LMSPublicKey lmsPubKey = pubKey.lmsPublicKey;
            for (int i = 0; i < sig.Nspk; i++) {
                byte[] keyArr = sig.pubList[i].keyArray();
                result &= LMSUtils.lmsVerify(lmsPubKey, sig.siglist[i], keyArr);
                lmsPubKey = sig.pubList[i];
            }

            result &= LMSUtils.lmsVerify(
                    lmsPubKey, sig.siglist[sig.Nspk], messageStream.toByteArray());
        } finally {
            messageStream.reset();
        }
        return result;
    }
    @Override
    protected void engineSetParameter(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
        if (params != null) {
            throw new InvalidAlgorithmParameterException("No parameters accepted");
        }
    }

    @Override
    protected AlgorithmParameters engineGetParameters() {
        return null;
    }

    static class LMSPublicKey {
        final int type;
        final int otsType;
        final LMSParams lmsParams;
        private final byte[] I;
        private final byte[] T1;

        LMSPublicKey(byte[] keyArray, int offset, boolean checkExactLength)
                throws InvalidKeyException {
            int inLen = keyArray.length - offset;
            if (inLen < 8) {
                throw new InvalidKeyException("LMS public key is too short");
            }
            type = LMSUtils.fourBytesToInt(keyArray, offset);
            otsType = LMSUtils.fourBytesToInt(keyArray, offset + 4);
            LMOTSParams lmotsParams;

            try {
                lmsParams = LMSParams.of(type);
                lmotsParams = LMOTSParams.of(otsType);
            } catch (IllegalArgumentException e) {
                throw new InvalidKeyException(e.getMessage());
            }

            int m = lmsParams.m;
            if ((inLen < (24 + m)) || (checkExactLength && (inLen != (24 + m))) ||
                    !lmsParams.hasSameHash(lmotsParams)) {
                throw new InvalidKeyException("Wrong LMS public key length");
            }

            I = Arrays.copyOfRange(keyArray, offset + 8, offset + 8 + 16);
            T1 = Arrays.copyOfRange(keyArray, offset + 24, offset + 24 + m);
        }

        void getI(byte[] arr, int pos) {
            System.arraycopy(I, 0, arr, pos, 16);
        }

        boolean isT1(byte[] arr, int pos) {
            int m = lmsParams.m;
            int diff = 0;
            for (int i = 0; i < m; i++) {
                diff |= (T1[i] ^ arr[pos + i]);
            }
            return (diff == 0);
        }

        byte[] keyArray() {
            byte[] result = new byte[keyArrayLength()];
            LMSUtils.intToFourBytes(type, result, 0);
            LMSUtils.intToFourBytes(otsType, result, 4);
            System.arraycopy(I, 0, result, 8, 16);
            System.arraycopy(T1, 0, result, 24, lmsParams.m);
            return result;
        }

        int keyArrayLength() {
            return 24 + lmsParams.m;
        }
    }

    static class LMSUtils {
        static final int LMS_RESERVED = 0;
        static final int LMS_SHA256_M32_H5 = 5;
        static final int LMS_SHA256_M32_H10 = 6;
        static final int LMS_SHA256_M32_H15 = 7;
        static final int LMS_SHA256_M32_H20 = 8;
        static final int LMS_SHA256_M32_H25 = 9;

        static String lmsType(int type) {
            String typeStr;
            switch (type) {
                case LMS_RESERVED: typeStr = "LMS_RESERVED"; break;
                case LMS_SHA256_M32_H5: typeStr = "LMS_SHA256_M32_H5"; break;
                case LMS_SHA256_M32_H10: typeStr = "LMS_SHA256_M32_H10"; break;
                case LMS_SHA256_M32_H15: typeStr = "LMS_SHA256_M32_H15"; break;
                case LMS_SHA256_M32_H20: typeStr = "LMS_SHA256_M32_H20"; break;
                case LMS_SHA256_M32_H25: typeStr = "LMS_SHA256_M32_H25"; break;
                default: typeStr = "unrecognized";
            }
            return typeStr;
        }

        static final int LMOTS_RESERVED = 0;
        static final int LMOTS_SHA256_N32_W1 = 1;
        static final int LMOTS_SHA256_N32_W2 = 2;
        static final int LMOTS_SHA256_N32_W4 = 3;
        static final int LMOTS_SHA256_N32_W8 = 4;

        static String lmotsType(int type) {
            String typeStr;
            switch (type) {
                case LMOTS_RESERVED: typeStr = "LMOTS_RESERVED"; break;
                case LMOTS_SHA256_N32_W1: typeStr = "LMOTS_SHA256_N32_W1"; break;
                case LMOTS_SHA256_N32_W2: typeStr = "LMOTS_SHA256_N32_W2"; break;
                case LMOTS_SHA256_N32_W4: typeStr = "LMOTS_SHA256_N32_W4"; break;
                case LMOTS_SHA256_N32_W8: typeStr = "LMOTS_SHA256_N32_W8"; break;
                default: typeStr = "unrecognized";
            }
            return typeStr;
        }


        static int fourBytesToInt(byte[] arr, int i) {
            return ((arr[i] & 0xff) << 24) |
                    ((arr[i + 1] & 0xff) << 16) |
                    ((arr[i + 2] & 0xff) << 8) |
                    (arr[i + 3] & 0xff);
        }

        static void intToFourBytes(int i, byte[] arr, int pos) {
            arr[pos] = (byte) (i >> 24);
            arr[pos + 1] = (byte) (i >> 16);
            arr[pos + 2] = (byte) (i >> 8);
            arr[pos + 3] = (byte) i;
        }
        static boolean lmsVerify(
                LMSPublicKey lmsPublicKey, LMSignature sig, byte[] message)
                throws SignatureException {

            if ((sig.sigOtsType != lmsPublicKey.otsType) ||
                    (sig.sigLmType != lmsPublicKey.type)) {
                return false;
            }
            LMOTSignature lmotSig = sig.lmotSig;
            LMOTSParams lmotsParams = lmotSig.lmotsParams;
            int q = sig.q;
            int m = lmsPublicKey.lmsParams.m;
            int hashAlg_m = lmsPublicKey.lmsParams.hashAlg_m;
            int n = lmotsParams.n;

            try {
                byte[] otsPkCandidate =
                        lmotsParams.lmotsPubKeyCandidate(sig, message, lmsPublicKey);
                int nodeNum = lmsPublicKey.lmsParams.twoPowh + q;
                int tmp0MsgLen = 22 + n;
                int tmpLoopMsgLen = 22 + m + hashAlg_m;
                byte[] tmpMsg = new byte[Integer.max(tmp0MsgLen, tmpLoopMsgLen)];
                lmsPublicKey.getI(tmpMsg, 0);
                MessageDigest md =
                        MessageDigest.getInstance(lmsPublicKey.lmsParams.hashAlgStr);
                LMSUtils.intToFourBytes(nodeNum, tmpMsg, 16);
                tmpMsg[20] = (byte) 0x82; // D_LEAF = 0x8282
                tmpMsg[21] = (byte) 0x82;
                System.arraycopy(otsPkCandidate, 0, tmpMsg, 22, n);
                md.update(tmpMsg, 0, tmp0MsgLen);
                if ((nodeNum & 1) == 1) {
                    md.digest(tmpMsg, 22 + m, hashAlg_m);
                } else {
                    md.digest(tmpMsg, 22, hashAlg_m);
                }
                tmpMsg[20] = (byte) 0x83; // D_INTR = 0x8383
                tmpMsg[21] = (byte) 0x83;

                int i = 0;
                while (nodeNum > 1) {
                    LMSUtils.intToFourBytes(nodeNum / 2, tmpMsg, 16);

                    if ((nodeNum & 1) == 1) {
                        sig.getPath(i, tmpMsg, 22);
                    } else {
                        sig.getPath(i, tmpMsg, 22 + m);
                    }
                    md.update(tmpMsg, 0, 22 + 2 * m);
                    nodeNum /= 2;
                    if ((nodeNum & 1) == 1) {
                        md.digest(tmpMsg, 22 + m, hashAlg_m);
                    } else {
                        md.digest(tmpMsg, 22, hashAlg_m);
                    }
                    i++;
                }
                return lmsPublicKey.isT1(tmpMsg, 22 + m);
            } catch (NoSuchAlgorithmException | DigestException e) {
                throw new ProviderException(e);
            }
        }
    }

    static class LMOTSignature {
        final int otSigType;
        final LMOTSParams lmotsParams;
        private final int n;
        private final byte[] C;
        private final byte[][] y;

        LMOTSignature(byte[] sigArray, LMOTSParams lmotsParams)
                throws InvalidParameterException {
            int inLen = sigArray.length;
            if (inLen < 4) {
                throw new InvalidParameterException("OTS signature is too short");
            }
            otSigType = lmotsParams.lmotSigType;
            this.lmotsParams = lmotsParams;
            n = lmotsParams.n;
            int p = lmotsParams.p;
            if (inLen != (4 + n * (p + 1))) {
                throw new InvalidParameterException("OTS signature has incorrect length");
            }
            C = Arrays.copyOfRange(sigArray, 4, 4 + n);
            int pStart = 4 + n;
            y = new byte[p][n];
            for (int i = 0; i < p; i++) {
                y[i] = Arrays.copyOfRange(sigArray, pStart, pStart + n);
                pStart += n;
            }
        }

        void getC(byte[] arr, int pos) {
            System.arraycopy(C, 0, arr, pos, n);
        }

        void getY(int i, byte[] arr, int pos) {
            System.arraycopy(y[i], 0, arr, pos, n);
        }
    }

    static class LMSParams {
        final int m; // the number of bytes used from the hash output
        final int hashAlg_m = 32; // output length of the LMS tree hash function
        final int h; // height of the LMS tree
        final int twoPowh;
        final String hashAlgStr;

        LMSParams(int m, int h, String hashAlgStr) {
            this.m = m;
            this.h = h;
            this.hashAlgStr = hashAlgStr;
            twoPowh = 1 << h;
        }

        static LMSParams of(int type) {
            int m;
            int h;
            String hashAlgStr;
            switch (type) {
                case LMSUtils.LMS_SHA256_M32_H5:
                    m = 32;
                    h = 5;
                    hashAlgStr = "SHA-256";
                    break;
                case LMSUtils.LMS_SHA256_M32_H10:
                    m = 32;
                    h = 10;
                    hashAlgStr = "SHA-256";
                    break;
                case LMSUtils.LMS_SHA256_M32_H15:
                    m = 32;
                    h = 15;
                    hashAlgStr = "SHA-256";
                    break;
                case LMSUtils.LMS_SHA256_M32_H20:
                    m = 32;
                    h = 20;
                    hashAlgStr = "SHA-256";
                    break;
                case LMSUtils.LMS_SHA256_M32_H25:
                    m = 32;
                    h = 25;
                    hashAlgStr = "SHA-256";
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported or bad LMS type");
            }

            return new LMSParams(m, h, hashAlgStr);
        }

        boolean hasSameHash(LMSParams other) {
            return other.hashAlgStr.equals(hashAlgStr) && (other.m == m);
        }

        boolean hasSameHash(LMOTSParams lmotsParams) {
            return lmotsParams.hashAlgName.equals(hashAlgStr) &&
                    (lmotsParams.n == m);
        }
    }

    static class LMSignature {
        final int sigLmType;
        final int sigOtsType;
        private final byte[] qArr;
        final int q; // serial number of the LMS key being used for this signature
        final LMOTSignature lmotSig;
        final int n; // output length of the hash function used in the OTS
        final int p; // number of hash chains in the signature
        final int m; // output length of the hash function used in the Merkle tree
        final int h; // height of the Merkle tree
        private final byte[][] path;

        LMSignature(byte[] sigArray, int offset, boolean checkExactLen)
                throws SignatureException {
            int inLen = sigArray.length - offset;
            if (inLen < 8) {
                throw new SignatureException("LMS signature is too short");
            }

            LMOTSParams lmotsParams;
            q = LMSUtils.fourBytesToInt(sigArray, offset);
            qArr = Arrays.copyOfRange(sigArray, offset, offset + 4);
            sigOtsType = LMSUtils.fourBytesToInt(sigArray, offset + 4);
            try {
                lmotsParams = LMOTSParams.of(sigOtsType);
            } catch (IllegalArgumentException e) {
                throw new SignatureException(e);
            }

            n = lmotsParams.n;
            p = lmotsParams.p;

            if (inLen < (12 + n * (p + 1))) {
                throw new SignatureException("LMS signature is too short");
            }

            int otsSigLen = 4 + n * (p + 1);
            byte[] otSigArr = Arrays.copyOfRange(
                    sigArray, offset + 4, offset + 4 + otsSigLen);
            lmotSig = new LMOTSignature(otSigArr, lmotsParams);

            int sigTypePos = offset + 4 + otsSigLen;
            sigLmType = LMSUtils.fourBytesToInt(sigArray, sigTypePos);

            LMSParams lmsParams;
            try {
                lmsParams = LMSParams.of(sigLmType);
            } catch (IllegalArgumentException e) {
                throw new SignatureException(e);
            }
            m = lmsParams.m;
            h = lmsParams.h;

            int sigArrLen = (12 + n * (p + 1) + m * h);
            if ((q >= (1 << h)) ||
                    (inLen < sigArrLen) ||
                    (checkExactLen && (inLen != sigArrLen))) {
                throw new SignatureException("LMS signature length is incorrect");
            }

            int pStart = offset + 12 + n * (p + 1);
            path = new byte[h][m];
            for (int i = 0; i < h; i++) {
                path[i] = Arrays.copyOfRange(sigArray, pStart, pStart + m);
                pStart += m;
            }
        }

        int sigArrayLength() {
            return 12 + n * (p + 1) + m * h;
        }

        void getQArr(byte[] arr, int pos) {
            System.arraycopy(qArr, 0, arr, pos, 4);
        }

        void getPath(int i, byte[] arr, int pos) {
            System.arraycopy(path[i], 0, arr, pos, m);
        }
    }

    static class LMOTSParams {
        final int lmotSigType;
        final int n; // the number of bytes used from the hash output
        final int hashAlg_n = 32; // the output length of the hash function
        final int w;
        final int twoPowWMinus1;
        final int ls;
        final int p;
        final String hashAlgName;

        // The initial buffer image for the lmotsPubKeyCandidate() function.
        // In that function a clone of this buffer is fed into the
        // hash function as input to the implDigestFixedLengthPreprocessed()
        // function (which is basically an allocation and padding computation
        // free digest() function, so we can avoid the update()-digest()
        // sequence) which is parametrized so that the digest output is copied
        // back into the buffer. This way, we avoid memory allocations and some
        // computations that would have to be done otherwise.
        final byte[] hashBuf;
        // Precomputed block for SHA256 when the message size is 55 bytes
        // (i.e. when SHA256 is used)
        private static final byte[] hashbufSha256_32 = {
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, (byte) 0x80,
                0, 0, 0, 0, 0, 0, 1, (byte) 0xb8
        };

        private LMOTSParams(
                int lmotSigType, int hLen, int w,
                int ls, int p, String hashAlgName) {
            this.lmotSigType = lmotSigType;
            this.n = hLen;
            this.w = w;
            this.ls = ls;
            this.p = p;
            twoPowWMinus1 = (1 << w) - 1;
            this.hashAlgName = hashAlgName;
            hashBuf = hashbufSha256_32;
        }

        static LMOTSParams of(int lmotsType) {
            LMOTSParams params;
            switch (lmotsType) {
                case LMSUtils.LMOTS_SHA256_N32_W1:
                    params = new LMOTSParams(
                            lmotsType, 32, 1, 7, 265, "SHA-256");
                    break;
                case LMSUtils.LMOTS_SHA256_N32_W2:
                    params = new LMOTSParams(
                            lmotsType, 32, 2, 6, 133, "SHA-256");
                    break;
                case LMSUtils.LMOTS_SHA256_N32_W4:
                    params = new LMOTSParams(
                            lmotsType, 32, 4, 4, 67, "SHA-256");
                    break;
                case LMSUtils.LMOTS_SHA256_N32_W8:
                    params = new LMOTSParams(
                            lmotsType, 32, 8, 0, 34, "SHA-256");
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Unsupported or bad OTS Algorithm Identifier.");
            }
            return params;
        }

        int coef(byte[] S, int i) {
            return (twoPowWMinus1 &
                    (S[i * w / 8] >> (8 - (w * (i % (8 / w)) + w))));
        }

        private void addCksm(byte[] S) {
            int len = n;
            int sum = 0;
            int numSlices = len * 8 / w;
            for (int i = 0; i < numSlices; i++) {
                sum += twoPowWMinus1 - coef(S, i);
            }
            sum = sum << ls;
            S[len] = (byte) (sum >> 8);
            S[len + 1] = (byte) (sum & 0xff);
        }

        void digestFixedLengthPreprocessed(
                SHA2.SHA256 sha256, byte[] input, int inLen,
                byte[] output, int outOffset, int outLen) {
            sha256.implDigestFixedLengthPreprocessed(
                    input, inLen, output, outOffset, outLen);
        }

        byte[] lmotsPubKeyCandidate(
                LMSignature lmSig, byte[] message, LMSPublicKey pKey)
                throws SignatureException {
            LMOTSignature lmOtSig = lmSig.lmotSig;
            if (lmOtSig.otSigType != pKey.otsType) {
                throw new SignatureException(
                        "OTS public key type and OTS signature type do not match");
            }

            byte[] preQ = new byte[22 + hashAlg_n];
            pKey.getI(preQ, 0);
            lmSig.getQArr(preQ, 16);
            preQ[20] = (byte) 0x81; // D_MSG = 0x8181
            preQ[21] = (byte) 0x81;
            lmOtSig.getC(preQ, 22);
            MessageDigest md;
            try {
                md = MessageDigest.getInstance(hashAlgName);
            } catch (NoSuchAlgorithmException e) { // This should not happen
                throw new ProviderException("Digest implementation not found", e);
            }
            byte[] result;
            try {
                md.update(preQ, 0, 22 + n);
                md.update(message);
                byte[] QWithChecksum = new byte[hashAlg_n + 2];
                md.digest(QWithChecksum, 0, hashAlg_n);
                // the MessageDigest object has now been reset
                addCksm(QWithChecksum);

                byte[] preCandidate = new byte[22 + (p - 1) * n + hashAlg_n];
                pKey.getI(preCandidate, 0);
                lmSig.getQArr(preCandidate, 16);
                preCandidate[20] = (byte) 0x80; // D_PBLC = 0x8080
                preCandidate[21] = (byte) 0x80;

                byte[] preZi = hashBuf.clone();
                int hashLen = hashBuf.length;
                SHA2.SHA256 sha256 = new SHA2.SHA256();
                pKey.getI(preZi, 0);
                lmSig.getQArr(preZi, 16);

                int twoPowWMinus2 = twoPowWMinus1 - 1;
                for (int i = 0; i < p; i++) {
                    int a = coef(QWithChecksum, i);
                    if (a == twoPowWMinus1) {
                        lmOtSig.getY(i, preCandidate, 22 + i * n);
                    } else {
                        preZi[20] = (byte) (i >> 8);
                        preZi[21] = (byte) i;
                        lmOtSig.getY(i, preZi, 23);
                    }

                    for (int j = a; j < twoPowWMinus1; j++) {
                        preZi[22] = (byte) j;
                        if (j < twoPowWMinus2) {
                            digestFixedLengthPreprocessed(
                                    sha256, preZi, hashLen, preZi, 23, n);
                        } else {
                            digestFixedLengthPreprocessed(
                                    sha256, preZi, hashLen, preCandidate, 22 + i * n, n);
                        }
                    }
                }
                md.update(preCandidate, 0, 22 + p * n);

                result = md.digest();
            } catch (DigestException e) { // This should not happen
                throw new ProviderException("Digest failed", e);
            }
            if (n != hashAlg_n) {
                result = Arrays.copyOfRange(result, 0, n);
            }
            return result;
        }
    }

    public static class KeyFactoryImpl extends KeyFactorySpi {
        @Override
        protected PublicKey engineGeneratePublic(KeySpec keySpec)
                throws InvalidKeySpecException {
            if (keySpec instanceof X509EncodedKeySpec x509Spec) {
                try {
                    return new HSSPublicKey(
                            x509Spec.getEncoded(), true);
                } catch (InvalidKeyException e) {
                    throw new InvalidKeySpecException(e);
                }
            } else if (keySpec instanceof RawKeySpec rawSpec) {
                try {
                    return new HSSPublicKey(rawSpec.getKeyArr(), false);
                } catch (InvalidKeyException e) {
                    throw new InvalidKeySpecException(e);
                }
            }
            throw new InvalidKeySpecException("Unrecognized KeySpec");
        }

        @Override
        protected PrivateKey engineGeneratePrivate(KeySpec keySpec)
                throws InvalidKeySpecException {
            throw new InvalidKeySpecException(
                    "Private key generation is not supported");
        }

        @Override
        protected <T extends KeySpec> T engineGetKeySpec(Key key, Class<T> keySpec)
                throws InvalidKeySpecException {
            if (key == null) {
                throw new InvalidKeySpecException("key should not be null");
            }
            if (key.getFormat().equals("X.509") &&
                    key.getAlgorithm().equalsIgnoreCase("HSS/LMS")) {
                if (keySpec.isAssignableFrom(X509EncodedKeySpec.class)) {
                    return keySpec.cast(new X509EncodedKeySpec(key.getEncoded()));
                }
                throw new InvalidKeySpecException(
                        "keySpec is not an X509EncodedKeySpec");
            }
            throw new InvalidKeySpecException("Wrong key format or key algorithm");
        }

        @Override
        protected Key engineTranslateKey(Key key) throws InvalidKeyException {
            if (key == null) {
                throw new InvalidKeyException("key cannot be null");
            }
            PublicKey pKey;
            try {
                // Check if key originates from this factory
                if (key instanceof HSSPublicKey) {
                    return key;
                }
                // Convert key to spec
                X509EncodedKeySpec x509EncodedKeySpec
                        = engineGetKeySpec(key, X509EncodedKeySpec.class);
                // Create key from spec, and return it
                pKey = engineGeneratePublic(x509EncodedKeySpec);
            } catch (InvalidKeySpecException e) {
                throw new InvalidKeyException(e);
            }
            return pKey;
        }
    }

    static class HSSPublicKey extends X509Key implements Serializable {
        @Serial
        private static final long serialVersionUID = 21;
        private transient int L;
        private transient LMSPublicKey lmsPublicKey;

        HSSPublicKey(byte[] keyArray, boolean x509Encoded)
                throws InvalidKeyException {
            if (x509Encoded) {
                decode(keyArray);
                if (!KnownOIDs.HSSLMS.value().equals(algid.getOID().toString()) ||
                        (algid.getParameters() != null)) {
                    throw new InvalidKeyException("X509Key is not an HSS key");
                }
            } else {
                int inLen = keyArray.length;
                if (inLen < 4) {
                    throw new InvalidKeyException("HSS public key too short");
                }
                L = LMSUtils.fourBytesToInt(keyArray, 0);
                lmsPublicKey =
                        new LMSPublicKey(
                                Arrays.copyOfRange(keyArray, 4, keyArray.length),
                                0, true);
                algid = new AlgorithmId(ObjectIdentifier.of(KnownOIDs.HSSLMS));
                this.setKey(new BitArray(
                        8 * keyArray.length, keyArray));
            }
        }

        @Override
        public String toString() {
            HexDumpEncoder encoder = new HexDumpEncoder();

            return "HSS/LMS public key, number of layers: " + L +
                    ", LMS type: " + LMSUtils.lmsType(lmsPublicKey.type) +
                    ",\nOTS type: " + LMSUtils.lmotsType(lmsPublicKey.otsType) +
                    ", byte array representation:\n" +
                    encoder.encode(getKey().toByteArray());
        }

        /**
         * Parse the key. Called by X509Key.
         */
        @Override
        protected void parseKeyBits() throws InvalidKeyException {
            byte[] keyArray = getKey().toByteArray();
            if (keyArray.length < 12) { // More length check in LMSPublicKey
                throw new InvalidKeyException("LMS public key is too short");
            }
            if (keyArray[0] == DerValue.tag_OctetString
                    && keyArray[1] == keyArray.length - 2) {
                // pre-8347596 format that has an inner OCTET STRING.
                keyArray = Arrays.copyOfRange(keyArray, 2, keyArray.length);
                setKey(new BitArray(keyArray.length * 8, keyArray));
            }
            L = LMSUtils.fourBytesToInt(keyArray, 0);
            lmsPublicKey = new LMSPublicKey(keyArray, 4, true);
        }

        @java.io.Serial
        private Object writeReplace() throws java.io.ObjectStreamException {
            return new KeyRep(KeyRep.Type.PUBLIC,
                    getAlgorithm(),
                    getFormat(),
                    getEncoded());
        }

        @java.io.Serial
        private void readObject(java.io.ObjectInputStream s)
                throws java.io.ObjectStreamException {
            throw new InvalidObjectException(
                    "HSS public keys are not directly deserializable");
        }
    }

    static class HSSSignature {
        private final int Nspk;
        private final LMSignature[] siglist;
        private final LMSPublicKey[] pubList;

        HSSSignature(byte[] sigArr, HSSPublicKey pubKey)
                throws SignatureException {
            if (sigArr.length < 4) {
                throw new SignatureException("HSS signature is too short");
            }
            Nspk = LMSUtils.fourBytesToInt(sigArr, 0);
            if (Nspk + 1 != pubKey.L) {
                throw new SignatureException(
                        "HSS signature and public key have different tree heights");
            }
            siglist = new LMSignature[Nspk + 1];
            pubList = new LMSPublicKey[Nspk];
            int index = 4;
            try {
                for (int i = 0; i < Nspk; i++) {
                    siglist[i] = new LMSignature(sigArr, index, false);
                    index += siglist[i].sigArrayLength();
                    pubList[i] = new LMSPublicKey(sigArr, index, false);
                    if (!pubKey
                            .lmsPublicKey
                            .lmsParams
                            .hasSameHash(pubList[i].lmsParams)) {
                        throw new SignatureException(
                                "Digest algorithm in public key and Signature do not match");
                    }
                    index += pubList[i].keyArrayLength();
                }
                siglist[Nspk] = new LMSignature(sigArr, index, true);
            } catch (InvalidKeyException e) {
                throw new SignatureException("Invalid key in HSS signature", e);
            }
        }
    }
}

