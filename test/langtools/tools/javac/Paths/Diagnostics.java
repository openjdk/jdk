/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4884487 6295519 6236704 6429613 8293877
 * @summary Test for proper diagnostics during path manipulation operations
 * @library /tools/lib
 * @build toolbox.ToolBox Util Diagnostics
 * @run main Diagnostics
 */


/*
 * Converted from Diagnostics.sh, originally written by Martin Buchholz.
 *
 * For the last version of the original, Diagnostics.sh, see
 * https://git.openjdk.org/jdk/blob/jdk-19%2B36/test/langtools/tools/javac/Paths/Diagnostics.sh
 *
 * This class primarily tests that javac generates warnings or errors
 * as appropriate for various input conditions.
 *
 * Note: only the {@code warning:} or {@code error:} prefixes are checked,
 * and not the subsequent text of the diagnostic.
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class Diagnostics extends Util {
    public static void main(String... args) throws Exception {
        new Diagnostics().run(args);
    }

    void run(String... args) throws Exception{
        setup();

        Locale prev = Locale.getDefault();
        Locale.setDefault(Locale.US); // diagnostics in English, please!
        try {
            tests();
        } finally {
            Locale.setDefault(prev);
        }

        cleanup();
        bottomLine();
    }

    void setup() throws IOException {
        cleanup();
        Files.writeString(Path.of("Main.java"), "public class Main{public static void main(String[]a){}}");
    }

    void cleanup() throws IOException {
        deleteFiles("Main.java", "Main.class");
        deleteFiles("classes", "classes.foo", "classes.jar", "classes.war", "classes.zip");
        deleteFiles("MANIFEST.MF", "classesRef.jar", "classesRefRef.jar", "jars");
    }

    void tests() throws Exception {
        /*----------------------------------------------------------------
         * No warnings unless -Xlint:path is used
         *----------------------------------------------------------------*/
        checkWarning(false, "Main.java");
        checkWarning(false, "-cp .${PS}classes Main.java");

        /*----------------------------------------------------------------
         * Warn for missing elts in user-specified paths
         *----------------------------------------------------------------*/

        // use --source 8 -target 8 with bootclasspath-related options
        String JDK8 = "-source 8 -target 8 -Xlint:-options ";
        checkWarning(true, "-Xlint:path -cp .${PS}classes         Main.java");
        checkWarning(true, JDK8 + "-Xlint:path -Xbootclasspath/p:classes Main.java");
        checkWarning(true, JDK8 + "-Xlint      -Xbootclasspath/a:classes Main.java");

        checkWarning(true, JDK8 + "-Xlint:-options -Xlint:path -endorseddirs classes   Main.java");
        checkWarning(true, JDK8 + "-Xlint:-options -Xlint      -extdirs      classes   Main.java");

        /*----------------------------------------------------------------
         * No warning for missing elts in "system" paths
         *----------------------------------------------------------------*/
        // TODO? there are system paths we could check, such as --module-path

        /*----------------------------------------------------------------
         * No warning if class path element exists
         *----------------------------------------------------------------*/
        tb.createDirectories("classes");

        checkWarning(false, "-Xlint:path -cp .${PS}classes         Main.java");
        checkWarning(false, JDK8 + "-Xlint:path -endorseddirs  classes Main.java");
        checkWarning(false, JDK8 + "-Xlint:path -extdirs       classes Main.java");
        checkWarning(false, JDK8 + "-Xlint:path -Xbootclasspath/p:classes Main.java");
        checkWarning(false, JDK8 + "-Xlint:path -Xbootclasspath/a:classes Main.java");

        jar("cf", "classes.jar", "Main.class");
        tb.copyFile("classes.jar", "classes.war");
        tb.copyFile("classes.war", "classes.zip");
        checkWarning(false, "-Xlint:path -cp .${PS}classes.jar     Main.java");
        checkWarning(true,  "-Xlint:path -cp .${PS}classes.war     Main.java");
        checkWarning(false, "-Xlint:path -cp .${PS}classes.zip     Main.java");

        /*----------------------------------------------------------------
         * Warn if -Xlint is used and if class path element refers to
         * regular file which doesn't look like a zip file, but is
         *----------------------------------------------------------------*/
        tb.copyFile("classes.war", "classes.foo");
        checkWarning(true, "-Xlint:path -cp .${PS}classes.foo     Main.java");

        /*----------------------------------------------------------------
         * No error if class path element refers to regular file which is
         * not a zip file
         *----------------------------------------------------------------*/
        checkError(false, "-cp Main.java Main.java"); // Main.java is NOT a jar file
        checkError(false, "Main.java");

        /*----------------------------------------------------------------
         * Warn if -Xlint is used and if class path element refers to
         * regular file which is not a zip file
         *----------------------------------------------------------------*/
        checkWarning(true, "-Xlint -cp Main.java Main.java"); // Main.java is NOT a jar file

        /*----------------------------------------------------------------
         * Test jar file class path reference recursion
         *----------------------------------------------------------------*/
        makeManifestWithClassPath("classesRef.jar");
        jar("cmf", "MANIFEST.MF", "classesRefRef.jar", "Main.class");

        /*----------------------------------------------------------------
         * Non-existent recursive Class-Path reference gives warning
         *----------------------------------------------------------------*/
        checkWarning(false, "                   -classpath   classesRefRef.jar Main.java");
        checkWarning(true, "        -Xlint      -classpath   classesRefRef.jar Main.java");
        checkWarning(false, JDK8 + "-Xlint -Xbootclasspath/p:classesRefRef.jar Main.java");

        createBadJarFiles("classesRef.jar");

        /*----------------------------------------------------------------
         * Non-jar file recursive Class-Path reference gives error
         *----------------------------------------------------------------*/

        checkError(true, "        -classpath        classesRefRef.jar Main.java");
        checkError(false, JDK8 + "-Xbootclasspath/a:classesRefRef.jar Main.java");

        makeManifestWithClassPath("classes");
        jar("cmf", "MANIFEST.MF", "classesRef.jar", "Main.class");

        /*----------------------------------------------------------------
         * Jar file recursive Class-Path reference is OK
         *----------------------------------------------------------------*/
        checkWarning(false, "       -Xlint      -classpath   classesRefRef.jar Main.java");
        checkWarning(false, JDK8 + "-Xlint -Xbootclasspath/p:classesRefRef.jar Main.java");


        /*----------------------------------------------------------------
         * Class-Path attribute followed in extdirs or endorseddirs
         *----------------------------------------------------------------*/
        tb.createDirectories("jars");
        tb.copyFile("classesRefRef.jar", "jars/.");
        checkWarning(true, JDK8 + "-Xlint -extdirs      jars Main.java");
        checkWarning(true, JDK8 + "-Xlint -endorseddirs jars Main.java");

        /*----------------------------------------------------------------
         * Bad Jar file in extdirs and endorseddirs should not be ignored
         *----------------------------------------------------------------*/
        createBadJarFiles("jars/classesRef.jar");
        checkError(true, JDK8 + "-Xlint -extdirs      jars Main.java");
        checkError(true, JDK8 + "-Xlint -endorseddirs jars Main.java");
    }

    void checkWarning(boolean expect, String args) throws Exception {
        Result result = javac(splitArgs(args));
        int exitCode = result.exitCode();
        if (exitCode != 0) {
            throw new Exception("javac failed: exit code " + exitCode);
        }
        String output = result.out();
        if (output.contains("warning:")) {
            if (!expect) {
                out.println("FAIL: Command 'javac " + args + "' printed an unexpected warning");
                failCount++;
            } else {
                passCount++;
            }
        } else {
            if (expect) {
                out.println("FAIL: Command 'javac " + args + "' did not generate the expected warning");
                failCount++;
            } else {
                passCount++;
            }
        }
    }

    void checkError(boolean expect, String args) throws Exception {
        Result result = javac(splitArgs(args));
        int exitCode = result.exitCode();
        boolean ok = true;
        if (expect) {
            if (exitCode == 0) {
                out.println("FAIL: Command 'javac " + args + " was supposed to exit with non-zero return code");
                ok = false;
            }
            if (!result.out().contains("error:")) {
                out.println("FAIL: Command 'javac " + args + " did not generate any error message");
                ok = false;
            }
        } else {
            if (exitCode != 0) {
                out.println("FAIL: Command 'javac " + args + " failed with a non-zero return code");
                ok = false;
            }
            if (result.out().contains("error:")) {
                out.println("FAIL: Command 'javac " + args + " printed an unexpected error message");
                ok = false;
            }
        }
        if (ok) {
            passCount++;
        } else {
            failCount++;
        }
    }

    void createBadJarFiles(String... paths) throws IOException {
        for (String p : paths) {
            Files.writeString(Path.of(p), "not a jar file\n");
        }
    }
}
