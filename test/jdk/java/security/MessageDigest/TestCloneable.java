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
 * @bug 8246077 8300416
 * @summary Make sure that digest spi and the resulting digest impl are
 * consistent in the impl of Cloneable interface, and that clones do not
 * share memory.
 * @run testng TestCloneable
 */
import java.nio.ByteBuffer;
import java.security.*;
import java.util.Arrays;
import java.util.Random;
import java.util.Objects;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.Assert;

public class TestCloneable {

    private static final Class<CloneNotSupportedException> CNSE =
            CloneNotSupportedException.class;

    private static String providerName = System.getProperty("test.provider.name", "SUN");

    @DataProvider
    public Object[][] testData() {
        return new Object[][] {
            { "MD2", providerName }, { "MD5", providerName }, { "SHA-1", providerName },
            { "SHA-224", providerName }, { "SHA-256", providerName },
            { "SHA-384", providerName }, { "SHA-512", providerName },
            { "SHA3-224", providerName }, { "SHA3-256", providerName },
            { "SHA3-384", providerName }, { "SHA3-512", providerName }
        };
    }

    @Test(dataProvider = "testData")
    public void test(String algo, String provName)
            throws NoSuchProviderException, NoSuchAlgorithmException,
            CloneNotSupportedException, InterruptedException {
        System.out.print("Testing " + algo + " impl from " + provName);
        Provider p = Security.getProvider(provName);
        Provider.Service s = p.getService("MessageDigest", algo);
        Objects.requireNonNull(s);
        MessageDigestSpi spi = (MessageDigestSpi) s.newInstance(null);
        MessageDigest md = MessageDigest.getInstance(algo, provName);
        if (spi instanceof Cloneable) {
            System.out.println(": Cloneable");
            Assert.assertTrue(md instanceof Cloneable);
            MessageDigest md2 = (MessageDigest) md.clone();
            Assert.assertEquals(md2.getAlgorithm(), algo);
            Assert.assertEquals(md2.getProvider().getName(), provName);
            Assert.assertTrue(md2 instanceof Cloneable);
        } else {
            System.out.println(": NOT Cloneable");
            Assert.assertThrows(CNSE, ()->md.clone());
        }

        System.out.print("Testing " + algo + " impl from " + provName);
        final var d1 = MessageDigest.getInstance(algo, provName);
        final var buffer = ByteBuffer.allocateDirect(1024);
        final var r = new Random(1024);

        fillBuffer(r, buffer);
        d1.update(buffer); // this statement triggers tempArray allocation
        final var d2 = (MessageDigest) d1.clone();
        assert Arrays.equals(d1.digest(), d2.digest());

        final var t1 = updateThread(d1);
        final var t2 = updateThread(d2);
        t1.join();
        t2.join();

        System.out.println(": Shared data check");
        // Random is producing the same sequence of bytes for each thread,
        // and thus each MessageDigest should be equal. When the memory is
        // shared, they inevitably overwrite each other's tempArray and
        // you get different results.
        if (!Arrays.equals(d1.digest(), d2.digest())) {
            throw new AssertionError("digests differ");
        }

        System.out.println("Test Passed");
    }

    private static void fillBuffer(final Random r, final ByteBuffer buffer) {
        final byte[] bytes = new byte[buffer.capacity()];
        r.nextBytes(bytes);
        buffer.clear();
        buffer.put(bytes);
        buffer.flip();
    }

    public static Thread updateThread(final MessageDigest d) {
        final var t = new Thread(() -> {
            final var r = new Random(1024);
            final ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
            for (int i = 0; i < 1024; i++) {
                fillBuffer(r, buffer);
                d.update(buffer);
            }
        });
        t.start();
        return t;
    }
}
