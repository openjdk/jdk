/*
 * Copyright (c) 2003, 2016, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.net.httpserver.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import jdk.testlibrary.*;
import jdk.testlibrary.JarUtils;
import sun.security.pkcs.ContentInfo;
import sun.security.pkcs.PKCS7;
import sun.security.pkcs.PKCS9Attribute;
import sun.security.pkcs.SignerInfo;
import sun.security.timestamp.TimestampToken;
import sun.security.util.DerOutputStream;
import sun.security.util.DerValue;
import sun.security.util.ObjectIdentifier;
import sun.security.x509.AlgorithmId;
import sun.security.x509.X500Name;

/*
 * @test
 * @bug 6543842 6543440 6939248 8009636 8024302 8163304 8169911
 * @summary checking response of timestamp
 * @modules java.base/sun.security.pkcs
 *          java.base/sun.security.timestamp
 *          java.base/sun.security.x509
 *          java.base/sun.security.util
 *          java.base/sun.security.tools.keytool
 * @library /lib/testlibrary
 * @run main/timeout=600 TimestampCheck
 */
public class TimestampCheck {

    static final String defaultPolicyId = "2.3.4";
    static String host = null;

    static class Handler implements HttpHandler, AutoCloseable {

        private final HttpServer httpServer;
        private final String keystore;

        @Override
        public void handle(HttpExchange t) throws IOException {
            int len = 0;
            for (String h: t.getRequestHeaders().keySet()) {
                if (h.equalsIgnoreCase("Content-length")) {
                    len = Integer.valueOf(t.getRequestHeaders().get(h).get(0));
                }
            }
            byte[] input = new byte[len];
            t.getRequestBody().read(input);

            try {
                String path = t.getRequestURI().getPath().substring(1);
                byte[] output = sign(input, path);
                Headers out = t.getResponseHeaders();
                out.set("Content-Type", "application/timestamp-reply");

                t.sendResponseHeaders(200, output.length);
                OutputStream os = t.getResponseBody();
                os.write(output);
            } catch (Exception e) {
                e.printStackTrace();
                t.sendResponseHeaders(500, 0);
            }
            t.close();
        }

