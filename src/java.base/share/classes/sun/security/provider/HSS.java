/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.security.*;
import java.security.spec.*;
import java.util.Arrays;
import java.util.Objects;

public class HSS extends SignatureSpi {
    private HSSPublicKey pubKey;
    private byte[] message;

    @Deprecated
    protected void engineSetParameter(String param, Object value) {
        throw new UnsupportedOperationException();
    }

    @Deprecated
    protected AlgorithmParameters engineGetParameter(String param) {
        throw new UnsupportedOperationException();
    }

    protected void engineInitSign(PrivateKey publicKey) {
        throw new UnsupportedOperationException();
    }

    protected byte[] engineSign() throws SignatureException {
        throw new UnsupportedOperationException();
    }

    protected void engineInitVerify(PublicKey publicKey) throws InvalidKeyException {
        if (!(publicKey instanceof HSSPublicKey pub)) {
            throw new InvalidKeyException("Not an HSS public key: ");
        }
        pubKey = pub;
        message = new byte[0];
    }

    protected void engineUpdate(byte data) {
        int mLen = message.length;
        byte[] newMessage = new byte[mLen + 1];
        System.arraycopy(message, 0, newMessage, 0, mLen);
        newMessage[mLen] = data;
        message = newMessage;
    }

    protected void engineUpdate(byte[] data, int off, int len) {
        int mLen = message.length;
        byte[] newMessage = new byte[mLen + len];
        System.arraycopy(message, 0, newMessage, 0, mLen);
        System.arraycopy(data, 0, newMessage, mLen, len);
        message = newMessage;
    }

    protected boolean engineVerify(byte[] signature) throws SignatureException {
        try {
            HSSSignature sig = new HSSSignature(signature, pubKey.L);
            LMSPublicKey lmsPubKey = pubKey.lmsPublicKey;
            boolean result = true;
            for (int i = 0; i < sig.Nspk; i++) {
                byte[] keyArr = sig.pubList[i].keyArray();
                result &= lmsVerify(lmsPubKey, sig.siglist[i], keyArr);
                lmsPubKey = sig.pubList[i];
            }
            return result & lmsVerify(lmsPubKey, sig.siglist[sig.Nspk], message);
        } catch (Exception e) {
            return false;
        }
    }

    protected boolean lmsVerify(LMSPublicKey lmsPublicKey, LMSignature sig, byte[] message) throws SignatureException {

        if ((sig.sigOtsType != lmsPublicKey.otsType) || (sig.sigLmType != lmsPublicKey.type)) {
            return false;
        }
        LMOTSignature lmotSig = sig.lmotSig;
        LMOTSParams lmotsParams = lmotSig.lmotsParams;
        int q = sig.q;
        int m = lmsPublicKey.lmParams.m;
        int hashAlg_m = lmsPublicKey.lmParams.hashAlg_m;
        int n = lmotsParams.n;

        try {
            byte[] otsPkCandidate = lmotsParams.lmotsPubKeyCandidate(sig, message, lmsPublicKey);
            int nodeNum = lmsPublicKey.lmParams.twoPowh + q;
            int tmp0MsgLen = 22 + n;
            int tmpLoopMsgLen = 22 + m + hashAlg_m;
            byte[] tmpMsg = new byte[Integer.max(tmp0MsgLen, tmpLoopMsgLen)];
            lmsPublicKey.getI(tmpMsg, 0);
            MessageDigest md = MessageDigest.getInstance(lmsPublicKey.lmParams.hashAlgStr);
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
        } catch (Exception e) {
            throw new SignatureException(e);
        }
    }

    static class LMSPublicKey implements Serializable {
        @java.io.Serial
        private static final long serialVersionUID = 21L;
        final int type;
        final int otsType;
        final transient LMParams lmParams;
        private final byte[] I;
        private final byte[] T1;

        public static LMSPublicKey of(byte[] keyArray) throws InvalidKeyException {
            return new LMSPublicKey(keyArray, 0, true);
        }

