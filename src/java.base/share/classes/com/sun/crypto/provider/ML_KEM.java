/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.crypto.provider;

import sun.security.provider.SHA3.SHAKE128;
import sun.security.provider.SHA3.SHAKE256;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.ProviderException;
import java.util.*;
import java.util.stream.IntStream;

public final class ML_KEM {

    static final int q = 3329;
    static final int nBits = 8;
    static final int zeta = 17;
    static final int eta2 = 2;

    static final int[] zetas = new int[q];
    static {
        zetas[0] = 1;
        for (var i = 1; i < q; i++) {
            zetas[i] = zetas[i - 1] * zeta % q;
        }
    }

    static final int n = 1 << nBits;
    static final int inv2 = (q+1) / 2; // # inverse of 2

    record Params(int k, int du, int dv, int eta1) {}
    static final Params params512  = new Params(2, 10, 4, 3);
    static final Params params768  = new Params(3, 10, 4, 2);
    static final Params params1024 = new Params(4, 11, 5, 2);

    static int smod(int x) {
        var r = x % q;
        if (r > (q - 1) / 2) r -= q;
        return r;
    }

    static int Round(float x) {
        return (int)(x + 0.5);
    }

    static int Compress(int x, int d) {
        return Round(((1 << d) * 1.0f / q) * x) % (1 << d);
    }

    static int Decompress(int y, int d) {
        assert 0 <= y && y <= (1 << d);
        return Round(1f * q / (1 << d) * y);
    }

    static int[] BitsToWords(int[] bs, int w) {
        assert bs.length % w == 0;
        var result = new int[bs.length / w];
        for (var i = 0; i < result.length; i++) {
            for (var j = 0; j < w; j++) {
                result[i] += bs[i * w + j] << j;
            }
        }
        return result;
    }

    static int[] WordsToBits(int[] bs, int w) {
        var result = new int[w * bs.length];
        int pos = 0;
        for (var b : bs) {
            for (var i = 0; i < w; i++) {
                result[pos++] = (b >> i) % 2;
            }
        }
        return result;
    }

    static byte[] Encode(int[] a, int w) {
        var ints = BitsToWords(WordsToBits(a, w), 8);
        var bytes = new byte[ints.length];
        for (var i = 0; i < bytes.length; i++) {
            bytes[i] = (byte)ints[i];
        }
        return bytes;
    }

    static int[] Decode(byte[] a, int w) {
        var ints = new int[a.length];
        for (var i = 0; i < ints.length; i++) {
            ints[i] = a[i] & 255;
        }
        return BitsToWords(WordsToBits(ints, 8), w);
    }

    static int brv(int x) {
        var out = 0;
        if (x % 2 == 1) out += 64;
        if (x / 2 % 2 == 1) out += 32;
        if (x / 4 % 2 == 1) out += 16;
        if (x / 8 % 2 == 1) out += 8;
        if (x / 16 % 2 == 1) out += 4;
        if (x / 32 % 2 == 1) out += 2;
        if (x / 64 % 2 == 1) out += 1;
        return out;
    }

    static class Poly {
        final int[] cs;

        Poly() {
            cs = new int[n];
        }

        Poly(int[] cs) {
            assert cs.length == n;
            this.cs = cs.clone();
        }

        Poly add(Poly other) {
            var out = new Poly();
            for (var i = 0; i < n; i++) {
                out.cs[i] = (cs[i] + other.cs[i]) % q;
            }
            return out;
        }

        Poly neg() {
            var out = new Poly(cs);
            for (var i = 0; i < n; i++) {
                out.cs[i] = q - cs[i];
            }
            return out;
        }

        Poly sub(Poly other) {
            return add(other.neg());
        }

        Poly NTT() {
            var cs = this.cs.clone();
            var layer = n / 2;
            var zi = 0;
            while (layer >= 2) {
                for (var offset = 0; offset < n - layer; offset += 2 * layer) {
                    zi += 1;
                    var z = zetas[brv(zi)];

                    for (var j = offset; j < offset + layer; j++) {
                        var t = ((z * cs[j + layer]) % q + q) % q;
                        cs[j + layer] = ((cs[j] - t) % q + q) % q;
                        cs[j] = ((cs[j] + t) % q + q) % q;
                    }
                }
                layer /= 2;
            }
            return new Poly(cs);
        }

