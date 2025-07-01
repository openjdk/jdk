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
 * @bug 8303613
 * @summary Check the destroy()/isDestroyed() of the EdDSA impl from SunEC
 * @library /test/lib
 * @run testng/othervm EdDSAPrivateKeyDestroyTest
 */

import org.testng.Assert;
import org.testng.annotations.Test;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;

public class EdDSAPrivateKeyDestroyTest {
    @Test
    public void test() throws Exception {
        KeyPairGenerator edDSAGen =
                KeyPairGenerator.getInstance("EdDSA", "SunEC");

        KeyPair kp = edDSAGen.generateKeyPair();

        PrivateKey priv1 = kp.getPrivate();

        KeySpec priv2Spec = new PKCS8EncodedKeySpec(priv1.getEncoded());
        KeyFactory edDSAFac2 = KeyFactory.getInstance("EdDSA", "SunEC");
        PrivateKey priv2 = edDSAFac2.generatePrivate(priv2Spec);


        // should be equal
        Assert.assertFalse(priv1.isDestroyed());
        Assert.assertFalse(priv2.isDestroyed());
        Assert.assertEquals(priv2, priv1);
        Assert.assertEquals(priv1, priv2);

        System.out.println("Past sanity checks");

        // destroy key1
        priv1.destroy();
        Assert.assertTrue(priv1.isDestroyed());
        Assert.assertNotEquals(priv2, priv1);
        Assert.assertNotEquals(priv1, priv2);

        System.out.println("Past destroy priv1");

        // also destroy key2
        priv2.destroy();
        Assert.assertTrue(priv2.isDestroyed());
        Assert.assertNotEquals(priv2, priv1);
        Assert.assertNotEquals(priv1, priv2);

        System.out.println("Past destroy priv2");

        // call destroy again to make sure no unexpected exceptions
        priv2.destroy();

    }
}
