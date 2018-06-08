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

/*
 * @test
 * @bug 8192920 8204588
 * @summary Test source mode
 * @modules jdk.compiler
 * @run main SourceMode
 */


import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SourceMode extends TestHelper {

    public static void main(String... args) throws Exception {
        new SourceMode().run(args);
    }

    // to reduce the chance of creating shebang lines that are too long,
    // we use a relative path to the java command if that is shorter
    private final Path shortJavaCmd;

    private final PrintStream log;

    private final boolean skipShebangTest;

    SourceMode() {
        Path cwd = Paths.get(System.getProperty("user.dir"));
        Path cmd = Paths.get(javaCmd);

        if (isWindows) {
            // Skip shebang tests on Windows, because that requires Cygwin.
            shortJavaCmd = cmd;
            skipShebangTest = true;
        } else {
            // Skip shebang tests if the path to the Java launcher is too long,
            // because that will cause tests to overflow the mostly undocumented
            // limit of 120 characters for a shebang line.
            Path p = cwd.relativize(cmd);
            shortJavaCmd = (p.toString().length() < cmd.toString().length()) ? p : cmd;
            skipShebangTest = shortJavaCmd.toString().length() > 100;
        }

        log = System.err;
    }

    // java Simple.java 1 2 3
    @Test
    void testSimpleJava() throws IOException {
        starting("testSimpleJava");
        Path file = getSimpleFile("Simple.java", false);
        TestResult tr = doExec(javaCmd, file.toString(), "1", "2", "3");
        if (!tr.isOK())
            error(tr, "Bad exit code: " + tr.exitValue);
        if (!tr.contains("[1, 2, 3]"))
            error(tr, "Expected output not found");
        show(tr);
    }

    // java --source 10 simple 1 2 3
    @Test
    void testSimple() throws IOException {
        starting("testSimple");
        Path file = getSimpleFile("simple", false);
        TestResult tr = doExec(javaCmd, "--source", "10", file.toString(), "1", "2", "3");
        if (!tr.isOK())
            error(tr, "Bad exit code: " + tr.exitValue);
        if (!tr.contains("[1, 2, 3]"))
            error(tr, "Expected output not found");
        show(tr);
    }

    // execSimple 1 2 3
    @Test
    void testExecSimple() throws IOException {
        starting("testExecSimple");
        if (skipShebangTest) {
            log.println("SKIPPED");
            return;
        }
        Path file = setExecutable(getSimpleFile("execSimple", true));
        TestResult tr = doExec(file.toAbsolutePath().toString(), "1", "2", "3");
        if (!tr.isOK())
            error(tr, "Bad exit code: " + tr.exitValue);
        if (!tr.contains("[1, 2, 3]"))
            error(tr, "Expected output not found");
        show(tr);
    }

    // java @simpleJava.at  (contains Simple.java 1 2 3)
    @Test
    void testSimpleJavaAtFile() throws IOException {
        starting("testSimpleJavaAtFile");
        Path file = getSimpleFile("Simple.java", false);
        Path atFile = Paths.get("simpleJava.at");
        createFile(atFile, List.of(file + " 1 2 3"));
        TestResult tr = doExec(javaCmd, "@" + atFile);
        if (!tr.isOK())
            error(tr, "Bad exit code: " + tr.exitValue);
        if (!tr.contains("[1, 2, 3]"))
            error(tr, "Expected output not found");
        show(tr);
    }

    // java @simple.at  (contains --source 10 simple 1 2 3)
    @Test
    void testSimpleAtFile() throws IOException {
        starting("testSimpleAtFile");
        Path file = getSimpleFile("simple", false);
        Path atFile = Paths.get("simple.at");
        createFile(atFile, List.of("--source 10 " + file + " 1 2 3"));
        TestResult tr = doExec(javaCmd, "@" + atFile);
        if (!tr.isOK())
            error(tr, "Bad exit code: " + tr.exitValue);
        if (!tr.contains("[1, 2, 3]"))
            error(tr, "Expected output not found");
        show(tr);
    }

    // java -cp classes Main.java 1 2 3
    @Test
    void testClasspath() throws IOException {
        starting("testClasspath");
        Path base = Files.createDirectories(Paths.get("testClasspath"));
        Path otherJava = base.resolve("Other.java");
        createFile(otherJava, List.of(
            "public class Other {",
            "  public static String join(String[] args) {",
            "    return String.join(\"-\", args);",
            "  }",
            "}"
        ));
        Path classes = Files.createDirectories(base.resolve("classes"));
        Path mainJava = base.resolve("Main.java");
        createFile(mainJava, List.of(
            "class Main {",
            "  public static void main(String[] args) {",
            "    System.out.println(Other.join(args));",
            "  }}"
        ));
        compile("-d", classes.toString(), otherJava.toString());
        TestResult tr = doExec(javaCmd, "-cp", classes.toString(),
                mainJava.toString(), "1", "2", "3");
        if (!tr.isOK())
            error(tr, "Bad exit code: " + tr.exitValue);
        if (!tr.contains("1-2-3"))
            error(tr, "Expected output not found");
        show(tr);
    }

    // java --add-exports=... Export.java --help
    @Test
    void testAddExports() throws IOException {
        starting("testAddExports");
        Path exportJava = Paths.get("Export.java");
        createFile(exportJava, List.of(
            "public class Export {",
            "  public static void main(String[] args) {",
            "    new com.sun.tools.javac.main.Main(\"demo\").compile(args);",
            "  }",
            "}"
        ));
        // verify access fails without --add-exports
        TestResult tr1 = doExec(javaCmd, exportJava.toString(), "--help");
        if (tr1.isOK())
            error(tr1, "Compilation succeeded unexpectedly");
        show(tr1);
        // verify access succeeds with --add-exports
        TestResult tr2 = doExec(javaCmd,
            "--add-exports", "jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
            exportJava.toString(), "--help");
        if (!tr2.isOK())
            error(tr2, "Bad exit code: " + tr2.exitValue);
        if (!(tr2.contains("demo") && tr2.contains("Usage")))
            error(tr2, "Expected output not found");
        show(tr2);
    }

    // java -cp ... HelloWorld.java  (for a class "java" in package "HelloWorld")
    @Test
    void testClassNamedJava() throws IOException {
        starting("testClassNamedJava");
        Path base = Files.createDirectories(Paths.get("testClassNamedJava"));
        Path src = Files.createDirectories(base.resolve("src"));
        Path srcfile = src.resolve("java.java");
        createFile(srcfile, List.of(
                "package HelloWorld;",
                "class java {",
                "    public static void main(String... args) {",
                "        System.out.println(HelloWorld.java.class.getName());",
                "    }",
                "}"
        ));
        Path classes = base.resolve("classes");
        compile("-d", classes.toString(), srcfile.toString());
        TestResult tr =
            doExec(javaCmd, "-cp", classes.toString(), "HelloWorld.java");
        if (!tr.isOK())
            error(tr, "Command failed");
        if (!tr.contains("HelloWorld.java"))
            error(tr, "Expected output not found");
        show(tr);
    }

    // java --source
    @Test
    void testSourceNoArg() throws IOException {
        starting("testSourceNoArg");
        TestResult tr = doExec(javaCmd, "--source");
        if (tr.isOK())
            error(tr, "Command succeeded unexpectedly");
        if (!tr.contains("--source requires source version"))
            error(tr, "Expected output not found");
        show(tr);
    }

    // java --source 10 -jar simple.jar
    @Test
    void testSourceJarConflict() throws IOException {
        starting("testSourceJarConflict");
        Path base = Files.createDirectories(Paths.get("testSourceJarConflict"));
        Path file = getSimpleFile("Simple.java", false);
        Path classes = Files.createDirectories(base.resolve("classes"));
        compile("-d", classes.toString(), file.toString());
        Path simpleJar = base.resolve("simple.jar");
        createJar("cf", simpleJar.toString(), "-C", classes.toString(), ".");
        TestResult tr =
            doExec(javaCmd, "--source", "10", "-jar", simpleJar.toString());
        if (tr.isOK())
            error(tr, "Command succeeded unexpectedly");
        if (!tr.contains("Option -jar is not allowed with --source"))
            error(tr, "Expected output not found");
        show(tr);
    }

    // java --source 10 -m jdk.compiler
    @Test
    void testSourceModuleConflict() throws IOException {
        starting("testSourceModuleConflict");
        TestResult tr = doExec(javaCmd, "--source", "10", "-m", "jdk.compiler");
        if (tr.isOK())
            error(tr, "Command succeeded unexpectedly");
        if (!tr.contains("Option -m is not allowed with --source"))
            error(tr, "Expected output not found");
        show(tr);
    }

    // #!.../java --source 10 -version
    @Test
    void testTerminalOptionInShebang() throws IOException {
        starting("testTerminalOptionInShebang");
        if (skipShebangTest) {
            log.println("SKIPPED");
            return;
        }
        Path base = Files.createDirectories(
            Paths.get("testTerminalOptionInShebang"));
        Path bad = base.resolve("bad");
        createFile(bad, List.of(
            "#!" + shortJavaCmd + " --source 10 -version"));
        setExecutable(bad);
        TestResult tr = doExec(bad.toString());
        if (!tr.contains("Option -version is not allowed in this context"))
            error(tr, "Expected output not found");
        show(tr);
    }

    // #!.../java --source 10 @bad.at  (contains -version)
    @Test
    void testTerminalOptionInShebangAtFile() throws IOException {
        starting("testTerminalOptionInShebangAtFile");
        if (skipShebangTest) {
            log.println("SKIPPED");
            return;
        }
        // Use a short directory name, to avoid line length limitations
        Path base = Files.createDirectories(Paths.get("testBadAtFile"));
        Path bad_at = base.resolve("bad.at");
        createFile(bad_at, List.of("-version"));
        Path bad = base.resolve("bad");
        createFile(bad, List.of(
            "#!" + shortJavaCmd + " --source 10 @" + bad_at));
        setExecutable(bad);
        TestResult tr = doExec(bad.toString());
        if (!tr.contains("Option -version in @testBadAtFile/bad.at is "
                + "not allowed in this context"))
            error(tr, "Expected output not found");
        show(tr);
    }

    // #!.../java --source 10 HelloWorld
    @Test
    void testMainClassInShebang() throws IOException {
        starting("testMainClassInShebang");
        if (skipShebangTest) {
            log.println("SKIPPED");
            return;
        }
        Path base = Files.createDirectories(Paths.get("testMainClassInShebang"));
        Path bad = base.resolve("bad");
        createFile(bad, List.of(
            "#!" + shortJavaCmd + " --source 10 HelloWorld"));
        setExecutable(bad);
        TestResult tr = doExec(bad.toString());
        if (!tr.contains("Cannot specify main class in this context"))
            error(tr, "Expected output not found");
        show(tr);
    }

    //--------------------------------------------------------------------------

    private void starting(String label) {
        System.out.println();
        System.out.println("*** Starting: " + label + " (stdout)");

        System.err.println();
        System.err.println("*** Starting: " + label + " (stderr)");
    }

    private void show(TestResult tr) {
        log.println("*** Test Output:");
        for (String line: tr.testOutput) {
            log.println(line);
        }
        log.println("*** End Of Test Output:");
    }

    private Map<String,String> getLauncherDebugEnv() {
        return Map.of("_JAVA_LAUNCHER_DEBUG", "1");
    }

    private Path getSimpleFile(String name, boolean shebang) throws IOException {
        Path file = Paths.get(name);
        if (!Files.exists(file)) {
            createFile(file, List.of(
                (shebang ? "#!" + shortJavaCmd + " --source 10" : ""),
                "public class Simple {",
                "  public static void main(String[] args) {",
                "    System.out.println(java.util.Arrays.toString(args));",
                "  }}"));
        }
        return file;
    }

    private void createFile(Path file, List<String> lines) throws IOException {
        lines.stream()
            .filter(line -> line.length() > 128)
            .forEach(line -> {
                    log.println("*** Warning: long line ("
                                        + line.length()
                                        + " chars) in file " + file);
                    log.println("*** " + line);
                });
        log.println("*** File: " + file);
        lines.stream().forEach(log::println);
        log.println("*** End Of File");
        createFile(file.toFile(), lines);
    }

    private Path setExecutable(Path file) throws IOException {
        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file);
        perms.add(PosixFilePermission.OWNER_EXECUTE);
        Files.setPosixFilePermissions(file, perms);
        return file;
    }

    private void error(TestResult tr, String message) {
        show(tr);
        throw new RuntimeException(message);
    }
}
