/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.Utils;
import jdk.test.lib.compiler.CompilerUtils;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.util.JarUtils;
import static jdk.test.lib.Utils.TEST_SRC;
import jdk.test.lib.process.OutputAnalyzer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
 * @test
 * @bug 8311299
 * @summary make sure socket is closed when the error happens for flushing
 * @library /test/lib
 * @modules jdk.compiler
 */

public class RunTest {

    private static final String MAIN_JAR = "main.jar";
    private static final String CORE_JAR = "core.jar";
    private static final String POLICY_FILE = "my.policy";

    public static void main(String[] args) throws Throwable {
        new RunTest().run();
    }

    private void run() throws Throwable {
        prepareTestClass();
        buildTestJars();
        preparePolicyFile();
        runTest();
    }

    private void buildTestJars() throws IOException {
        // The launcher
        buildJar(MAIN_JAR, "(Main.*|Data)\\.class");
    }

    private void prepareTestClass() throws IOException {
        Path srcPath = Path.of("src");
        Files.createDirectories(srcPath);
        Files.find(Path.of(TEST_SRC), 1,
                (file, attrs) -> file.toString().endsWith(".java") && !file
                        .toString()
                        .contains(this.getClass().getName() + ".java"))
                .forEach(p -> {
                    try {
                        Files.copy(p, srcPath.resolve(p.getFileName()));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

        if (!CompilerUtils.compile(srcPath, Path.of(""))) {
            throw new RuntimeException("Compile failed.");
        }
    }

    private void buildJar(String jarName, String classesNamePattern)
            throws IOException {
        JarUtils.createJar(jarName, Files.find(Path.of(""), 1,
                (file, attrs) -> file.getFileName().toString()
                        .matches(classesNamePattern))
                .map(p -> p.getFileName().toString()).toArray(String[]::new));
    }

    private void preparePolicyFile() throws IOException {
        List<String> content = new ArrayList<>();
        content.add("grant codeBase \"file:./" + CORE_JAR + "\" {");
        content.add("  permission java.security.AllPermission;");
        content.add("};");
        content.add("grant codeBase \"file:./" + MAIN_JAR + "\" {");
        content.add(
                "  permission java.lang.RuntimePermission \"accessClassInPackage.com.sun.jndi.ldap\";");
        content.add("};");

        Files.write(Path.of(POLICY_FILE), content);
    }

    private void runTest() throws Throwable {
        String classPath = Stream.of(MAIN_JAR, CORE_JAR)
                .collect(Collectors.joining(File.pathSeparator));
        // When main app is restricted, even trusted code cannot do bad things
        OutputAnalyzer outputAnalyzer = ProcessTools.executeCommand(
                Stream.of(List.of(JDKToolFinder.getJDKTool("java")),
                        List.of(Utils.getTestJavaOpts()),
                        List.of("-Djava.security.manager",
                                "-Djava.security.policy=" + POLICY_FILE, "-cp",
                                classPath, "Main")).flatMap(Collection::stream)
                        .toArray(String[]::new));//.shouldHaveExitValue(1).;

        outputAnalyzer.stdoutShouldContain(Main.SOCKET_CLOSED_MSG);
        outputAnalyzer.stdoutShouldNotContain(Main.SOCKET_NOT_CLOSED_MSG);

        outputAnalyzer.stdoutShouldContain(Main.BAD_FLUSH);
    }
}