        public LMSPublicKey(byte[] keyArray, int offset, boolean checkExactLength) throws InvalidKeyException {
            int inLen = keyArray.length - offset;
            if (inLen < 8)
                throw new InvalidKeyException("Invalid LMS public key");
            type = LMSUtils.fourBytesToInt(keyArray, offset);
            otsType = LMSUtils.fourBytesToInt(keyArray, offset + 4);
            // ??? do these have to have the same hash alg ???

            lmParams = new LMParams(type);

            int m = lmParams.m;
            if ((inLen < (24 + m)) || (checkExactLength && (inLen != (24 + m)))) {
                throw new InvalidKeyException("Invalid LMS public Key");
            }

            I = Arrays.copyOfRange(keyArray, offset + 8, offset + 8 + 16);
            T1 = Arrays.copyOfRange(keyArray, offset + 24, offset + 24 + m);
        }

        public void getI(byte[] arr, int pos) {
            System.arraycopy(I, 0, arr, pos, 16);
        }

        public boolean isT1(byte[] arr, int pos) {
            int m = lmParams.m;
            int diff = 0;
            for (int i = 0; i < m; i++) {
                diff |= (T1[i] ^ arr[pos + i]);
            }
            return (diff == 0);
        }

        public byte[] keyArray() {
            byte[] result = new byte[keyArrayLength()];
            LMSUtils.intToFourBytes(type, result, 0);
            LMSUtils.intToFourBytes(otsType, result, 4);
            System.arraycopy(I, 0, result, 8, 16);
            System.arraycopy(T1, 0, result, 24, lmParams.m);
            return result;
        }

        public int keyArrayLength() {
            return 24 + lmParams.m;
        }

        public String getDigestAlgorithm() {
            return lmParams.hashAlgStr;
        }
    }

    static class LMSUtils {
        public final static int LMS_RESERVED = 0;
        public final static int LMS_SHA256_M32_H5 = 5;
        public final static int LMS_SHA256_M32_H10 = 6;
        public final static int LMS_SHA256_M32_H15 = 7;
        public final static int LMS_SHA256_M32_H20 = 8;
        public final static int LMS_SHA256_M32_H25 = 9;
        public final static int LMS_SHA256_M24_H5 = 10;
        public final static int LMS_SHA256_M24_H10 = 11;
        public final static int LMS_SHA256_M24_H15 = 12;
        public final static int LMS_SHA256_M24_H20 = 13;
        public final static int LMS_SHA256_M24_H25 = 14;
        public final static int LMS_SHAKE_M32_H5 = 15;
        public final static int LMS_SHAKE_M32_H10 = 16;
        public final static int LMS_SHAKE_M32_H15 = 17;
        public final static int LMS_SHAKE_M32_H20 = 18;
        public final static int LMS_SHAKE_M32_H25 = 19;
        public final static int LMS_SHAKE_M24_H5 = 20;
        public final static int LMS_SHAKE_M24_H10 = 21;
        public final static int LMS_SHAKE_M24_H15 = 22;
        public final static int LMS_SHAKE_M24_H20 = 23;
        public final static int LMS_SHAKE_M24_H25 = 24;

        public final static int LMOTS_RESERVED = 0;
        public final static int LMOTS_SHA256_N32_W1 = 1;
        public final static int LMOTS_SHA256_N32_W2 = 2;
        public final static int LMOTS_SHA256_N32_W4 = 3;
        public final static int LMOTS_SHA256_N32_W8 = 4;
        public final static int LMOTS_SHA256_N24_W1 = 5;
        public final static int LMOTS_SHA256_N24_W2 = 6;
        public final static int LMOTS_SHA256_N24_W4 = 7;
        public final static int LMOTS_SHA256_N24_W8 = 8;
        public final static int LMOTS_SHAKE_N32_W1 = 9;
        public final static int LMOTS_SHAKE_N32_W2 = 10;
        public final static int LMOTS_SHAKE_N32_W4 = 11;
        public final static int LMOTS_SHAKE_N32_W8 = 12;
        public final static int LMOTS_SHAKE_N24_W1 = 13;
        public final static int LMOTS_SHAKE_N24_W2 = 14;
        public final static int LMOTS_SHAKE_N24_W4 = 15;
        public final static int LMOTS_SHAKE_N24_W8 = 16;

        public static int fourBytesToInt(byte[] arr, int i) {
            return ((arr[i] & 0xff) << 24) |
                    ((arr[i + 1] & 0xff) << 16) |
                    ((arr[i + 2] & 0xff) << 8) |
                    (arr[i + 3] & 0xff);
        }

        public static void intToFourBytes(int i, byte[] arr, int pos) {
            arr[pos] = (byte) (i >> 24);
            arr[pos + 1] = (byte) (i >> 16);
            arr[pos + 2] = (byte) (i >> 8);
            arr[pos + 3] = (byte) i;
        }
    }

