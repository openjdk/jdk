/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8335288 8348732
 * @library /test/lib ..
 * @modules jdk.crypto.cryptoki
 * @summary check that if any required mech is unavailable, then the
 * mechanism will be unavailable as well.
 * @run testng/othervm RequiredMechCheck
 */
import java.nio.file.Path;
import java.security.Provider;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;

import jtreg.SkippedException;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RequiredMechCheck extends PKCS11Test {

    private static record TestData(String serviceType, String algo,
            boolean disabled) {}

    private static TestData[] testValues = {
        new TestData("MAC", "HmacPBESHA1", false),
        new TestData("MAC", "HmacPBESHA224", true),
        new TestData("MAC", "HmacPBESHA256", true),
        new TestData("MAC", "HmacPBESHA384", false),
        new TestData("MAC", "HmacPBESHA512", false),
        new TestData("SKF", "PBKDF2WithHmacSHA1", false),
        new TestData("SKF", "PBKDF2WithHmacSHA224", true),
        new TestData("SKF", "PBKDF2WithHmacSHA256", true),
        new TestData("SKF", "PBKDF2WithHmacSHA384", false),
        new TestData("SKF", "PBKDF2WithHmacSHA512", false),
        new TestData("CIP", "PBEWithHmacSHA1AndAES_128", false),
        new TestData("CIP", "PBEWithHmacSHA224AndAES_128", true),
        new TestData("CIP", "PBEWithHmacSHA256AndAES_128", true),
        new TestData("CIP", "PBEWithHmacSHA384AndAES_128", false),
        new TestData("CIP", "PBEWithHmacSHA512AndAES_128", false),
    };

    @BeforeClass
    public void setUp() throws Exception {
        Path configPath = Path.of(BASE).resolve("RequiredMechCheck.cfg");
        System.setProperty("CUSTOM_P11_CONFIG", configPath.toString());
    }

    @Test
    public void test() throws Exception {
        try {
            main(new RequiredMechCheck());
        } catch (SkippedException se) {
            throw new SkipException("One or more tests are skipped");
        }
    }

    public void main(Provider p) throws Exception {
        for (TestData td : testValues) {
            String desc = td.serviceType + " " + td.algo;
            Object t;
            try {
                switch (td.serviceType) {
                    case "MAC":
                        t = Mac.getInstance(td.algo, p);
                    break;
                    case "SKF":
                        t = SecretKeyFactory.getInstance(td.algo, p);
                    break;
                    case "CIP":
                        t = Cipher.getInstance(td.algo, p);
                    break;
                    default:
                        throw new RuntimeException("Unsupported Test Type!");
                }

                if (td.disabled) {
                    throw new RuntimeException("Fail, no NSAE for " + desc);
                } else {
                    System.out.println("Ok, getInstance() works for " + desc);
                }
            } catch (NoSuchAlgorithmException e) {
                if (td.disabled) {
                    System.out.println("Ok, NSAE thrown for " + desc);
                } else {
                    throw new RuntimeException("Unexpected Ex for " + desc, e);
                }
            }
        }
    }
}
