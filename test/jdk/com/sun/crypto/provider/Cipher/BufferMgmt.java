
/*
 * Copyright (c) 2007, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @summary Use Cipher update with a mixture of byte[], bytebuffer, and offset
 * while verifying return values.
 */

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class BufferMgmt {
    enum valuetype { BYTE, HEAP, DIRECT };
    static HashMap<String, Data> datamap = new HashMap<>();
    // List of enum values for order of operation
    List<valuetype> ops;
    Data data;

    static class Data {
        SecretKey key;
        byte[] iv;
        byte[] pt;
        byte[] aad;
        byte[] ct;
        byte[] tag;

        Data(String keyalgo, String key, String iv, String pt, String aad, String ct, String tag) {
            this.key = new SecretKeySpec(HexToBytes(key), keyalgo);
            this.iv = HexToBytes(iv);
            this.pt = HexToBytes(pt);
            this.aad = HexToBytes(aad);
            this.ct = HexToBytes(ct);
            this.tag = HexToBytes(tag);
        }

    }

    BufferMgmt(String algo, List<valuetype> ops) throws Exception {
        this.ops = ops;
        System.out.println("Algo: " + algo + "  \tOps: " + ops.toString());
        data = datamap.get(algo);
        encrypt(algo);
    }

    static final int AESBLOCK = 16;

    void encrypt(String algo) throws Exception {
        Cipher cipher = Cipher.getInstance(algo);
        cipher.init(Cipher.ENCRYPT_MODE, data.key,
            new GCMParameterSpec(data.tag.length * 8, data.tag));
        int plen = data.pt.length / ops.size(); // partial input length
        int olen = plen - (plen % AESBLOCK); // output length
        int outOfs = 0;
        int inOfs = 0;
        int index = 0;
        int rlen = 0; // result length

        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        for (valuetype v : ops) {
            if (++index < ops.size()) {
                olen = plen - (plen % AESBLOCK); // output length
                switch (v) {
                    case BYTE -> {
                        byte[] out = new byte[olen];
                        rlen = cipher.update(data.pt, 0, plen, out);
                        ba.write(out, 0, rlen);
                    }
                    case HEAP -> {
                        ByteBuffer b = ByteBuffer.allocate(plen);
                        ByteBuffer out = ByteBuffer.allocate(olen + data.tag.length);
                        b.put(data.pt, outOfs, plen);
                        rlen = cipher.update(b, out);
                        ba.write(out.array(), 0, rlen);
                    }
                    case DIRECT -> {
                        ByteBuffer b = ByteBuffer.allocateDirect(plen);
                        ByteBuffer out = ByteBuffer.allocateDirect(olen + data.tag.length);
                        b.put(data.pt, outOfs, plen);
                        b.flip();
                        rlen = cipher.update(b, out);
                        byte[] o = new byte[olen];
                        b.flip();
                        b.get(o, 0, rlen);
                        ba.write(o);
                    }
                    default -> throw new Exception("Unknown op: " + v.name());
                }

                if (rlen != olen) {
                    throw new Exception("Wrong update return len (" +
                        v.name() + "):  " + "rlen=" + rlen +
                        ", expected output len=" + olen);
                }


                outOfs += rlen;
                inOfs += plen;

            } else {
                plen = data.pt.length - inOfs;
                olen = (data.ct.length + data.tag.length) - outOfs;
                switch (v) {
                    case BYTE -> {
                        byte[] out = new byte[olen];
                        rlen = cipher.doFinal(data.pt, inOfs,
                            plen, out, 0);
                        ba.write(out, 0, rlen);
                    }
                    case HEAP -> {
                        ByteBuffer b = ByteBuffer.allocate(
                            plen);
                        ByteBuffer out = ByteBuffer.allocate(olen);
                        b.put(data.pt, inOfs, plen);
                        rlen = cipher.doFinal(b, out);
                        ba.write(out.array(), 0, rlen);
                    }
                    case DIRECT -> {
                        ByteBuffer b = ByteBuffer.allocateDirect(
                            plen);
                        ByteBuffer out = ByteBuffer.allocateDirect(olen);
                        b.put(data.pt, inOfs, plen);
                        b.flip();
                        rlen = cipher.doFinal(b, out);
                        byte[] o = new byte[olen];
                        out.flip();
                        out.get(o, 0, rlen);
                        ba.write(o);
                    }
                    default -> throw new Exception("Unknown op: " + v.name());
                }

                if (rlen != olen) {
                    throw new Exception("Wrong doFinal return len (" +
                        v.name() + "):  " + "rlen=" + rlen +
                        ", expected output len=" + olen);
                }
                int ctlen = data.ct.length + data.tag.length;
                byte[] ctresult = ba.toByteArray();
                if (ctresult.length != ctlen) {
                    throw new Exception("Output lengths mismatch (" +
                        v.name() + "):  " + "received=" + ctresult.length +
                        ", expected output len=" + ctlen);
                }
                if (Arrays.compare(data.ct, ctresult) != 0) {
                    throw new Exception("Ciphertext what was expected:" +
                        "\nresult:   " +
                        String.format("%0" + (ctresult.length << 1) + "x",
                            new BigInteger(1, ctresult)) +
                        "\nexpected: " +
                    String.format("%0" + (ctresult.length << 1) + "x",
                        new BigInteger(1, data.ct)));
                }
            }
        }
    }

    public static void main (String args[]) throws Exception {
        new BufferMgmt("AES/GCM/NoPadding", List.of(valuetype.BYTE));
        new BufferMgmt("AES/GCM/NoPadding", List.of(valuetype.BYTE, valuetype.BYTE));
        new BufferMgmt("AES/GCM/NoPadding", List.of(valuetype.DIRECT));
        new BufferMgmt("AES/GCM/NoPadding", List.of(valuetype.DIRECT, valuetype.DIRECT));
    }

    // Utility methods
    private static byte[] HexToBytes(String hexVal) {
        if (hexVal == null) return new byte[0];
        byte[] result = new byte[hexVal.length()/2];
        for (int i = 0; i < result.length; i++) {
            // 2 characters at a time
            String byteVal = hexVal.substring(2*i, 2*i +2);
            result[i] = Integer.valueOf(byteVal, 16).byteValue();
        }
        return result;
    }

    static {
        datamap.put("AES/GCM/NoPadding", new Data("AES",
            "141f1ce91989b07e7eb6ae1dbd81ea5e",
            "49451da24bd6074509d3cebc2c0394c972e6934b45a1d91f3ce1d3ca69e19" +
                "4aa1958a7c21b6f21d530ce6d2cc5256a3f846b6f9d2f38df0102c4791e5" +
                "7df038f6e69085646007df999751e248e06c47245f4cd3b8004585a7470d" +
                "ee1690e9d2d63169a58d243c0b57b3e5b4a481a3e4e8c60007094ef3adea" +
                "2e8f05dd3a1396f",
            "d384305af2388699aa302f510913fed0f2cb63ba42efa8c5c9de2922a2ec" +
                "2fe87719dadf1eb0aef212b51e74c9c5b934104a43",
            "630cf18a91cc5a6481ac9eefd65c24b1a3c93396bd7294d6b8ba3239517" +
                "27666c947a21894a079ef061ee159c05beeb4",
            "f4c34e5fbe74c0297313268296cd561d59ccc95bbfcdfcdc71b0097dbd83" +
                "240446b28dc088abd42b0fc687f208190ff24c0548",
            "dbb93bbb56d0439cd09f620a57687f5d"));
    }
}
