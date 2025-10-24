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
 * @key randomness
 * @modules java.management
 * @library /test/lib
 * @compile MutateFinals.java
 * @run junit/native/timeout=300 MutateFinalsTest
 */

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

class MutateFinalsTest {

    static String javaLibraryPath;

    @BeforeAll
    static void init() {
        javaLibraryPath = System.getProperty("java.library.path");
    }

    /**
     * The names of the test methods that use JNI to set final instance fields.
     */
    static Stream<String> mutateInstanceFieldMethods() {
        return Stream.of(
                "testJniSetObjectField",
                "testJniSetBooleanField",
                "testJniSetByteField",
                "testJniSetCharField",
                "testJniSetShortField",
                "testJniSetIntField",
                "testJniSetLongField",
                "testJniSetFloatField",
                "testJniSetDoubleField"
        );
    }

    /**
     * The names of the test methods that use JNI to set final static fields.
     */
    static Stream<String> mutateStaticFieldMethods() {
        return Stream.of(
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
     * The names of all test methods that use JNI to set final fields.
     */
    static Stream<String> allMutationMethods() {
        return Stream.concat(mutateInstanceFieldMethods(), mutateStaticFieldMethods());
    }

    /**
     * Mutate a final field with JNI.
     */
    @ParameterizedTest
    @MethodSource("allMutationMethods")
    void testMutateFinal(String methodName) throws Exception {
        MutateFinals.invoke(methodName);
    }

    /**
     * Mutate a final instance field with JNI. The test launches a child VM with -Xcheck:jni
     * and expects a warning in the output.
     */
    @ParameterizedTest
    @MethodSource("mutateInstanceFieldMethods")
    void testMutateInstanceFinalWithXCheckJni(String methodName) throws Exception {
        test(methodName, "-Xcheck:jni")
            .shouldContain("WARNING in native method: Set<Type>Field called to mutate final instance field")
            .shouldHaveExitValue(0);
    }

    /**
     * Mutate final static fields with JNI. The test launches a child VM with -Xcheck:jni
     * and expects a warning in the output.
     */
    @ParameterizedTest
    @MethodSource("mutateStaticFieldMethods")
    void testMutateStaticFinalWithXCheckJni(String methodName) throws Exception {
        test(methodName, "-Xcheck:jni")
            .shouldContain("WARNING in native method: SetStatic<Type>Field called to mutate final static field")
            .shouldHaveExitValue(0);
    }

    /**
     * Mutate a final instance field with JNI. The test launches a child VM with -Xlog
     * and expects a log message in the output.
     */
    @ParameterizedTest
    @MethodSource("mutateInstanceFieldMethods")
    void testMutateInstanceFinalWithLogging(String methodName) throws Exception {
        String type = methodName.contains("Object") ? "Object" : "<Type>";
        test(methodName, "-Xlog:jni=debug")
            .shouldContain("[debug][jni] Set" + type + "Field mutated final instance field")
            .shouldHaveExitValue(0);
    }

    /**
     * Mutate a final static field with JNI. The test launches a child VM with -Xlog
     * and expects a log message in the output.
     */
    @ParameterizedTest
    @MethodSource("mutateStaticFieldMethods")
    void testMutateStaticFinalWithLogging(String methodName) throws Exception {
        String type = methodName.contains("Object") ? "Object" : "<Type>";
        test(methodName, "-Xlog:jni=debug")
            .shouldContain("[debug][jni] SetStatic" + type + "Field mutated final static field")
            .shouldHaveExitValue(0);
    }

    /**
     * Launches MutateFinals with the given method name as parameter, and the given VM options.
     */
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