        /**
         * @param input The data to sign
         * @param path different cases to simulate, impl on URL path
         * @returns the signed
         */
        byte[] sign(byte[] input, String path) throws Exception {

            DerValue value = new DerValue(input);
            System.err.println("\nIncoming Request\n===================");
            System.err.println("Version: " + value.data.getInteger());
            DerValue messageImprint = value.data.getDerValue();
            AlgorithmId aid = AlgorithmId.parse(
                    messageImprint.data.getDerValue());
            System.err.println("AlgorithmId: " + aid);

            ObjectIdentifier policyId = new ObjectIdentifier(defaultPolicyId);
            BigInteger nonce = null;
            while (value.data.available() > 0) {
                DerValue v = value.data.getDerValue();
                if (v.tag == DerValue.tag_Integer) {
                    nonce = v.getBigInteger();
                    System.err.println("nonce: " + nonce);
                } else if (v.tag == DerValue.tag_Boolean) {
                    System.err.println("certReq: " + v.getBoolean());
                } else if (v.tag == DerValue.tag_ObjectId) {
                    policyId = v.getOID();
                    System.err.println("PolicyID: " + policyId);
                }
            }

            System.err.println("\nResponse\n===================");
            KeyStore ks = KeyStore.getInstance(
                    new File(keystore), "changeit".toCharArray());

            String alias = "ts";
            if (path.startsWith("bad") || path.equals("weak")) {
                alias = "ts" + path;
            }

            if (path.equals("diffpolicy")) {
                policyId = new ObjectIdentifier(defaultPolicyId);
            }

            DerOutputStream statusInfo = new DerOutputStream();
            statusInfo.putInteger(0);

            AlgorithmId[] algorithms = {aid};
            Certificate[] chain = ks.getCertificateChain(alias);
            X509Certificate[] signerCertificateChain;
            X509Certificate signer = (X509Certificate)chain[0];

            if (path.equals("fullchain")) {   // Only case 5 uses full chain
                signerCertificateChain = new X509Certificate[chain.length];
                for (int i=0; i<chain.length; i++) {
                    signerCertificateChain[i] = (X509Certificate)chain[i];
                }
            } else if (path.equals("nocert")) {
                signerCertificateChain = new X509Certificate[0];
            } else {
                signerCertificateChain = new X509Certificate[1];
                signerCertificateChain[0] = (X509Certificate)chain[0];
            }

            DerOutputStream tst = new DerOutputStream();

            tst.putInteger(1);
            tst.putOID(policyId);

            if (!path.equals("baddigest") && !path.equals("diffalg")) {
                tst.putDerValue(messageImprint);
            } else {
                byte[] data = messageImprint.toByteArray();
                if (path.equals("diffalg")) {
                    data[6] = (byte)0x01;
                } else {
                    data[data.length-1] = (byte)0x01;
                    data[data.length-2] = (byte)0x02;
                    data[data.length-3] = (byte)0x03;
                }
                tst.write(data);
            }

            tst.putInteger(1);

            Calendar cal = Calendar.getInstance();
            tst.putGeneralizedTime(cal.getTime());

            if (path.equals("diffnonce")) {
                tst.putInteger(1234);
            } else if (path.equals("nononce")) {
                // no noce
            } else {
                tst.putInteger(nonce);
            }

            DerOutputStream tstInfo = new DerOutputStream();
            tstInfo.write(DerValue.tag_Sequence, tst);

            DerOutputStream tstInfo2 = new DerOutputStream();
            tstInfo2.putOctetString(tstInfo.toByteArray());

            // Always use the same algorithm at timestamp signing
            // so it is different from the hash algorithm.
            Signature sig = Signature.getInstance("SHA1withRSA");
            sig.initSign((PrivateKey)(ks.getKey(
                    alias, "changeit".toCharArray())));
            sig.update(tstInfo.toByteArray());

            ContentInfo contentInfo = new ContentInfo(new ObjectIdentifier(
                    "1.2.840.113549.1.9.16.1.4"),
                    new DerValue(tstInfo2.toByteArray()));

            System.err.println("Signing...");
            System.err.println(new X500Name(signer
                    .getIssuerX500Principal().getName()));
            System.err.println(signer.getSerialNumber());

            SignerInfo signerInfo = new SignerInfo(
                    new X500Name(signer.getIssuerX500Principal().getName()),
                    signer.getSerialNumber(),
                    AlgorithmId.get("SHA-1"), AlgorithmId.get("RSA"), sig.sign());

            SignerInfo[] signerInfos = {signerInfo};
            PKCS7 p7 = new PKCS7(algorithms, contentInfo,
                    signerCertificateChain, signerInfos);
            ByteArrayOutputStream p7out = new ByteArrayOutputStream();
            p7.encodeSignedData(p7out);

            DerOutputStream response = new DerOutputStream();
            response.write(DerValue.tag_Sequence, statusInfo);
            response.putDerValue(new DerValue(p7out.toByteArray()));

            DerOutputStream out = new DerOutputStream();
            out.write(DerValue.tag_Sequence, response);

            return out.toByteArray();
        }

        private Handler(HttpServer httpServer, String keystore) {
            this.httpServer = httpServer;
            this.keystore = keystore;
        }

        /**
         * Initialize TSA instance.
         *
         * Extended Key Info extension of certificate that is used for
         * signing TSA responses should contain timeStamping value.
         */
        static Handler init(int port, String keystore) throws IOException {
            HttpServer httpServer = HttpServer.create(
                    new InetSocketAddress(port), 0);
            Handler tsa = new Handler(httpServer, keystore);
            httpServer.createContext("/", tsa);
            return tsa;
        }

        /**
         * Start TSA service.
         */
        void start() {
            httpServer.start();
        }

        /**
         * Stop TSA service.
         */
        void stop() {
            httpServer.stop(0);
        }

        /**
         * Return server port number.
         */
        int getPort() {
            return httpServer.getAddress().getPort();
        }

        @Override
        public void close() throws Exception {
            stop();
        }
    }

