/*
 * Copyright (c) 2005, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6268383 8172309 8293877
 * @summary Test classpath wildcards for javac and java -classpath option.
 * @library /tools/lib
 * @build toolbox.ToolBox Util WildcardMineField
 * @run main WildcardMineField
 */

/*
 * Converted from wcMineField.sh, originally written by Martin Buchholz.
 *
 * For the last version of the original, wcMineField.sh, see
 * https://git.openjdk.org/jdk/blob/jdk-19%2B36/test/langtools/tools/javac/Paths/wcMineField.sh
 *
 * This class primarily tests support for "classpath wildcards", which is a feature
 * by which elements of a classpath option ending in {@code *} are expanded into
 * the set of jar files found in the directory preceding the {@code *}.
 *
 * Note that this feature is only implemented in the launcher, even for javac,
 * and so is only available when running javac via its launcher, in a separate process.
 *
 * Note that this feature does not affect the use of {@code *} elsewhere in any path,
 * classpath or otherwise, and so this class also tests the use of {@code *} and other special
 * characters (like {@code ,} and {@code ;}) in filenames. Some of these tests,
 * labelled in the original code as "UnixOnly", do not apply to Windows.
 *
 * For information on the launcher support for the {@code -classpath} option,
 * see the java man page. As of September 2022, there is no equivalent documentation
 * for javac, except to say that the support is only in the native launcher for javac,
 * and not in the main javac source code.
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import toolbox.ToolBox;

public class WildcardMineField extends Util {
    public static void main(String... args) throws Exception {
        new WildcardMineField().run(args);
    }

    void run(String... args) throws Exception {
        setup();
        tests();
        cleanup();
        bottomLine();
    }

    void setup() throws Exception {
        cleanup();
        tb.createDirectories("GooSrc", "GooJar", "GooZip", "GooClass", "GooJar/SubDir");
        tb.createDirectories("BadSrc", "BadJar", "BadZip", "BadClass");
        tb.createDirectories("SpeSrc", "SpeJar", "SpeZip", "SpeClass");
        tb.createDirectories("JarNClass", "StarJar", "MixJar");
        Files.writeString(Path.of("Lib.java"), "public class Lib  {public static void f(){}}");
        Files.writeString(Path.of("Lib2.java"), "public class Lib2 {public static void g(){}}");
        Files.writeString(Path.of("Lib3.java"), "public class Lib3 {public static void h(){}}");
        javac("Lib.java", "Lib2.java", "Lib3.java");
        tb.copyFile("Lib.class", "JarNClass/.");
        jar("cf", "GooJar/Lib.jar", "Lib.class");
        jar("cf", "GooJar/SubDir/Lib2.jar", "Lib2.class");
        jar("cf", "JarNClass/Lib.jar", "Lib.class");

        jar("cf", "GooZip/Lib.zip", "Lib.class");
        tb.moveFile("Lib.class", "GooClass/.");
        tb.moveFile("Lib2.class", "GooClass/.");
        tb.moveFile("Lib3.class", "GooClass/.");
        tb.moveFile("Lib.java", "GooSrc/.");
        tb.moveFile("Lib2.java", "GooSrc/.");
        tb.moveFile("Lib3.java", "GooSrc/.");

        checkFiles("GooZip/Lib.zip", "GooJar/Lib.jar", "GooSrc/Lib.java");
        checkFiles("GooSrc/Lib2.java", "GooSrc/Lib3.java", "GooJar/SubDir/Lib2.jar");

        Files.writeString(Path.of("Spe1.java"), "public class Spe1 {public static void f(){}}");
        Files.writeString(Path.of("Spe2.java"), "public class Spe2 {public static void f(){}}");
        Files.writeString(Path.of("Spe3.java"), "public class Spe3 {public static void f(){}}");
        Files.writeString(Path.of("Spe4.java"), "public class Spe4 {public static void f(){}}");
        javac("Spe1.java", "Spe2.java", "Spe3.java", "Spe4.java");

        if (!ToolBox.isWindows()) {
            jar("cf", "SpeJar/Spe:Colon.jar", "Spe1.class");
            jar("cf", "SpeJar/Spe*wc.jar", "Spe4.class");
            checkFiles("SpeJar/Spe*wc.jar");

            jar("cf", "StarJar/*jar.jar", "Spe2.class");
            jar("cf", "StarJar/jar*.jar", "Spe3.class");
            jar("cf", "StarJar/*jar*.jar", "Spe4.class");
            checkFiles("StarJar/*jar.jar", "StarJar/jar*.jar", "StarJar/*jar*.jar");
        }

        jar("cf", "SpeJar/Spe,Comma.jar", "Spe2.class");
        jar("cf", "SpeJar/Spe;Semi.jar", "Spe3.class");

        jar("cf", "MixJar/mix.jAr", "Spe1.class");
        jar("cf", "MixJar/mix2.JAR", "Spe2.class");
        jar("cf", "MixJar/mix3.zip", "Spe3.class");
        jar("cf", "MixJar/.hiddenjar.jar", "Spe4.class");

        moveFiles(listFiles(curDir, "Spe*.class"), Path.of("SpeClass/."));
        moveFiles(listFiles(curDir, "Spe*.java"), Path.of("SpeSrc/."));
        checkFiles("SpeJar/Spe,Comma.jar", "SpeJar/Spe;Semi.jar", "SpeSrc/Spe2.java", "SpeSrc/Spe3." +
                "java", "SpeSrc/Spe4.java");
        checkFiles("MixJar/mix.jAr", "MixJar/mix2.JAR", "MixJar/mix3.zip", "MixJar/.hiddenjar.jar");

        Files.writeString(Path.of("Main.java"), "public class Main {public static void main(String[] a) {Lib.f();}}");
        Files.writeString(Path.of("Main1.java"), "public class Main1 {public static void main(String[] a) {Lib2.g();}}");
        Files.writeString(Path.of("Main1b.java"), "public class Main1b {public static void main(String[] a) {Spe1.f();}}");
        Files.writeString(Path.of("Main2.java"), "public class Main2 {public static void main(String[] a) {Spe2.f();}}");
        Files.writeString(Path.of("Main3.java"), "public class Main3 {public static void main(String[] a) {Spe3.f();}}");
        Files.writeString(Path.of("Main4.java"), "public class Main4 {public static void main(String[] a) {Spe4.f();}}");
        Files.writeString(Path.of("Main5.java"), "public class Main5 {public static void main(String[] a) {Spe2.f(); Lib.f();}}");
        Files.writeString(Path.of("Main6.java"), "public class Main6 {public static void main(String[] a) {Lib3.h();}}");
    }

    void cleanup() throws IOException {
        deleteFiles("GooSrc", "GooJar", "GooZip", "GooClass");
        deleteFiles("SpeSrc", "SpeJar", "SpeZip", "SpeClass");
        deleteFiles("BadSrc", "BadJar", "BadZip", "BadClass");
        deleteFiles("JarNClass", "StarJar", "MixJar", "StarDir");
        deleteFiles("OneDir", "MANIFEST.MF");
        deleteFiles(listFiles(curDir, "*.class"));
        deleteFiles(listFiles(curDir, "Main*.java"));
    }

    void tests() throws Exception {
        if (!ToolBox.isWindows()) {
            starDirTests();
        }

        /*----------------------------------------------------------------
         * Verify the basic jar file works
         *----------------------------------------------------------------*/

        // baseline test to verify it works.
        expectPass(JAVAC, "-cp GooJar/Lib.jar Main.java");
        expectPass(JAVAC, "-classpath GooJar/Lib.jar Main.java");
        expectPass(JAVA, "-classpath GooJar/Lib.jar${PS}. Main");
        expectPass(JAVA, "-cp GooJar/Lib.jar${PS}. Main");

        // basic test of one jar to be loaded
        if (!ToolBox.isWindows()) {
            expectPass(JAVAC, "-classpath GooJar/* Main.java");
        }
        expectPass(JAVAC, "-classpath GooJar/*${PS}. Main.java");
        expectPass(JAVA, "-classpath GooJar/*${PS}. Main");

        // in a subdir. First * should not load jars in subdirectories unless specified
        expectFail(JAVAC, "-classpath GooJar/* Main1.java");
        expectFail(JAVAC, " -classpath GooJar/*${PS}. Main1.java");
        expectPass(JAVAC, "-cp GooJar/SubDir/* Main1.java");
        expectPass(JAVAC, "-classpath GooJar/SubDir/* Main1.java");
        expectPass(JAVAC, "--class-path GooJar/SubDir/* Main1.java");
        expectPass(JAVAC, "--class-path=GooJar/SubDir/* Main1.java");

        // Same with launcher. Should not load jar in subdirectories unless specified
        expectFail(JAVA, "-classpath GooJar/*${PS}. Main1");
        expectPass(JAVA, "-classpath GooJar/SubDir/*${PS}. Main1");
        expectPass(JAVA, "-cp GooJar/SubDir/*${PS}. Main1");

        expectPass(classpath("GooJar/SubDir/*"), JAVAC, "Main1.java");
        expectPass(classpath("GooJar/SubDir/*${PS}."), JAVA, "Main1");

        /*----------------------------------------------------------------
         * Verify the jar files in 2 directories
         *----------------------------------------------------------------*/

        expectPass(JAVAC, "-classpath GooJar/Lib.jar${PS}SpeJar/Spe,Comma.jar Main5.java");
        expectPass(JAVA, "-classpath GooJar/Lib.jar${PS}SpeJar/Spe,Comma.jar${PS}. Main5");

        expectPass(JAVAC, "-classpath GooJar/*${PS}SpeJar/* Main5.java");
        expectPass(JAVA, "-classpath GooJar/*${PS}SpeJar/*${PS}. Main5");

        /*----------------------------------------------------------------
         * Verify jar file and class file in same directory.
         *----------------------------------------------------------------*/

        expectPass(JAVAC, "-classpath JarNClass/*${PS} Main.java");
        expectPass(JAVA, "-classpath JarNClass/*${PS}. Main");

        /*----------------------------------------------------------------
         * Verify these odd jar files work explicitly on classpath, kind of
         * a baseline. Last one is also a test with * in a jar name.
         *----------------------------------------------------------------*/

        expectFail(JAVAC, "-classpath SpeJar/Spe:Colon.jar Main1.java");

        expectPass(JAVAC, "-classpath SpeJar/Spe,Comma.jar Main2.java");
        expectPass(JAVA, "-classpath SpeJar/Spe,Comma.jar${PS}. Main2");

        if (!ToolBox.isWindows()) {
            expectPass(JAVAC, "-classpath SpeJar/Spe;Semi.jar Main3.java");
            expectPass(JAVA, "-classpath SpeJar/Spe;Semi.jar${PS}. Main3");

            expectPass(JAVAC, "-classpath SpeJar/Spe*wc.jar Main4.java");
            expectPass(JAVA, "-classpath SpeJar/Spe*wc.jar${PS}. Main4");
        }

        if (!ToolBox.isWindows()) {
            speJar();
        }

        if (!ToolBox.isWindows()) {
            starJar();
        }

        /*----------------------------------------------------------------
         * Verify these jar files with varying extensions
         *----------------------------------------------------------------*/

        // Mixed case extensions should not be loaded.
        expectFail(JAVAC, "-classpath MixJar/* Main1b.java");
        expectPass(JAVAC, "-classpath MixJar/mix.jAr Main1b.java");
        expectFail(JAVAC, "-classpath MixJar/* Main1b");

        // upper case, .JAR, extension should be loaded
        if (!ToolBox.isWindows()) {
            expectPass(JAVAC, "-classpath MixJar/* Main2.java");
        }
        expectPass(JAVAC, "-classpath .${PS}MixJar/* Main2.java");

        expectPass(JAVA, "-classpath MixJar/*${PS}. Main2");

        // zip extensions should not be loaded
        expectFail(JAVAC, "-classpath MixJar/* Main3.java");
        expectPass(JAVAC, "-classpath MixJar/mix3.zip Main3.java");
        expectFail(JAVA, "-classpath MixJar/*${PS}. Main3");

        // unix "hidden" file
        if (!ToolBox.isWindows()) {
            expectPass(JAVAC, "-classpath MixJar/* Main4.java");
            expectPass(JAVA, "-classpath MixJar/*${PS}. Main4");
        }
    }

    void starDirTests() throws Exception {
        out.println("Running tests with directory named \"*\"");
        deleteFiles("./StarDir");
        tb.createDirectories("StarDir/*");
        tb.copyFile("GooClass/Lib2.class", "StarDir/*/Lib2.class");
        jar("cf", "StarDir/Lib3.jar", "-C", "GooClass", "Lib3.class");
        jar("cf", "StarDir/*/Lib.jar", "-C", "GooClass", "Lib.class");
        checkFiles("StarDir/*/Lib.jar", "StarDir/*/Lib2.class", "StarDir/Lib3.jar");
        tb.copyFile("Main6.java", "./StarDir/.");
        tb.copyFile("Main.java", "./StarDir/*/.");
        tb.copyFile("Main1.java", "./StarDir/*/.");
        Path StarDir = Path.of("StarDir");
        expectFail(StarDir, JAVAC, "-classpath * Main6.java");
        expectFail(StarDir, JAVAC, "-classpath ./* Main6.java");
        deleteFiles(listFiles(StarDir, "Main6.*"));
        Path StarDir_star = StarDir.resolve("*");
        expectPass(StarDir_star, JAVAC, "-classpath * Main.java");
        expectPass(StarDir_star, JAVA, "-classpath .${PS}* Main");
        expectPass(StarDir_star, JAVAC, "Main1.java");
        expectPass(StarDir_star, JAVA, "-classpath . Main1");

        expectFail(JAVAC, "-classpath StarDir/* Main6.java");

        expectPass(JAVAC, "-classpath StarDir/* Main1.java");
        expectPass(JAVA, "-classpath StarDir/*:. Main1");

        expectPass(JAVAC, "-classpath StarDir/* Main1.java");
        expectPass(JAVA, "-classpath .${PS}StarDir/* Main1");

        expectFail(JAVAC, "-classpath StarDir/\\*/* Main.java");
        expectPass(JAVAC, "-classpath StarDir/*/* Main.java");

        expectPass(JAVA, "-classpath .${PS}StarDir/*/* Main");
        expectFail(JAVA, "-classpath .${PS}StarDir/\\*/* Main");

        expectPass(JAVAC, "-classpath StarDir/Lib3.jar Main6.java");
        expectPass(JAVA, "-classpath .${PS}StarDir/Lib3.jar Main6");

        expectPass(JAVAC, "-classpath StarDir/*/Lib.jar Main.java");
        expectPass(JAVA, "-classpath .${PS}StarDir/*/Lib.jar Main");
    }

    void speJar() throws Exception {
        out.println("Running tests with jar file names containing special characters");

        expectPass(JAVAC, "-classpath SpeJar/* Main2.java");
        expectPass(JAVA, "-classpath SpeJar/*${PS}. Main2");

        expectPass(JAVAC, "-classpath SpeJar/* Main3.java");
        expectPass(JAVA, "-classpath SpeJar/*${PS}. Main3");

        expectPass(JAVAC, "-classpath SpeJar/* Main4.java");
        expectPass(JAVA, "-classpath SpeJar/*${PS}. Main4");
    }

    /*----------------------------------------------------------------
     * Verify these jar files with asterisk in jar file name
     *----------------------------------------------------------------*/
    void starJar() throws Exception {
        out.println("Running tests with jar file names containing \"*\"");
        expectPass(JAVAC, "-classpath StarJar/*jar.jar Main2.java");
        expectPass(JAVA, "-classpath StarJar/*jar.jar${PS}. Main2");

        expectPass(JAVAC, "-classpath StarJar/jar*.jar Main3.java");
        expectPass(JAVA, "-classpath StarJar/jar*.jar${PS}. Main3");

        expectPass(JAVAC, "-classpath StarJar/*jar*.jar Main4.java");
        expectPass(JAVA, "-classpath StarJar/*jar*.jar${PS}. Main4");

        expectPass(JAVAC, "-classpath StarJar/* Main2.java");
        expectPass(JAVA, "-classpath StarJar/*${PS}. Main2");

        expectPass(JAVAC, "-classpath StarJar/* Main3.java");
        expectPass(JAVA, "-classpath StarJar/*${PS}. Main3");

        expectPass(JAVAC, "-classpath StarJar/* Main4.java");
        expectPass(JAVA, "-classpath StarJar/*${PS}. Main4");
    }
}
