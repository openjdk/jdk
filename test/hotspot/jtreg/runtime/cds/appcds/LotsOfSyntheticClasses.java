/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 *
 */

import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

import jdk.test.lib.cds.CDSJarUtils;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.OutputAnalyzer;

/*
 * @test
 * @summary Try to archive lots and lots of classes.
 * @requires vm.cds
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @run driver/timeout=500 LotsOfSyntheticClasses
 */

public class LotsOfSyntheticClasses {

    // Generate 70 top-level classes, each containing 1000 nested classes.
    // 70K total classes is enough to push the CDS limits. Any archived
    // collection that holds Classes should not have backing storage larger
    // than CDS archival limit (256KB). This means we want at least 64K classes
    // to probe that limit.
    private static final int NUM_CLASSES = 70;
    private static final int NUM_NESTED_CLASSES = 1000;

    private static final Path USER_DIR = Paths.get(CDSTestUtils.getOutputDir());
    private static final Path APP_JAR = USER_DIR.resolve("test.jar");
    private static final Path SRC_DIR = USER_DIR.resolve("src");

    private static final String TOP_CLASS_NAME = "Class";
    private static final String NESTED_CLASS_NAME = "Nested";
    private static final String MAIN_CLASS_NAME = "Main";

    public static List<String> generateClass(int idx) {
        List<String> out = new ArrayList<>();
        out.add("public class " + TOP_CLASS_NAME + idx + " {");
        out.add("public " + TOP_CLASS_NAME + idx + "() {");
        for (int c = 0; c < NUM_NESTED_CLASSES; c++) {
            out.add("new " + NESTED_CLASS_NAME + c + "();");
        }
        out.add("}");
        for (int c = 0; c < NUM_NESTED_CLASSES; c++) {
            out.add("public static class " + NESTED_CLASS_NAME + c + " {}");
        }
        out.add("}");
        return out;
    }

    public static List<String> generateMainClass() {
        List<String> out = new ArrayList<>();
        out.add("public class " + MAIN_CLASS_NAME + " {");
        out.add("public static void main(String... args) {");
        for (int c = 0; c < NUM_CLASSES; c++) {
            out.add("new " + TOP_CLASS_NAME + c + "();");
        }
        out.add("System.out.println(\"Success\");");
        out.add("}");
        out.add("}");
        return out;
    }

    public static String[] listAppClasses() {
        String[] res = new String[NUM_CLASSES * NUM_NESTED_CLASSES];
        for (int c = 0; c < NUM_CLASSES; c++) {
            for (int sc = 0; sc < NUM_NESTED_CLASSES; sc++) {
                res[c * NUM_NESTED_CLASSES + sc] = TOP_CLASS_NAME + c + "$" + NESTED_CLASS_NAME + sc;
            }
        }
        return res;
    }

    public static void main(String[] args) throws Exception {
        // Step 1. Generate classes and build the JAR with them.
        {
            SRC_DIR.toFile().mkdirs();

            for (int i = 0; i < NUM_CLASSES; i++) {
                Path file = SRC_DIR.resolve(TOP_CLASS_NAME + i + ".java");
                Files.write(file, generateClass(i));
            }

            Path mainFile = SRC_DIR.resolve(MAIN_CLASS_NAME + ".java");
            Files.write(mainFile, generateMainClass());

            CDSJarUtils.buildFromSourceDirectory(
                APP_JAR.toString(),
                SRC_DIR.toString()
            );
        }

        // Step 2. Try to dump the archive.
        {
            OutputAnalyzer output = TestCommon.createArchive(
                APP_JAR.toString(),
                listAppClasses(),
                MAIN_CLASS_NAME,
                // Verification for lots of classes slows down the test.
                "-XX:+IgnoreUnrecognizedVMOptions",
                "-XX:-VerifyDependencies",
                "-XX:-VerifyBeforeExit"
            );
            TestCommon.checkDump(output);
        }

        // Step 3. Try to run, touching every class.
        {
            TestCommon.run(
                // Verification for lots of classes slows down the test.
                "-XX:+IgnoreUnrecognizedVMOptions",
                "-XX:-VerifyDependencies",
                "-XX:-VerifyBeforeExit",
                "-cp", APP_JAR.toString(),
                MAIN_CLASS_NAME).
                    assertNormalExit("Success");
        }

    }
}
