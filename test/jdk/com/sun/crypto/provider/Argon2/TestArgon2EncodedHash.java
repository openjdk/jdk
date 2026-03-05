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
import java.util.Locale;
import javax.crypto.KDF;
import javax.crypto.spec.Argon2ParameterSpec;
import com.sun.crypto.provider.Argon2Impl;
import com.sun.crypto.provider.Argon2DerivedKey;
import sun.security.util.Argon2Util;
import static sun.security.util.Argon2Util.Argon2Info;

/**
 * @test
 * @bug 8253914
 * @modules java.base/sun.security.util:+open java.base/com.sun.crypto.provider:+open
 * @summary Test the Argon2 encoded hash parsing
 */
public class TestArgon2EncodedHash {

    static class TestVector {
        final String encodedStr;
        final byte[] msg;
        Argon2Info info;

        TestVector(String encodedStr, String inStr) {
            this.encodedStr = encodedStr;
            this.msg = inStr.getBytes();
            this.info = Argon2Util.decodeHash(encodedStr);
        }

        public String type() {
            return info.algo().toUpperCase(Locale.ENGLISH);
        }

        public Argon2ParameterSpec getParameters() {
            return info.builder().build(msg);
        }
    }

    private static TestVector[] TEST_VALUES = {
            new TestVector("$argon2i$v=19$m=16,t=2,p=1$d3VVZ0s3bnBIN25UbXVzQw$h682lr+siItjK7c6QJhhcw", "12345678"),
            new TestVector("$argon2d$v=19$m=16,t=2,p=1$d3VVZ0s3bnBIN25UbXVzQw$4piwyhK7cr9pZM1N88kyoQ", "12345678"),
            new TestVector("$argon2id$v=19$m=16,t=2,p=1$d3VVZ0s3bnBIN25UbXVzQw$y4JucCGmImWC66EyPsQNGg", "12345678"),
    };

    private static void run(TestVector tv) throws Exception {
        String expected = tv.encodedStr;
        System.out.println("Testing " + expected);
        Argon2ParameterSpec params = tv.getParameters();
        System.out.println("params = " + params);
        String algo = tv.type();
        Argon2Impl res = new Argon2Impl(algo);
        byte[] bytes = res.derive(params);
        String encoded2 = new Argon2DerivedKey(algo, params, bytes,
                "Generic").toString();
        if (!expected.equals(encoded2)) {
            throw new RuntimeException("Failed! got " + encoded2);
        }

        if (algo.equals("ARGON2ID")) {
            KDF kdf = KDF.getInstance(algo);
            encoded2 = kdf.deriveKey("Generic", params).toString();
            if (!expected.equals(encoded2)) {
                throw new RuntimeException("Failed! got " + encoded2);
            }
        }
    }

    public static void main(String[] argv) throws Exception {
        for (TestVector tv : TEST_VALUES) {
            run(tv);
        }
    }
}