    static class LMOTSignature {
        final int otSigType;
        final LMOTSParams lmotsParams;
        final int n;
        final int p;
        private final byte[] C;
        private final byte[][] y;

        LMOTSignature(byte[] sigArray, LMOTSParams lmotsParams) throws InvalidParameterException {
            int inLen = sigArray.length;
            if (inLen < 4)
                throw new InvalidParameterException("Invalid LMS signature");
            otSigType = lmotsParams.lmotSigType;
            this.lmotsParams = lmotsParams;
            n = lmotsParams.n;
            p = lmotsParams.p;
            if (inLen != (4 + n * (p + 1)))
                throw new InvalidParameterException("Invalid LMS signature");
            C = Arrays.copyOfRange(sigArray, 4, 4 + n);
            int pStart = 4 + n;
            y = new byte[p][n];
            for (int i = 0; i < p; i++) {
                y[i] = Arrays.copyOfRange(sigArray, pStart, pStart + n);
                pStart += n;
            }
        }

        public void getC(byte[] arr, int pos) {
            System.arraycopy(C, 0, arr, pos, n);
        }

        public void getY(int i, byte[] arr, int pos) {
            System.arraycopy(y[i], 0, arr, pos, n);
        }
    }

    static class LMParams {
        final int type;
        final int m; // the number of bytes used from the hash output
        final int hashAlg_m = 32; // output length of the hash function used for the LMS tree
        final int h; // height of the LMS tree
        final int twoPowh;
        final String hashAlgStr;

        LMParams(int type) {
            this.type = type;
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

/*
                case LMSUtils.LMS_SHAKE_M32_H5:
                    m = 32;
                    h = 5;
                    hashAlgStr = "SHAKE256";
                    break;
                case LMSUtils.LMS_SHAKE_M32_H10:
                    m = 32;
                    h = 10;
                    hashAlgStr = "SHAKE256";
                    break;
                case LMSUtils.LMS_SHAKE_M32_H15:
                    m = 32;
                    h = 15;
                    hashAlgStr = "SHAKE256";
                    break;
                case LMSUtils.LMS_SHAKE_M32_H20:
                    m = 32;
                    h = 20;
                    hashAlgStr = "SHAKE256";
                    break;
                case LMSUtils.LMS_SHAKE_M32_H25:
                    m = 32;
                    h = 25;
                    hashAlgStr = "SHAKE256";
                    break;
 */

                case LMSUtils.LMS_SHA256_M24_H5:
                    m = 24;
                    h = 5;
                    hashAlgStr = "SHA-256";
                    break;
                case LMSUtils.LMS_SHA256_M24_H10:
                    m = 24;
                    h = 10;
                    hashAlgStr = "SHA-256";
                    break;
                case LMSUtils.LMS_SHA256_M24_H15:
                    m = 24;
                    h = 15;
                    hashAlgStr = "SHA-256";
                    break;
                case LMSUtils.LMS_SHA256_M24_H20:
                    m = 24;
                    h = 20;
                    hashAlgStr = "SHA-256";
                    break;
                case LMSUtils.LMS_SHA256_M24_H25:
                    m = 24;
                    h = 25;
                    hashAlgStr = "SHA-256";
                    break;
/*
                case LMSUtils.LMS_SHAKE_M24_H5:
                    m = 24;
                    h = 5;
                    hashAlgStr = "SHAKE256";
                    break;
                case LMSUtils.LMS_SHAKE_M24_H10:
                    m = 24;
                    h = 10;
                    hashAlgStr = "SHAKE256";
                    break;
                case LMSUtils.LMS_SHAKE_M24_H15:
                    m = 24;
                    h = 15;
                    hashAlgStr = "SHAKE256";
                    break;
                case LMSUtils.LMS_SHAKE_M24_H20:
                    m = 24;
                    h = 20;
                    hashAlgStr = "SHAKE256";
                    break;
                case LMSUtils.LMS_SHAKE_M24_H25:
                    m = 24;
                    h = 25;
                    hashAlgStr = "SHAKE256";
                    break;
 */

                default:
                    throw new IllegalArgumentException("Unsupported or bad LMS type");
            }

