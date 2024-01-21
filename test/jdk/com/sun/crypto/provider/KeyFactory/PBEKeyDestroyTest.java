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

/*
 * @test
 * @bug 8312306
 * @summary Check the destroy()/isDestroyed() of the PBEKey impl from SunJCE
 * @library /test/lib
 * @run testng/othervm PBEKeyDestroyTest
 */
import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import org.testng.Assert;
import org.testng.annotations.Test;

public class PBEKeyDestroyTest {

    @Test
    public void test() throws Exception {
        PBEKeySpec keySpec = new PBEKeySpec("12345678".toCharArray(),
                "abcdefgh".getBytes(StandardCharsets.UTF_8), 100000, 128 >> 3);

        SecretKeyFactory skf = SecretKeyFactory.getInstance
                ("PBEWithHmacSHA1AndAES_128", "SunJCE");

        SecretKey key1 = skf.generateSecret(keySpec);
        SecretKey key2 = skf.generateSecret(keySpec);

        // should be equal
        Assert.assertFalse(key1.isDestroyed());
        Assert.assertFalse(key2.isDestroyed());
        Assert.assertTrue(key1.equals(key2));
        Assert.assertTrue(key2.equals(key1));

        // destroy key1
        key1.destroy();
        Assert.assertTrue(key1.isDestroyed());
        Assert.assertFalse(key1.equals(key2));
        Assert.assertFalse(key2.equals(key1));

        // also destroy key2
        key2.destroy();
        Assert.assertTrue(key2.isDestroyed());
        Assert.assertFalse(key1.equals(key2));
        Assert.assertFalse(key2.equals(key1));

        // call destroy again to make sure no unexpected exceptions
        key2.destroy();
    }
}
