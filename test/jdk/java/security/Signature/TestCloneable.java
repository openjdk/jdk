/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8246077
 * @summary Make sure that signature objects which are cloneable
 *         implement the Cloneable interface
 * @run testng TestCloneable
 */
import java.security.NoSuchProviderException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.Assert;

public class TestCloneable {

    private static final Class<CloneNotSupportedException> CNSE =
            CloneNotSupportedException.class;

    @DataProvider
    public Object[][] testData() {
        String dsaProviderName = System.getProperty("test.provider.name", "SUN");
        String ecProviderName  = System.getProperty("test.provider.name", "SunEC");
        String rsaProviderName = System.getProperty("test.provider.name", "SunRsaSign");

        return new Object[][] {
            { "SHA1withDSA", dsaProviderName }, { "NONEwithDSA", dsaProviderName },
            { "SHA224withDSA", dsaProviderName }, { "SHA256withDSA", dsaProviderName },
            { "EdDSA", ecProviderName }, { "Ed25519", ecProviderName }, { "Ed448", ecProviderName },
            { "SHA1withECDSA", ecProviderName }, { "SHA224withECDSA", ecProviderName },
            { "SHA256withECDSA", ecProviderName }, { "SHA384withECDSA", ecProviderName },
            { "SHA512withECDSA", ecProviderName }, { "NONEwithECDSA", ecProviderName },
            { "MD2withRSA", rsaProviderName }, { "MD5withRSA", rsaProviderName },
            { "SHA1withRSA", rsaProviderName }, { "SHA224withRSA", rsaProviderName },
            { "SHA256withRSA", rsaProviderName },
            { "SHA384withRSA", rsaProviderName },
            { "SHA512withRSA", rsaProviderName },
            { "SHA512/224withRSA", rsaProviderName },
            { "SHA512/256withRSA", rsaProviderName },
            { "RSASSA-PSS", rsaProviderName },
            { "NONEwithRSA", "SunMSCAPI" },
            { "SHA1withRSA", "SunMSCAPI" }, { "SHA256withRSA", "SunMSCAPI" },
            { "SHA384withRSA", "SunMSCAPI" }, { "SHA512withRSA", "SunMSCAPI" },
            { "RSASSA-PSS", "SunMSCAPI" },
            { "MD5withRSA", "SunMSCAPI" }, { "MD2withRSA", "SunMSCAPI" },
            { "SHA1withECDSA", "SunMSCAPI" },
            { "SHA224withECDSA", "SunMSCAPI" },
            { "SHA256withECDSA", "SunMSCAPI" },
            { "SHA384withECDSA", "SunMSCAPI" },
            { "SHA512withECDSA", "SunMSCAPI" }
        };
    }

    @Test(dataProvider = "testData")
    public void test(String algo, String provName)
            throws NoSuchAlgorithmException, CloneNotSupportedException {
        System.out.print("Testing " + algo + " impl from " + provName);
        try {
            Signature sig = Signature.getInstance(algo, provName);
            if (sig instanceof Cloneable) {
                System.out.println(": Cloneable");
                Signature sig2 = (Signature) sig.clone();
                Assert.assertEquals(sig2.getAlgorithm(), algo);
                Assert.assertEquals(sig2.getProvider().getName(), provName);
                Assert.assertTrue(sig2 instanceof Cloneable);
            } else {
                System.out.println(": NOT Cloneable");
                Assert.assertThrows(CNSE, ()->sig.clone());
            }
            System.out.println("Test Passed");
        } catch (NoSuchProviderException nspe) {
            // skip testing
            System.out.println("Skip " + provName + " - not available");
        }
    }
}
