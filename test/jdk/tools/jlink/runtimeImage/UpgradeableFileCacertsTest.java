/*
 * Copyright (c) 2025, Red Hat, Inc.
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import jdk.test.lib.process.OutputAnalyzer;
import tests.Helper;

/*
 * @test
 * @summary Verify that no errors are reported for files that have been
 *          upgraded when linking from the run-time image
 * @requires (vm.compMode != "Xcomp" & os.maxMemory >= 2g)
 * @library ../../lib /test/lib
 * @modules java.base/jdk.internal.jimage
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.plugin
 *          jdk.jlink/jdk.tools.jimage
 * @build tests.* jdk.test.lib.process.OutputAnalyzer
 *        jdk.test.lib.process.ProcessTools
 * @run main/othervm -Xmx1g UpgradeableFileCacertsTest
 */
public class UpgradeableFileCacertsTest extends ModifiedFilesTest {

    /*
     * Generated with:
     * $ rm -f server.keystore && keytool -genkey -alias jlink-upgrade-test \
     *                              -keyalg RSA -dname CN=jlink-upgrade-test \
     *                              -storepass changeit -keysize 3072 -sigalg SHA512withRSA \
     *                              -validity 7300 -keystore server.keystore
     * $ keytool -export -alias jlink-upgrade-test -storepass changeit \
     *           -keystore server.keystore -rfc
     */
    private static final String CERT = """
            -----BEGIN CERTIFICATE-----
            MIID3jCCAkagAwIBAgIJALiT/+HXBkSIMA0GCSqGSIb3DQEBDQUAMB0xGzAZBgNV
            BAMTEmpsaW5rLXVwZ3JhZGUtdGVzdDAeFw0yNTA0MDQxMjA3MjJaFw00NTAzMzAx
            MjA3MjJaMB0xGzAZBgNVBAMTEmpsaW5rLXVwZ3JhZGUtdGVzdDCCAaIwDQYJKoZI
            hvcNAQEBBQADggGPADCCAYoCggGBANmrnCDKqSXEJRIiSi4yHWN97ILls3RqYjED
            la3AZTeXnZrrEIgSjVFUMxCztYqbWoVzKa2lov42Vue2BXVYffcQ8TKc2EJDNO+2
            uRKQZpsN7RI4QoVBR2Rq8emrO8CrdOQT7Hh4agxkN9AOvGKMFdt+fXeCIPIuflKP
            f+RfvhLfC2A70Y+Uu74C5uWgLloA/HF0SsVxf9KmqS9fZBQaiTYhKyoDghCRlWpa
            nPIHB1XVaRdw8aSpCuzIOQzSCTTlLcammJkBjbFwMZdQG7eglTWzIYryZwe/cyY2
            xctLVW3xhUHvnMFG+MajeFny2mxNu163Rxf/rBu4e7jRC/LGSU784nJGapq5K170
            WbaeceKp+YORJBviFFORrmkPIwIgE+iGCD6PD6Xwu8vcpeuTVDgsSWMlfgCL3NoI
            GXmdGiI2Xc/hQX7uzu3UBF6IcPDMTcYr2JKYbgu3v2/vDlJu3qO2ycUeePo5jhuG
            X2WgcHkb6uOU4W5qdbCA+wFPVZBuwQIDAQABoyEwHzAdBgNVHQ4EFgQUtMJM0+ct
            ssKqryRckk4YEWdYAZkwDQYJKoZIhvcNAQENBQADggGBAI8A6gJQ8wDx12sy2ZI4
            1q9b+WG6w3LcFEF6Fko5NBizhtfmVycQv4mBa/NJgx4DZmd+5d60gJcTp/hJXGY0
            LZyFilm/AgxsLNUUQLbHAV6TWqd3ODWwswAuew9sFU6izl286a9W65tbMWL5r1EA
            t34ZYVWZYbCS9+czU98WomH4uarRAOlzcEUui3ZX6ZcQxWbz/R2wtKcUPUAYnsqH
            JPivpE25G5xW2Dp/yeQTrlffq9OLgZWVz0jtOguBUMnsUsgCcpQZtqZX08//wtpz
            ohLHFGvpXTPbRumRasWWtnRR/QqGRT66tYDqybXXz37UtKZ8VKW0sv2ypVbmAEs5
            pLkA/3XiXlstJuCD6cW0Gfbpb5rrPPD46O3FDVlmqlTH3b/MsiQREdydqGzqY7uG
            AA2GFVaKFASA5ls01CfHLAcrKxSVixditXvsjeIqhddB7Pnbsx20RdzPQoeo9/hF
            WeIrh4zePDPZChuLR8ZyxeVJhLB71nTrTDDjwXarVez9Xw==
            -----END CERTIFICATE-----
            """;

    private static final String CERT_ALIAS = "jlink-upgrade-test";

    public static void main(String[] args) throws Exception {
        UpgradeableFileCacertsTest test = new UpgradeableFileCacertsTest();
        test.run();
    }

    @Override
    String initialImageName() {
        return "java-base-jlink-upgrade-cacerts";
    }

    @Override
    void testAndAssert(Path modifiedFile, Helper helper, Path initialImage) throws Exception {
        CapturingHandler handler = new CapturingHandler();
        jlinkUsingImage(new JlinkSpecBuilder()
                                .helper(helper)
                                .imagePath(initialImage)
                                .name("java-base-jlink-upgrade-cacerts-target")
                                .addModule("java.base")
                                .validatingModule("java.base")
                                .build(), handler);
        OutputAnalyzer analyzer = handler.analyzer();
        // verify we don't get any modified warning
        analyzer.stdoutShouldNotContain(modifiedFile.toString() + " has been modified");
        analyzer.stdoutShouldNotContain("java.lang.IllegalArgumentException");
        analyzer.stdoutShouldNotContain("IOException");
    }

    // Add an extra certificate in the cacerts file so that it no longer matches
    // the recorded hash sum at build time.
    protected Path modifyFileInImage(Path jmodLessImg)
            throws IOException, AssertionError {
        Path cacerts = jmodLessImg.resolve(Path.of("lib", "security", "cacerts"));
        try (FileInputStream fin = new FileInputStream(cacerts.toFile())) {
            KeyStore certStore = KeyStore.getInstance(cacerts.toFile(),
                                                      (char[])null);
            certStore.load(fin, (char[])null);
            X509Certificate cert;
            try (ByteArrayInputStream bin = new ByteArrayInputStream(CERT.getBytes())) {
                cert = (X509Certificate)generateCertificate(bin);
            } catch (ClassCastException | CertificateException ce) {
                throw new AssertionError("Test failed unexpectedly", ce);
            }
            certStore.setCertificateEntry(CERT_ALIAS, cert);
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            certStore.store(bout, (char[])null);
            try (FileOutputStream fout = new FileOutputStream(cacerts.toFile())) {
                fout.write(bout.toByteArray());
            }
        } catch (Exception e) {
            throw new AssertionError("Test failed unexpectedly: ", e);
        }
        return cacerts;
    }

    private Certificate generateCertificate(InputStream in)
            throws CertificateException, IOException {
        byte[] data = in.readAllBytes();
        return CertificateFactory.getInstance("X.509")
                                 .generateCertificate(new ByteArrayInputStream(data));
    }
}
