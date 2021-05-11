/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @summary deadlock while loading the JNI code
 * @library /test/lib
 * @build LoadLibraryDeadlock Class1 p.Class2
 * @run main/othervm/native -Xcheck:jni TestLoadLibraryDeadlock
 */

import jdk.test.lib.Asserts;
import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.process.*;

import java.lang.ProcessBuilder;
import java.lang.Process;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;

public class TestLoadLibraryDeadlock {

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
        runCommandInTestClassPath("rm", "-f", KEYSTORE);
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

    private static OutputAnalyzer createJar(String outputJar, String... classes) throws Throwable {
        String jar = JDKToolFinder.getJDKTool("jar");
        List<String> commands = new ArrayList<String>();
        Collections.addAll(commands, jar, "cvf", outputJar);
        Collections.addAll(commands, classes);
        return runCommandInTestClassPath(commands.toArray(new String[0]));
    }

    private static OutputAnalyzer signJar(String jarToSign) throws Throwable {
        String jarsigner = JDKToolFinder.getJDKTool("jarsigner");
        return runCommandInTestClassPath(jarsigner,
                "-keystore", KEYSTORE,
                "-storepass", STOREPASS,
                jarToSign, ALIAS
        );
    }

    private static Process runJavaCommand(String... command) throws Throwable {
        String java = JDKToolFinder.getJDKTool("java");
        List<String> commands = new ArrayList<String>();
        Collections.addAll(commands, java);
        Collections.addAll(commands, command);
        System.out.println("COMMAND: " + String.join(" ", commands));
        return new ProcessBuilder(commands.toArray(new String[0]))
                .redirectErrorStream(true)
                .directory(new File(testClassPath))
                .start();
    }

    private static OutputAnalyzer jcmd(long pid, String command) throws Throwable {
        String jcmd = JDKToolFinder.getJDKTool("jcmd");
        return runCommandInTestClassPath(jcmd,
                String.valueOf(pid),
                command
        );
    }

    private static String readAvailable(final InputStream is) throws Throwable {
        final List<String> list = Collections.synchronizedList(new ArrayList<String>());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<String> future = executor.submit(new Callable<String>() {
            public String call() {
                String result = new String();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                try {
                    while(true) {
                        String s = reader.readLine();
                        if (s.length() > 0) {
                            list.add(s);
                            result += s + "\n";
                        }
                    }
                } catch (IOException ignore) {}
                return result;
            }
        });
        try {
            return future.get(1000, TimeUnit.MILLISECONDS);
        } catch (Exception ignoreAll) {
            future.cancel(true);
            return String.join("\n", list);
        }
    }

    public static void main(String[] args) throws Throwable {
        genKey()
                .shouldHaveExitValue(0);

        runCommandInTestClassPath("rm", "-f", "*.jar")
                .shouldHaveExitValue(0);

        createJar("a.jar",
                "LoadLibraryDeadlock.class",
                "LoadLibraryDeadlock$1.class",
                "LoadLibraryDeadlock$2.class")
                .shouldHaveExitValue(0);

        createJar("b.jar",
                "Class1.class")
                .shouldHaveExitValue(0);

        createJar("c.jar",
                "p/Class2.class")
                .shouldHaveExitValue(0);

        signJar("c.jar")
                .shouldHaveExitValue(0);

        // load trigger class
        Process process = runJavaCommand("-cp",
                "a.jar" + classPathSeparator +
                "b.jar" + classPathSeparator +
                "c.jar",
                "-Djava.library.path=" + testLibraryPath,
                "LoadLibraryDeadlock");

        // wait for a while to grab some output
        process.waitFor(5, TimeUnit.SECONDS);

        // dump available output
        String output = readAvailable(process.getInputStream());
        OutputAnalyzer outputAnalyzer = new OutputAnalyzer(output);
        outputAnalyzer
                .asLines().stream()
                .forEach(s -> System.out.println(s));

        // if the process is still running, get the thread dump
        OutputAnalyzer outputAnalyzerJcmd = jcmd(process.pid(), "Thread.print");
        outputAnalyzerJcmd
                .asLines().stream()
                .forEach(s -> System.out.println(s));

        Asserts.assertTrue(outputAnalyzerJcmd
                .asLines().stream()
                .filter(s -> s.contains("Java-level deadlock"))
                .count() == 0,
                "Found a deadlock.");

        // if no deadlock, make sure all components have been loaded
        Asserts.assertTrue(outputAnalyzer
                .asLines().stream()
                .filter(s -> s.contains("Class Class1 not found."))
                .count() == 0,
                "Unable to load class. Class1 not found.");

        Asserts.assertTrue(outputAnalyzer
                .asLines().stream()
                .filter(s -> s.contains("Class Class2 not found."))
                .count() == 0,
                "Unable to load class. Class2 not found.");

        Asserts.assertTrue(outputAnalyzer
                .asLines().stream()
                .filter(s -> s.contains("Native library loaded."))
                .count() > 0,
                "Unable to load native library.");

        Asserts.assertTrue(outputAnalyzer
                .asLines().stream()
                .filter(s -> s.contains("Signed jar loaded."))
                .count() > 0,
                "Unable to load signed jar.");

        Asserts.assertTrue(outputAnalyzer
                .asLines().stream()
                .filter(s -> s.contains("Signed jar loaded from native library."))
                .count() > 0,
                "Unable to load signed jar from native library.");

        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            // if process is still frozen, fail the test even though
            // the "deadlock" text hasn't been found
            process.destroyForcibly();
            Asserts.assertTrue(process.waitFor() == 0,
                    "Process frozen.");
        }
    }
}
