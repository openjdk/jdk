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
 * @bug 8353835
 * @summary Test JNI SetXXXField methods to set final instance and final static fields
 * @modules java.management
 * @library /test/lib
 * @compile MutateFinals.java
 * @run junit/native MutateFinalsTest
 */

import java.lang.management.ManagementFactory;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assumptions.*;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

class MutateFinalsTest {

    static String javaLibraryPath;

    @BeforeAll
    static void init() {
        javaLibraryPath = System.getProperty("java.library.path");
    }

    static Stream<String> methodNames() {
        return Stream.of(
                "testJniSetObjectField",
                "testJniSetBooleanField",
                "testJniSetByteField",
                "testJniSetCharField",
                "testJniSetShortField",
                "testJniSetIntField",
                "testJniSetLongField",
                "testJniSetFloatField",
                "testJniSetDoubleField",
                "testJniSetStaticObjectField",
                "testJniSetStaticBooleanField",
                "testJniSetStaticByteField",
                "testJniSetStaticCharField",
                "testJniSetStaticShortField",
                "testJniSetStaticIntField",
                "testJniSetStaticLongField",
                "testJniSetStaticFloatField",
                "testJniSetStaticDoubleField"
        );
    }

    /**
     * Mutate final fields with JNI.
     */
    @ParameterizedTest
    @MethodSource("methodNames")
    void testMutateFinal(String methodName) throws Exception {
        assumeFalse(ManagementFactory.getRuntimeMXBean()
                .getInputArguments()
                .contains("-Xcheck:jni"), "Skip when running with -Xcheck:jni");
        MutateFinals.invoke(methodName);
    }

    /**
     * Launch child VM with -Xcheck:jni to attempt to mutate final fields with JNI. The
     * child VM is expected to exit with a fatal error.
     */
    @ParameterizedTest
    @MethodSource("methodNames")
    void testMutateFinalWithXCheckJni(String methodName) throws Exception {
        test(methodName, "-Xcheck:jni")
                .shouldContain("FATAL ERROR in native method")
                .shouldContain("attempting to mutate final")
                .shouldNotHaveExitValue(0);
    }

    private OutputAnalyzer test(String methodName, String... vmopts) throws Exception {
        Stream<String> s1 = Stream.of(
                "-Djava.library.path=" + javaLibraryPath,
                "--enable-native-access=ALL-UNNAMED");
        Stream<String> s2 = Stream.of(vmopts);
        Stream<String> s3 = Stream.of("MutateFinals", methodName);
        String[] opts = Stream.concat(Stream.concat(s1, s2), s3).toArray(String[]::new);
        var outputAnalyzer = ProcessTools
                .executeTestJava(opts)
                .outputTo(System.err)
                .errorTo(System.err);
        return outputAnalyzer;
    }
}