        Poly RefNTT() {
            var cs = new int[n];
            for (var i = 0; i < n; i += 2) {
                for (var j = 0; j < n / 2; j++) {
                    var z = zetas[(2 * brv(i / 2) + 1) * j];
                    cs[i] = (cs[i] + this.cs[2 * j] * z) % q;
                    cs[i + 1] = (cs[i + 1] + this.cs[2 * j + 1] * z) % q;
                }
            }
            return new Poly(cs);
        }

        Poly InvNTT() {
            var cs = this.cs.clone();
            var layer = 2;
            var zi = n / 2;
            while (layer < n) {
                for (var offset = 0; offset < n - layer; offset += 2 * layer) {
                    zi -= 1;
                    var z = zetas[brv(zi)];

                    for (var j = offset; j < offset + layer; j++) {
                        var t = ((cs[j + layer] - cs[j]) % q + q) % q;
                        cs[j] = (inv2 * (cs[j] + cs[j + layer])) % q;
                        cs[j + layer] = (inv2 * z %q    * t) % q;
                    }
                }
                layer *= 2;
            }
            return new Poly(cs);
        }

        Poly MulNTT(Poly other) {
            var cs = new int[n];
            for (var i = 0; i < n; i += 2) {
                var a1 = this.cs[i];
                var a2 = this.cs[i + 1];
                var b1 = other.cs[i];
                var b2 = other.cs[i + 1];
                var z = zetas[2 * brv(i / 2) + 1];
                cs[i] = ((a1 * b1 + z * a2 % q * b2) % q + q) % q;
                cs[i + 1] = ((a2 * b1 + a1 * b2) % q + q) % q;
            }
            return new Poly(cs);
        }

        Poly Compress(int d) {
            var out = new int[cs.length];
            for (var i = 0; i < n; i++) {
                out[i] = ML_KEM.Compress(cs[i], d);
            }
            return new Poly(out);
        }

        Poly Decompress(int d) {
            var out = new int[cs.length];
            for (var i = 0; i < n; i++) {
                out[i] = ML_KEM.Decompress(cs[i], d);
            }
            return new Poly(out);
        }

        byte[] Encode(int d) {
            return ML_KEM.Encode(cs, d);
        }

        public String toString() {
            return String.format("Poly(%d,%d,...%d,%d;%d)",
                    cs[0], cs[1], cs[cs.length - 2],
                    cs[cs.length - 1], Arrays.stream(cs).sum());
        }
    }

    static Poly sampleUniform(SHAKE128 stream) {
        var cs = new int[n];
        var pos = 0;
        while (true) {
            var b = stream.squeeze(3);
            var d1 = (b[0] & 0xff) + 256 * ((b[1] & 0xff)% 16);
            var d2 = ((b[1] & 0xff) >> 4) + 16 * (b[2] & 0xff);
            assert d1 + (1 << 12) * d2 == (b[0] & 0xff) + (1 << 8) * (b[1] & 0xff) + (1 << 16) * (b[2] & 0xff);
            for (var d : new int[]{d1, d2}) {
                if (d >= q) continue;
                cs[pos++] = d;
                if (pos == n) {
                    return new Poly(cs);
                }
            }
        }
    }

    static Poly CBD(byte[] a, int eta) {
        assert a.length == 64 * eta;
        var ints = new int[a.length];
        for (var i = 0; i < a.length; i++) {
            ints[i] = a[i] & 0xff;
        }
        var b = WordsToBits(ints, 8);
        var cs = new int[n];
        var pos = 0;
        for (var i = 0; i < n; i++) {
            var sum = 0;
            for (var j = 0; j < eta; j++) {
                sum += b[j + pos];
            }
            for (var j = eta; j < 2 * eta; j++) {
                sum -= b[j + pos];
            }
            cs[i] = (sum % q + q) % q;
            pos += 2 * eta;
        }
        return new Poly(cs);
    }

    static SHAKE128 xof(byte[] rho, int j, int i) {
        var h = new SHAKE128(0);
        h.update(rho);
        h.update((byte)j);
        h.update((byte)i);
        return h;
    }

