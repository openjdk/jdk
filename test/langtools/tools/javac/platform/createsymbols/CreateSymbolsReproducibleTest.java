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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.lang.model.SourceVersion;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/*
 * @test
 * @bug 8327466
 * @summary verifies that the ct.sym file created by build.tools.symbolgenerator.CreateSymbols
 *          is reproducible
 * @library /test/lib
 * @modules java.compiler
 *          jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.jvm
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 *
 * @compile ${test.root}/../../make/langtools/src/classes/build/tools/symbolgenerator/CreateSymbols.java
 *
 * @run junit CreateSymbolsReproducibleTest
 */
public class CreateSymbolsReproducibleTest {

    // the fully qualified class name of the tool that we launch to generate the ct.sym file
    private static final String CREATE_SYMBOLS_CLASS_FQN = "build.tools.symbolgenerator.CreateSymbols";
    // a reproducible timestamp (in seconds) that we pass to "CreateSymbols build-ctsym" as input
    // when generating the ct.sym file
    private static final long SOURCE_EPOCH_DATE = Instant.now().getEpochSecond();
    // arbitrary set of packages that will be included in a include list file
    // that will be given as input to "CreateSymbols build-description-incremental" command
    // for generating the symbol text file
    private static final String INCLUDE_PKGS = """
            +java/io/
            +java/lang/
            +java/lang/annotation/
            +java/lang/instrument/
            +java/lang/invoke/
            """;

    private static Path symTxtFile;

    @BeforeAll
    static void beforeAll() throws Exception {
        symTxtFile = createSymTxtFile();
        System.out.println("created sym.txt file at " + symTxtFile);
    }

    /*
     * Launches the "CreateSymbols build-ctsym" tool multiple times to generate ct.sym files.
     * Each time with the same inputs and the same timestamp. For each of these attempts, we use
     * a different timezone when launching the tool. The test verifies that irrespective of
     * what timezone gets used, the generated ct.sym files don't differ.
     */
    @Test
    void testDifferentTimezone() throws Exception {
        final Path destDir = Files.createTempDirectory(Path.of("."), "").toAbsolutePath();
        final List<Path> ctSymFiles = new ArrayList<>();
        final List<Optional<String>> timezones = List.of(
                Optional.empty(), // no explicit timezone
                Optional.of("UTC"),
                Optional.of("America/Los_Angeles"),
                Optional.of("Asia/Tokyo")
        );
        int num = 0;
        // create several ct.sym files by launching the tool with different timezones
        // but the same timestamp value as input
        for (final Optional<String> timezone : timezones) {
            num++;
            final String destCtSymFileName = "ct-" + num + ".sym";
            final Path destCtSym = destDir.resolve(destCtSymFileName);
            System.out.println("using timezone " + timezone + " to create ct.sym file at "
                    + destCtSym);
            createCtSym(destCtSym, symTxtFile, timezone);
            ctSymFiles.add(destCtSym);
        }
        // verify that each of these generated ct.sym files are exactly the same in content
        for (int i = 0; i < ctSymFiles.size() - 1; i++) {
            final Path ctSym1 = ctSymFiles.get(i);
            final Path ctSym2 = ctSymFiles.get(i + 1);
            final long mismatchOffset = Files.mismatch(ctSym1, ctSym2);
            if (mismatchOffset != -1) {
                throw new AssertionError("contents of files " + ctSym1 + " and " + ctSym2
                        + " unexpectedly differ" + " (at " + mismatchOffset + " offset)");
            }
        }
    }

    private static Path createSymTxtFile() throws Exception {
        final Path tmpDir = Files.createTempDirectory(Path.of("."), "").toAbsolutePath();
        final Path destSymTxtFile = tmpDir.resolve("sym.txt");
        Files.writeString(destSymTxtFile, "");
        final Path includeList = tmpDir.resolve("include.list");
        Files.writeString(includeList, INCLUDE_PKGS);
        final String[] cmd = new String[]{
                "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
                "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
                CREATE_SYMBOLS_CLASS_FQN,
                "build-description-incremental",
                destSymTxtFile.toString(),
                includeList.toString()
        };
        final OutputAnalyzer oa = ProcessTools.executeTestJava(cmd);
        oa.shouldHaveExitValue(0);
        // verify the file was created
        if (Files.notExists(destSymTxtFile)) {
            oa.reportDiagnosticSummary();
            throw new AssertionError(CREATE_SYMBOLS_CLASS_FQN
                    + " build-description-incremental failed to create " + destSymTxtFile);
        }
        return destSymTxtFile;
    }

    private static void createCtSym(final Path destCtSymFile, final Path symTxtFile,
                                    final Optional<String> timezone) throws Exception {
        final Path modulesDir = Path.of(".").resolve("modules");
        Files.createDirectories(modulesDir);
        final Path modulesList = Path.of(".").resolve("modules-list");
        // an empty file
        Files.writeString(modulesList, "");

        final List<String> cmd = new ArrayList<>();
        timezone.ifPresent((tz) -> {
            // launch the tool with a specific timezone (if any)
            cmd.add("-Duser.timezone=" + tz);
        });
        cmd.add(CREATE_SYMBOLS_CLASS_FQN);
        cmd.add("build-ctsym"); // command to CreateSymbols tool
        cmd.add("non-existent-ct-desc-file");
        cmd.add(symTxtFile.toString()); // a previously generated a sym.txt file
        cmd.add(destCtSymFile.toString()); // target ct.sym file to generate
        cmd.add(Long.toString(SOURCE_EPOCH_DATE)); // reproducible timestamp (in seconds)
        cmd.add(Integer.toString(SourceVersion.latest().ordinal()));
        cmd.add("does-not-matter-pre-release-tag");
        cmd.add(modulesDir.toString());
        cmd.add(modulesList.toString());
        final OutputAnalyzer oa = ProcessTools.executeTestJava(cmd);
        oa.shouldHaveExitValue(0);
        // verify the ct.sym file was generated
        if (Files.notExists(destCtSymFile)) {
            oa.reportDiagnosticSummary();
            throw new AssertionError("ct.sym file missing at " + destCtSymFile);
        }
    }
}
