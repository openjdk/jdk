/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6976117
 * @summary SSLContext.getInstance("TLSv1.1") returns SSLEngines/SSLSockets
 *          without TLSv1.1 enabled
 */

import javax.net.ssl.*;

public class SSLContextVersion {
    static enum ContextVersion {
        TLS_CV_01("SSL", "TLSv1", "TLSv1.2"),
        TLS_CV_02("TLS", "TLSv1", "TLSv1.2"),
        TLS_CV_03("SSLv3", "TLSv1", "TLSv1.2"),
        TLS_CV_04("TLSv1", "TLSv1", "TLSv1.2"),
        TLS_CV_05("TLSv1.1", "TLSv1.1", "TLSv1.2"),
        TLS_CV_06("TLSv1.2", "TLSv1.2", "TLSv1.2"),
        TLS_CV_07("Default", "TLSv1", "TLSv1.2");

        final String contextVersion;
        final String defaultProtocolVersion;
        final String supportedProtocolVersion;

        ContextVersion(String contextVersion, String defaultProtocolVersion,
                String supportedProtocolVersion) {
            this.contextVersion = contextVersion;
            this.defaultProtocolVersion = defaultProtocolVersion;
            this.supportedProtocolVersion = supportedProtocolVersion;
        }
    }

    public static void main(String[] args) throws Exception {
        for (ContextVersion cv : ContextVersion.values()) {
            System.out.println("Checking SSLContext of " + cv.contextVersion);
            SSLContext context = SSLContext.getInstance(cv.contextVersion);

            // Default SSLContext is initialized automatically.
            if (!cv.contextVersion.equals("Default")) {
                // Use default TK, KM and random.
                context.init((KeyManager[])null, (TrustManager[])null, null);
            }

            SSLParameters parameters = context.getDefaultSSLParameters();

            String[] protocols = parameters.getProtocols();
            String[] ciphers = parameters.getCipherSuites();

            if (protocols.length == 0 || ciphers.length == 0) {
                throw new Exception("No default protocols or cipher suites");
            }

            boolean isMatch = false;
            for (String protocol : protocols) {
                System.out.println("\tdefault protocol version " + protocol);
                if (protocol.equals(cv.defaultProtocolVersion)) {
                    isMatch = true;
                    break;
                }
            }

            if (!isMatch) {
                throw new Exception("No matched default protocol");
            }

            parameters = context.getSupportedSSLParameters();

            protocols = parameters.getProtocols();
            ciphers = parameters.getCipherSuites();

            if (protocols.length == 0 || ciphers.length == 0) {
                throw new Exception("No default protocols or cipher suites");
            }

            isMatch = false;
            for (String protocol : protocols) {
                System.out.println("\tsupported protocol version " + protocol);
                if (protocol.equals(cv.supportedProtocolVersion)) {
                    isMatch = true;
                    break;
                }
            }

            if (!isMatch) {
                throw new Exception("No matched default protocol");
            }
            System.out.println("\t... Success");
        }
    }
}
