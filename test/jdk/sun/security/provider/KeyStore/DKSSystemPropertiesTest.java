/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8007755 8374808
 * @library /test/lib
 * @summary Support the logical grouping of keystores
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import java.net.URI;
import java.nio.file.Paths;
import java.security.DomainLoadStoreParameter;
import java.security.KeyStore;
import java.util.Collections;
import java.util.Date;
import java.util.List;

// Load and store entries in domain keystores

public class DKSSystemPropertiesTest {

    private static final String TEST_SRC = System.getProperty("test.src");
    private static final String USER_DIR = System.getProperty("user.dir", ".");
    private static final String CONFIG = Paths.get(
            TEST_SRC, "domains.cfg").toUri().toString();

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            // Environment variable and system properties referred in domains.cfg used by this Test.
            ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                    List.of("-Dtest.src=" + TEST_SRC, "-Duser.dir=" + USER_DIR,
                            "DKSSystemPropertiesTest", "run"));
            pb.environment().putAll(System.getenv());
            pb.environment().put("KEYSTORE_PWD", "test12");
            pb.environment().put("TRUSTSTORE_PWD", "changeit");
            OutputAnalyzer output = ProcessTools.executeProcess(pb);
            output.shouldHaveExitValue(0);
            output.outputTo(System.out);
            return;
        }

        /*
         * domain keystore: system_env
         */
        URI config = new URI(CONFIG + "#system_env");
        KeyStore keystore = KeyStore.getInstance("DKS");
        // load entries
        keystore.load(
                new DomainLoadStoreParameter(config,
                        Collections.<String, KeyStore.ProtectionParameter>emptyMap()));

        int expected = keystore.size();

        System.out.println("\nLoading domain keystore: " + config + "\t[" +
                           expected + " entries]");
        checkEntries(keystore, expected);
    }

    static void checkEntries(KeyStore keystore, int expectedCount)
            throws Exception {
        int currCount = 0;
        for (String alias : Collections.list(keystore.aliases())) {
            System.out.print(".");
            currCount++;

            // check creation date and instant
            if (!keystore.getCreationDate(alias).equals(
                    Date.from(keystore.getCreationInstant(alias)))
            ) {
                throw new RuntimeException(
                        "Creation Date is not the same as Instant timestamp");
            }
        }
        System.out.println();
        // Check if current count is expected
        if (expectedCount != currCount) {
            throw new RuntimeException("Error: unexpected entry count in " +
                                       "keystore: loaded=" + currCount + ", " +
                                       "expected=" + expectedCount);
        }
    }

}
