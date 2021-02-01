/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8211227
 * @library ../../
 * @library /test/lib
 * @summary Tests for consistency in logging format of TLS Versions
 *
 * @run main LoggingFormatConsistency
 */

/*
 * This test runs in another process so we can monitor the debug
 * results.  The OutputAnalyzer must see correct debug output to return a
 * success.
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.security.SecurityUtils;

import javax.net.ssl.SSLHandshakeException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;

public class LoggingFormatConsistency {
    public static void main(String[] args) throws Exception {
        if (args.length == 0){

            var testSrc = "-Dtest.src=" + System.getProperty("test.src");
            var javaxNetDebug = "-Djavax.net.debug=all";
            var correctTlsVersionsFormat = new String[]{"TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3"};
            var incorrectTLSVersionsFormat = new String[]{"TLS10", "TLS11", "TLS12", "TLS13"};

            for (int i = 0; i < correctTlsVersionsFormat.length; i++) {
                String expectedTLSVersion = correctTlsVersionsFormat[i];
                String incorrectTLSVersion = incorrectTLSVersionsFormat[i];

                System.out.println("TESTING " + expectedTLSVersion);
                String activeTLSProtocol = "-Djdk.tls.client.protocols=" + expectedTLSVersion;
                var output = ProcessTools.executeTestJvm(
                        testSrc,
                        activeTLSProtocol,
                        javaxNetDebug,
                        "LoggingFormatConsistency",
                        "t");

                output.shouldContain(expectedTLSVersion);
                output.shouldNotContain(incorrectTLSVersion);
            }
        }
        else {
            var test = new LoggingFormatConsistency();
            test.simpleSSLConnectionTest();
        }
    }

    private void simpleSSLConnectionTest() throws Exception {
        // Re-enabling as test depends on these algorithms
        SecurityUtils.removeFromDisabledTlsAlgs("TLSv1", "TLSv1.1");
        var url = new URL("https://jpg-data.us.oracle.com/");
        try {
            var in = new BufferedReader(new InputStreamReader(url.openStream()));
        }
        catch(SSLHandshakeException sslEx) {
            System.out.println(sslEx.getMessage());
            System.out.println(sslEx.getCause());
        }
    }
}
