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
 *
 */

/*
 * @test
 * @summary Test multi-release jar with CFLH
 * @requires vm.cds
 * @requires vm.jvmti
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @run main/othervm/native MultiReleaseJars
 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.IOException;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.OutputAnalyzer;

public class MultiReleaseJars {

    static final int BASE_VERSION = 9;
    static final String BASE_VERSION_STRING = Integer.toString(BASE_VERSION);
    static final int MAJOR_VERSION = Runtime.version().major();
    static final String MAJOR_VERSION_STRING = String.valueOf(MAJOR_VERSION);

    static String[] getMain() {
        String[] sts = {
            "public class Main {",
            "    public static void main(String[] args) throws Exception {",
            "        System.out.println(Class.forName(\"Foo\"));",
            "        System.out.println(Class.forName(\"Bar\"));",
            "    }",
            "}"
        };
        return sts;
    }

    static String[] getFoo() {
        String[] sts = {
            "class Foo {",
            "    static {",
            "        System.out.println(\"Hello from Foo\");",
            "    }",
            "}",
        };
        return sts;
    }

    static String[] getBar() {
        String[] sts = {
            "class Bar {",
            "    static {",
            "        System.out.println(\"Hello from Bar\");",
            "    }",
            "}",
        };
        return sts;
    }

    static void writeFile(File file, String... contents) throws Exception {
        if (contents == null) {
            throw new java.lang.RuntimeException("No input for writing to file" + file);
        }
        try (
             FileOutputStream fos = new FileOutputStream(file);
             PrintStream ps = new PrintStream(fos)
        ) {
            for (String str : contents) {
                ps.println(str);
            }
        }
    }

    /* version.jar entries and files:
     * META-INF/
     * META-INF/MANIFEST.MF
     * Bar.class
     * Main.class
     * META-INF/versions/9/
     * META-INF/versions/9/Bar.class
     * META-INF/versions/9/Foo.class
     */
    static void createClassFilesAndJar() throws Exception {
        String tempDir = CDSTestUtils.getOutputDir();
        File baseDir = new File(tempDir + File.separator + "base");
        File vDir    = new File(tempDir + File.separator + BASE_VERSION_STRING);

        baseDir.mkdirs();
        vDir.mkdirs();

        File fileFoo = TestCommon.getOutputSourceFile("Foo.java");
        writeFile(fileFoo, getFoo());
        JarBuilder.compile(vDir.getAbsolutePath(), fileFoo.getAbsolutePath(), "--release", BASE_VERSION_STRING);

        File fileMain = TestCommon.getOutputSourceFile("Main.java");
        writeFile(fileMain, getMain());
        JarBuilder.compile(baseDir.getAbsolutePath(), fileMain.getAbsolutePath());
        File fileBar = TestCommon.getOutputSourceFile("Bar.java");
        writeFile(fileBar, getBar());
        JarBuilder.compile(baseDir.getAbsolutePath(), fileBar.getAbsolutePath());
        JarBuilder.compile(vDir.getAbsolutePath(), fileBar.getAbsolutePath(), "--release", BASE_VERSION_STRING);

        String[] meta = {
            "Multi-Release: true",
            "Main-Class: Main"
        };
        File metainf = new File(tempDir, "mf.txt");
        writeFile(metainf, meta);

        JarBuilder.build("multi-version", baseDir, metainf.getAbsolutePath(),
            "--release", BASE_VERSION_STRING, "-C", vDir.getAbsolutePath(), ".");

    }

    static void checkExecOutput(OutputAnalyzer output, String expectedOutput) throws Exception {
        try {
            TestCommon.checkExec(output, expectedOutput);
        } catch (java.lang.RuntimeException re) {
            String cause = re.getMessage();
            if (!expectedOutput.equals(cause)) {
                throw re;
            }
        }
    }

    public static void main(String... args) throws Exception {
        // create multi-version.jar which contains Main.class, Foo.class and Bar.class.
        // Foo.class has only one version: base version 9.
        // Bar.class has two versions: base version 9 and default version.
        createClassFilesAndJar();

        String mainClass    = "Main";
        String appJar       = TestCommon.getTestJar("multi-version.jar");
        String appClasses[] = {"Foo", "Bar"};

        OutputAnalyzer output = TestCommon.dump(appJar, appClasses);
        output.shouldContain("Loading classes to share: done.")
              .shouldHaveExitValue(0);

        String agentCmdArg = "-agentlib:SimpleClassFileLoadHook=Foo,Hello,HELLO";
        output = TestCommon.execAuto("-cp", appJar,
                                     "-Xlog:cds=info,class+load",
                                     agentCmdArg,
                                     mainClass);

        output.shouldMatch(".*Foo.source:.*multi-version.jar")
              // Foo is loaded from jar since it was modified by CFLH
              .shouldContain("HELLO from Foo") // CFLH changed "Hello" to "HELLO"
              .shouldContain("class Foo") // output from Main
              // Bar is loaded from archive
              .shouldContain("Bar source: shared objects file")
              .shouldContain("Hello from Bar")
              .shouldContain("class Bar"); // output from Main
    }
}
