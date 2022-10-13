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
 * @bug 4758537 4809833 8149599 8293877
 * @summary Test that javac and java find files in similar ways
 * @library /tools/lib
 * @build toolbox.ToolBox Util MineField
 * @run main MineField
 */

/*
 * Converted from MineField.sh, originally written by Martin Buchholz.
 *
 * For the last version of the original, MineField.sh, see
 * https://git.openjdk.org/jdk/blob/jdk-19%2B36/test/langtools/tools/javac/Paths/MineField.sh
 *
 * This class primarily tests that javac and the java launcher provide
 * equivalent handling of all path-related options, like {@code -classpath}.
 */

/*
#----------------------------------------------------------------
# The search order for classes used by both java and javac is:
#
# -Xbootclasspath/p:<path>
# -endorseddirs <dirs> or -Djava.endorsed.dirs=<dirs> (search for jar/zip only)
# -bootclasspath <path> or -Xbootclasspath:<path>
# -Xbootclasspath/a:<path>
# -extdirs <dirs> or -Djava.ext.dirs=<dirs> (search for jar/zip only)
# -classpath <path>, -cp <path>, env CLASSPATH=<path>
#
# Peculiarities of the class file search:
# - Empty elements of the (user) classpath default to ".",
#   while empty elements of other paths are ignored.
# - Only for the user classpath is an empty string value equivalent to "."
# - Specifying a bootclasspath on the command line obliterates any
#   previous -Xbootclasspath/p: or -Xbootclasspath/a: command line flags.
#
# JDK 9 update:
#   java: The java launcher does not support any of the following:
#       * -Xbootclasspath/p: -Xbootclasspath:
#       * -endorseddirs -Djava.endorsed.dirs
#       * -extdirs -Djava.ext.dirs
#       All test cases exercising these features have been removed.
#   javac: The following features are only supported when compiling
#       for older releases:
#       * -Xbootclasspath/p: -Xbootclasspath: -bootclasspath -Xbootclasspath/a:
#       * -endorseddirs -Djava.endorsed.dirs
#       * -extdirs -Djava.ext.dirs
#       All test cases exercising these features have been modified to
#       use -source 8 -target 8.  In addition, javac test cases involving
#       use of the runtime properties java.endorsed.dirs and java.extdirs
#       (by means of -J-Dname=value) have been removed.
#       Although the primary purpose of the test cases in this file is to
#       compare javac and java behavior, some tests remain for javac for
#       which there is no java equivalent. However, the cases remain as useful
#       test cases for javac handling of the paths involved.
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class MineField extends Util {
    public static void main(String... args) throws Exception {
        new MineField().run(args);
    }

    void run(String... args) throws Exception{
        setup();
        tests();
        cleanup();
        bottomLine();
    }

    void cleanup() throws IOException {
        deleteFiles("GooSrc", "GooJar", "GooZip", "GooClass");
        deleteFiles("BadSrc", "BadJar", "BadZip", "BadClass");
        deleteFiles("OneDir", "Main.java", "MANIFEST.MF");
        deleteFiles(listFiles(Path.of("."), "*.class"));
        deleteFiles("java-lang.jar");
    }

    /**
     * "Prepare the minefield".
     */
    void setup() throws Exception {
        cleanup();

        tb.createDirectories("GooSrc", "GooJar", "GooZip", "GooClass");
        tb.createDirectories("BadSrc", "BadJar", "BadZip", "BadClass");

        Files.writeString(Path.of("Lib.java"),
                "public class Lib {public static void f(){}}");
        javac("Lib.java");
        jar("cf", "GooJar/Lib.jar", "Lib.class");
        jar("cf", "GooZip/Lib.zip", "Lib.class");
        tb.moveFile("Lib.class", "GooClass/.");
        tb.moveFile("Lib.java", "GooSrc/.");
        checkFiles("GooZip/Lib.zip", "GooJar/Lib.jar", "GooSrc/Lib.java");

        Files.writeString(Path.of("Lib.java"),
                "public class Lib {/* Bad */}");
        javac("Lib.java");
        jar("cf", "BadJar/Lib.jar", "Lib.class");
        jar("cf", "BadZip/Lib.zip", "Lib.class");
        tb.moveFile("Lib.class", "BadClass/.");
        tb.moveFile("Lib.java", "BadSrc/.");
        checkFiles("BadZip/Lib.zip", "BadJar/Lib.jar", "BadSrc/Lib.java");

        Files.writeString(Path.of("Main.java"),
                "public class Main {public static void main(String[] a) {Lib.f();}}");
        Path libModules = javaHome.resolve("lib").resolve("modules");
        if (Files.isReadable(libModules)) {
            jimage("extract", "--dir", "modules", libModules.toString());
            jar("cf", "java-lang.jar", "-C", "modules/java.base", "java/lang");
            deleteFiles("modules");
        } else {
            Path modules = javaHome.resolve("modules");
            if (Files.isDirectory(modules)) {
                jar("cf", "java-lang.jar", "-C", modules.resolve("java.base").toString(), "java/lang");
            } else {
                throw new Exception("Cannot create java-lang.jar");
            }
        }
    }

    void tests() throws Exception {

        //----------------------------------------------------------------
        // Verify that javac class search order is the same as java's
        //----------------------------------------------------------------

        expectFail(JAVAC, """
                -source 8 -target 8
                -Xbootclasspath/p:GooClass
                -bootclasspath java-lang.jar${PS}BadZip/Lib.zip
                Main.java""");

        expectPass(JAVAC, """
                -source 8 -target 8
                -Xbootclasspath/p:BadClass${PS}GooClass
                -bootclasspath java-lang.jar${PS}GooZip/Lib.zip${PS}BadClass
                Main.java""");

        expectPass(JAVAC, """
                -source 8 -target 8
                -Xbootclasspath/p:BadJar/Lib.jar
                -Xbootclasspath:java-lang.jar${PS}GooClass
                Main.java""");

        //----------------------------------------------------------------

        expectFail(JAVAC, """
                -source 8 -target 8
                -bootclasspath java-lang.jar${PS}GooZip/Lib.zip
                -Xbootclasspath/p:BadClass
                Main.java""");

        expectPass(JAVAC, """
                -source 8 -target 8
                -bootclasspath java-lang.jar${PS}BadZip/Lib.zip
                -Xbootclasspath/p:GooClass${PS}BadJar/Lib.jar
                Main.java""");

        //----------------------------------------------------------------

        expectFail(JAVAC, """
                -source 8 -target 8
                -Xbootclasspath/p:BadClass
                -Xbootclasspath/a:GooClass
                Main.java""");

        expectPass(JAVAC, """
                -source 8 -target 8
                -Xbootclasspath/p:GooClass${PS}BadClass
                -Xbootclasspath/a:BadClass
                Main.java""");

        expectPass(JAVA, """
                -Xbootclasspath/a:GooClass
                Main""");

        //----------------------------------------------------------------

        expectFail(JAVAC, """
                -source 8 -target 8
                -Xbootclasspath/p:GooClass
                -Xbootclasspath:BadClass${PS}java-lang.jar
                -Xbootclasspath/a:GooClass
                Main.java""");

        expectPass(JAVAC, """
                -source 8 -target 8
                -Xbootclasspath/p:BadClass
                -Xbootclasspath:GooClass${PS}BadClass${PS}java-lang.jar
                -Xbootclasspath/a:BadClass
                Main.java""");

        //----------------------------------------------------------------

        expectPass(JAVAC, """
                -source 8 -target 8
                -endorseddirs BadClass${PS}GooZip${PS}BadJar
                -Xbootclasspath:"BadClass${PS}java-lang.jar
                Main.java""");

        expectPass(JAVAC, """
                -source 8 -target 8
                -Djava.endorsed.dirs=BadClass${PS}GooZip${PS}BadJar
                -Xbootclasspath:BadClass${PS}java-lang.jar
                Main.java""");

        //----------------------------------------------------------------

        expectFail(JAVAC, """
                -source 8 -target 8
                -Xbootclasspath/a:BadClass
                -extdirs GooZip
                Main.java""");

        expectPass(JAVAC, """
                -source 8 -target 8
                -Xbootclasspath/a:GooClass${PS}BadClass
                -extdirs BadZip
                Main.java""");

        //----------------------------------------------------------------

        expectFail(JAVAC, """
                -source 8 -target 8
                -extdirs GooClass${PS}BadZip
                -cp GooZip/Lib.zip
                Main.java""");

        expectPass(JAVAC, """
                -source 8 -target 8
                -extdirs BadClass${PS}GooZip${PS}BadJar
                -cp BadZip/Lib.zip
                Main.java""");

        expectPass(JAVAC, """
                -source 8 -target 8
                -Djava.ext.dirs=GooZip${PS}BadJar
                -classpath BadZip/Lib.zip
                Main.java""");

        //----------------------------------------------------------------

        expectFail(JAVAC, "-classpath BadClass${PS}GooClass Main.java");
        expectPass(JAVAC, "-classpath GooClass${PS}BadClass Main.java");
        expectFail(JAVA,  "-classpath BadClass${PS}GooClass${PS}. Main");
        expectPass(JAVA,  "-classpath GooClass${PS}BadClass${PS}. Main");

        expectFail(JAVAC, "-cp BadJar/Lib.jar${PS}GooZip/Lib.zip Main.java");
        expectPass(JAVAC, "-cp GooJar/Lib.jar${PS}BadZip/Lib.zip Main.java");
        expectFail(JAVA,  "-cp BadJar/Lib.jar${PS}${PS}GooZip/Lib.zip Main");
        expectPass(JAVA,  "-cp GooJar/Lib.jar${PS}${PS}BadZip/Lib.zip Main");

        //----------------------------------------------------------------

        expectFail(classpath("BadZip/Lib.zip${PS}GooJar/Lib.jar"), JAVAC,"Main.java");
        expectPass(classpath("GooZip/Lib.zip${PS}BadJar/Lib.jar"), JAVAC, "Main.java");
        expectFail(classpath("${PS}BadZip/Lib.zip${PS}GooJar/Lib.jar"), JAVA, "Main");
        expectPass(classpath("${PS}GooZip/Lib.zip${PS}BadJar/Lib.jar"), JAVA, "Main");

        //----------------------------------------------------------------
        // Check behavior of empty paths and empty path elements
        //----------------------------------------------------------------

        Path GooClass = Path.of("GooClass");
        Path GooJar = Path.of("GooJar");

        expectFail(GooClass,  JAVAC, "-cp .. ../Main.java");
        expectFail(GooClass,  JAVA, "-cp .. Main");

        // Unspecified classpath defaults to "."
        Path OneDir = Path.of("OneDir");
        tb.createDirectories(OneDir);
        tb.copyFile(Path.of("Main.java"), OneDir);
        tb.copyFile(GooClass.resolve("Lib.class"), OneDir);
        expectPass(OneDir,  JAVAC, "Main.java");
        expectPass(OneDir,  JAVA, "Main");

        // Empty classpath elements mean "."
        expectPass(GooClass,  JAVAC, "-cp ${PS}.. ../Main.java");
        expectPass(GooClass,  JAVA,  "-cp ${PS}.. Main");

        expectPass(GooClass,  JAVAC, "-cp ..${PS} ../Main.java");
        expectPass(GooClass,  JAVA,  "-cp ..${PS} Main");

        expectPass(GooClass,  JAVAC, "-cp ..${PS}${PS}/xyzzy ../Main.java");
        expectPass(GooClass,  JAVA,  "-cp ..${PS}${PS}/xyzzy Main");

        // All other empty path elements are ignored.

        // note presence of empty arg in this invocation
        expectFail(GooJar,  null, JAVAC, "-source", "8", "-target", "8", "-extdirs", "", "-cp", "..", "../Main.java");

        expectFail(GooJar,  JAVAC, "-source 8 -target 8 -extdirs        ${PS} -cp .. ../Main.java");
        expectFail(GooJar,  JAVAC, "-source 8 -target 8 -Djava.ext.dirs=${PS} -cp .. ../Main.java");

        expectPass(GooJar,  JAVAC, "-source 8 -target 8 -extdirs        . -cp .. ../Main.java");
        expectPass(GooJar,  JAVAC, "-source 8 -target 8 -Djava.ext.dirs=. -cp .. ../Main.java");

        expectFail(GooJar,  JAVAC, "-source 8 -target 8 -Djava.endorsed.dirs= -cp .. ../Main.java");

        expectFail(GooJar,  JAVAC, "-source 8 -target 8 -endorseddirs        ${PS} -cp .. ../Main.java");

        expectPass(GooJar,  JAVAC, "-source 8 -target 8 -Djava.endorsed.dirs=. -cp .. ../Main.java");

        expectFail(GooClass,  JAVAC, "-source 8 -target 8 -Xbootclasspath/p: -cp .. ../Main.java");

        expectPass(GooClass,  JAVAC, "-source 8 -target 8 -Xbootclasspath/p:. -cp .. ../Main.java");

        expectFail(GooClass,  JAVAC, "-source 8 -target 8 -Xbootclasspath:../java-lang.jar -cp .. ../Main.java");

        expectPass(GooClass,  JAVAC, "-source 8 -target 8 -Xbootclasspath:../java-lang.jar${PS}. -cp .. ../Main.java");

        expectFail(GooClass,  JAVAC, "-source 8 -target 8 -Xbootclasspath/a: -cp .. ../Main.java");
        expectFail(GooClass,  JAVA, "-Xbootclasspath/a: -cp .. Main");

        expectPass(GooClass,  JAVAC, "-source 8 -target 8 -Xbootclasspath/a:. -cp .. ../Main.java");
        expectPass(GooClass,  JAVA, "-Xbootclasspath/a:. -cp .. Main");

    }


}
