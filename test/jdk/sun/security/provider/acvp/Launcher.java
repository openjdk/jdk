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
import jdk.test.lib.compiler.CompilerUtils;
import jdk.test.lib.json.JSONValue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Provider;
import java.security.Security;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * @test
 * @bug 8342442
 * @library /test/lib
 * @build jdk.test.lib.Asserts jdk.test.lib.json.JSONValue
 * @run main Launcher
 */
public class Launcher {

    public interface Test {
        /// Algorithms (as named in ACVP JSON files) this test supports
        List<String> supportedAlgs();
        /// Runs the KAT with an optional provider
        void run(JSONValue kat, Provider p) throws Exception;
    }

    private static Map<String, Test> tests = new HashMap<>();

    public static void main(String[] args) throws Exception {

        // This test runs on "internalProjection.json"-style files generated
        // by NIST's ACVP Server. See https://github.com/usnistgov/ACVP-Server.
        //
        // The files are either put into the "data" directory or another
        // directory specified by the "acvp.test.data" system property.
        // The test walks through the directory recursively and looks for
        // file names equals to or ends with "internalProjection.json" and
        // run test on it. Only very limited algorithms are supported.
        // Sample files can be downloaded from
        // https://github.com/usnistgov/ACVP-Server/tree/master/gen-val/json-files.
        //
        // By default, the test uses system-preferred implementations.
        // If you want to test on a specific provider, set the
        // "acvp.test.provider" system property. The provider must be
        // registered.
        //
        // Tests are including in this directory and each must implement
        // the Launcher.Test interface.

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

        setup();

        try (var stream = Files.walk(dataPath)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString()
                            .endsWith("internalProjection.json"))
                    .forEach(p -> run(provider, p));
        }
    }

    static void setup() throws Exception {
        var srcDir = Path.of(System.getProperty("test.src"));
        try (var files = Files.newDirectoryStream(srcDir)) {
            for (var file : files) {
                var name = file.getFileName().toString();
                if (!name.equals("Launcher.java") && name.endsWith(".java")) {
                    CompilerUtils.compile(file,
                            Path.of(System.getProperty("test.classes")),
                            "-cp",
                            System.getProperty("test.class.path"));
                    var obj = Class.forName(name.substring(0, name.length() - 5))
                            .getConstructor().newInstance();
                    if (obj instanceof Test t) {
                        for (var alg : t.supportedAlgs()) {
                            tests.put(alg, t);
                        }
                    } else {
                        throw new RuntimeException(
                                name + " has not implemented Test");
                    }
                }
            }
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
            var t = tests.get(alg);
            if (t != null) {
                t.run(kat, provider);
            } else {
                System.out.println("Skipped unsupported algorithm: " + alg);
            }
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
