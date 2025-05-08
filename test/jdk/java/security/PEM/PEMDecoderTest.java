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

/*
 * @test
 * @bug 8298420
 * @modules java.base/sun.security.pkcs
 *          java.base/sun.security.util
 * @summary Testing basic PEM API decoding
 * @enablePreview
 */

import javax.crypto.EncryptedPrivateKeyInfo;
import java.io.*;
import java.lang.Class;
import java.security.*;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.security.interfaces.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.Arrays;

import sun.security.util.Pem;

public class PEMDecoderTest {

    static HexFormat hex = HexFormat.of();

    public static void main(String[] args) throws IOException {
        System.out.println("Decoder test:");
        PEMData.entryList.forEach(PEMDecoderTest::test);
        System.out.println("Decoder test returning DEREncodable class:");
        PEMData.entryList.forEach(entry -> test(entry, DEREncodable.class));
        System.out.println("Decoder test with encrypted PEM:");
        PEMData.encryptedList.forEach(PEMDecoderTest::testEncrypted);
        System.out.println("Decoder test with OAS:");
        testTwoKeys();
        System.out.println("Decoder test RSA PEM setting RSAKey.class returned:");
        test(PEMData.rsapriv, RSAKey.class);
        System.out.println("Decoder test failures:");
        PEMData.failureEntryList.forEach(PEMDecoderTest::testFailure);
        System.out.println("Decoder test ecsecp256 PEM asking for ECPublicKey.class returned:");
        testFailure(PEMData.ecsecp256, ECPublicKey.class);
        System.out.println("Decoder test rsapriv PEM setting P8EKS.class returned:");
        testClass(PEMData.rsapriv, RSAPrivateKey.class);
        System.out.println("Decoder test rsaOpenSSL P1 PEM asking for RSAPublicKey.class returned:");
        testFailure(PEMData.rsaOpenSSL, RSAPublicKey.class);
        System.out.println("Decoder test rsapub PEM asking X509EKS.class returned:");
        testClass(PEMData.rsapub, X509EncodedKeySpec.class, true);
        System.out.println("Decoder test rsapriv PEM asking X509EKS.class returned:");
        testClass(PEMData.rsapriv, X509EncodedKeySpec.class, false);
        System.out.println("Decoder test RSAcert PEM asking X509EKS.class returned:");
        testClass(PEMData.rsaCert, X509EncodedKeySpec.class, false);
        System.out.println("Decoder test OAS RFC PEM asking PrivateKey.class returned:");
        testClass(PEMData.oasrfc8410, PrivateKey.class, true);
        testClass(PEMData.oasrfc8410, PublicKey.class, true);
        System.out.println("Decoder test ecsecp256:");
        testFailure(PEMData.ecsecp256pub.makeNoCRLF("pubecpem-no"));
        System.out.println("Decoder test RSAcert with decryption Decoder:");
        PEMDecoder d = PEMDecoder.of().withDecryption("123".toCharArray());
        d.decode(PEMData.rsaCert.pem());
        System.out.println("Decoder test ecsecp256 with decryption Decoder:");
        PrivateKey pkey = ((KeyPair) d.decode(PEMData.ecsecp256.pem())).getPrivate();
        System.out.println("Decoder test ecsecp256 to P8EKS:");
        PKCS8EncodedKeySpec p8 = d.decode(PEMData.ecsecp256.pem(),
            PKCS8EncodedKeySpec.class);

        System.out.println("Checking if decode() returns the same encoding:");
        PEMData.privList.forEach(PEMDecoderTest::testDERCheck);
        PEMData.oasList.forEach(PEMDecoderTest::testDERCheck);

        System.out.println("Check a Signature/Verify op is successful:");
        PEMData.privList.forEach(PEMDecoderTest::testSignature);
        PEMData.oasList.forEach(PEMDecoderTest::testSignature);


        // PEMRecord tests
        System.out.println("Checking if ecCSR:");
        test(PEMData.ecCSR);

        System.out.println("Checking if ecCSR with preData:");
        DEREncodable result = test(PEMData.ecCSRWithData);
        if (result instanceof PEMRecord rec) {
            if (PEMData.preData.compareTo(new String(rec.leadingData())) != 0) {
                System.err.println("expected: " + PEMData.preData);
                System.err.println("received: " + new String(rec.leadingData()));
                throw new AssertionError("ecCSRWithData preData wrong");
            }
            if (rec.pem().lastIndexOf("F") > rec.pem().length() - 5) {
                System.err.println("received: " + rec.pem());
                throw new AssertionError("ecCSRWithData: " +
                    "End of PEM data has an unexpected character");
            }
        } else {
            throw new AssertionError("ecCSRWithData didn't return a PEMRecord");
        }

        System.out.println("Decoding RSA pub using class PEMRecord:");
        result = PEMDecoder.of().decode(PEMData.rsapub.pem(), PEMRecord.class);
        if (!(result instanceof PEMRecord)) {
            throw new AssertionError("pubecpem didn't return a PEMRecord");
        }
        if (((PEMRecord) result).type().compareTo(Pem.PUBLIC_KEY) != 0) {
            throw new AssertionError("pubecpem PEMRecord didn't decode as a Public Key");
        }

        testInputStream();
        testPEMRecord(PEMData.rsapub);
        testPEMRecord(PEMData.ecCert);
        testPEMRecord(PEMData.ec25519priv);
        testPEMRecord(PEMData.ecCSR);
        testPEMRecord(PEMData.ecCSRWithData);
        testPEMRecordDecode(PEMData.rsapub);
        testPEMRecordDecode(PEMData.ecCert);
        testPEMRecordDecode(PEMData.ec25519priv);
        testPEMRecordDecode(PEMData.ecCSR);
        testPEMRecordDecode(PEMData.ecCSRWithData);
    }

