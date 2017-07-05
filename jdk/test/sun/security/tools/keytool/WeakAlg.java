/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8171319
 * @summary keytool should print out warnings when reading or generating
  *         cert/cert req using weak algorithms
 * @library /test/lib
 * @modules java.base/sun.security.tools.keytool
 *          java.base/sun.security.tools
 *          java.base/sun.security.util
 * @run main/othervm/timeout=600 -Duser.language=en -Duser.country=US WeakAlg
 */

import jdk.test.lib.SecurityTools;
import jdk.test.lib.process.OutputAnalyzer;
import sun.security.tools.KeyStoreUtil;
import sun.security.util.DisabledAlgorithmConstraints;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.CryptoPrimitive;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WeakAlg {

    public static void main(String[] args) throws Throwable {

        rm("ks");

        // -genkeypair, and -printcert, -list -alias, -exportcert
        // (w/ different formats)
        checkGenKeyPair("a", "-keyalg RSA -sigalg MD5withRSA", "MD5withRSA");
        checkGenKeyPair("b", "-keyalg RSA -keysize 512", "512-bit RSA key");
        checkGenKeyPair("c", "-keyalg RSA", null);

        kt("-list")
                .shouldContain("Warning:")
                .shouldMatch("<a>.*MD5withRSA.*risk")
                .shouldMatch("<b>.*512-bit RSA key.*risk");
        kt("-list -v")
                .shouldContain("Warning:")
                .shouldMatch("<a>.*MD5withRSA.*risk")
                .shouldContain("MD5withRSA (weak)")
                .shouldMatch("<b>.*512-bit RSA key.*risk")
                .shouldContain("512-bit RSA key (weak)");

        // Multiple warnings for multiple cert in -printcert or -list or -exportcert

        // -certreq, -printcertreq, -gencert
        checkCertReq("a", "", null);
        gencert("c-a", "")
                .shouldNotContain("Warning"); // new sigalg is not weak
        gencert("c-a", "-sigalg MD2withRSA")
                .shouldContain("Warning:")
                .shouldMatch("The generated certificate.*MD2withRSA.*risk");

        checkCertReq("a", "-sigalg MD5withRSA", "MD5withRSA");
        gencert("c-a", "")
                .shouldContain("Warning:")
                .shouldMatch("The certificate request.*MD5withRSA.*risk");
        gencert("c-a", "-sigalg MD2withRSA")
                .shouldContain("Warning:")
                .shouldMatch("The certificate request.*MD5withRSA.*risk")
                .shouldMatch("The generated certificate.*MD2withRSA.*risk");

        checkCertReq("b", "", "512-bit RSA key");
        gencert("c-b", "")
                .shouldContain("Warning:")
                .shouldMatch("The certificate request.*512-bit RSA key.*risk")
                .shouldMatch("The generated certificate.*512-bit RSA key.*risk");

        checkCertReq("c", "", null);
        gencert("a-c", "")
                .shouldContain("Warning:")
                .shouldMatch("The issuer.*MD5withRSA.*risk");

        // but the new cert is not weak
        kt("-printcert -file a-c.cert")
                .shouldNotContain("Warning")
                .shouldNotContain("weak");

        gencert("b-c", "")
                .shouldContain("Warning:")
                .shouldMatch("The issuer.*512-bit RSA key.*risk");

        // -importcert
        checkImport();

        // -importkeystore
        checkImportKeyStore();

        // -gencrl, -printcrl

        checkGenCRL("a", "", null);
        checkGenCRL("a", "-sigalg MD5withRSA", "MD5withRSA");
        checkGenCRL("b", "", "512-bit RSA key");
        checkGenCRL("c", "", null);

        kt("-delete -alias b");
        kt("-printcrl -file b.crl")
                .shouldContain("WARNING: not verified");
    }

    static void checkImportKeyStore() throws Exception {

        saveStore();

        rm("ks");
        kt("-importkeystore -srckeystore ks2 -srcstorepass changeit")
                .shouldContain("3 entries successfully imported")
                .shouldContain("Warning")
                .shouldMatch("<b>.*512-bit RSA key.*risk")
                .shouldMatch("<a>.*MD5withRSA.*risk");

        rm("ks");
        kt("-importkeystore -srckeystore ks2 -srcstorepass changeit -srcalias a")
                .shouldContain("Warning")
                .shouldMatch("<a>.*MD5withRSA.*risk");

        reStore();
    }

    static void checkImport() throws Exception {

        saveStore();

        // add trusted cert

        // cert already in
        kt("-importcert -alias d -file a.cert", "no")
                .shouldContain("Certificate already exists in keystore")
                .shouldContain("Warning")
                .shouldMatch("The input.*MD5withRSA.*risk")
                .shouldContain("Do you still want to add it?");
        kt("-importcert -alias d -file a.cert -noprompt")
                .shouldContain("Warning")
                .shouldMatch("The input.*MD5withRSA.*risk")
                .shouldNotContain("[no]");

        // cert is self-signed
        kt("-delete -alias a");
        kt("-delete -alias d");
        kt("-importcert -alias d -file a.cert", "no")
                .shouldContain("Warning")
                .shouldContain("MD5withRSA (weak)")
                .shouldMatch("The input.*MD5withRSA.*risk")
                .shouldContain("Trust this certificate?");
        kt("-importcert -alias d -file a.cert -noprompt")
                .shouldContain("Warning")
                .shouldMatch("The input.*MD5withRSA.*risk")
                .shouldNotContain("[no]");

        // cert is self-signed cacerts
        String weakSigAlgCA = null;
        KeyStore ks = KeyStoreUtil.getCacertsKeyStore();
        if (ks != null) {
            DisabledAlgorithmConstraints disabledCheck =
                    new DisabledAlgorithmConstraints(
                            DisabledAlgorithmConstraints.PROPERTY_CERTPATH_DISABLED_ALGS);
            Set<CryptoPrimitive> sigPrimitiveSet = Collections
                    .unmodifiableSet(EnumSet.of(CryptoPrimitive.SIGNATURE));

            for (String s : Collections.list(ks.aliases())) {
                if (ks.isCertificateEntry(s)) {
                    X509Certificate c = (X509Certificate)ks.getCertificate(s);
                    String sigAlg = c.getSigAlgName();
                    if (!disabledCheck.permits(sigPrimitiveSet, sigAlg, null)) {
                        weakSigAlgCA = sigAlg;
                        Files.write(Paths.get("ca.cert"),
                                ks.getCertificate(s).getEncoded());
                        break;
                    }
                }
            }
        }
        if (weakSigAlgCA != null) {
            kt("-delete -alias d");
            kt("-importcert -alias d -trustcacerts -file ca.cert", "no")
                    .shouldContain("Certificate already exists in system-wide CA")
                    .shouldContain("Warning")
                    .shouldMatch("The input.*" + weakSigAlgCA + ".*risk")
                    .shouldContain("Do you still want to add it to your own keystore?");
            kt("-importcert -alias d -file ca.cert -noprompt")
                    .shouldContain("Warning")
                    .shouldMatch("The input.*" + weakSigAlgCA + ".*risk")
                    .shouldNotContain("[no]");
        }

        // a non self-signed weak cert
        reStore();
        certreq("b", "");
        gencert("c-b", "");
        kt("-importcert -alias d -file c-b.cert")   // weak only, no prompt
                .shouldContain("Warning")
                .shouldNotContain("512-bit RSA key (weak)")
                .shouldMatch("The input.*512-bit RSA key.*risk")
                .shouldNotContain("[no]");

        kt("-delete -alias b");
        kt("-delete -alias c");
        kt("-delete -alias d");

        kt("-importcert -alias d -file c-b.cert", "no") // weak and not trusted
                .shouldContain("Warning")
                .shouldContain("512-bit RSA key (weak)")
                .shouldMatch("The input.*512-bit RSA key.*risk")
                .shouldContain("Trust this certificate?");
        kt("-importcert -alias d -file c-b.cert -noprompt")
                .shouldContain("Warning")
                .shouldMatch("The input.*512-bit RSA key.*risk")
                .shouldNotContain("[no]");

        // a non self-signed strong cert
        reStore();
        certreq("a", "");
        gencert("c-a", "");
        kt("-importcert -alias d -file c-a.cert") // trusted
                .shouldNotContain("Warning")
                .shouldNotContain("[no]");

        kt("-delete -alias a");
        kt("-delete -alias c");
        kt("-delete -alias d");

        kt("-importcert -alias d -file c-a.cert", "no") // not trusted
                .shouldNotContain("Warning")
                .shouldContain("Trust this certificate?");
        kt("-importcert -alias d -file c-a.cert -noprompt")
                .shouldNotContain("Warning")
                .shouldNotContain("[no]");

        // install reply

        reStore();

        gencert("a-b", "");
        gencert("b-c", "");

        // Full chain with root
        cat("a-a-b-c.cert", "b-c.cert", "a-b.cert", "a.cert");
        kt("-importcert -alias c -file a-a-b-c.cert")   // only weak
                .shouldContain("Warning")
                .shouldMatch("Reply #2 of 3.*512-bit RSA key.*risk")
                .shouldMatch("Reply #3 of 3.*MD5withRSA.*risk")
                .shouldNotContain("[no]");

        // Without root
        cat("a-b-c.cert", "b-c.cert", "a-b.cert");
        kt("-importcert -alias c -file a-b-c.cert")     // only weak
                .shouldContain("Warning")
                .shouldMatch("Reply #2 of 2.*512-bit RSA key.*risk")
                .shouldMatch("Issuer <a>.*MD5withRSA.*risk")
                .shouldNotContain("[no]");

        reStore();
        gencert("b-a", "");

        kt("-importcert -alias a -file b-a.cert")
                .shouldContain("Warning")
                .shouldMatch("Issuer <b>.*512-bit RSA key.*risk")
                .shouldNotContain("[no]");

        kt("-importcert -alias a -file c-a.cert")
                .shouldNotContain("Warning");

        kt("-importcert -alias b -file c-b.cert")
                .shouldContain("Warning")
                .shouldMatch("The input.*512-bit RSA key.*risk")
                .shouldNotContain("[no]");

        reStore();
        gencert("b-a", "");

        cat("c-b-a.cert", "b-a.cert", "c-b.cert");

        kt("-printcert -file c-b-a.cert")
                .shouldContain("Warning")
                .shouldMatch("The certificate #2 of 2.*512-bit RSA key.*risk");

        kt("-delete -alias b");

        kt("-importcert -alias a -file c-b-a.cert")
                .shouldContain("Warning")
                .shouldMatch("Reply #2 of 2.*512-bit RSA key.*risk")
                .shouldNotContain("[no]");

        kt("-delete -alias c");
        kt("-importcert -alias a -file c-b-a.cert", "no")
                .shouldContain("Top-level certificate in reply:")
                .shouldContain("512-bit RSA key (weak)")
                .shouldContain("Warning")
                .shouldMatch("Reply #2 of 2.*512-bit RSA key.*risk")
                .shouldContain("Install reply anyway?");
        kt("-importcert -alias a -file c-b-a.cert -noprompt")
                .shouldContain("Warning")
                .shouldMatch("Reply #2 of 2.*512-bit RSA key.*risk")
                .shouldNotContain("[no]");

        reStore();
    }

    private static void cat(String dest, String... src) throws IOException {
        System.out.println("---------------------------------------------");
        System.out.printf("$ cat ");

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        for (String s : src) {
            System.out.printf(s + " ");
            bout.write(Files.readAllBytes(Paths.get(s)));
        }
        Files.write(Paths.get(dest), bout.toByteArray());
        System.out.println("> " + dest);
    }

    static void checkGenCRL(String alias, String options, String bad) {

        OutputAnalyzer oa = kt("-gencrl -alias " + alias
                + " -id 1 -file " + alias + ".crl " + options);
        if (bad == null) {
            oa.shouldNotContain("Warning");
        } else {
            oa.shouldContain("Warning")
                    .shouldMatch("The generated CRL.*" + bad + ".*risk");
        }

        oa = kt("-printcrl -file " + alias + ".crl");
        if (bad == null) {
            oa.shouldNotContain("Warning")
                    .shouldContain("Verified by " + alias + " in keystore")
                    .shouldNotContain("(weak");
        } else {
            oa.shouldContain("Warning:")
                    .shouldMatch("The CRL.*" + bad + ".*risk")
                    .shouldContain("Verified by " + alias + " in keystore")
                    .shouldContain(bad + " (weak)");
        }
    }

    static void checkCertReq(
            String alias, String options, String bad) {

        OutputAnalyzer oa = certreq(alias, options);
        if (bad == null) {
            oa.shouldNotContain("Warning");
        } else {
            oa.shouldContain("Warning")
                    .shouldMatch("The generated certificate request.*" + bad + ".*risk");
        }

        oa = kt("-printcertreq -file " + alias + ".req");
        if (bad == null) {
            oa.shouldNotContain("Warning")
                    .shouldNotContain("(weak)");
        } else {
            oa.shouldContain("Warning")
                    .shouldMatch("The certificate request.*" + bad + ".*risk")
                    .shouldContain(bad + " (weak)");
        }
    }

    static void checkGenKeyPair(
            String alias, String options, String bad) {

        OutputAnalyzer oa = genkeypair(alias, options);
        if (bad == null) {
            oa.shouldNotContain("Warning");
        } else {
            oa.shouldContain("Warning")
                    .shouldMatch("The generated certificate.*" + bad + ".*risk");
        }

        oa = kt("-exportcert -alias " + alias + " -file " + alias + ".cert");
        if (bad == null) {
            oa.shouldNotContain("Warning");
        } else {
            oa.shouldContain("Warning")
                    .shouldMatch("The certificate.*" + bad + ".*risk");
        }

        oa = kt("-exportcert -rfc -alias " + alias + " -file " + alias + ".cert");
        if (bad == null) {
            oa.shouldNotContain("Warning");
        } else {
            oa.shouldContain("Warning")
                    .shouldMatch("The certificate.*" + bad + ".*risk");
        }

        oa = kt("-printcert -rfc -file " + alias + ".cert");
        if (bad == null) {
            oa.shouldNotContain("Warning");
        } else {
            oa.shouldContain("Warning")
                    .shouldMatch("The certificate.*" + bad + ".*risk");
        }

        oa = kt("-list -alias " + alias);
        if (bad == null) {
            oa.shouldNotContain("Warning");
        } else {
            oa.shouldContain("Warning")
                    .shouldMatch("The certificate.*" + bad + ".*risk");
        }

        // With cert content

        oa = kt("-printcert -file " + alias + ".cert");
        if (bad == null) {
            oa.shouldNotContain("Warning");
        } else {
            oa.shouldContain("Warning")
                    .shouldContain(bad + " (weak)")
                    .shouldMatch("The certificate.*" + bad + ".*risk");
        }

        oa = kt("-list -v -alias " + alias);
        if (bad == null) {
            oa.shouldNotContain("Warning");
        } else {
            oa.shouldContain("Warning")
                    .shouldContain(bad + " (weak)")
                    .shouldMatch("The certificate.*" + bad + ".*risk");
        }
    }

    // This is slow, but real keytool process is launched.
    static OutputAnalyzer kt1(String cmd, String... input) {
        cmd = "-keystore ks -storepass changeit " +
                "-keypass changeit " + cmd;
        System.out.println("---------------------------------------------");
        try {
            SecurityTools.setResponse(input);
            return SecurityTools.keytool(cmd);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    // Fast keytool execution by directly calling its main() method
    static OutputAnalyzer kt(String cmd, String... input) {
        PrintStream out = System.out;
        PrintStream err = System.err;
        InputStream ins = System.in;
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ByteArrayOutputStream berr = new ByteArrayOutputStream();
        boolean succeed = true;
        try {
            cmd = "-keystore ks -storepass changeit " +
                    "-keypass changeit " + cmd;
            System.out.println("---------------------------------------------");
            System.out.println("$ keytool " + cmd);
            System.out.println();
            String feed = "";
            if (input.length > 0) {
                feed = Stream.of(input).collect(Collectors.joining("\n")) + "\n";
            }
            System.setIn(new ByteArrayInputStream(feed.getBytes()));
            System.setOut(new PrintStream(bout));
            System.setErr(new PrintStream(berr));
            sun.security.tools.keytool.Main.main(
                    cmd.trim().split("\\s+"));
        } catch (Exception e) {
            // Might be a normal exception when -debug is on or
            // SecurityException (thrown by jtreg) when System.exit() is called
            if (!(e instanceof SecurityException)) {
                e.printStackTrace();
            }
            succeed = false;
        } finally {
            System.setOut(out);
            System.setErr(err);
            System.setIn(ins);
        }
        String sout = new String(bout.toByteArray());
        String serr = new String(berr.toByteArray());
        System.out.println("STDOUT:\n" + sout + "\nSTDERR:\n" + serr);
        if (!succeed) {
            throw new RuntimeException();
        }
        return new OutputAnalyzer(sout, serr);
    }

    static OutputAnalyzer genkeypair(String alias, String options) {
        return kt("-genkeypair -alias " + alias + " -dname CN=" + alias
                + " -keyalg RSA -storetype JKS " + options);
    }

    static OutputAnalyzer certreq(String alias, String options) {
        return kt("-certreq -alias " + alias
                + " -file " + alias + ".req " + options);
    }

    static OutputAnalyzer exportcert(String alias) {
        return kt("-exportcert -alias " + alias + " -file " + alias + ".cert");
    }

    static OutputAnalyzer gencert(String relation, String options) {
        int pos = relation.indexOf("-");
        String issuer = relation.substring(0, pos);
        String subject = relation.substring(pos + 1);
        return kt(" -gencert -alias " + issuer + " -infile " + subject
                + ".req -outfile " + relation + ".cert " + options);
    }

    static void saveStore() throws IOException {
        System.out.println("---------------------------------------------");
        System.out.println("$ cp ks ks2");
        Files.copy(Paths.get("ks"), Paths.get("ks2"),
                StandardCopyOption.REPLACE_EXISTING);
    }

    static void reStore() throws IOException {
        System.out.println("---------------------------------------------");
        System.out.println("$ cp ks2 ks");
        Files.copy(Paths.get("ks2"), Paths.get("ks"),
                StandardCopyOption.REPLACE_EXISTING);
    }

    static void rm(String s) throws IOException {
        System.out.println("---------------------------------------------");
        System.out.println("$ rm " + s);
        Files.deleteIfExists(Paths.get(s));
    }
}
