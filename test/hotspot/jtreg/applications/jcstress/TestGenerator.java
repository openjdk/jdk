/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

package applications.jcstress;

import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.EnumSet;
import java.util.function.Predicate;


/**
 * @notest THIS IS NOT A TEST.
 * This is a test generator, should be run only when jcstress is changed
 *
 * @library /test/lib /
 * @run main applications.jcstress.TestGenerator
 */

/**
 * Use jcstress test suite to generate jtreg tests in 'test.src' or current
 * directory. Used version is defined in JcstressRunner class.
 *
 * Each generated jtreg test file will contain several tests. Subdirectories are
 * used to allow running all tests from a file using command line. 'copy',
 * 'acqrel', 'fences', 'atomicity', 'seqcst.sync', 'seqcst.volatiles' and
 * 'other' tests will be generated.
 *
 * This generator depends on testlibrary, therefore it should be compiled and
 * added to classpath. One can replace @notest by @test in jtreg test
 * description above to run this class with jtreg.
 *
 * @see <a href=https://wiki.openjdk.java.net/display/CodeTools/jcstress>jcstress</a>
 */
public class TestGenerator {
    private static final String COPYRIGHT;
    static {
        String years;
        final int firstYear = 2017;
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        if (firstYear < currentYear) {
            years = String.format("%d, %d", firstYear, currentYear);
        } else {
            years = "" + firstYear;
        }
        COPYRIGHT = String.format("/*\n" +
                " * Copyright (c) %s, Oracle and/or its affiliates. All rights reserved.\n" +
                " * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.\n" +
                " *\n" +
                " * This code is free software; you can redistribute it and/or modify it\n" +
                " * under the terms of the GNU General Public License version 2 only, as\n" +
                " * published by the Free Software Foundation.\n" +
                " *\n" +
                " * This code is distributed in the hope that it will be useful, but WITHOUT\n" +
                " * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or\n" +
                " * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License\n" +
                " * version 2 for more details (a copy is included in the LICENSE file that\n" +
                " * accompanied this code).\n" +
                " *\n" +
                " * You should have received a copy of the GNU General Public License version\n" +
                " * 2 along with this work; if not, write to the Free Software Foundation,\n" +
                " * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.\n" +
                " *\n" +
                " * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA\n" +
                " * or visit www.oracle.com if you need additional information or have any\n" +
                " * questions.\n" +
                " */\n\n", years);
    }

    private static enum JcstressGroup {
        MEMEFFECTS("memeffects"),
        COPY("copy"),
        ACQREL("acqrel"),
        FENCES("fences"),
        ATOMICITY("atomicity"),
        SEQCST_SYNC("seqcst.sync"),
        SEQCST_VOLATILES("seqcst.volatiles"),
        OTHER("other", JcstressGroup.otherFilter());

        private final String groupName;
        private final Predicate<String> filter;

        private JcstressGroup(String groupName, Predicate<String> filter) {
            this.groupName = groupName;
            this.filter = filter;
        }

        private JcstressGroup(String groupName) {
            this(groupName, JcstressGroup.nameFilter(groupName));
        }

        private static Predicate<String> nameFilter(String group) {
            return s -> s.startsWith("org.openjdk.jcstress.tests." + group + ".");
        }

        private static Predicate<String> otherFilter() {
            return (s) -> {
                for (JcstressGroup g : EnumSet.complementOf(EnumSet.of(OTHER))) {
                    if (g.filter.test(s)) {
                        return false;
                    }
                }
                return true;
            };
        }
    }

    public static String DESC_FORMAT = "\n"
            + "/**\n"
            + " * @test %1$s\n"
            + " * @library /test/lib /\n"
            + " * @run driver/timeout=2400 " + JcstressRunner.class.getName()
                    // verbose output
                    + " -v"
                    // test mode preset
                    + " -m default"
                    // test name
                    + " -t %1$s\n"
            + " */\n";

    public static void main(String[] args)  {
        Path path = JcstressRunner.pathToArtifact();
        Path output;
        try {
            output = Files.createTempFile("jcstress", ".out");
            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                    "-jar",
                    path.toAbsolutePath().toString(),
                    "-l");
            pb.redirectOutput(output.toFile());
            new OutputAnalyzer(pb.start()).shouldHaveExitValue(0);
        } catch (Exception e) {
            throw new Error("Can not get list of tests", e);
        }
        for (JcstressGroup group : JcstressGroup.values()) {
            try {
                try (BufferedReader reader = Files.newBufferedReader(output)) {
                    // skip first 4 lines: name, -{80}, revision and empty line
                    for (int i = 0; i < 4; ++i) {
                        reader.readLine();
                    }
                    new TestGenerator(group).generate(reader);
                }
            } catch (IOException e) {
                throw new Error("Generating tests for " + group.name()
                                + " has failed", e);
            }
        }
        output.toFile().delete();
    }

    private final JcstressGroup group;

    private TestGenerator(JcstressGroup group) {
        this.group = group;
    }

    private void generate(BufferedReader reader) throws IOException {
        // array is needed to change value inside a lambda
        long[] count = {0L};
        String root = Utils.TEST_SRC;
        Path testFile = Paths.get(root)
                             .resolve(group.groupName)
                             .resolve("Test.java");
        File testDir = testFile.getParent().toFile();
        if (!testDir.mkdirs() && !testDir.exists()) {
            throw new Error("Can not create directories for "
                            + testFile.toString());
        }

        try (PrintStream ps = new PrintStream(testFile.toFile())) {
            ps.print(COPYRIGHT);
            ps.printf("/* DO NOT MODIFY THIS FILE. GENERATED BY %s */\n",
                      getClass().getName());

            reader.lines()
                  .filter(group.filter)
                  .forEach(s -> {
                      count[0]++;
                      ps.printf(DESC_FORMAT, s);
                  });
            ps.print('\n');
        }
        System.out.printf("%d tests generated in %s%n",
                count[0], group.groupName);
    }
}
