/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8312306 8358451
 * @summary Check the destroy()/isDestroyed() of the PBEKey impl from SunJCE
 * @library /test/lib
 * @run testng/othervm PBEKeyDestroyTest
 */
import java.io.ByteArrayOutputStream;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.security.InvalidKeyException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import org.testng.Assert;
import org.testng.annotations.Test;

public class PBEKeyDestroyTest {

    private static void printKeyInfo(SecretKey k, String name) {
        System.out.println(name);
        System.out.println("algo: " + k.getAlgorithm());
        System.out.println("format: " + k.getFormat());
        System.out.println("hashCode: " + k.hashCode());
    }

    @Test
    public void test() throws Exception {
        PBEKeySpec keySpec = new PBEKeySpec("12345678".toCharArray(),
                "abcdefgh".getBytes(StandardCharsets.UTF_8), 100000, 128 >> 3);

        SecretKeyFactory skf = SecretKeyFactory.getInstance
                ("PBEWithHmacSHA1AndAES_128", "SunJCE");

        SecretKey key1 = skf.generateSecret(keySpec);
        SecretKey key2 = skf.generateSecret(keySpec);

        printKeyInfo(key1, "key1");

        // both keys should be equal
        Assert.assertFalse(key1.isDestroyed());
        Assert.assertFalse(key2.isDestroyed());
        Assert.assertTrue(key1.equals(key2));
        Assert.assertTrue(key2.equals(key1));
        Assert.assertTrue(key1.hashCode() == key2.hashCode());

        // destroy key1
        key1.destroy();

        // make sure no exception when retrieving algo, format, hashCode
        printKeyInfo(key1, "destroyed key1");

        Assert.assertTrue(key1.isDestroyed());
        Assert.assertFalse(key1.equals(key2));
        Assert.assertFalse(key2.equals(key1));
        try {
            byte[] val = key1.getEncoded();
            throw new Exception("getEncoded() should error out, encoding = " +
                    Arrays.toString(val));
        } catch (IllegalStateException ise) {
            // expected exception
            System.out.println("Expected ISE is thrown for getEncoded()");
        }

        // serialization should fail
        ObjectOutputStream oos = new ObjectOutputStream(
                new ByteArrayOutputStream());
        try {
            oos.writeObject(key1);
            throw new Exception("Serialization should error out");
        } catch (NotSerializableException e) {
            // expected exception
            System.out.println("Expected NSE is thrown for serialization");
        }
        try {
            skf.translateKey(key1);
            throw new Exception("translateKey() should error out");
        } catch (InvalidKeyException ike) {
            // expected exception
            System.out.println("Expected IKE is thrown for translateKey()");
        }
        try {
            skf.getKeySpec(key1, PBEKeySpec.class);
            throw new Exception("getKeySpec() should error out");
        } catch (InvalidKeySpecException ikse) {
            // expected exception
            System.out.println("Expected IKSE is thrown for getKeySpec()");
        }

        // also destroy key2
        key2.destroy();
        Assert.assertTrue(key2.isDestroyed());
        Assert.assertFalse(key1.equals(key2));
        Assert.assertFalse(key2.equals(key1));

        // call destroy again to make sure no unexpected exceptions
        key2.destroy();
    }
}
