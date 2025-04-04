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
 * @library /test/lib ..
 * @modules jdk.crypto.cryptoki
 *          java.base/sun.security.util
 * @run main/othervm TestSlice p11-nss-sensitive.txt
 * @enablePreview
 */

import jdk.test.lib.Asserts;
import sun.security.util.SliceableSecretKey;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.*;
import java.util.Arrays;

public class TestSlice extends PKCS11Test {
    public static void main(String[] args) throws Exception {
        main(new TestSlice(args[0]), args);
    }

    public TestSlice(String conf) throws IOException {
        copyNssCertKeyToClassesDir();
        setCommonSystemProps();
        System.setProperty("CUSTOM_P11_CONFIG",
                Path.of(System.getProperty("test.src", "."), "../nss/" + conf).toString());
        System.setProperty("TOKEN", "nss");
        System.setProperty("TEST", "basic");
    }

    @Override
    public void main(Provider p) throws Exception {
        var data = new byte[48];
        for (var i = 0; i < 48; i++) data[i] = (byte)i;
        var sk = new SecretKeySpec(data, "Generic");
        var hk = SecretKeyFactory.getInstance("Generic", p).translateKey(sk);
        if (hk instanceof SliceableSecretKey ssk) {
            var msg = "hello".getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < 16; i++) {
                var slice = ssk.slice("HmacSHA256", i, i + 32);
                System.out.println(slice);
                var enc = slice.getEncoded();
                var expected = Arrays.copyOfRange(data, i, i + 32);
                if (enc != null) {
                    // If extractable, just compare key material
                    Asserts.assertEqualsByteArray(expected, enc);
                } else {
                    // Otherwise, see if they are equivalent as Hmac key
                    var h1 = Mac.getInstance("HmacSHA256");
                    h1.init(new SecretKeySpec(expected, "HmacSHA256"));
                    h1.update(msg);
                    var m1 = h1.doFinal();
                    var h2 = Mac.getInstance("HmacSHA256", p);
                    h2.init(slice);
                    h2.update(msg);
                    var m2 = h2.doFinal();
                    Asserts.assertEqualsByteArray(m1, m2);
                }
            }
        } else {
            throw new Exception("This should be a SliceableSecretKey");
        }
    }
}
