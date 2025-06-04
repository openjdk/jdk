/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8300140
 * @summary Make sure signature related files in subdirectories of META-INF are not considered for verification
 * @modules java.base/jdk.internal.access
 * @modules java.base/sun.security.util
 * @modules java.base/sun.security.tools.keytool
 * @modules jdk.jartool/sun.security.tools.jarsigner
 * @run main/othervm IgnoreUnrelatedSignatureFiles
 */

import jdk.internal.access.JavaUtilZipFileAccess;
import jdk.internal.access.SharedSecrets;
import jdk.security.jarsigner.JarSigner;
import sun.security.tools.jarsigner.Main;
import sun.security.util.SignatureFileVerifier;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertPath;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class IgnoreUnrelatedSignatureFiles {

    private static final JavaUtilZipFileAccess JUZA = SharedSecrets.getJavaUtilZipFileAccess();

    // This path resides in a subdirectory of META-INF, so it should not be considered signature related
    public static final String SUBDIR_SF_PATH = "META-INF/subdirectory/META-INF/SIGNER.SF";


    public static void main(String[] args) throws Exception {

        // Regular signed JAR
        Path j = createJarFile();
        Path s = signJarFile(j, "SIGNER1", "signed");

        // Singed JAR with unrelated signature files
        Path m = moveSignatureRelated(s);
        Path sm = signJarFile(m, "SIGNER2", "modified-signed");

        // Signed JAR with custom SIG-* files
        Path ca = createCustomAlgJar();
        Path cas = signJarFile(ca, "SIGNER1", "custom-signed");

        // 0: Sanity check that the basic signed JAR verifies
        try (JarFile jf = new JarFile(s.toFile(), true)) {
            Map<String, Attributes> entries = jf.getManifest().getEntries();
            if (entries.size() != 1) {
                throw new Exception("Expected a single manifest entry for the digest of a.txt, instead found entries: " + entries.keySet());
            }
            JarEntry entry = jf.getJarEntry("a.txt");
            try (InputStream in = jf.getInputStream(entry)) {
                in.transferTo(OutputStream.nullOutputStream());
            }
        }
        // 1: Check ZipFile.Source.isSignatureRelated
        try (JarFile jarFile = new JarFile(m.toFile())) {
            List<String> manifestAndSignatureRelatedFiles = JUZA.getManifestAndSignatureRelatedFiles(jarFile);
            for (String signatureRelatedFile : manifestAndSignatureRelatedFiles) {
                String dir = signatureRelatedFile.substring(0, signatureRelatedFile.lastIndexOf("/"));
                if (!"META-INF".equals(dir)) {
                    throw new Exception("Signature related file does not reside directly in META-INF/ : " + signatureRelatedFile);
                }
            }
        }

        // 2: Check SignatureFileVerifier.isSigningRelated
        if (SignatureFileVerifier.isSigningRelated(SUBDIR_SF_PATH)) {
            throw new Exception("Signature related file does not reside directly in META-INF/ : " + SUBDIR_SF_PATH);
        }

        // 3: Check JarInputStream with doVerify = true
        try (JarInputStream in = new JarInputStream(Files.newInputStream(m), true)) {
             while (in.getNextEntry() != null) {
                in.transferTo(OutputStream.nullOutputStream());
            }
        }

        // 4: Check that a JAR containing unrelated .SF, .RSA files is signed as-if it is unsigned
        try (ZipFile zf = new ZipFile(sm.toFile())) {
            ZipEntry mf = zf.getEntry("META-INF/MANIFEST.MF");
            try (InputStream stream = zf.getInputStream(mf)) {
                String manifest = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                // When JarSigner considers a jar to not be already signed,
                // the 'Manifest-Version' attributed name will be case-normalized
                // Assert that manifest-version is not in lowercase
                if (manifest.startsWith("manifest-version")) {
                    throw new Exception("JarSigner unexpectedly treated unsigned jar as signed");
                }
            }
        }

        // 5: Check that a JAR containing non signature related .SF, .RSA files can be signed
        try (JarFile jf = new JarFile(sm.toFile(), true)) {
            checkSignedBy(jf, "a.txt", "CN=SIGNER2");
            checkSignedBy(jf, "META-INF/subdirectory/META-INF/SIGNER1.SF", "CN=SIGNER2");
        }

        // 6: Check that JarSigner does not move unrelated [SF,RSA] files to the beginning of signed JARs
        try (JarFile zf = new JarFile(sm.toFile())) {

            List<String> actualOrder = zf.stream().map(ZipEntry::getName).toList();

            List<String> expectedOrder = List.of(
                    "META-INF/MANIFEST.MF",
                    "META-INF/SIGNER2.SF",
                    "META-INF/SIGNER2.RSA",
                    "META-INF/subdirectory/META-INF/SIGNER1.SF",
                    "META-INF/subdirectory/META-INF/SIGNER1.RSA",
                    "a.txt",
                    "META-INF/subdirectory2/META-INF/SIGNER1.SF",
                    "META-INF/subdirectory2/META-INF/SIGNER1.RSA"
            );

            if (!expectedOrder.equals(actualOrder)) {
                String msg = ("""
                        Unexpected file order in JAR with unrelated SF,RSA files
                        Expected order: %s
                        Actual order: %s""")
                        .formatted(expectedOrder, actualOrder);
                throw new Exception(msg);
            }
        }

        // 7: Check that jarsigner ignores unrelated signature files
        String message = jarSignerVerify(m);
        if (message.contains("WARNING")) {
            throw new Exception("jarsigner output contains unexpected  warning: " +message);
        }

        // 8: Check that SignatureFileVerifier.isSigningRelated handles custom SIG-* files correctly
        try (JarFile jf = new JarFile(cas.toFile(), true)) {

            // These files are not signature-related and should be signed
            Set<String> expectedSigned = Set.of("a.txt",
                    "META-INF/unrelated.txt",
                    "META-INF/SIG-CUSTOM2.C-1",
                    "META-INF/SIG-CUSTOM2.",
                    "META-INF/SIG-CUSTOM2.ABCD",
                    "META-INF/subdirectory/SIG-CUSTOM2.SF",
                    "META-INF/subdirectory/SIG-CUSTOM2.CS1"
            );

            Set<String> actualSigned = jf.getManifest().getEntries().keySet();

            if (!expectedSigned.equals(actualSigned)) {
                throw new Exception("Unexpected MANIFEST entries. Expected %s, got %s"
                        .formatted(expectedSigned, actualSigned));
            }
        }
    }

    /**
     * run "jarsigner -verify" on the JAR and return the captured output
     */
    private static String jarSignerVerify(Path m) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream currentOut = System.out;
        try {
            System.setOut(new PrintStream(out));
            Main.main(new String[] {"-verify", m.toAbsolutePath().toString()});
            return out.toString(StandardCharsets.UTF_8);
        } finally {
            System.setOut(currentOut);
        }
    }

    /**
     * Check that a path of a given JAR is signed once by the expected signer CN
     */
    private static void checkSignedBy(JarFile jf, String name, String expectedSigner) throws Exception {
        JarEntry je = jf.getJarEntry(name);

        // Read the contents to trigger verification
        try (InputStream in = jf.getInputStream(je)) {
            in.transferTo(OutputStream.nullOutputStream());
        }

        // Verify that the entry is signed
        CodeSigner[] signers = je.getCodeSigners();
        if (signers == null) {
            throw new Exception(String.format("Expected %s to be signed", name));
        }

        // There should be a single signer
        if (signers.length != 1) {
            throw new Exception(String.format("Expected %s to be signed by exactly one signer", name));
        }

        String actualSigner = ((X509Certificate) signers[0]
                .getSignerCertPath().getCertificates().get(0))
                .getIssuerX500Principal().getName();

        if (!actualSigner.equals(expectedSigner)) {
            throw new Exception(String.format("Expected %s to be signed by %s, was signed by %s", name, expectedSigner, actualSigner));
        }
    }

    /**
     * Create a jar file with a '*.SF' file residing in META-INF/subdirectory/
     */
    private static Path createJarFile() throws Exception {

        Path jar = Path.of("unrelated-signature-file.jar");

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            write(out, "a.txt", "a");
        }

        return jar;
    }

    private static Path createCustomAlgJar() throws Exception {
        Path jar = Path.of("unrelated-signature-file-custom-sig.jar");

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            // Regular file
            write(out, "a.txt", "a");
            // Regular file in META-INF
            write(out, "META-INF/unrelated.txt", "a");

            // Custom SIG files with valid extension (no extension is also OK)
            write(out, "META-INF/SIG-CUSTOM.SF", "");
            write(out, "META-INF/SIG-CUSTOM.CS1", "");
            write(out, "META-INF/SIG-CUSTOM", "");

            // Custom SIG files with invalid extensions
            write(out, "META-INF/SIG-CUSTOM2.SF", "");
            write(out, "META-INF/SIG-CUSTOM2.C-1", "");
            write(out, "META-INF/SIG-CUSTOM2.", "");
            write(out, "META-INF/SIG-CUSTOM2.ABCD", "");

            // Custom SIG files with valid extensions in subdirectories
            write(out, "META-INF/subdirectory/SIG-CUSTOM2.SF", "");
            write(out, "META-INF/subdirectory/SIG-CUSTOM2.CS1", "");

        }

        return jar;
    }

    private static void write(JarOutputStream out, String name, String content) throws IOException {
        out.putNextEntry(new JarEntry(name));
        out.write(content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Create a signed version of the given jar file
     */
    private static Path signJarFile(Path jar, String signerName, String classifier) throws Exception {
        Path s = Path.of("unrelated-signature-files-" + classifier +".jar");

        Files.deleteIfExists(Path.of("ks"));

        sun.security.tools.keytool.Main.main(
                ("-keystore ks -storepass changeit -keypass changeit -dname" +
                        " CN=" + signerName +" -alias r -genkeypair -keyalg rsa").split(" "));

        char[] pass = "changeit".toCharArray();

        KeyStore ks = KeyStore.getInstance(
                new File("ks"), pass);
        PrivateKey pkr = (PrivateKey)ks.getKey("r", pass);

        CertPath cp = CertificateFactory.getInstance("X.509")
                .generateCertPath(Arrays.asList(ks.getCertificateChain("r")));

        JarSigner signer = new JarSigner.Builder(pkr, cp)
                .digestAlgorithm("SHA-256")
                .signatureAlgorithm("SHA256withRSA")
                .signerName(signerName)
                .build();

        try (ZipFile in = new ZipFile(jar.toFile());
            OutputStream out = Files.newOutputStream(s)) {
            signer.sign(in, out);
        }

        return s;
    }

    /**
     * Create a modified version of a signed jar file where signature-related files
     * are moved into a subdirectory of META-INF/ and the manifest is changed to trigger
     * a digest mismatch.
     *
     * Since the signature related files are moved out of META-INF/, the returned jar file should
     * not be considered signed
     */
    private static Path moveSignatureRelated(Path s) throws Exception {
        Path m = Path.of("unrelated-signature-files-modified.jar");

        try (ZipFile in = new ZipFile(s.toFile());
            ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(m))) {

            // Change the digest of the manifest by lower-casing the Manifest-Version attribute:
            out.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            out.write("manifest-version: 1.0\n\n".getBytes(StandardCharsets.UTF_8));

            copy("META-INF/SIGNER1.SF", "META-INF/subdirectory/META-INF/SIGNER1.SF", in, out);
            copy("META-INF/SIGNER1.RSA", "META-INF/subdirectory/META-INF/SIGNER1.RSA", in, out);

            // Copy over the regular a.txt file
            copy("a.txt", "a.txt", in, out);

            // These are also just regular files in their new location, but putting them at end
            // allows us to verify that JarSigner does not move them to the beginning of the signed JAR
            copy("META-INF/SIGNER1.SF", "META-INF/subdirectory2/META-INF/SIGNER1.SF", in, out);
            copy("META-INF/SIGNER1.RSA", "META-INF/subdirectory2/META-INF/SIGNER1.RSA", in, out);
        }
        return m;
    }

    // Copy a file from a ZipFile into a ZipOutputStream
    private static void copy(String from, String to, ZipFile in, ZipOutputStream out) throws Exception {
        out.putNextEntry(new ZipEntry(to));
        try (InputStream zi = in.getInputStream(new ZipEntry(from))) {
            zi.transferTo(out);
        }
    }
}
