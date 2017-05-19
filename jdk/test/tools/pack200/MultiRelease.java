/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/*
 * @test
 * @bug 8066272
 * @summary tests a simple multi-versioned jar file
 * @compile -XDignore.symbol.file Utils.java MultiRelease.java
 * @run main MultiRelease
 * @author ksrini
 */

public class MultiRelease {
    private static final File cwd = new File(".");
    private static int pass = 0;
    private static int fail = 0;
    // specify alternate name via arguments to verify
    // if permanent fix works

    private static final String PropKey = "pack200.MultiRelease.META-INF";
    private static final String MetaInfName = System.getProperty(PropKey, "META-INF");

    public static void main(String... args) throws Exception {
        new MultiRelease().run();
    }

    void run() throws Exception {
        List<TestCase> testCases = new ArrayList<>();
        testCases.add(new TestCase1());
        testCases.add(new TestCase2());
        for (TestCase tc : testCases) {
            tc.run();
        }
        if (fail > 0) {
            throw new Exception(fail + "/" + testCases.size() + " tests fails");
        } else {
            System.out.println("All tests(" + pass + ") passes");
        }
        Utils.cleanup();
    }

    /*
     * An abstract class to eliminate test boiler plating.
     */
    static abstract class TestCase {
        final File tcwd;
        final File metaInfDir;
        final File versionsDir;
        final File manifestFile;

        TestCase(String directory) throws IOException {
            System.out.println("initializing directories");
            tcwd = new File(cwd, directory);
            metaInfDir = mkdir(new File(tcwd, MetaInfName));
            versionsDir = mkdir(new File(metaInfDir, "versions"));
            manifestFile = new File(tcwd, "manifest.tmp");
            List<String> scratch = new ArrayList<>();
            scratch.add("Multi-Release: true");
            Utils.createFile(manifestFile, scratch);
        }

        File mkdir(File f) throws IOException {
            if (f.exists() && f.isDirectory() && f.canRead() && f.canWrite()) {
                return f;
            }
            if (!f.mkdirs()) {
               throw new IOException("mkdirs failed: " + f.getAbsolutePath());
            }
            return f;
        }

        abstract void emitClassFiles() throws Exception;

        void run() {
            try {
                emitClassFiles();
                // jar the file up
                File testFile = new File(tcwd, "test" + Utils.JAR_FILE_EXT);
                Utils.jar("cvfm",
                        testFile.getAbsolutePath(),
                        manifestFile.getAbsolutePath(),
                        "-C",
                        tcwd.getAbsolutePath(),
                        ".");
                File outFile = new File(tcwd, "test-repacked" + Utils.JAR_FILE_EXT);
                List<String> cmdsList = new ArrayList<>();

                cmdsList.add(Utils.getPack200Cmd());
                cmdsList.add("-J-ea");
                cmdsList.add("-J-esa");
                cmdsList.add("-v");
                cmdsList.add("--repack");
                cmdsList.add(outFile.getAbsolutePath());
                cmdsList.add(testFile.getAbsolutePath());
                List<String> output = Utils.runExec(cmdsList);
                Utils.doCompareVerify(testFile.getAbsoluteFile(), outFile.getAbsoluteFile());
                pass++;
            } catch (Exception e) {
                e.printStackTrace(System.err);
                fail++;
            }
        }
    }

    static class TestCase1 extends TestCase {
        private TestCase1(String directory) throws IOException {
            super(directory);
        }

        public TestCase1() throws Exception {
            this("case1");
        }

        @Override
        void emitClassFiles() throws Exception {
            emitClassFile("");
            emitClassFile("7");
            emitClassFile("8");
            emitClassFile("9");
        }

        /*
         * Adds different variants of types
         */
        void emitClassFile(String version) throws IOException {
            final File outDir = mkdir(version.isEmpty()
                    ? tcwd
                    : new File(versionsDir, version));

            final File srcDir = mkdir(version.isEmpty()
                            ? new File(tcwd, "src")
                            : new File(new File(versionsDir, version), "src"));

            final String fname = "Foo";
            final File srcFile = new File(srcDir, fname + Utils.JAVA_FILE_EXT);
            List<String> scratch = new ArrayList<>();

            scratch.add("package pkg;");
            switch (version) {
                case "7":
                    scratch.add("public class Foo {");
                    scratch.add("public static final class Bar {}");
                    break;
                case "8":
                    scratch.add("public abstract class Foo {");
                    scratch.add("public final class Bar {}");
                    break;
                case "9":
                    scratch.add("public interface Foo {");
                    scratch.add("public final class Bar {}");
                    break;
                default:
                    scratch.add("public class Foo {");
                    scratch.add("public final class Bar {}");
                    break;
            }
            scratch.add("}");

            Utils.createFile(srcFile, scratch);
            Utils.compiler("-d",
                    outDir.getAbsolutePath(),
                    srcFile.getAbsolutePath());
        }
    }

    static class TestCase2 extends TestCase {
        private TestCase2(String directory) throws IOException {
            super(directory);
        }

        TestCase2() throws Exception {
            this("case2");
        }

        @Override
        void emitClassFiles() throws Exception {
            emitClassFile("");
            emitClassFile("8");
        }

        /*
         * Adds different variants of types and tries to invoke an
         * interface or concrete method defined by them.
         */
        void emitClassFile(String version) throws IOException {

            final File outDir = mkdir(version.isEmpty()
                    ? tcwd
                    : new File(versionsDir, version));

            final File srcDir = mkdir(version.isEmpty()
                    ? new File(tcwd, "src")
                    : new File(new File(versionsDir, version), "src"));

            List<String> scratch = new ArrayList<>();
            final String fname1 = "Ab";
            final File srcFile1 = new File(srcDir, fname1 + Utils.JAVA_FILE_EXT);

            final String fname2 = "AbNormal";
            final File srcFile2 = new File(srcDir, fname2 + Utils.JAVA_FILE_EXT);
            switch (version) {
                case "8":
                    scratch.clear();
                    scratch.add("import java.io.IOException;");
                    scratch.add("public interface " + fname1 + "{");
                    scratch.add("    public abstract void close() throws IOException ;");
                    scratch.add("}");
                    Utils.createFile(srcFile1, scratch);
                    break;
                default:
                    scratch.clear();
                    scratch.add("import java.io.IOException;");
                    scratch.add("public abstract class " + fname1 + "{");
                    scratch.add("    public abstract void close() throws IOException ;");
                    scratch.add("}");
                    Utils.createFile(srcFile1, scratch);
            }

            scratch.clear();
            scratch.add("import java.io.IOException;");
            scratch.add("public class " + fname2 + "{");
            scratch.add("    public void doSomething(Ab ab) throws IOException {");
            scratch.add("       ab.close();");
            scratch.add("    }");
            scratch.add("}");

            Utils.createFile(srcFile2, scratch);
            Utils.compiler("-d",
                    outDir.getAbsolutePath(),
                    srcFile1.getAbsolutePath(),
                    srcFile2.getAbsolutePath());
        }
    }
}
