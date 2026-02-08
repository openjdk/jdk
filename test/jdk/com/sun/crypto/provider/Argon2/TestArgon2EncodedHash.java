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
import java.util.Arrays;
import java.util.HexFormat;
import com.sun.crypto.provider.Argon2Impl;
import javax.crypto.SecretKey;
import javax.crypto.KDF;
import javax.crypto.spec.Argon2ParameterSpec;
import static javax.crypto.spec.Argon2ParameterSpec.Type;

/**
 * @test
 * @bug 8253914
 * @modules java.base/com.sun.crypto.provider:+open
 * @summary Test the Argon2 encoded hash parsing
 */
public class TestArgon2EncodedHash {

    record TestVector(String encodedStr, Argon2ParameterSpec.Type type,
            String inStr, String saltStr, int p, int m, int t, int outLen) {
        public Argon2ParameterSpec[] getParameters() {
            // return using both newBuilder factory methods
            byte[] pwdBytes = inStr.getBytes();
            Argon2ParameterSpec[] res = new Argon2ParameterSpec[2];
            res[0] = Argon2ParameterSpec.newBuilder(type)
                .nonce(saltStr.getBytes()).parallelism(p).memoryKB(m)
                .iterations(t).tagLen(outLen).build(pwdBytes);
            res[1] = Argon2ParameterSpec.newBuilder(encodedStr).build(pwdBytes);
            return res;
        }
    }

    private static TestVector[] TEST_VALUES = {
            new TestVector("$argon2i$v=19$m=16,t=2,p=1$d3VVZ0s3bnBIN25UbXVzQw$h682lr+siItjK7c6QJhhcw",
            Type.ARGON2I, "12345678", "wuUgK7npH7nTmusC",
            1, 16, 2, 16),
            new TestVector("$argon2d$v=19$m=16,t=2,p=1$d3VVZ0s3bnBIN25UbXVzQw$4piwyhK7cr9pZM1N88kyoQ",
            Type.ARGON2D, "12345678", "wuUgK7npH7nTmusC",
            1, 16, 2, 16),
            new TestVector("$argon2id$v=19$m=16,t=2,p=1$d3VVZ0s3bnBIN25UbXVzQw$y4JucCGmImWC66EyPsQNGg",
            Type.ARGON2ID, "12345678", "wuUgK7npH7nTmusC",
            1, 16, 2, 16),
    };

    private static void run(TestVector tv) throws Exception {
        Argon2ParameterSpec[] params = tv.getParameters();
        byte[] data = null;
        System.out.println("Testing " + tv.encodedStr());
        for (Argon2ParameterSpec p : params) {
            System.out.println("params = " + p);
            Argon2Impl res = new Argon2Impl(p.type());
            String encoded = res.deriveKey("Argon2", p).toString();
            if (!tv.encodedStr().equals(encoded)) {
                System.out.println("Actual: " + encoded);
                System.out.println("Expected: " + tv.encodedStr());
                throw new RuntimeException("Failed Key/Hash check");
            }
            if (data == null) {
                data = res.deriveData(p);
            } else {
                if (!Arrays.equals(data, res.deriveData(p))) {
                    throw new RuntimeException("Failed Data check");
                }
                data = null;
            }

            if (tv.type() == Type.ARGON2ID) {
                KDF kdf = KDF.getInstance(tv.type().name());
                String encoded2 = kdf.deriveKey("Argon2", p).toString();
                if (!tv.encodedStr().equals(encoded2)) {
                    System.out.println("Actual: " + encoded2);
                    System.out.println("Expected: " + tv.encodedStr());
                    throw new RuntimeException("Encoded Hash Check Failed!");
                }
            }
        }
    }

    public static void main(String[] argv) throws Exception {
        for (TestVector tv : TEST_VALUES) {
            run(tv);
        }
    }
}
