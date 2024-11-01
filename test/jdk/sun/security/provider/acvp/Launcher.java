/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import jdk.test.lib.json.JSONValue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Provider;
import java.security.Security;

/*
 * @test
 * @bug 8342442
 * @library /test/lib
 */
public class Launcher {

    public static void main(String[] args) throws Exception {

        // This test runs on "internalProjection.json"-style files generated
        // by NIST's ACVP Server. See https://github.com/usnistgov/ACVP-Server.
        //
        // The files are either put into the "data" directory or another
        // directory specified by the "acvp.test.data" system property.
        // The test walks through the directory recursively and looks for
        // file names equals to or ending with "internalProjection.json" and
        // runs test on them. Only very limited algorithms are supported.
        // Sample files can be downloaded from
        // https://github.com/usnistgov/ACVP-Server/tree/master/gen-val/json-files.
        //
        // By default, the test uses system-preferred implementations.
        // If you want to test on a specific provider, set the
        // "acvp.test.provider" system property. The provider must be
        // registered.
        //
        // Tests for each algorithm must be compliant to its specification linked from
        // https://github.com/usnistgov/ACVP?tab=readme-ov-file#supported-algorithms.

        var testDataProp = System.getProperty("acvp.test.data");
        Path dataPath = testDataProp != null
                ? Path.of(testDataProp)
                : Path.of(System.getProperty("test.src"), "data");
        System.out.println("Data path: " + dataPath);

        var provProp = System.getProperty("acvp.test.provider");
        Provider provider = provProp != null
                ? Security.getProvider(provProp)
                : null;
        if (provider != null) {
            System.out.println("Provider: " + provProp);
        }

        try (var stream = Files.walk(dataPath)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString()
                            .endsWith("internalProjection.json"))
                    .forEach(p -> run(provider, p));
        }
    }

    static void run(Provider provider, Path test) {
        System.out.println(">>> Testing " + test + "...");
        try {
            JSONValue kat;
            try {
                kat = JSONValue.parse(Files.readString(test));
            } catch (Exception e) {
                System.out.println("Warning: cannot parse JSON. Skipped");
                return;
            }
            var alg = kat.get("algorithm").asString();
            switch (alg) {
                case "ML-DSA" -> ML_DSA_Test.run(kat, provider);
                case "ML-KEM" -> ML_KEM_Test.run(kat, provider);
                case "SHA2-256", "SHA2-224", "SHA3-256", "SHA3-224"
                    -> SHA_Test.run(kat, provider);
                default -> System.out.println("Skipped unsupported algorithm: " + alg);
            }
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