            twoPowh = 1 << h;
        }

    }

    static class LMSignature {
        final int sigLmType;
        final int sigOtsType;
        final private byte[] qArr;
        final int q; // serial number of the LMS key being used for this signature
        final LMOTSignature lmotSig;
        final int n; // output length of the hash function used in the one time signature
        final int p; // number of hash chains in the signature
        final int m; // output length of the hash fubction used in the Merkle tree
        final int h; // height of the Merkle tree
        final byte[][] path;
        final byte[] sigArr;

        public static LMSignature of(byte[] sigArray) throws InvalidParameterException {
            return new LMSignature(sigArray, 0, true);
        }

        public LMSignature(byte[] sigArray, int offset, boolean checkExactLen) throws InvalidParameterException {
            int inLen = sigArray.length - offset;
            if (inLen < 8)
                throw new InvalidParameterException("Invalid LMS signature");

            q = LMSUtils.fourBytesToInt(sigArray, offset);
            qArr = Arrays.copyOfRange(sigArray, offset, offset + 4);
            sigOtsType = LMSUtils.fourBytesToInt(sigArray, offset + 4);
            LMOTSParams lmotsParams = LMOTSParams.of(sigOtsType);

            n = lmotsParams.n;
            p = lmotsParams.p;

            if (inLen < (12 + n * (p + 1)))
                throw new InvalidParameterException("Invalid LMS signature");

            int otsSigLen = 4 + n * (p + 1);
            byte[] otSigArr = Arrays.copyOfRange(sigArray, offset + 4, offset + 4 + otsSigLen);
            lmotSig = new LMOTSignature(otSigArr, lmotsParams);

            int sigTypePos = offset + 4 + otsSigLen;
            sigLmType = LMSUtils.fourBytesToInt(sigArray, sigTypePos);

            LMParams lmParams = new LMParams(sigLmType);
            m = lmParams.m;
            h = lmParams.h;

            int sigArrLen = (12 + n * (p + 1) + m * h);
            if ((q >= (1 << h)) || (inLen < sigArrLen) || (checkExactLen && (inLen != sigArrLen)))
                throw new InvalidParameterException("Invalid LMS signature");

            sigArr = Arrays.copyOfRange(sigArray, offset, offset + sigArrLen);

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

        public void getQArr(byte[] arr, int pos) {
            System.arraycopy(qArr, 0, arr, pos, 4);
        }

        public void getPath(int i, byte[] arr, int pos) {
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

        // The buffer for the lmotsPubKeyCandidate() function. In that function this buffer is fed into the
        // hash function as input to the implDigestFixedLengthPreprocessed() function (which is
        // basically an allocation and padding computation free digest() function, so we can avoid the
        // update()-digest() sequence) which is parametrized so that the digest output is copied back into this buffer.
        // This way, we avoid memory allocations and some computations that would have to be done otherwise.
        final byte[] hashBuf;

        // Precomputed block for SHA256 when the message size is 47 bytes (i.e. when SHA256-192 is used)
        final byte[] hashbufSha256_24 = {
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, (byte) 0x80,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 1, 0x78
        };

        // Precomputed block for SHA256 when the message size is 55 bytes (i.e. when SHA256 is used)
        final byte[] hashbufSha256_32 = {
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, (byte) 0x80,
                0, 0, 0, 0, 0, 0, 1, (byte) 0xb8
        };

        final SHA2.SHA256 sha256Fix;

        private LMOTSParams(int lmotSigType, int hLen, int w, int ls, int p, String hashAlgName) {
            this.lmotSigType = lmotSigType;
            this.n = hLen;
            this.w = w;
            this.ls = ls;
            this.p = p;
            twoPowWMinus1 = (1 << w) - 1;
            this.hashAlgName = hashAlgName;
            if (this.n == 24) {
                hashBuf = hashbufSha256_24;
            } else {
                hashBuf = hashbufSha256_32;
            }
            if (Objects.equals(hashAlgName, "SHA-256"))
                sha256Fix = new SHA2.SHA256();
            else
                sha256Fix = null;
        }

        static LMOTSParams of(int lmotsType) {
            LMOTSParams params;
            switch (lmotsType) {
                case LMSUtils.LMOTS_SHA256_N32_W1:
                    params = new LMOTSParams(lmotsType, 32, 1, 7, 265, "SHA-256");
                    break;
                case LMSUtils.LMOTS_SHA256_N32_W2:
                    params = new LMOTSParams(lmotsType, 32, 2, 6, 133, "SHA-256");
                    break;
                case LMSUtils.LMOTS_SHA256_N32_W4:
                    params = new LMOTSParams(lmotsType, 32, 4, 4, 67, "SHA-256");
                    break;
                case LMSUtils.LMOTS_SHA256_N32_W8:
                    params = new LMOTSParams(lmotsType, 32, 8, 0, 34, "SHA-256");
                    break;
                case LMSUtils.LMOTS_SHA256_N24_W1:
                    params = new LMOTSParams(lmotsType, 24, 1, 8, 200, "SHA-256");
                    break;
                case LMSUtils.LMOTS_SHA256_N24_W2:
                    params = new LMOTSParams(lmotsType, 24, 2, 6, 101, "SHA-256");
                    break;
                case LMSUtils.LMOTS_SHA256_N24_W4:
                    params = new LMOTSParams(lmotsType, 32, 4, 4, 51, "SHA-256");
                    break;
                case LMSUtils.LMOTS_SHA256_N24_W8:
                    params = new LMOTSParams(lmotsType, 24, 8, 0, 26, "SHA-256");
                    break;

/*
                case LMSUtils.LMOTS_SHAKE_N32_W1:
                    params = new LMOTSParams(lmotsType, 32, 1, 7, 265, "SHAKE256");
                    break;
                case LMSUtils.LMOTS_SHAKE_N32_W2:
                    params = new LMOTSParams(lmotsType, 32, 2, 6, 133, "SHAKE256");
                    break;
                case LMSUtils.LMOTS_SHAKE_N32_W4:
                    params = new LMOTSParams(lmotsType, 32, 4, 4, 67, "SHAKE256");
                    break;
                case LMSUtils.LMOTS_SHAKE_N32_W8:
                    params = new LMOTSParams(lmotsType, 32, 8, 0, 34, "SHAKE256");
                    break;
                case LMSUtils.LMOTS_SHAKE_N24_W1:
                    params = new LMOTSParams(lmotsType, 24, 1, 8, 200, "SHAKE256");
                    break;
                case LMSUtils.LMOTS_SHAKE_N24_W2:
                    params = new LMOTSParams(lmotsType, 24, 2, 6, 101, "SHAKE256");
                    break;
                case LMSUtils.LMOTS_SHAKE_N24_W4:
                    params = new LMOTSParams(lmotsType, 24, 4, 4, 51, "SHAKE256");
                    break;
                case LMSUtils.LMOTS_SHAKE_N24_W8:
                    params = new LMOTSParams(lmotsType, 24, 8, 0, 26, "SHAKE256");
                    break;
 */

                default:
                    throw new IllegalArgumentException("Unsupported or bad OTS Algorithm Identifier.");
            }
            return params;
        }

        public int coef(byte[] S, int i) {
            return (twoPowWMinus1 & (S[i * w / 8] >> (8 - (w * (i % (8 / w)) + w))));
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

        public void digestFixedLengthPreprocessed(
                byte[] input, int inLen, byte[] output, int outOffset, int outLen) {
            if (sha256Fix != null) {
                sha256Fix.implDigestFixedLengthPreprocessed(input, inLen, output, outOffset, outLen);
            }
        }
        public byte[] lmotsPubKeyCandidate(LMSignature lmSig, byte[] message, LMSPublicKey pKey)
                throws NoSuchAlgorithmException, DigestException {
            LMOTSignature lmOtSig = lmSig.lmotSig;
            if (lmOtSig.otSigType != pKey.otsType) {
                throw new IllegalArgumentException("OTS public key type and OTS signature type do not match");
            }

            byte[] preQ = new byte[22 + hashAlg_n];
            pKey.getI(preQ, 0);
            lmSig.getQArr(preQ, 16);
            preQ[20] = (byte) 0x81; // D_MSG = 0x8181
            preQ[21] = (byte) 0x81;
            lmOtSig.getC(preQ, 22);
            MessageDigest md = MessageDigest.getInstance(hashAlgName);
            md.update(preQ, 0, 22 + n);
            md.update(message);
            byte[] QWithChecksum = new byte[hashAlg_n + 2];
            md.digest(QWithChecksum, 0, hashAlg_n); // digest resets the MessageDigest object
            addCksm(QWithChecksum);

            byte[] preCandidate = new byte[22 + (p - 1) * n + hashAlg_n];
            pKey.getI(preCandidate, 0);
            lmSig.getQArr(preCandidate, 16);
            preCandidate[20] = (byte) 0x80; // D_PBLC = 0x8080
            preCandidate[21] = (byte) 0x80;

            byte[] preZi = hashBuf;
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
                        digestFixedLengthPreprocessed(preZi, 64, preZi, 23, n);
                    } else {
                        digestFixedLengthPreprocessed(preZi, 64, preCandidate, 22 + i * n, n);
                    }
                }
            }
            md.update(preCandidate, 0, 22 + p * n);

            byte[] result = md.digest();
            if (n != hashAlg_n) {
                result = Arrays.copyOfRange(result, 0, n);
            }
            return result;
        }
    }

    public static class KeyFactoryImpl extends KeyFactorySpi {
        @Override
        protected PublicKey engineGeneratePublic(KeySpec keySpec) throws InvalidKeySpecException {
            if (keySpec instanceof X509EncodedKeySpec x) {
                try {
                    var val = new DerValue(new ByteArrayInputStream(x.getEncoded()));
                    val.data.getDerValue();
                    return new HSSPublicKey(new DerValue(val.data.getBitString()).getOctetString());
                } catch (IOException | InvalidKeyException e) {
                    throw new InvalidKeySpecException(e);
                }
            } else if (keySpec instanceof RawKeySpec x) {
                try {
                    return new HSSPublicKey(x.getKeyArr());
                } catch (InvalidKeyException e) {
                    throw new InvalidKeySpecException(e);
                }
            }
            throw new InvalidKeySpecException();
        }

        @Override
        protected PrivateKey engineGeneratePrivate(KeySpec keySpec) throws InvalidKeySpecException {
            throw new InvalidKeySpecException();
        }

        @Override
        protected <T extends KeySpec> T engineGetKeySpec(Key key, Class<T> keySpec) throws InvalidKeySpecException {
            if (key instanceof HSSPublicKey p) {
                if (keySpec.isAssignableFrom(X509EncodedKeySpec.class)) {
                    return keySpec.cast(new X509EncodedKeySpec(p.getEncoded()));
                }
            }
            throw new InvalidKeySpecException();
        }

        @Override
        protected Key engineTranslateKey(Key key) throws InvalidKeyException {
            if (key instanceof HSSPublicKey) {
                return key;
            }
            throw new InvalidKeyException();
        }
    }

    public static class HSSPublicKey extends X509Key implements Length {
        @Serial
        private static final long serialVersionUID = 21;
        final int L;
        final LMSPublicKey lmsPublicKey;

        @SuppressWarnings("deprecation")
        public HSSPublicKey(byte[] keyArray) throws InvalidKeyException {
            int inLen = keyArray.length;
            if (inLen < 4)
                throw new InvalidKeyException("Invalid HSS public key");
            L = LMSUtils.fourBytesToInt(keyArray, 0);
            lmsPublicKey = LMSPublicKey.of(Arrays.copyOfRange(keyArray, 4, keyArray.length));
            algid = new AlgorithmId(ObjectIdentifier.of(KnownOIDs.HSSLMS));
            key = new DerOutputStream().putOctetString(keyArray).toByteArray();
        }

        public String getDigestAlgorithm() {
            return lmsPublicKey.getDigestAlgorithm();
        }

        @Override
        @SuppressWarnings("deprecation")
        public int length() {
            return key.length * 8; // length in bits
        }
    }

    static class HSSSignature {
        final int Nspk;
        final LMSignature[] siglist;
        final LMSPublicKey[] pubList;

        HSSSignature(byte[] sigArr, int pubKeyL) throws SignatureException {
            if (sigArr.length < 4) {
                throw new SignatureException("Bad HSS signature");
            }
            Nspk = LMSUtils.fourBytesToInt(sigArr, 0);
            if (Nspk + 1 != pubKeyL) {
                throw new SignatureException("Bad HSS signature");
            }
            siglist = new LMSignature[Nspk + 1];
            pubList = new LMSPublicKey[Nspk];
            int index = 4;
            try {
                for (int i = 0; i < Nspk; i++) {
                    siglist[i] = new LMSignature(sigArr, index, false);
                    index += siglist[i].sigArrayLength();
                    pubList[i] = new LMSPublicKey(sigArr, index, false);
                    index += pubList[i].keyArrayLength();
                }
                siglist[Nspk] = new LMSignature(sigArr, index, true);
            } catch (Exception E) {
                throw new SignatureException("Bad HSS signature");
            }
        }
    }
}