    public static void main(String[] args) throws Throwable {

        prepare();

        try (Handler tsa = Handler.init(0, "tsks");) {
            tsa.start();
            int port = tsa.getPort();
            host = "http://localhost:" + port + "/";

            if (args.length == 0) {         // Run this test
                sign("none")
                        .shouldContain("is not timestamped")
                        .shouldHaveExitValue(0);

                sign("badku")
                        .shouldHaveExitValue(0);
                checkBadKU("badku.jar");

                sign("normal")
                        .shouldNotContain("is not timestamped")
                        .shouldHaveExitValue(0);

                sign("nononce")
                        .shouldHaveExitValue(1);
                sign("diffnonce")
                        .shouldHaveExitValue(1);
                sign("baddigest")
                        .shouldHaveExitValue(1);
                sign("diffalg")
                        .shouldHaveExitValue(1);
                sign("fullchain")
                        .shouldHaveExitValue(0);   // Success, 6543440 solved.
                sign("bad1")
                        .shouldHaveExitValue(1);
                sign("bad2")
                        .shouldHaveExitValue(1);
                sign("bad3")
                        .shouldHaveExitValue(1);
                sign("nocert")
                        .shouldHaveExitValue(1);

                sign("policy", "-tsapolicyid",  "1.2.3")
                        .shouldHaveExitValue(0);
                checkTimestamp("policy.jar", "1.2.3", "SHA-256");

                sign("diffpolicy", "-tsapolicyid", "1.2.3")
                        .shouldHaveExitValue(1);

                sign("tsaalg", "-tsadigestalg", "SHA")
                        .shouldHaveExitValue(0);
                checkTimestamp("tsaalg.jar", defaultPolicyId, "SHA-1");

                sign("weak", "-digestalg", "MD5",
                                "-sigalg", "MD5withRSA", "-tsadigestalg", "MD5")
                        .shouldHaveExitValue(0)
                        .shouldMatch("MD5.*-digestalg.*risk")
                        .shouldMatch("MD5.*-tsadigestalg.*risk")
                        .shouldMatch("MD5withRSA.*-sigalg.*risk");
                checkWeak("weak.jar");

                signWithAliasAndTsa("halfWeak", "old.jar", "old", "-digestalg", "MD5")
                        .shouldHaveExitValue(0);
                checkHalfWeak("halfWeak.jar");

                // sign with DSA key
                signWithAliasAndTsa("sign1", "old.jar", "dsakey")
                        .shouldHaveExitValue(0);
                // sign with RSAkeysize < 1024
                signWithAliasAndTsa("sign2", "sign1.jar", "weakkeysize")
                        .shouldHaveExitValue(0);
                checkMultiple("sign2.jar");

                // When .SF or .RSA is missing or invalid
                checkMissingOrInvalidFiles("normal.jar");
            } else {                        // Run as a standalone server
                System.err.println("Press Enter to quit server");
                System.in.read();
            }
        }
    }

    private static void checkMissingOrInvalidFiles(String s)
            throws Throwable {
        JarUtils.updateJar(s, "1.jar", "-", "META-INF/OLD.SF");
        verify("1.jar", "-verbose")
                .shouldHaveExitValue(0)
                .shouldContain("treated as unsigned")
                .shouldContain("Missing signature-related file META-INF/OLD.SF");
        JarUtils.updateJar(s, "2.jar", "-", "META-INF/OLD.RSA");
        verify("2.jar", "-verbose")
                .shouldHaveExitValue(0)
                .shouldContain("treated as unsigned")
                .shouldContain("Missing block file for signature-related file META-INF/OLD.SF");
        JarUtils.updateJar(s, "3.jar", "META-INF/OLD.SF");
        verify("3.jar", "-verbose")
                .shouldHaveExitValue(0)
                .shouldContain("treated as unsigned")
                .shouldContain("Unparsable signature-related file META-INF/OLD.SF");
        JarUtils.updateJar(s, "4.jar", "META-INF/OLD.RSA");
        verify("4.jar", "-verbose")
                .shouldHaveExitValue(0)
                .shouldContain("treated as unsigned")
                .shouldContain("Unparsable signature-related file META-INF/OLD.RSA");
    }

