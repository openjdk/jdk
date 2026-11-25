/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.Asserts;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.security.util.KeyUtil;

import java.security.AlgorithmParameters;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.Security;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.InvalidParameterSpecException;
import java.util.ArrayList;
import java.util.List;

/*
 * @test
 * @summary Tests for sun.security.util.KeyUtil
 * @library /test/lib
 * @modules java.base/sun.security.util
 * @run junit/othervm KeyUtilTests
 */
public class KeyUtilTests {

    @BeforeAll
    public static void setup() {
        // Register the custom provider
        Security.addProvider(new CustomSunEC());
    }

    @Test
    public void testProvidersAdded() {
        final List<String> providers = new ArrayList<>();
        for (final Provider provider : Security.getProviders()) {
            providers.add(provider.getName());
        }
        Assertions.assertTrue(providers.contains("CustomSunEC"));
    }

    @Test
    public void testGetKeySizeCustomSunEc()
            throws NoSuchAlgorithmException,
            NoSuchProviderException,
            InvalidParameterSpecException {

        final int keySize = 256;

        final KeyPairGenerator keyPairGenerator
                = KeyPairGenerator.getInstance("EC", "CustomSunEC");
        keyPairGenerator.initialize(keySize);
        final KeyPair keyPair = keyPairGenerator.generateKeyPair();

        final ECPrivateKey ecPrivateKey = (ECPrivateKey) keyPair.getPrivate();
        final AlgorithmParameters params
                = AlgorithmParameters.getInstance("EC", "CustomSunEC");
        params.init(ecPrivateKey.getParams());

        int keySizeResult = KeyUtil.getKeySize(params);
        Asserts.assertEquals(keySizeResult, keySize, "Key size is");
    }

    @Test
    public void testGetKeySizeSunEc()
            throws NoSuchAlgorithmException,
            NoSuchProviderException,
            InvalidParameterSpecException {

        final int keySize = 256;

        final KeyPairGenerator keyPairGenerator
                = KeyPairGenerator.getInstance("EC", "SunEC");
        keyPairGenerator.initialize(keySize);
        final KeyPair keyPair = keyPairGenerator.generateKeyPair();

        final ECPrivateKey ecPrivateKey = (ECPrivateKey) keyPair.getPrivate();
        final AlgorithmParameters params
                = AlgorithmParameters.getInstance("EC", "SunEC");
        params.init(ecPrivateKey.getParams());

        int keySizeResult = KeyUtil.getKeySize(params);
        Asserts.assertEquals(keySizeResult, keySize, "Key size is");
    }


}

class CustomSunEC extends Provider {
    public CustomSunEC() {
        super("CustomSunEC",
                "1.0",
                "Custom SunEC Provider");

        final Provider sunECProvider = Security.getProvider("SunEC");
        if (sunECProvider != null) {
            // Copy all service mappings from SunEC to CustomSunEC
            sunECProvider.getServices().forEach(service ->
                    putService(new Service(
                            this,
                            service.getType(),
                            service.getAlgorithm(),
                            service.getClassName(),
                            null,
                            null)));
        }
    }
}


