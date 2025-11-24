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

/**
 * @test
 * @bug 8353835
 * @summary Test native thread attaching to the VM with JNI AttachCurrentThread and directly
 *    invoking Field.set to set a final field
 * @library /test/lib
 * @build m/*
 * @compile JNIAttachMutator.java
 * @run junit JNIAttachMutatorTest
 */

import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

class JNIAttachMutatorTest {
    private static String testClasses;
    private static String modulesDir;
    private static String javaLibraryPath;

    @BeforeAll
    static void setup() {
        testClasses = System.getProperty("test.classes");
        modulesDir = Path.of(testClasses, "modules").toString();
        javaLibraryPath = System.getProperty("java.library.path");
    }

    /**
     * Final final mutation allowed. All final fields are public, in public classes,
     * and in packages exported to all modules.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "JNIAttachMutator$C1",     // unnamed module
            "p.C1",                    // named module
    })
    void testAllowed(String cn) throws Exception {
        test(cn, false);
    }

    /**
     * Final final mutation not allowed.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            // unnamed module
            "JNIAttachMutator$C2",      // public class, non-public final field
            "JNIAttachMutator$C3",      // non-public class, public final field

            // named module
            "p.C2",                     // public class, non-public final field, exported package
            "p.C3",                     // non-public class, public final field, exported package
            "q.C"                       // public class, public final field, package not exported
    })
    void testDenied(String cn) throws Exception {
        test(cn, true);
    }

    /**
     * public final field, public class, package exported to some modules.
     */
    @Test
    void testQualifiedExports() throws Exception {
        test("q.C", true, "--add-exports", "m/q=ALL-UNNAMED");
    }

    /**
     * Launches JNIAttachMutator to test a JNI attached thread mutating a final field.
     * @param className the class with the field final
     * @param expectIAE if IllegalAccessException is expected
     * @param extraOps additional VM options
     */
    private void test(String className, boolean expectIAE, String... extraOps) throws Exception {
        Stream<String> s1 = Stream.of(extraOps);
        Stream<String> s2 = Stream.of(
                "-cp", testClasses,
                "-Djava.library.path=" + javaLibraryPath,
                "--module-path", modulesDir,
                "--add-modules", "m",
                "--add-opens", "m/p=ALL-UNNAMED",    // allow setAccessible
                "--add-opens", "m/q=ALL-UNNAMED",
                "--enable-native-access=ALL-UNNAMED",
                "--enable-final-field-mutation=ALL-UNNAMED",
                "--illegal-final-field-mutation=deny",
                "JNIAttachMutator",
                className,
                expectIAE ? "true" : "false");
        String[] opts = Stream.concat(s1, s2).toArray(String[]::new);
        OutputAnalyzer outputAnalyzer = ProcessTools
                .executeTestJava(opts)
                .outputTo(System.out)
                .errorTo(System.out);
        outputAnalyzer.shouldHaveExitValue(0);
    }
}