    static OutputAnalyzer jarsigner(List<String> extra)
            throws Throwable {
        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jarsigner")
                .addVMArg("-Duser.language=en")
                .addVMArg("-Duser.country=US")
                .addToolArg("-keystore")
                .addToolArg("tsks")
                .addToolArg("-storepass")
                .addToolArg("changeit");
        for (String s : extra) {
            if (s.startsWith("-J")) {
                launcher.addVMArg(s.substring(2));
            } else {
                launcher.addToolArg(s);
            }
        }
        return ProcessTools.executeCommand(launcher.getCommand());
    }

    static OutputAnalyzer verify(String file, String... extra)
            throws Throwable {
        List<String> args = new ArrayList<>();
        args.add("-verify");
        args.add(file);
        args.addAll(Arrays.asList(extra));
        return jarsigner(args);
    }

    static void checkBadKU(String file) throws Throwable {
        verify(file)
                .shouldHaveExitValue(0)
                .shouldContain("treated as unsigned")
                .shouldContain("re-run jarsigner with debug enabled");
        verify(file, "-verbose")
                .shouldHaveExitValue(0)
                .shouldContain("Signed by")
                .shouldContain("treated as unsigned")
                .shouldContain("re-run jarsigner with debug enabled");
        verify(file, "-J-Djava.security.debug=jar")
                .shouldHaveExitValue(0)
                .shouldContain("SignatureException: Key usage restricted")
                .shouldContain("treated as unsigned")
                .shouldContain("re-run jarsigner with debug enabled");
    }

    static void checkWeak(String file) throws Throwable {
        verify(file)
                .shouldHaveExitValue(0)
                .shouldContain("treated as unsigned")
                .shouldMatch("weak algorithm that is now disabled.")
                .shouldMatch("Re-run jarsigner with the -verbose option for more details");
        verify(file, "-verbose")
                .shouldHaveExitValue(0)
                .shouldContain("treated as unsigned")
                .shouldMatch("weak algorithm that is now disabled by")
                .shouldMatch("Digest algorithm: .*weak")
                .shouldMatch("Signature algorithm: .*weak")
                .shouldMatch("Timestamp digest algorithm: .*weak")
                .shouldNotMatch("Timestamp signature algorithm: .*weak.*weak")
                .shouldMatch("Timestamp signature algorithm: .*key.*weak");
        verify(file, "-J-Djava.security.debug=jar")
                .shouldHaveExitValue(0)
                .shouldMatch("SignatureException:.*disabled");
    }

    static void checkHalfWeak(String file) throws Throwable {
        verify(file)
                .shouldHaveExitValue(0)
                .shouldContain("treated as unsigned")
                .shouldMatch("weak algorithm that is now disabled.")
                .shouldMatch("Re-run jarsigner with the -verbose option for more details");
        verify(file, "-verbose")
                .shouldHaveExitValue(0)
                .shouldContain("treated as unsigned")
                .shouldMatch("weak algorithm that is now disabled by")
                .shouldMatch("Digest algorithm: .*weak")
                .shouldNotMatch("Signature algorithm: .*weak")
                .shouldNotMatch("Timestamp digest algorithm: .*weak")
                .shouldNotMatch("Timestamp signature algorithm: .*weak.*weak")
                .shouldNotMatch("Timestamp signature algorithm: .*key.*weak");
     }

    static void checkMultiple(String file) throws Throwable {
        verify(file)
                .shouldHaveExitValue(0)
                .shouldContain("jar verified");
        verify(file, "-verbose", "-certs")
                .shouldHaveExitValue(0)
                .shouldContain("jar verified")
                .shouldMatch("X.509.*CN=dsakey")
                .shouldNotMatch("X.509.*CN=weakkeysize")
                .shouldMatch("Signed by .*CN=dsakey")
                .shouldMatch("Signed by .*CN=weakkeysize")
                .shouldMatch("Signature algorithm: .*key.*weak");
     }