    static byte[] PRF1(byte[]seed, int nonce, int n) {
        assert seed.length == 32;
        var h = new SHAKE256(n);
        h.update(seed);
        h.update((byte) nonce);
        return h.digest();
    }

    static byte[] PRF2(byte[] seed, byte[] msg) {
        assert seed.length == 32;
        var h = new SHAKE256(32);
        h.update(seed);
        h.update(msg);
        return h.digest();
    }

    static byte[][] G(byte[] seed) {
        try {
            var h = MessageDigest.getInstance("SHA3-512").digest(seed);
            return new byte[][] { Arrays.copyOfRange(h, 0, 32), Arrays.copyOfRange(h, 32, 64) };
        } catch (NoSuchAlgorithmException e) {
            throw new ProviderException(e);
        }
    }

    static byte[] H(byte[] msg) {
        try {
            return MessageDigest.getInstance("SHA3-256").digest(msg);
        } catch (NoSuchAlgorithmException e) {
            throw new ProviderException(e);
        }
    }

    static class Vec {

        final List<Poly> ps;

        Vec(List<Poly> ps) {
            this.ps = ps;
        }

        Vec NTT() {
            return new Vec(ps.stream().map(Poly::NTT).toList());
        }

        Vec InvNTT() {
            return new Vec(ps.stream().map(Poly::InvNTT).toList());
        }

        Poly DotNTT(Vec other) {
            var out = new Poly();
            for (var i = 0; i < ps.size(); i++) {
                out = out.add(ps.get(i).MulNTT(other.ps.get(i)));
            }
            return out;
        }

        Vec add(Vec other) {
            var out = new ArrayList<Poly>();
            for (var i = 0; i < ps.size(); i++) {
                out.add(ps.get(i).add(other.ps.get(i)));
            }
            return new Vec(out);
        }

        Vec Compress(int d) {
            var out = new ArrayList<Poly>();
            for (var i = 0; i < ps.size(); i++) {
                out.add(ps.get(i).Compress(d));
            }
            return new Vec(out);
        }

        Vec Decompress(int d) {
            // """ Computes the dot product <self, other> in NTT domain. """
            var out = new ArrayList<Poly>();
            for (var i = 0; i < ps.size(); i++) {
                out.add(ps.get(i).Decompress(d));
            }
            return new Vec(out);
        }

        byte[] Encode(int d) {
            var out = new ByteArrayOutputStream();
            for (var i = 0; i < ps.size(); i++) {
                out.writeBytes(ps.get(i).Encode(d));
            }
            return out.toByteArray();
        }

        @Override
        public String toString() {
            var sb = new StringBuilder("Vec(");
            for (var p : ps) {
                sb.append(p).append(", ");
            }
            sb.append(")");
            return sb.toString();
        }

    }

    static byte[] EncodeVec(Vec v, int w) {
        var out = new ByteArrayOutputStream();
        for (var p : v.ps) {
            out.writeBytes(Encode(p.cs, w));
        }
        return out.toByteArray();
    }

    static Vec DecodeVec(byte[] bs, int k, int w) {
        var cs = Decode(bs, w);
        var list = new ArrayList<Poly>();
        for (var i = 0; i < k; i++) {
            list.add(new Poly(Arrays.copyOfRange(cs, n * i, n * (i + 1))));
        }
        return new Vec(list);
    }

    static Poly DecodePoly(byte[] bs, int w) {
        return new Poly(Decode(bs, w));
    }

    static class Matrix {
        final List<Vec> cs;

        Matrix(List<Vec> cs) {
            this.cs = cs;
        }

        Vec MulNTT(Vec vec) {
            return new Vec(cs.stream().map(r -> r.DotNTT(vec)).toList());
        }

        Matrix T() {
            var k = cs.size();
            var newCs = new ArrayList<Vec>();
            for (var i = 0; i < k; i++) {
                var newPs = new ArrayList<Poly>();
                for (var j = 0; j < k; j++) {
                    newPs.add(cs.get(j).ps.get(i));
                }
                newCs.add(new Vec(newPs));
            }
            return new Matrix(newCs);
        }

        @Override
        public String toString() {
            var sb = new StringBuilder("Matrix(");
            for (var p : cs) {
                sb.append(p).append(", ");
            }
            sb.append(")");
            return sb.toString();
        }
    }

