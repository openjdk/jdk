/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8366364
 * @summary Return enabled signature schemes with
 *          SSLConfiguration#getSSLParameters() call
 * @library /javax/net/ssl/templates
 *          /test/lib
 * @comment *_sha224 signatures schemes are not available on Windows
 * @requires os.family != "windows"
 *
 * @run main/othervm DefaultSSLConfigSignatureSchemes
 * @run main/othervm
 *          -Djdk.tls.server.SignatureSchemes=ecdsa_secp384r1_sha384,ed25519
 *          -Djdk.tls.client.SignatureSchemes=ecdsa_secp256r1_sha256,ed448
 *          DefaultSSLConfigSignatureSchemes
 */

import static jdk.test.lib.Asserts.assertFalse;
import static jdk.test.lib.Asserts.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import javax.net.ssl.SSLEngine;
import jdk.test.lib.security.SecurityUtils;

public class DefaultSSLConfigSignatureSchemes extends SSLEngineTemplate {

    protected static final String DISABLED_SS = "ecdsa_secp521r1_sha512";
    protected static final String[] CUSTOM_SS = new String[]{
            "ecdsa_secp256r1_sha256",
            "ecdsa_secp384r1_sha384"};
    protected static final List<String> REFERENCE_SS = Stream.of(
                    "ecdsa_secp256r1_sha256",
                    "ecdsa_secp384r1_sha384",
                    "ecdsa_secp521r1_sha512",
                    "ed25519",
                    "ed448",
                    "rsa_pss_rsae_sha256",
                    "rsa_pss_rsae_sha384",
                    "rsa_pss_rsae_sha512",
                    "rsa_pss_pss_sha256",
                    "rsa_pss_pss_sha384",
                    "rsa_pss_pss_sha512",
                    "rsa_pkcs1_sha256",
                    "rsa_pkcs1_sha384",
                    "rsa_pkcs1_sha512",
                    "dsa_sha256",
                    "ecdsa_sha224",
                    "rsa_sha224",
                    "dsa_sha224",
                    "ecdsa_sha1",
                    "rsa_pkcs1_sha1",
                    "dsa_sha1")
            .sorted()
            .toList();

    protected DefaultSSLConfigSignatureSchemes() throws Exception {
        super();
    }

    public static void main(String[] args) throws Exception {
        SecurityUtils.addToDisabledTlsAlgs(DISABLED_SS);
        var test = new DefaultSSLConfigSignatureSchemes();
        var propertyClientSS =
                System.getProperty("jdk.tls.client.SignatureSchemes");
        var propertyServerSS =
                System.getProperty("jdk.tls.server.SignatureSchemes");

        // Test jdk.tls.client.SignatureSchemes system property.
        if (propertyClientSS != null) {
            comparePropertySSWithEngineSS(propertyClientSS, test.clientEngine);
        }

        // Test jdk.tls.server.SignatureSchemes system property.
        if (propertyServerSS != null) {
            comparePropertySSWithEngineSS(propertyServerSS, test.serverEngine);
        }

        // Test default signature schemes and custom values if no system
        // properties are present.
        if (propertyClientSS == null && propertyServerSS == null) {
            for (SSLEngine engine :
                    new SSLEngine[]{test.serverEngine, test.clientEngine}) {

                // Test default config signature schemes.
                checkEngineDefaultSS(engine);

                // Test custom values.
                var sslParams = engine.getSSLParameters();
                sslParams.setSignatureSchemes(CUSTOM_SS);
                engine.setSSLParameters(sslParams);
                assertTrue(Arrays.equals(CUSTOM_SS,
                        engine.getSSLParameters().getSignatureSchemes()));

                // Set null custom value, default signature schemes should
                // be returned.
                sslParams.setSignatureSchemes(null);
                engine.setSSLParameters(sslParams);
                checkEngineDefaultSS(engine);
            }
        }
    }

    private static void comparePropertySSWithEngineSS(
            String property, SSLEngine engine) {
        var engineSS = Stream.of(engine
                .getSSLParameters().getSignatureSchemes()).sorted().toList();
        var propertySS = Stream.of(property.split(",")).sorted().toList();
        assertTrue(engineSS.equals(propertySS), "Engine signature scheme: "
                + engineSS + "; Property signature scheme: " + propertySS);
    }

    private static void checkEngineDefaultSS(SSLEngine engine) {
        var defaultConfigSS = new ArrayList<>(List.of(
                engine.getSSLParameters().getSignatureSchemes()));

        assertFalse(defaultConfigSS.contains(DISABLED_SS));
        defaultConfigSS.add(DISABLED_SS);
        assertTrue(REFERENCE_SS.equals(
                        defaultConfigSS.stream().sorted().toList()),
                "Signature schemes returned by engine: " + defaultConfigSS);
    }
}
