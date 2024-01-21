/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8297878
 * @summary RSA_KEM example
 * @modules java.base/sun.security.jca
 *          java.base/sun.security.rsa
 *          java.base/sun.security.util
 *          java.base/javax.crypto:+open
 */
import sun.security.jca.JCAUtil;
import sun.security.rsa.RSACore;
import sun.security.util.*;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// This test implements RSA-KEM as described in RFC 5990. In this KEM, the
// sender configures the encapsulator with an RSAKEMParameterSpec object.
// This object is encoded as a byte array and included in the Encapsulated
// output. The receiver is then able to recover the same RSAKEMParameterSpec
// object from the encoding using an AlgorithmParameters implementation
// and use the object to configure the decapsulator.
public class RSA_KEM {
    public static void main(String[] args) throws Exception {
        Provider p = new ProviderImpl();
        RSAKEMParameterSpec[] kspecs = new RSAKEMParameterSpec[] {
                RSAKEMParameterSpec.kdf1("SHA-256", "AES_128/KW/NoPadding"),
                RSAKEMParameterSpec.kdf1("SHA-512", "AES_256/KW/NoPadding"),
                RSAKEMParameterSpec.kdf2("SHA-256", "AES_128/KW/NoPadding"),
                RSAKEMParameterSpec.kdf2("SHA-512", "AES_256/KW/NoPadding"),
                RSAKEMParameterSpec.kdf3("SHA-256", new byte[10], "AES_128/KW/NoPadding"),
                RSAKEMParameterSpec.kdf3("SHA-256", new byte[0], "AES_128/KW/NoPadding"),
                RSAKEMParameterSpec.kdf3("SHA-512", new byte[0], "AES_128/KW/NoPadding"),
        };
        for (RSAKEMParameterSpec kspec : kspecs) {
            System.err.println("---------");
            System.err.println(kspec);
            AlgorithmParameters d = AlgorithmParameters.getInstance("RSA-KEM", p);
            d.init(kspec);
            AlgorithmParameters s = AlgorithmParameters.getInstance("RSA-KEM", p);
            s.init(d.getEncoded());
            AlgorithmParameterSpec spec = s.getParameterSpec(AlgorithmParameterSpec.class);
            if (!spec.toString().equals(kspec.toString())) {
                throw new RuntimeException(spec.toString());
            }
        }
        byte[] msg = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] iv = new byte[16];
        for (int size : List.of(1024, 2048)) {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(size);
            KeyPair kp = g.generateKeyPair();
            for (RSAKEMParameterSpec kspec : kspecs) {
                SecretKey cek = KeyGenerator.getInstance("AES").generateKey();
                KEM kem1 = getKemImpl(p);
                Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
                c.init(Cipher.ENCRYPT_MODE, cek, new IvParameterSpec(iv));
                byte[] ciphertext = c.doFinal(msg);

                KEM.Encapsulator e = kem1.newEncapsulator(kp.getPublic(), kspec, null);
                KEM.Encapsulated enc = e.encapsulate(0, e.secretSize(), "AES");
                Cipher c2 = Cipher.getInstance(kspec.encAlg);
                c2.init(Cipher.WRAP_MODE, enc.key());
                byte[] ek = c2.wrap(cek);

                AlgorithmParameters a = AlgorithmParameters.getInstance("RSA-KEM", p);
                a.init(enc.params());
                KEM kem2 = getKemImpl(p);
                KEM.Decapsulator d = kem2.newDecapsulator(kp.getPrivate(), a.getParameterSpec(AlgorithmParameterSpec.class));
                SecretKey k = d.decapsulate(enc.encapsulation(), 0, d.secretSize(), "AES");
                Cipher c3 = Cipher.getInstance(kspec.encAlg);
                c3.init(Cipher.UNWRAP_MODE, k);
                cek = (SecretKey) c3.unwrap(ek, "AES", Cipher.SECRET_KEY);
                Cipher c4 = Cipher.getInstance("AES/CBC/PKCS5Padding");
                c4.init(Cipher.DECRYPT_MODE, cek, new IvParameterSpec(iv));
                byte[] cleartext = c4.doFinal(ciphertext);

                if (!Arrays.equals(cleartext, msg)) {
                    throw new RuntimeException();
                }
                System.out.printf("%4d %20s - %11d %11d %11d %11d %s\n",
                        size, kspec,
                        e.secretSize(), e.encapsulationSize(),
                        d.secretSize(), d.encapsulationSize(), k.getAlgorithm());
            }
        }
    }

    // To bypass the JCE security provider signature check
    private static KEM getKemImpl(Provider p) throws Exception {
        var ctor = KEM.class.getDeclaredConstructor(
                String.class, KEMSpi.class, Provider.class);
        ctor.setAccessible(true);
        return ctor.newInstance("RSA-KEM", new KEMImpl(), p);
    }

    static final String RSA_KEM = "1.2.840.113549.1.9.16.3.14";
    static final String KEM_RSA = "1.0.18033.2.2.4";

    public static class ProviderImpl extends Provider {
        public ProviderImpl() {
            super("MYKEM", "1", "RSA-KEM");
            List<String> alias = List.of(RSA_KEM, "OID." + RSA_KEM);
            Map<String, String> attrs = Map.of(
                    "SupportedKeyClasses", "java.security.interfaces.RSAKey");
            putService(new Service(this, "KEM", "RSA-KEM",
                    "RSA_KEM$KEMImpl", alias, attrs));
            putService(new Service(this, "AlgorithmParameters", "RSA-KEM",
                    "RSA_KEM$AlgorithmParametersImpl", alias, attrs));
        }
    }

    public static class AlgorithmParametersImpl extends AlgorithmParametersSpi {
        RSAKEMParameterSpec spec;
        @Override
        protected void engineInit(AlgorithmParameterSpec paramSpec)
                throws InvalidParameterSpecException {
            if (paramSpec instanceof RSAKEMParameterSpec rspec) {
                spec = rspec;
            } else {
                throw new InvalidParameterSpecException();
            }
        }

        @Override
        protected void engineInit(byte[] params) throws IOException {
            spec = decode(params);
        }

        @Override
        protected void engineInit(byte[] params, String format) throws IOException {
            spec = decode(params);
        }

        @Override
        protected <T extends AlgorithmParameterSpec> T engineGetParameterSpec(
                Class<T> paramSpec) throws InvalidParameterSpecException {
            if (paramSpec.isAssignableFrom(RSAKEMParameterSpec.class)) {
                return paramSpec.cast(spec);
            } else {
                throw new InvalidParameterSpecException();
            }
        }

        @Override
        protected byte[] engineGetEncoded() {
            return encode(spec);
        }

        @Override
        protected byte[] engineGetEncoded(String format) {
            return encode(spec);
        }

        @Override
        protected String engineToString() {
            return spec == null ? "<null>" : spec.toString();
        }

        static final ObjectIdentifier id_rsa_kem;
        static final ObjectIdentifier id_kem_rsa;
        static final ObjectIdentifier id_kdf1;
        static final ObjectIdentifier id_kdf2;
        static final ObjectIdentifier id_kdf3;

        static {
            try {
                id_rsa_kem = ObjectIdentifier.of("1.2.840.113549.1.9.16.3.14");
                id_kem_rsa = ObjectIdentifier.of("1.0.18033.2.2.4");
                id_kdf1 = ObjectIdentifier.of("1.3.133.16.840.9.44.1.0"); // fake
                id_kdf2 = ObjectIdentifier.of("1.3.133.16.840.9.44.1.1");
                id_kdf3 = ObjectIdentifier.of("1.3.133.16.840.9.44.1.2");
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }

        static byte[] encode(RSAKEMParameterSpec spec) {
            DerOutputStream kdf = new DerOutputStream()
                    .write(DerValue.tag_Sequence, new DerOutputStream()
                        .putOID(oid4(spec.kdfAlg))
                        .write(DerValue.tag_Sequence, new DerOutputStream()
                            .putOID(oid4(spec.hashAlg))))
                    .putInteger(spec.kdfLen());
            // The next line is not in RFC 5990
            if (spec.fixedInfo != null) {
                kdf.putOctetString(spec.fixedInfo);
            }
            return new DerOutputStream()
                    .write(DerValue.tag_Sequence, new DerOutputStream()
                            .write(DerValue.tag_Sequence, new DerOutputStream()
                                    .putOID(id_kem_rsa)
                                    .write(DerValue.tag_Sequence, kdf))
                            .write(DerValue.tag_Sequence, new DerOutputStream()
                                    .putOID(oid4(spec.encAlg)))).toByteArray();
        }

        static RSAKEMParameterSpec decode(byte[] der) throws IOException {
            String kdfAlg, encAlg, hashAlg;
            int kdfLen;
            byte[] fixedInfo;
            DerInputStream d2 = new DerValue(der).toDerInputStream();
            DerInputStream d3 = d2.getDerValue().toDerInputStream();
            if (!d3.getOID().equals(id_kem_rsa)) {
                throw new IOException("not id_kem_rsa");
            }
            DerInputStream d4 = d3.getDerValue().toDerInputStream();
            DerInputStream d5 = d4.getDerValue().toDerInputStream();
            kdfLen = d4.getInteger();
            fixedInfo = d4.available() > 0 ? d4.getOctetString() : null;
            d4.atEnd();
            ObjectIdentifier kdfOid = d5.getOID();
            if (kdfOid.equals(id_kdf1)) {
                kdfAlg = "kdf1";
            } else if (kdfOid.equals(id_kdf2)) {
                kdfAlg = "kdf2";
            } else if (kdfOid.equals(id_kdf3)) {
                kdfAlg = "kdf3";
            } else {
                throw new IOException("unknown kdf");
            }
            DerInputStream d6 = d5.getDerValue().toDerInputStream();
            String hashOID = d6.getOID().toString();
            KnownOIDs k = KnownOIDs.findMatch(hashOID);
            hashAlg = k == null ? hashOID : k.stdName();
            d6.atEnd();
            d5.atEnd();

            d3.atEnd();
            DerInputStream d7 = d2.getDerValue().toDerInputStream();
            String encOID = d7.getOID().toString();
            KnownOIDs e = KnownOIDs.findMatch(encOID);
            encAlg = e == null ? encOID : e.stdName();
            d7.atEnd();
            d2.atEnd();
            if (kdfLen != RSAKEMParameterSpec.kdfLen(encAlg)) {
                throw new IOException("kdfLen does not match encAlg");
            }
            return new RSAKEMParameterSpec(kdfAlg, hashAlg, fixedInfo, encAlg);
        }

        static ObjectIdentifier oid4(String s) {
            return switch (s) {
                case "kdf1" -> id_kdf1;
                case "kdf2" -> id_kdf2;
                case "kdf3" -> id_kdf3;
                default -> {
                    KnownOIDs k = KnownOIDs.findMatch(s);
                    if (k == null) throw new UnsupportedOperationException();
                    yield ObjectIdentifier.of(k);
                }
            };
        }
    }

    public static class RSAKEMParameterSpec implements AlgorithmParameterSpec {
        private final String kdfAlg;
        private final String hashAlg;
        private final byte[] fixedInfo;
        private final String encAlg;

        private RSAKEMParameterSpec(String kdfAlg, String hashAlg, byte[] fixedInfo, String encAlg) {
            this.hashAlg = hashAlg;
            this.kdfAlg = kdfAlg;
            this.fixedInfo = fixedInfo == null ? null : fixedInfo.clone();
            this.encAlg = encAlg;
        }

        public static RSAKEMParameterSpec kdf1(String hashAlg, String encAlg) {
            return new RSAKEMParameterSpec("kdf1", hashAlg, null, encAlg);
        }
        public static RSAKEMParameterSpec kdf2(String hashAlg, String encAlg) {
            return new RSAKEMParameterSpec("kdf2", hashAlg, null, encAlg);
        }
        public static RSAKEMParameterSpec kdf3(String hashAlg, byte[] fixedInfo, String encAlg) {
            return new RSAKEMParameterSpec("kdf3", hashAlg, fixedInfo, encAlg);
        }

        public int kdfLen() {
            return RSAKEMParameterSpec.kdfLen(encAlg);
        }

        public static int kdfLen(String encAlg) {
            return Integer.parseInt(encAlg, 4, 7, 10) / 8;
        }

        public String hashAlgorithm() {
            return hashAlg;
        }
        public String kdfAlgorithm() {
            return kdfAlg;
        }
        public byte[] fixedInfo() {
            return fixedInfo == null ? null : fixedInfo.clone();
        }

        public String getEncAlg() {
            return encAlg;
        }

        @Override
        public String toString() {
            return String.format("[%s,%s,%s]", kdfAlg, hashAlg, encAlg);
        }
    }

    public static class KEMImpl implements KEMSpi {

        @Override
        public KEMSpi.EncapsulatorSpi engineNewEncapsulator(
                PublicKey pk, AlgorithmParameterSpec spec, SecureRandom secureRandom)
                throws InvalidAlgorithmParameterException, InvalidKeyException {
            if (!(pk instanceof RSAPublicKey rpk)) {
                throw new InvalidKeyException("Not an RSA key");
            }
            return Handler.newEncapsulator(spec, rpk, secureRandom);
        }

        @Override
        public KEMSpi.DecapsulatorSpi engineNewDecapsulator(
                PrivateKey sk, AlgorithmParameterSpec spec)
                throws InvalidAlgorithmParameterException, InvalidKeyException {
            if (!(sk instanceof RSAPrivateCrtKey rsk)) {
                throw new InvalidKeyException("Not an RSA key");
            }
            return Handler.newDecapsulator(spec, rsk);
        }

        static class Handler implements KEMSpi.EncapsulatorSpi, KEMSpi.DecapsulatorSpi {

            private final RSAPublicKey rpk; // not null for encapsulator
            private final RSAPrivateKey rsk; // not null for decapsulator
            private final RSAKEMParameterSpec kspec; // not null
            private final SecureRandom sr; // not null for encapsulator

            Handler(AlgorithmParameterSpec spec, RSAPublicKey rpk, RSAPrivateCrtKey rsk, SecureRandom sr)
                    throws InvalidAlgorithmParameterException {
                this.rpk = rpk;
                this.rsk = rsk;
                this.sr = sr;
                if (spec != null) {
                    if (spec instanceof RSAKEMParameterSpec rs) {
                        this.kspec = rs;
                    } else {
                        throw new InvalidAlgorithmParameterException();
                    }
                } else {
                    this.kspec = RSAKEMParameterSpec
                            .kdf2("SHA-256", "AES_256/KW/NoPadding");
                }
            }

            static Handler newEncapsulator(AlgorithmParameterSpec spec, RSAPublicKey rpk, SecureRandom sr)
                    throws InvalidAlgorithmParameterException {
                if (sr == null) {
                    sr = JCAUtil.getDefSecureRandom();
                }
                return new Handler(spec, rpk, null, sr);
            }

            static Handler newDecapsulator(AlgorithmParameterSpec spec, RSAPrivateCrtKey rsk)
                    throws InvalidAlgorithmParameterException {
                return new Handler(spec, null, rsk, null);
            }

            @Override
            public SecretKey engineDecapsulate(byte[] encapsulation,
                    int from, int to, String algorithm)
                    throws DecapsulateException {
                Objects.checkFromToIndex(from, to, kspec.kdfLen());
                Objects.requireNonNull(algorithm, "null algorithm");
                Objects.requireNonNull(encapsulation, "null encapsulation");
                if (encapsulation.length != KeyUtil.getKeySize(rsk) / 8) {
                    throw new DecapsulateException("incorrect encapsulation size");
                }
                try {
                    byte[] Z = RSACore.rsa(encapsulation, rsk, false);
                    return new SecretKeySpec(kdf(Z), from, to - from, algorithm);
                } catch (BadPaddingException e) {
                    throw new DecapsulateException("cannot decrypt", e);
                }
            }

            @Override
            public KEM.Encapsulated engineEncapsulate(int from, int to, String algorithm) {
                Objects.checkFromToIndex(from, to, kspec.kdfLen());
                Objects.requireNonNull(algorithm, "null algorithm");
                int nLen = rpk.getModulus().bitLength();
                int nSize = (nLen + 7) / 8;
                BigInteger z;
                int tried = 0;
                while (true) {
                    z = new BigInteger(nLen, sr);
                    if (z.compareTo(rpk.getModulus()) < 0) {
                        break;
                    }
                    if (tried++ > 20) {
                        throw new ProviderException("Cannot get good random number");
                    }
                }
                byte[] Z = z.toByteArray();
                if (Z.length > nSize) {
                    Z = Arrays.copyOfRange(Z, Z.length - nSize, Z.length);
                } else if (Z.length < nSize) {
                    byte[] tmp = new byte[nSize];
                    System.arraycopy(Z, 0, tmp, nSize - Z.length, Z.length);
                    Z = tmp;
                }
                byte[] c;
                try {
                    c = RSACore.rsa(Z, rpk);
                } catch (BadPaddingException e) {
                    throw new AssertionError(e);
                }
                return new KEM.Encapsulated(
                        new SecretKeySpec(kdf(Z), from, to - from, algorithm),
                        c, AlgorithmParametersImpl.encode(kspec));
            }

            byte[] kdf(byte[] input) {
                String hashAlg = kspec.hashAlgorithm();
                MessageDigest md;
                try {
                    md = MessageDigest.getInstance(hashAlg);
                } catch (NoSuchAlgorithmException e) {
                    throw new ProviderException(e);
                }
                String kdfAlg = kspec.kdfAlgorithm();
                byte[] fixedInput = kspec.fixedInfo();
                int length = kspec.kdfLen();

                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                int n = kdfAlg.equals("kdf1") ? 0 : 1;
                while (true) {
                    switch (kdfAlg) {
                        case "kdf1", "kdf2" -> {
                            md.update(input);
                            md.update(u32str(n));
                        }
                        case "kdf3" -> {
                            md.update(u32str(n));
                            md.update(input);
                            md.update(fixedInput);
                        }
                        default -> throw new ProviderException();
                    }
                    bout.writeBytes(md.digest());
                    if (bout.size() > length) break;
                    n++;
                }
                byte[] result = bout.toByteArray();
                return result.length == length
                        ? result
                        : Arrays.copyOf(result, length);
            }

            @Override
            public int engineSecretSize() {
                return kspec.kdfLen();
            }

            @Override
            public int engineEncapsulationSize() {
                return KeyUtil.getKeySize(rsk == null ? rpk : rsk) / 8;
            }
        }
    }

    static byte[] u32str(int i) {
        return new byte[] {
                (byte)(i >> 24), (byte)(i >> 16), (byte)(i >> 8), (byte)i };
    }
}