    static Matrix sampleMatrix(byte[] rho, int k) {
        var newCs = new ArrayList<Vec>();
        for (var i = 0; i < k; i++) {
            var newPs = new ArrayList<Poly>();
            for (var j = 0; j < k; j++) {
                newPs.add(sampleUniform(xof(rho, j, i)));
            }
            newCs.add(new Vec(newPs));
        }
        return new Matrix(newCs);
    }


    static Vec sampleNoise(byte[] sigma, int eta, int offset, int k) {
        return new Vec(IntStream.range(0, k).mapToObj(i -> CBD(PRF1(sigma, i + offset, 64 * eta), eta)).toList());
    }


    static byte[] constantTimeSelectOnEquality(byte[] a, byte[] b, byte[] ifEq, byte[] ifNeq) {
        //    # WARNING! In production code this must be done in a
        //    # data-independent constant-time manner, which this implementation
        //    # is not. In fact, many more lines of code in this
        //    # file are not constant-time.
        return MessageDigest.isEqual(a, b) ? ifEq : ifNeq;
    }

    static byte[] concat(byte[]... input) {
        var sum = 0;
        for (var i : input) {
            sum += i.length;
        }
        var out = new byte[sum];
        sum = 0;
        for (var i : input) {
            System.arraycopy(i, 0, out, sum, i.length);
            sum += i.length;
        }
        return out;
    }

    static byte[][] InnerKeyGen(byte[] seed, Params params) {
        assert seed.length == 32;
        seed = Arrays.copyOf(seed, 33);
        seed[32] = (byte)params.k;
        var g = G(seed);
        var rho = g[0];
        var sigma = g[1];
        var A = sampleMatrix(rho, params.k);
        var s = sampleNoise(sigma, params.eta1, 0, params.k);
        var e = sampleNoise(sigma, params.eta1, params.k, params.k);
        var sHat = s.NTT();
        var eHat = e.NTT();
        var tHat = A.MulNTT(sHat).add(eHat);
        var pk = concat(EncodeVec(tHat, 12), rho);
        var sk = EncodeVec(sHat, 12);
        return new byte[][]{pk, sk};
    }

    static byte[] InnerEnc(byte[] pk, byte[] msg, byte[] seed, Params params) {
        assert msg.length == 32;
        var tHat = DecodeVec(Arrays.copyOf(pk, pk.length - 32), params.k, 12);
        var rho = Arrays.copyOfRange(pk, pk.length - 32, pk.length);
        var A = sampleMatrix(rho, params.k);
        var r = sampleNoise(seed, params.eta1, 0, params.k);
        var e1 = sampleNoise(seed, eta2, params.k, params.k);
        var e2 = sampleNoise(seed, eta2, 2 * params.k, 1).ps.get(0);
        var rHat = r.NTT();
        var u = A.T().MulNTT(rHat).InvNTT().add(e1);
        var m = new Poly(Decode(msg, 1)).Decompress(1);
        var v = tHat.DotNTT(rHat).InvNTT().add(e2).add(m);
        var c1 = u.Compress(params.du).Encode(params.du);
        var c2 = v.Compress(params.dv).Encode(params.dv);
        return concat(c1, c2);
    }

    static byte[] InnerDec(byte[] sk, byte[] ct, Params params) {
        var split = params.du * params.k * n / 8;
        var c1 = Arrays.copyOfRange(ct, 0, split);
        var c2 = Arrays.copyOfRange(ct, split, ct.length);
        var u = DecodeVec(c1, params.k, params.du).Decompress(params.du);
        var v = DecodePoly(c2, params.dv).Decompress(params.dv);
        var sHat = DecodeVec(sk, params.k, 12);
        var out = v.sub(sHat.DotNTT(u.NTT()).InvNTT()).Compress(1).Encode(1);
        return out;
    }

    static byte[][] KeyGen(byte[] seed, Params params) {
        assert seed.length == 64;
        var z = Arrays.copyOfRange(seed, 32, 64);
        var ik = InnerKeyGen(Arrays.copyOf(seed, 32), params);
        var pk = ik[0];
        var sk2 = ik[1];
        var h = H(pk);
        return new byte[][]{pk, concat(sk2, pk, h, z)};
    }

