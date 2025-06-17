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
 * @requires (os.family == "linux" | os.family == "mac")
 * @library /test/lib
 * @compile JNIMutator.java
 * @run junit JNIMutatorTest
 */

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

class JNIMutatorTest {
    private static final String TEST_CLASSES = System.getProperty("test.classes");
    private static final String JAVA_LIBRARY_PATH = System.getProperty("java.library.path");

    /**
     * Test Field.set from JNI attached thread, public field in public class, code in
     * unnamed modules allowed to mutate final fields.
     */
    @Test
    void testPublicClassPublicFieldAllowMutation() throws Exception {
        test("JNIMutator$C1", false, "--enable-final-field-mutation=ALL-UNNAMED");
    }

    /**
     * Test Field.set from JNI attached thread, public field in public class, code in
     * unnamed modules not allowed to mutate final fields.
     */
    @Test
    void testPublicClassPublicFieldDisallowMutation() throws Exception {
        test("JNIMutator$C1", true);
    }

    /**
     * Test Field.set from JNI attached thread, non-public final field in public class.
     */
    @Test
    void testPublicClassAllowMutation() throws Exception {
        test("JNIMutator$C2", true, "--enable-final-field-mutation=ALL-UNNAMED");
    }

    /**
     * Test Field.set from JNI attached thread, public field in non-public class.
     */
    @Test
    void testPublicFieldAllowMutation() throws Exception {
        test("JNIMutator$C3", true, "--enable-final-field-mutation=ALL-UNNAMED");
    }

    private void test(String className, boolean expectIAE, String... extraOps) throws Exception {
        Stream<String> s1 = Stream.of(extraOps);
        Stream<String> s2 = Stream.of(
                "-cp", TEST_CLASSES,
                "-Djava.library.path=" + JAVA_LIBRARY_PATH,
                "--enable-native-access=ALL-UNNAMED",
                "--illegal-final-field-mutation=deny",
                "JNIMutator",
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