    static void testInputStream() throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream(2048);
        OutputStreamWriter os = new OutputStreamWriter(ba);
        os.write(PEMData.preData);
        os.write(PEMData.rsapub.pem());
        os.write(PEMData.preData);
        os.write(PEMData.rsapub.pem());
        os.write(PEMData.postData);
        os.flush();
        ByteArrayInputStream is = new ByteArrayInputStream(ba.toByteArray());

        System.out.println("Decoding 2 RSA pub with pre & post data:");
        PEMRecord obj;
        int keys = 0;
        while (keys++ < 2) {
            obj = PEMDecoder.of().decode(is, PEMRecord.class);
            if (!PEMData.preData.equalsIgnoreCase(
                new String(obj.leadingData()))) {
                System.out.println("expected: \"" + PEMData.preData + "\"");
                System.out.println("returned: \"" +
                    new String(obj.leadingData()) + "\"");
                throw new AssertionError("Leading data incorrect");
            }
            System.out.println("  Read public key.");
        }
        obj = PEMDecoder.of().decode(is, PEMRecord.class);
        if (obj.pem() != null) {
            throw new AssertionError("3rd PEMRecord shouldn't have PEM data");
        }

        System.out.println("  Checking post data...");
        if (!PEMData.postData.equalsIgnoreCase(new String(obj.leadingData()))) {
            System.out.println("expected: \"" + PEMData.postData + "\"");
            System.out.println("returned: \"" + new String(obj.leadingData()) +
                "\"");
            throw new AssertionError("Post bytes incorrect");
        }

        // End of stream
        try {
            System.out.println("Failed: There should be no PEMRecord: " +
                PEMDecoder.of().decode(is, PEMRecord.class));
        } catch (EOFException e) {
            System.out.println("Success");
            return;
        } catch (Exception e) {
            throw new AssertionError("Caught unexpected exception " +
                "should have been IOE EOF.");
        }

