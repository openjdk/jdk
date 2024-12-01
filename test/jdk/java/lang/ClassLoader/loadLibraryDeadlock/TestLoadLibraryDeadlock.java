/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, BELLSOFT. All rights reserved.
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
 * @bug 8266310
 * @summary Checks if there's no deadlock between the two lock objects -
 *          class loading lock and ClassLoader.loadedLibraryNames hashmap.
 * @library /test/lib
 * @build LoadLibraryDeadlock Class1 p.Class2
 * @run main/othervm/native -Xcheck:jni TestLoadLibraryDeadlock
 */

import jdk.test.lib.Asserts;
import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.process.*;
import jdk.test.lib.util.FileUtils;

import java.lang.ProcessBuilder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.*;
import java.util.*;
import java.util.spi.ToolProvider;

import static jdk.test.lib.process.ProcessTools.*;

public class TestLoadLibraryDeadlock {

    private static final ToolProvider JAR = ToolProvider.findFirst("jar")
            .orElseThrow(() -> new RuntimeException("ToolProvider for jar not found"));

    private static final String KEYSTORE = "keystore.jks";
    private static final String STOREPASS = "changeit";
    private static final String KEYPASS = "changeit";
    private static final String ALIAS = "test";
    private static final String DNAME = "CN=test";
    private static final String VALIDITY = "366";

    private static String testClassPath = System.getProperty("test.classes");
    private static String testLibraryPath = System.getProperty("test.nativepath");
    private static String classPathSeparator = System.getProperty("path.separator");

    private static OutputAnalyzer runCommand(File workingDirectory, String... commands) throws Throwable {
        ProcessBuilder pb = new ProcessBuilder(commands);
        pb.directory(workingDirectory);
        System.out.println("COMMAND: " + String.join(" ", commands));
        return ProcessTools.executeProcess(pb);
    }

    private static OutputAnalyzer runCommandInTestClassPath(String... commands) throws Throwable {
        return runCommand(new File(testClassPath), commands);
    }

    private static OutputAnalyzer genKey() throws Throwable {
        FileUtils.deleteFileIfExistsWithRetry(
                Paths.get(testClassPath, KEYSTORE));
        String keytool = JDKToolFinder.getJDKTool("keytool");
        return runCommandInTestClassPath(keytool,
                "-storepass", STOREPASS,
                "-keypass", KEYPASS,
                "-keystore", KEYSTORE,
                "-keyalg", "rsa", "-keysize", "2048",
                "-genkeypair",
                "-alias", ALIAS,
                "-dname", DNAME,
                "-validity", VALIDITY
        );
    }

    private static void createJar(String outputJar, String... classes) throws Throwable {
        List<String> args = new ArrayList<>();
        Collections.addAll(args, "cvf", Paths.get(testClassPath, outputJar).toString());
        for (String c : classes) {
            Collections.addAll(args, "-C", testClassPath, c);
        }
        if (JAR.run(System.out, System.err, args.toArray(new String[0])) != 0) {
            throw new RuntimeException("jar operation failed");
        }
    }

    private static OutputAnalyzer signJar(String jarToSign) throws Throwable {
        String jarsigner = JDKToolFinder.getJDKTool("jarsigner");
        return runCommandInTestClassPath(jarsigner,
                "-keystore", KEYSTORE,
                "-storepass", STOREPASS,
                jarToSign, ALIAS
        );
    }

    private final static long countLines(OutputAnalyzer output, String string) {
        return output.asLines()
                     .stream()
                     .filter(s -> s.contains(string))
                     .count();
    }

    public static void main(String[] args) throws Throwable {
        genKey()
                .shouldHaveExitValue(0);

        Path aJar = Path.of(testClassPath, "a.jar");
        Path bJar = Path.of(testClassPath, "b.jar");
        Path cJar = Path.of(testClassPath, "c.jar");

        FileUtils.deleteFileIfExistsWithRetry(aJar);
        FileUtils.deleteFileIfExistsWithRetry(bJar);
        FileUtils.deleteFileIfExistsWithRetry(cJar);

        createJar("a.jar",
                "LoadLibraryDeadlock.class",
                "LoadLibraryDeadlock$1.class",
                "LoadLibraryDeadlock$2.class");

        createJar("b.jar",
                "Class1.class");

        createJar("c.jar",
                "p/Class2.class");

        signJar("c.jar")
                .shouldHaveExitValue(0);

        // load trigger class
        OutputAnalyzer outputAnalyzer = executeCommand(createTestJavaProcessBuilder("-cp",
                aJar.toString() + classPathSeparator +
                bJar.toString() + classPathSeparator +
                cJar.toString(),
                "-Djava.library.path=" + testLibraryPath,
                "LoadLibraryDeadlock"));
        outputAnalyzer.shouldHaveExitValue(0);

        Asserts.assertTrue(
                countLines(outputAnalyzer, "Java-level deadlock") == 0,
                "Found a deadlock.");

        // if no deadlock, make sure all components have been loaded
        Asserts.assertTrue(
                countLines(outputAnalyzer, "Class Class1 not found.") == 0,
                "Unable to load class. Class1 not found.");

        Asserts.assertTrue(
                countLines(outputAnalyzer, "Class Class2 not found.") == 0,
                "Unable to load class. Class2 not found.");

        Asserts.assertTrue(
                countLines(outputAnalyzer, "Native library loaded.") > 0,
                "Unable to load native library.");

        Asserts.assertTrue(
                countLines(outputAnalyzer, "Class1 loaded from " + toLocationString(bJar)) > 0,
                "Unable to load " + toLocationString(bJar));

        Asserts.assertTrue(
                countLines(outputAnalyzer, "Class2 loaded from " + toLocationString(cJar)) > 0,
                "Unable to load signed " + toLocationString(cJar));

        Asserts.assertTrue(
                countLines(outputAnalyzer, "Signed jar loaded from native library.") > 0,
                "Unable to load signed jar from native library.");
    }

    private static String toLocationString(Path path) {
        // same format as returned by LoadLibraryDeadlock::getLocation
        return path.toUri().getPath();
    }
}
