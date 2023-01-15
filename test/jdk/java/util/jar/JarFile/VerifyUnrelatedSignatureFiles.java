/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Make sure signature related files in subdirectories of META-INF are not considered for verification
 * @modules java.base/jdk.internal.access
 * @modules java.base/sun.security.util
 * @modules java.base/sun.security.tools.keytool
 * @compile VerifyUnrelatedSignatureFiles.java
 * @run main/othervm VerifyUnrelatedSignatureFiles
 */

import jdk.internal.access.JavaUtilZipFileAccess;
import jdk.internal.access.SharedSecrets;
import jdk.security.jarsigner.JarSigner;
import sun.security.util.SignatureFileVerifier;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.CodeSigner;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertPath;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.jar.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class VerifyUnrelatedSignatureFiles {

    private static final JavaUtilZipFileAccess JUZA = SharedSecrets.getJavaUtilZipFileAccess();

    // This path resides in a subdirectory of META-INF, so it should not be considered signature related
    public static final String SUBDIR_SF_PATH = "META-INF/subdirectory/META-INF/SIGNER.SF";


    public static void main(String[] args) throws Exception {

        File j = createJarFile();
        File s = signJarFile(j, "SIGNER1", "signed");
        File m = moveSignatureRelated(s);
        File sm = signJarFile(m, "SIGNER2", "modified-signed");

        // 1: Check ZipFile.Source.isSignatureRelated
        try(JarFile jarFile = new JarFile(m)) {
            final List<String> manifestAndSignatureRelatedFiles = JUZA.getManifestAndSignatureRelatedFiles(jarFile);
            for (String signatureRelatedFile : manifestAndSignatureRelatedFiles) {
                String dir = signatureRelatedFile.substring(0, signatureRelatedFile.lastIndexOf("/"));
                if(!"META-INF".equals(dir)) {
                    throw new Exception("Signature related file does not reside directly in META-INF/ : " + signatureRelatedFile);
                }
            }
        }

        // 2: Check SignatureFileVerifier.isSigningRelated
        if(SignatureFileVerifier.isSigningRelated(SUBDIR_SF_PATH)) {
            throw new Exception("Signature related file does not reside directly in META-INF/ : " + SUBDIR_SF_PATH);
        }

        // 3: Check JarInputStream with doVerify = true
        try(JarInputStream in = new JarInputStream(new FileInputStream(m), true)) {
            while( in.getNextEntry() != null) {
                in.transferTo(OutputStream.nullOutputStream());
            }
        }

        // 4: Check that a JAR containing unrelated .SF, .RSA files is signed as-if it is unsigned
        try(ZipFile zf = new ZipFile(sm)) {
            final ZipEntry mf = zf.getEntry("META-INF/MANIFEST.MF");
            try(InputStream stream = zf.getInputStream(mf)) {
                final String manifest = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                // When JarSigner considers a jar to not be already signed,
                // the 'Manifest-Version' attributed name will be case-normalized
                // Assert that manifest-version is not in lowercase
                if(manifest.startsWith("manifest-version")) {
                    throw new Exception("JarSigner unexpectedly treated unsigned jar as signed");
                }
            }
        }

        // 5: Check that a JAR containing non signature related .SF, .RSA files can be signed
        try(JarFile jf = new JarFile(sm, true)) {
            checkSignedBy(jf, "a.txt", "CN=SIGNER2");
            checkSignedBy(jf, "META-INF/subdirectory/META-INF/SIGNER1.SF", "CN=SIGNER2");
        }

        // 6: Check that JarSigner does not move unrelated [SF,RSA] files to the beginning of signed JARs
        try(JarFile zf = new JarFile(sm)) {

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

            if(!expectedOrder.equals(actualOrder)) {
                String msg = ("""
                        Unexpected file order in JAR with unrelated SF,RSA files
                        Expected order: %s
                        Actual order: %s""")
                        .formatted(expectedOrder, actualOrder);
                throw new Exception(msg);
            }
        }
    }

    /**
     * Check that a path of a given JAR is signed once by the expected signer CN
     */
    private static void checkSignedBy(JarFile jf, String name, String expectedSigner) throws Exception {
        JarEntry je = jf.getJarEntry(name);

        // Read the contents to trigger verification
        try(InputStream in = jf.getInputStream(je)) {
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

        if(!actualSigner.equals(expectedSigner)) {
            throw new Exception(String.format("Expected %s to be signed by %s, was signed by %s", name, expectedSigner, actualSigner));
        }
    }

    /**
     * Create a jar file with a '*.SF' file residing in META-INF/subdirectory/
     */
    private static File createJarFile() throws Exception {

        File f = File.createTempFile("unrelated-signature-file-", ".jar");


        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try(JarOutputStream out = new JarOutputStream(new FileOutputStream(f), manifest)) {
            out.putNextEntry(new JarEntry("a.txt"));
            out.write("a".getBytes(StandardCharsets.UTF_8));
        }

        return f;
    }
    /**
     * Create a signed version of the given jar file
     */
    private static File signJarFile(File f, String signerName, String classifier) throws Exception {
        File s = File.createTempFile("unrelated-signature-files-" + classifier +"-", ".jar");

        new File("ks").delete();

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

        try(ZipFile in = new ZipFile(f);
            OutputStream out = new FileOutputStream(s)) {
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
    private static File moveSignatureRelated(File s) throws Exception {
        File m = File.createTempFile("unrelated-signature-files-modified-", ".jar");

        try(ZipFile in = new ZipFile(s);
            ZipOutputStream out = new ZipOutputStream(new FileOutputStream(m))) {

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
        try(InputStream zi = in.getInputStream(new ZipEntry(from))) {
            zi.transferTo(out);
        }
    }
}
