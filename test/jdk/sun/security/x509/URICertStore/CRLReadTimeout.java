/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8191808 8179502
 * @summary check that CRL download is interrupted if it takes too long
 * @modules java.base/sun.security.x509
 *          java.base/sun.security.util
 * @library /test/lib
 * @run main/othervm -Dcom.sun.security.crl.readtimeout=1
 *      CRLReadTimeout 5000 false
 * @run main/othervm -Dcom.sun.security.crl.readtimeout=1s
 *      CRLReadTimeout 5000 false
 * @run main/othervm -Dcom.sun.security.crl.readtimeout=4
 *      CRLReadTimeout 1000 true
 * @run main/othervm -Dcom.sun.security.crl.readtimeout=1500ms
 *      CRLReadTimeout 5000 false
 * @run main/othervm -Dcom.sun.security.crl.readtimeout=4500ms
 *      CRLReadTimeout 1000 true
 */

import java.io.*;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.*;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static java.security.cert.PKIXRevocationChecker.Option.*;

import com.sun.net.httpserver.HttpServer;
import jdk.test.lib.SecurityTools;
import jdk.test.lib.process.OutputAnalyzer;
import sun.security.util.SignatureUtil;
import sun.security.x509.*;

public class CRLReadTimeout {

    public static final String PASS = "changeit";
    public static X509CRL crl;

    public static void main(String[] args) throws Exception {

        int serverTimeout = (args != null && args[0] != null) ?
                Integer.parseInt(args[0]) : 15000;
        boolean expectedPass = args != null && args[1] != null &&
                Boolean.parseBoolean(args[1]);
        System.out.println("Server timeout is " + serverTimeout + " msec.");
        System.out.println("Test is expected to " + (expectedPass ? "pass" : "fail"));

        CrlHttpServer crlServer = new CrlHttpServer(serverTimeout);
        try {
            crlServer.start();
            testTimeout(crlServer.getPort(), expectedPass);
        } finally {
            crlServer.stop();
        }
    }

    private static void testTimeout(int port, boolean expectedPass)
            throws Exception {

        // create certificate chain with two certs, root and end-entity
        keytool("-alias duke -dname CN=duke -genkey -keyalg RSA");
        keytool("-alias root -dname CN=root -genkey -keyalg RSA");
        keytool("-certreq -alias duke -file duke.req");
        // set CRL URI to local server
        keytool("-gencert -infile duke.req -alias root -rfc -outfile duke.cert "
                + "-ext crl=uri:http://localhost:" + port + "/crl");
        keytool("-importcert -file duke.cert -alias duke");

        KeyStore ks = KeyStore.getInstance(new File("ks"), PASS.toCharArray());
        X509Certificate cert = (X509Certificate)ks.getCertificate("duke");
        X509Certificate root = (X509Certificate)ks.getCertificate("root");
        crl = genCrl(ks, "root", PASS);

        // validate chain
        CertPathValidator cpv = CertPathValidator.getInstance("PKIX");
        PKIXRevocationChecker prc =
            (PKIXRevocationChecker)cpv.getRevocationChecker();
        prc.setOptions(EnumSet.of(PREFER_CRLS, NO_FALLBACK, SOFT_FAIL));
        PKIXParameters params =
            new PKIXParameters(Set.of(new TrustAnchor(root, null)));
        params.addCertPathChecker(prc);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        CertPath cp = cf.generateCertPath(List.of(cert));
        cpv.validate(cp, params);

        // unwrap soft fail exceptions and check for SocketTimeoutException
        List<CertPathValidatorException> softExc = prc.getSoftFailExceptions();
        if (expectedPass) {
            if (softExc.size() > 0) {
                throw new RuntimeException("Expected to pass, found " +
                        softExc.size() + " soft fail exceptions");
            }
        } else {
            boolean foundSockTOExc = false;
            for (CertPathValidatorException softFail : softExc) {
                Throwable cause = softFail.getCause();
                while (cause != null) {
                    if (cause instanceof SocketTimeoutException) {
                        foundSockTOExc = true;
                        break;
                    }
                    cause = cause.getCause();
                }
                if (foundSockTOExc) {
                    break;
                }
            }
            if (!foundSockTOExc) {
                throw new Exception("SocketTimeoutException not thrown");
            }
        }
    }

    private static OutputAnalyzer keytool(String cmd) throws Exception {
        return SecurityTools.keytool("-storepass " + PASS +
                " -keystore ks " + cmd);
    }

    private static X509CRL genCrl(KeyStore ks, String issAlias, String pass)
            throws GeneralSecurityException, IOException {
        // Create an empty CRL with a 1-day validity period.
        X509Certificate issuerCert = (X509Certificate)ks.getCertificate(issAlias);
        PrivateKey issuerKey = (PrivateKey)ks.getKey(issAlias, pass.toCharArray());

        long curTime = System.currentTimeMillis();
        Date thisUp = new Date(curTime - TimeUnit.SECONDS.toMillis(43200));
        Date nextUp = new Date(curTime + TimeUnit.SECONDS.toMillis(43200));
        CRLExtensions exts = new CRLExtensions();
        var aki = new AuthorityKeyIdentifierExtension(new KeyIdentifier(
                issuerCert.getPublicKey()), null, null);
        var crlNum = new CRLNumberExtension(BigInteger.ONE);
        exts.setExtension(aki.getId(), aki);
        exts.setExtension(crlNum.getId(), crlNum);
        X509CRLImpl.TBSCertList cList = new X509CRLImpl.TBSCertList(
                new X500Name(issuerCert.getSubjectX500Principal().toString()),
                thisUp, nextUp, null, exts);
        X509CRL crl = X509CRLImpl.newSigned(cList, issuerKey,
                SignatureUtil.getDefaultSigAlgForKey(issuerKey));
        System.out.println("ISSUED CRL:\n" + crl);
        return crl;
    }

    private static class CrlHttpServer {

        private final HttpServer server;
        private final int timeout;

        public CrlHttpServer(int timeout) throws IOException {
            server = HttpServer.create();
            this.timeout = timeout;
        }

        public void start() throws IOException {
            server.bind(new InetSocketAddress(0), 0);
            server.createContext("/crl", t -> {
                try (InputStream is = t.getRequestBody()) {
                    is.readAllBytes();
                }
                try {
                    // Sleep in order to simulate network latency
                    Thread.sleep(timeout);

                    byte[] derCrl = crl.getEncoded();
                    t.getResponseHeaders().add("Content-Type",
                            "application/pkix-crl");
                    t.sendResponseHeaders(200, derCrl.length);
                    try (OutputStream os = t.getResponseBody()) {
                        os.write(derCrl);
                    }
                } catch (InterruptedException | CRLException exc) {
                    throw new IOException(exc);
                }
            });
            server.setExecutor(null);
            server.start();
        }

        public void stop() {
            server.stop(0);
        }

        int getPort() {
            return server.getAddress().getPort();
        }
    }
}