    static void checkTimestamp(String file, String policyId, String digestAlg)
            throws Exception {
        try (JarFile jf = new JarFile(file)) {
            JarEntry je = jf.getJarEntry("META-INF/OLD.RSA");
            try (InputStream is = jf.getInputStream(je)) {
                byte[] content = is.readAllBytes();
                PKCS7 p7 = new PKCS7(content);
                SignerInfo[] si = p7.getSignerInfos();
                if (si == null || si.length == 0) {
                    throw new Exception("Not signed");
                }
                PKCS9Attribute p9 = si[0].getUnauthenticatedAttributes()
                        .getAttribute(PKCS9Attribute.SIGNATURE_TIMESTAMP_TOKEN_OID);
                PKCS7 tsToken = new PKCS7((byte[]) p9.getValue());
                TimestampToken tt =
                        new TimestampToken(tsToken.getContentInfo().getData());
                if (!tt.getHashAlgorithm().toString().equals(digestAlg)) {
                    throw new Exception("Digest alg different");
                }
                if (!tt.getPolicyID().equals(policyId)) {
                    throw new Exception("policyId different");
                }
            }
        }
    }

    static int which = 0;

    /**
     * @param extra more args given to jarsigner
     */
    static OutputAnalyzer sign(String path, String... extra)
            throws Throwable {
        String alias = path.equals("badku") ? "badku" : "old";
        return signWithAliasAndTsa(path, "old.jar", alias, extra);
    }

    static OutputAnalyzer signWithAliasAndTsa (String path, String jar,
            String alias, String...extra) throws Throwable {
        which++;
        System.err.println("\n>> Test #" + which + ": " + Arrays.toString(extra));
        List<String> args = List.of("-J-Djava.security.egd=file:/dev/./urandom",
                "-debug", "-signedjar", path + ".jar", jar, alias);
        args = new ArrayList<>(args);
        if (!path.equals("none") && !path.equals("badku")) {
            args.add("-tsa");
            args.add(host + path);
        }
        args.addAll(Arrays.asList(extra));
        return jarsigner(args);
    }

    static void prepare() throws Exception {
        jdk.testlibrary.JarUtils.createJar("old.jar", "A");
        Files.deleteIfExists(Paths.get("tsks"));
        keytool("-alias ca -genkeypair -ext bc -dname CN=CA");
        keytool("-alias old -genkeypair -dname CN=old");
        keytool("-alias dsakey -genkeypair -keyalg DSA -dname CN=dsakey");
        keytool("-alias weakkeysize -genkeypair -keysize 512 -dname CN=weakkeysize");
        keytool("-alias badku -genkeypair -dname CN=badku");
        keytool("-alias ts -genkeypair -dname CN=ts");
        keytool("-alias tsweak -genkeypair -keysize 512 -dname CN=tsbad1");
        keytool("-alias tsbad1 -genkeypair -dname CN=tsbad1");
        keytool("-alias tsbad2 -genkeypair -dname CN=tsbad2");
        keytool("-alias tsbad3 -genkeypair -dname CN=tsbad3");

        gencert("old");
        gencert("dsakey");
        gencert("weakkeysize");
        gencert("badku", "-ext ku:critical=keyAgreement");
        gencert("ts", "-ext eku:critical=ts");
        gencert("tsweak", "-ext eku:critical=ts");
        gencert("tsbad1");
        gencert("tsbad2", "-ext eku=ts");
        gencert("tsbad3", "-ext eku:critical=cs");
    }

    static void gencert(String alias, String... extra) throws Exception {
        keytool("-alias " + alias + " -certreq -file " + alias + ".req");
        String genCmd = "-gencert -alias ca -infile " +
                alias + ".req -outfile " + alias + ".cert";
        for (String s : extra) {
            genCmd += " " + s;
        }
        keytool(genCmd);
        keytool("-alias " + alias + " -importcert -file " + alias + ".cert");
    }

    static void keytool(String cmd) throws Exception {
        cmd = "-keystore tsks -storepass changeit -keypass changeit " +
                "-keyalg rsa -validity 200 " + cmd;
        sun.security.tools.keytool.Main.main(cmd.split(" "));
    }
}