        throw new AssertionError("Failed");
    }

    static void testPEMRecord(PEMData.Entry entry) {
        PEMRecord r = PEMDecoder.of().decode(entry.pem(), PEMRecord.class);
        String expected = entry.pem().split("-----")[2].replace(System.lineSeparator(), "");
        if (!r.pem().equalsIgnoreCase(expected)) {
            System.err.println("expected: " + expected);
            System.err.println("received: " + r.pem());
            throw new AssertionError("PEMRecord expected pem " +
                "does not match.");
        }

        boolean result = switch(r.type()) {
            case Pem.PRIVATE_KEY ->
                PrivateKey.class.isAssignableFrom(entry.clazz());
            case Pem.PUBLIC_KEY ->
                PublicKey.class.isAssignableFrom(entry.clazz());
            case Pem.CERTIFICATE, Pem.X509_CERTIFICATE ->
                entry.clazz().isAssignableFrom(X509Certificate.class);
            case Pem.X509_CRL ->
                entry.clazz().isAssignableFrom(X509CRL.class);
            case "CERTIFICATE REQUEST" ->
                entry.clazz().isAssignableFrom(PEMRecord.class);
            default -> false;
        };

        if (!result) {
            System.err.println("PEMRecord type is a " + r.type());
            System.err.println("Entry is a " + entry.clazz().getName());
            throw new AssertionError("PEMRecord class didn't match:" +
                entry.name());
        }
        System.out.println("Success (" + entry.name() + ")");
    }


    static void testPEMRecordDecode(PEMData.Entry entry) {
        PEMRecord r = PEMDecoder.of().decode(entry.pem(), PEMRecord.class);
        DEREncodable de = PEMDecoder.of().decode(r.toString());

        boolean result = switch(r.type()) {
            case Pem.PRIVATE_KEY ->
                PrivateKey.class.isAssignableFrom(de.getClass());
            case Pem.PUBLIC_KEY ->
                PublicKey.class.isAssignableFrom(de.getClass());
            case Pem.CERTIFICATE, Pem.X509_CERTIFICATE ->
                (de instanceof X509Certificate);
            case Pem.X509_CRL -> (de instanceof X509CRL);
            case "CERTIFICATE REQUEST" -> (de instanceof PEMRecord);
            default -> false;
        };

        if (!result) {
            System.err.println("Entry is a " + entry.clazz().getName());
            System.err.println("PEMRecord type is a " + r.type());
            System.err.println("Returned was a " + entry.clazz().getName());
            throw new AssertionError("PEMRecord class didn't match:" +
                entry.name());
        }
        System.out.println("Success (" + entry.name() + ")");
    }


    static void testFailure(PEMData.Entry entry) {
        testFailure(entry, entry.clazz());
    }

    static void testFailure(PEMData.Entry entry, Class c) {
        try {
            test(entry.pem(), c, PEMDecoder.of());
            throw new AssertionError("Failure with " +
                entry.name() + ":  Not supposed to succeed.");
        } catch (NullPointerException e) {
            System.out.println("PASS (" + entry.name() + "):  " + e.getClass() +
                ": " + e.getMessage());
        } catch (IOException | RuntimeException e) {
            System.out.println("PASS (" + entry.name() + "):  " + e.getClass() +
                ": " + e.getMessage());
        }
    }

    static DEREncodable testEncrypted(PEMData.Entry entry) {
        PEMDecoder decoder = PEMDecoder.of();
        if (!Objects.equals(entry.clazz(), EncryptedPrivateKeyInfo.class)) {
            decoder = decoder.withDecryption(entry.password());
        }

        try {
            return test(entry.pem(), entry.clazz(), decoder);
        } catch (Exception | AssertionError e) {
            throw new RuntimeException("Error with PEM (" + entry.name() +
                "):  " + e.getMessage(), e);
        }
    }

    // Change the Entry to use the given class as the expected class returned
    static DEREncodable test(PEMData.Entry entry, Class c) {
        return test(entry.newClass(c));
    }

    // Run test with a given Entry
    static DEREncodable test(PEMData.Entry entry) {
        try {
            DEREncodable r = test(entry.pem(), entry.clazz(), PEMDecoder.of());
            System.out.println("PASS (" + entry.name() + ")");
            return r;
        } catch (Exception | AssertionError e) {
            throw new RuntimeException("Error with PEM (" + entry.name() +
                "):  " + e.getMessage(), e);
        }
    }

    static List getInterfaceList(Class ccc) {
        Class<?>[] interfaces = ccc.getInterfaces();
        List<Class> list = new ArrayList<>(Arrays.asList(interfaces));
        var x = ccc.getSuperclass();
        if (x != null) {
            list.add(x);
        }
        List<Class> results = new ArrayList<>(list);
        if (list.size() > 0) {
            for (Class cname : list) {
                try {
                    if (cname != null &&
                        cname.getName().startsWith("java.security.")) {
                        results.addAll(getInterfaceList(cname));
                    }
                } catch (Exception e) {
                    System.err.println("Exception with " + cname);
                }
            }
        }
        return results;
    }

    /**
     * Perform the decoding test with the given decoder, on the given pem, and
     * expect the clazz to be returned.
     */
    static DEREncodable test(String pem, Class clazz, PEMDecoder decoder)
        throws IOException {
        DEREncodable pk = decoder.decode(pem);

//        if (pk instanceof KeyPair kp) {
//            pk = kp.getPrivate();
//        }

        // Check that clazz matches what pk returned.
        if (pk.getClass().equals(clazz)) {
            return pk;
        }

        // Search interfaces and inheritance to find a match with clazz
        List<Class> list = getInterfaceList(pk.getClass());
        for (Class cc : list) {
            if (cc != null && cc.equals(clazz)) {
                return pk;
            }
        }

        throw new RuntimeException("Entry did not contain expected: " +
            clazz.getName());
    }

    // Run the same key twice through the same decoder and make sure the
    // result is the same
    static void testTwoKeys() throws IOException {
        PublicKey p1, p2;
        PEMDecoder pd = PEMDecoder.of();
        p1 = pd.decode(PEMData.rsapub.pem(), RSAPublicKey.class);
        p2 = pd.decode(PEMData.rsapub.pem(), RSAPublicKey.class);
        if (!Arrays.equals(p1.getEncoded(), p2.getEncoded())) {
            System.err.println("These two should have matched:");
            System.err.println(hex.parseHex(new String(p1.getEncoded())));
            System.err.println(hex.parseHex(new String(p2.getEncoded())));
            throw new AssertionError("Two decoding of the same" +
                " key failed to match: ");
        }
    }

    static void testClass(PEMData.Entry entry, Class clazz) throws IOException {
        var pk = PEMDecoder.of().decode(entry.pem(), clazz);
    }

    static void testClass(PEMData.Entry entry, Class clazz, boolean pass)
        throws RuntimeException {
        try {
            testClass(entry, clazz);
        } catch (Exception e) {
            if (pass) {
                throw new RuntimeException(e);
            }
        }
    }

    // Run test with a given Entry
    static void testDERCheck(PEMData.Entry entry) {
        if (entry.name().equals("rsaOpenSSL") ||  // PKCS1 data
            entry.name().equals("ed25519ekpi")) {
            return;
        }

        PKCS8EncodedKeySpec p8 = PEMDecoder.of().decode(entry.pem(),
                PKCS8EncodedKeySpec.class);
        int result = Arrays.compare(entry.der(), p8.getEncoded());
        if (result != 0) {
            System.err.println("Compare error with " + entry.name() + "(" +
                result + ")");
            System.err.println("Expected DER: " + HexFormat.of().
                formatHex(entry.der()));
            System.err.println("Returned DER: " + HexFormat.of().
                formatHex(p8.getEncoded()));
                throw new AssertionError("Failed to match " +
                "expected DER");
        }
        System.out.println("PASS (" + entry.name() + ")");
        System.out.flush();
    }

    /**
     * Run decoded keys through Signature to make sure they are valid keys
     */
    static void testSignature(PEMData.Entry entry) {
        Signature s;
        byte[] data = "12345678".getBytes();
        PrivateKey privateKey;

        DEREncodable d = PEMDecoder.of().decode(entry.pem());
        switch (d) {
            case PrivateKey p -> privateKey = p;
            case KeyPair kp -> privateKey = kp.getPrivate();
            case EncryptedPrivateKeyInfo e -> {
                System.out.println("SKIP: EncryptedPrivateKeyInfo " +
                    entry.name());
                return;
            }
            default -> throw new AssertionError("Private key " +
                "should not be null");
        }

        String algorithm = switch(privateKey.getAlgorithm()) {
            case "EC" -> "SHA256withECDSA";
            case "EdDSA" -> "EdDSA";
            case null -> {
                System.out.println("Algorithm is null " +
                    entry.name());
                throw new AssertionError("PrivateKey algorithm" +
                    "should not be null");
            }
            default -> "SHA256with" + privateKey.getAlgorithm();
        };

        try {
            if (d instanceof PrivateKey) {
                s = Signature.getInstance(algorithm);
                s.initSign(privateKey);
                s.update(data);
                s.sign();
                System.out.println("PASS (Sign): " + entry.name());
            } else if (d instanceof KeyPair) {
                s = Signature.getInstance(algorithm);
                s.initSign(privateKey);
                s.update(data);
                byte[] sig = s.sign();
                s.initVerify(((KeyPair)d).getPublic());
                s.verify(sig);
                System.out.println("PASS (Sign/Verify): " + entry.name());
            } else {
                System.out.println("SKIP: " + entry.name());
            }
        } catch (Exception e) {
            System.out.println("FAIL: " + entry.name());
            throw new AssertionError(e);
        }
    }
}