    static byte[][] Enc(byte[] pk, byte[] seed, Params params) {
        assert seed.length == 32;
        var g = G(concat(seed, H(pk)));
        var K = g[0];
        var r = g[1];
        var ct = InnerEnc(pk, seed, r, params);
        return new byte[][]{ct, K};
    }

    static byte[] Dec(byte[] sk, byte[] ct, Params params) {
        var sk2 = Arrays.copyOfRange(sk, 0, 12 * params.k * n / 8);
        var pk = Arrays.copyOfRange(sk, 12 * params.k * n / 8, 24 * params.k * n / 8 + 32);
        var h = Arrays.copyOfRange(sk, 24 * params.k * n / 8 + 32, 24 * params.k * n / 8 + 64);
        var z = Arrays.copyOfRange(sk, 24 * params.k * n / 8 + 64, 24 * params.k * n / 8 + 96);
        var m2 = InnerDec(sk, ct, params);
        var g = G(concat(m2, h));
        var K2 = g[0];
        var r2 = g[1];
        var ct2 = InnerEnc(pk, m2, r2, params);
        return constantTimeSelectOnEquality(
                ct2, ct, K2, PRF2(z, ct));
    }

    public static void main(String[] args) {
        fromZero();
        intemediateV();
    }

    static void intemediateV() {
        // Data from https://csrc.nist.gov/csrc/media/Projects/post-quantum-cryptography/documents/example-files/PQC%20Intermediate%20Values.zip,
        // which is linked from https://csrc.nist.gov/Projects/post-quantum-cryptography/post-quantum-cryptography-standardization/example-files
        var seed = concat(xeh("CD119AFDC8559442424A87C13EA101E29FCA11881869077E4092E751BEDCA8BC" + "CD119AFDC8559442424A87C13EA101E29FCA11881869077E4092E751BEDCA8BC"));
        KeyGen(seed, params512);
        var pk = xeh("A5409718CB72F2438A3555A3C8F18F2671A1F81403DF7B5A4659A51F50827BA6577AA70800D78D8BC5AA86B89E08B58F3480A89E104DC6922EDBC12D06F891027C654E994A22F91A2AF63404CA98D7B67EEA25911B24C70DEB8146A0821F34A302551F2D510C0588C8BCA74EB4DC0CFA4603C1C5A3C5537061789068682C4CC3143FBA9BB5542F9778BDF23B3652F2A7524756FA73909DDAC7E532522659218CBA25F33B6B0458CB03DA7935BA59111955312B15CCE2C0F73466A8006283A2AA7CBB61022ABBC2D19F2920BC302472DC97C4A1788C9BD3BBEDC9122B827B279C074C80443141119F4B1629F62F10D4CE2BE3BB343816CAD16A1C87582F2B70E26635B08BB390C13398FCCDA7E9BB3D9B0B7803750C955C57A028A5D26C270316BB2B815C3B972BA6782DAB02F306821E61285BB072BF79781CABC386142A50C7AAAE66A947585BB0D8288DBCAF4B3B85BB7926987BAF7643AAB5FB02210580A0264352E69C6098989CFB87483395960A3A4F31BEFDA80B5F286ECFDAA555D4390AF6B55D313920929093449CD6729D00218E2D86570ADC0C4F6545FFB5632EFB3AAE2625A6982670FACE8D16126FA607E6D0A1FF616A46ECA642CC6AAC554DBBC43DFCF57F364C190CEA5776C1CEB58B7007505FD79C5F005A4BA218CF0693B058B510A4CA204324602F59BB8F2281C4D7B0BC8625E7881650F57C89E32CF4809144775C9073B673E39412A27C914321CCB6A7CF7C37C5BCBE7CA51BE0C928466A458EB778D6466A892A0ACBC09638784A27739C970CA58BC2595AD6BFA4E52EB438AC97C41623802248E110B074838F31A6E7503737704E7AE4AD91299572A8C13603500F3609B625B4E24CAE332B0D7A5BB47A038512A081BC27CDF0F2923CD3479F5307020B77F149584564060E5083CED55312B6A6A465A82B4577D63A4B49C80B07A9367E39778AF76FA8EC2CF528722856CE7813401A8383BDB7151B9B6D2DD6BFF55401D28AC612818C88C9287347B098A966EB9C0A2DB71F0A75555E1757D3AC4E3D802C8DC6A261521255186ABB98C2480301B8C6B31228B54461BC44EA3C2CF94B86C7A5B82C55167A7606CA9DC8253B7604E44A07F3ED55CD5B5E");
        var seed2 = xeh("109A248FE8052F84271FF57BAC156B1BA6A509CDCDBCC96CCDB1CCB85CA49315");
        var env = Enc(pk, seed2, params512);
        assert Arrays.equals(env[0], xeh("597A06DEB88172BA8D7CDE8D82CAA234B8112AF8A72F1AB4CEA1EFCB2D868D53D212E303B70E7E521AB0F4B5DB4F51159248BFB275361BEF883752C78B8D4712275385536A4B0A96E3C23EA6C17EA92B602616E5821E5753A4736C4039C20C923CCECB579805587C0CE72218BB1AB12452F8E154CB8643328142F9B340A641C6F295E5ECF2E048BC7FC79BC5B94277C868D8E536B50425809DCFA024A3905CBA550AD3BB52B459AC38FABC9BC00EBA03EC0906725B4FE4E976F174320047B31D15891365BA482388F0FB973B85224FB00BA865AFAB3C9A1B7D489F7B982D0BD470EF948ECB5B3920AF89035960123B1F8630D763681BFD671567EFBB1E6276AA4FB2DFA9C3948DB7F083F28383B77BC514AF9D68D22E2487C20163C02B0BBF23BBCE0650F84FF8CE02C74E9E11D6F30EC5FA8A012ADC3B89627C7DE855C1FBBEB5DCDE84D05E36C5566E5551B58750A411642639B27864F7E005978FFE256B757D13DA663FC3BB0794A27CF7585D12F22D953B285459FDC9BCDFCDCCB7BF3E4E362D2891D583855F5D9487E6FB217E2E45EE0BD9AFC289F4D564581209A3ACA31795A124BD1BBAEA846755C8EA7810EAA73060E86FB5FDF3FBE72F806BB1BFBFBAC0C7B16BFE74250277ECF5F541571B8A975050917FDF781FEA17B585E3C6DBFE77B1E48A16504C3A38901156100CAFEC2ED939AE9A9EDFC9C0F8C7F55CC93E5DDD0B3DE1C6EDAE2B7EE34C6101F011B5904F693D286356B54C86CE8BCFEA9DBFEC21C1EF0ECC9105005BAA377D829DCA2CBF5EA5F31B71D446B833E00619819D7FC6024052499757A2765F19CD2B36C2488599DC5247494FABE81EEBEFD3BE75C4780E43A50418C5DB2FF359C5A6DE286EF5951E2709486EDC9CC49D0724ECA3F2C0B75F8A36CE862388F00B3C593D1C8C6AC45D73A72FF6B4F805B131ED4EAF5601D7B73B0E3724E75D58DD50F5871C54A37C1481331759F4BE86FB58A2EE003130F66E187C8BA5015BE713296589ACAFBF6596897E03D4920C91F26333B7BF1798AF815C93D4DF55BD47A08249BF113063FBB39503E9B6D43EAC7B0C305A"));
        var dk = xeh("174313EFA93520E28A7076C888096E02B0BDD86830497B61FDEAB6209C6CF71C625C4680775C3477581C427A6FE1B0356EAB048BCA434F83B542C8B860010696A57299BB262268891FFC72142CA1A866185CA82D05406695BA57D4C930F9C17D6223523CF5A4F2A433A364459AC0ACDE7254481329288B1BE187CC25219F48C2443C532199859355320D04F0B80DE969F169A3D2BA3411B4ADBC01B66271824CD9543C78BA4804AE81F3AF00336C5CC3698354C0E01873A2A17D6A95A312689A99DC89084150A8D52BB31C3FF3D4215FA3C4111B401992866E513E5128A20ED95FDEE61485DC937E099D76F79B92734DC4CBB9A7A413FEA6285BC0C27C961E47D1983644C4BF913D72F4B030D34738427263E87AB4C0B7DF0B72CA8AA0BAA67B079939D587801D60C87A20405E5C52603C072FDB63E2E1C2A95CC26F5ABEF6088333800886D093CA01A76F57005E053569542E0A076B98736D4D39B00FC1653FBC2D12EA32A94B9B92C68BA4B68A4E7B370A23B03FE8221639B01244806C27067A58031DB80D2D03661A017BB46BB3711ACB568A4FABEBAFC5FA06F7CA0E4D962E3170CB11C0A8D18A09CE27A6A9763E123885450224DE07CC17546C17951FDE476E083583EF10BF76A98AFFF9B12DB5401CD3673495392D741291C3AA78420C8A7CB5FFE65012997C4DA4322EA90B5014B5B4D0180100247047341E4C24B96B8D7C0020524B7C1D66C3E08CB299EB4EC6FA0EE8EA05FD430F57605E892B232D2047CA9B4ECAD9BDD09C9951196916525D1EC921B6E3CE0EE692EBA728B4DB10F3381FBF584ABB7B6A9210C7C424CE4A369370CB48D608634ABA0BFF91C5620A1189D0CA97421D423429FB663952DC1231B4362B7162FE3A42111C91D76A964CB4154194209EDBAA1F481BD126C325D15678E39BCCE4C704EA487246648A6C6C2540B5F680A35EE2824246450A7293F21A90CFD14EFAF78FA3D7322251C641A50E95BB5EC5CA0B60E89D7C18B7A44A0FAFB4BCADE9B588D1B7FCF12BA1E1084D56B197EA90A79A3D83927A2307603BC211C0830CB7062C04254824575B226CAD9A27C2A45519AE39546467690485498A320AD56993B15A9D22C6191446CB40AA7547401681DCC7E36596B10C07FA2A20B43C4B0124401F8A0E744878C7296623C7395B6994D18C4787A289DBB05CB1827451D83F072904537594F515CA1017991620A33E096EE0DC091AE4CA960603B101B5B4E23E9A5B65E1F6C2A8CC89341383B706725ED5B3485769181B8F76439C05636A0C3436FFBA8B86A5306FA111F6FC71EB779B25707CFAE0A6DA7B0AD5D94B10F21E4FCA92893B9FFE73210763401377837A10CA9625346C42ADC705BD92DB3426D926CE4B5EC24A5CDF27CB91E5A7E7164D1BDC99D75679FBC93A58F647DAC1086CE931BC089233E9487E0867BC58472B01BF2895C323B64DBE4A17A9E841B053CADB5C76D035724C321BBC13666F0A35DFDA0721E8987623256A994D95FA1C05F57C1E15A30C4A0C8318A0D83C410C362862E817DD6ABBAA4BBE75B736CCCBB4AF2A188402BD4CE597932008862865332562F324C7A424151FB59D0AE1821F2864C7E698127AAD92C33B313988C29A09E260449BCA7BEE360862314E47519EF3918DDDE403E7B92AC9908F93C6369CC5C47B8CB1DC3A3479C762F62A18FE05A9B0645A5311A01828723AEB51FA505E96B29E3D2B6E5B1327DE3A61AB0C50BE0124B64B33314B32D6122510E46445857AA0E2C4B0D256955620A8681D1E555126D00509E35BF59683DDAA40E82C519B855852C366CB54452BF910B001692330345708653F511800B10E009D9F7D10A53B8B30BF13B06F254EC8A6BA539700F6358DE0463A019540C9873F3F4680E2113A7CCC55FF754D85AA67E9E55F887424E0B2625682A5DDA218F03C3C10A246CDB0CC91D19D8F024DB9B1415F50ACD8F65DE2787B9103C575B687765572CFFA59026C2BCEE77423BCAFD3054BF8E2713FB85B0BF6A46E716152F5C9A3011EC90114C76B01516799BD5911415B704544077F188806755EEC4131E55556DB903F4284C1F90086FF431B68F51F629812F320B55F219D72A1928F38C9A1EC823BA198BA9ABBACF62902B3CA0AFC95EA8AC303FB8BDD29BB9D18A03BA44E58B1B0B85A2A1662E6A31DA7545511A478A18177889061EF76631264239ADEBD04A8C52B72E2B1F3A2DFBBD8C054E70CC2A742E7B7D417DFED314422187DE1B2954481195755EC04BB7671C4331446BBE8952514905321A2176E935B5420C0D5EA4465");
        var ct = xeh("84A188A072E4D4F449A4BE170274DD2A5F3E356E95B96E40AD3FF1455E36C6A71E909DD2C0DFF8AD2C9F503BAC9065716248083BDA40CECB38E3B3058BAF51A7572384FF8406A8136A4FC6D912A54B2EB5B9D598FB689E72ED3DEFD2FF8355ED9E9CCA53E82C0886E094C592C392311F04FEC68F9A1C531CF3419030892B5BDCACEEF6A0E7F1BD44903F49DE8E37B02BA3FC5121D99F8CC3040F66832F77021B4CA35F7A4825038936564CA2E673FF9CC0519C25F6A52D87EDD965B2464AA365D2BF068B72FC68B65E88515E2C832BBDB27D61BF512B5FC2D8590FB35F49500CAFE70E7D0776B5C4E4503A7189ADBAFF5D5B515CC68B2F81D993C6D7FA7D3D1D90EBFF51DA3FBBB4430E5BBEDBCA8DA078DCE8EC815B168BFC09AB4A20678870F4868B1FAE28D209C75368A799317DFA08C2B651FAC72DCA2A1B4CBB75E873F15C51B6D0B5E6F5E60E2AF6C40D2CABCBF3588F44BCEA6D72D359F40F9CF5E0EC40A5215E5ACEEAF0DA00D923D4CEFF5C3A3AB1E46C754F4AE052C2BC49FDB4521AE44DF634D56E433DAD3DF3C07115406FF8BFD0D7C93B4941D0F09213C1681CFD5C8663DF02041A3CBD162F5C4D80CB1DC7D4A501AD06FE96EB348B6E331C8296FE904EB97C087456328D703B85BDAC2FB43C728D0B05FC54B8C155C010EF0DB14CC668D1B1BC727AF8864076736B898BABA1C81DCA2053F58587D3C4E33C694A264BE2897E7D2EEFADDA9FF88D70BF3731F1228CB3E131EB0CB76FDBD2CCB1CBC18D1450AC7A16349E7129CAB720D5CB70B56E855E8305DCDA730BBD0EA33EF0815D02190BB98E30F73BF7789CDD673C613B0C57CB2EF32E670A98D2D630670773C59D8A6A2CFCFF1C7CA1BB55C17A32CB65A2EA19C7B8E295C6898CF32FEE1DEB01472BE76C3A78CB242EDFE21D961FCB85C3CF6CEE218986C1BD932BF97BC6DECAABF8C62940C0A58E87C6EDDCD74B7F715D8C22520546239F3AAA10A435820103B4E3295311D992C9C8771A3CE849868F36F31214F9639C028F4A5F4945F2BEC9585077BF2F637D2549F8348C00ECBF19C470DF255EFF6232813429F853");
        var k2 = Dec(dk, ct, params512);
        assert Arrays.equals(k2, xeh("224b9c051213ef46549243796532282973fa7cf97e8913c339c1940ac17e05e0"));
        // Too bad the KeyGen still uses an ols version with no k in hash input.
    }

    static void fromZero() {
        assert test(params512).equals("4ad53a06b29f12568421a552c08195b58673c82f870cc1ccd65a08e4325feb27");
        assert test(params768).equals("b4d29cd55bab43e16554b74b9098cdfce583996c968bcd2cfd1ad9455e351fbf");
        assert test(params1024).equals("760a9793cd6c81c3cdeb8c679ae7f5741caaa97452898345fc081fef29069885");
    }

    static String test(Params p) {
        var g = KeyGen(r(64), p);
        var enc = Enc(g[0], r(32), p);
        var k2 = Dec(g[1], enc[0], p);
        assert Arrays.equals(enc[1], k2);
        System.out.println("Success");
        return hex(k2);
    }

    static Random r = new Random();
    static byte[] r(int n) {
        var out = new byte[n];
//        r.nextBytes(out);
        return out;
    }

    static String hex(byte[] in) {
        return HexFormat.of().formatHex(in);
    }

    static byte[] xeh(String in) {
        return HexFormat.of().parseHex(in);
    }
}
