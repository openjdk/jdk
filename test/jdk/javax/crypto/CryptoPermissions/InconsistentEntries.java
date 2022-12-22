/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

/**
 * @test
 * @bug 8286779
 * @summary Test limited/default_local.policy containing inconsistent entries
 * @run main/manual InconsistentEntries
 */
import javax.crypto.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Security;

public class InconsistentEntries {

    public static void main(String[] args) throws Exception {
        System.out.println("***********************************************************");
        System.out.println("// This is a manual test to test a custom \"default_local.policy\" containing inconsistent entries");
        System.out.println("// under a new subfolder \"$JAVA_HOME/conf/security/policy\" directory.");
        System.out.println("// This test fails when the policy directory \"testlimited\" or the policy \"default_local.policy");
        System.out.println("// does not exist or is empty.");
        System.out.println("// - Create a new subfolder \"testlimited\" under \"$JAVA_HOME/conf/security/policy\"");
        System.out.println("// - Place the custom \"default_local.policy\" under \"testlimited\" directory");
        System.out.println("// - default_local.policy contains:");
        System.out.println("//   grant {");
        System.out.println("//       permission javax.crypto.CryptoAllPermission;");
        System.out.println("//       permission javax.crypto.CryptoPermission \"DES\", 64;");
        System.out.println("//   };");
        System.out.println("***********************************************************");

        String JAVA_HOME = System.getProperty("java.home");
        String FS = System.getProperty("file.separator");
        Path testlimited = Path.of(JAVA_HOME + FS + "conf" + FS + "security" +
                FS + "policy" + FS + "testlimited");
        if (!Files.exists(testlimited)) {
            throw new RuntimeException("custom policy subdirectory: testlimited does not exist");
        }

        File testpolicy = new File(JAVA_HOME + FS + "conf" + FS + "security" +
                FS + "policy" + FS + "testlimited" + FS + "default_local.policy");
        if (testpolicy.length() == 0) {
            throw new RuntimeException("policy: default_local.policy does not exist or is empty");
        }

        Security.setProperty("crypto.policy", "testlimited");

        try {
            int maxKeyLen = Cipher.getMaxAllowedKeyLength("AES");
            throw new RuntimeException("Should fail due to inconsistent entries in policy file");
        } catch (ExceptionInInitializerError e) {
            e.printStackTrace();
            System.out.println("Test completed successfully");
        }
    }
}
