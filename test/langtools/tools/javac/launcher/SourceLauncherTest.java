/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8192920 8204588 8246774 8248843 8268869 8235876 8328339 8335896 8344706
 * @summary Test source launcher
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.launcher
 *          jdk.compiler/com.sun.tools.javac.main
 *          java.base/jdk.internal.module
 * @build toolbox.JavaTask toolbox.JavacTask toolbox.TestRunner toolbox.ToolBox
 * @run main SourceLauncherTest
 */

import java.lang.classfile.*;
import java.lang.classfile.attribute.ModuleResolutionAttribute;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import com.sun.tools.javac.launcher.SourceLauncher;
import com.sun.tools.javac.launcher.Fault;

import toolbox.JavaTask;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.TestRunner;
import toolbox.ToolBox;

import static jdk.internal.module.ClassFileConstants.WARN_INCUBATING;

public class SourceLauncherTest extends TestRunner {
    public static void main(String... args) throws Exception {
        SourceLauncherTest t = new SourceLauncherTest();
        t.runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    SourceLauncherTest() {
        super(System.err);
        tb = new ToolBox();
        System.err.println("version: " + thisVersion);
    }

    private final ToolBox tb;
    private static final String thisVersion = System.getProperty("java.specification.version");

    /*
     * Positive tests.
     */

    @Test
    public void testHelloWorld(Path base) throws IOException {
        tb.writeJavaFiles(base,
            "import java.util.Arrays;\n" +
            "class HelloWorld {\n" +
            "    public static void main(String... args) {\n" +
            "        System.out.println(\"Hello World! \" + Arrays.toString(args));\n" +
            "    }\n" +
            "}");
        testSuccess(base.resolve("HelloWorld.java"), "Hello World! [1, 2, 3]\n");
    }

    @Test
    public void testHelloWorldInPackage(Path base) throws IOException {
        tb.writeJavaFiles(base,
            "package hello;\n" +
            "import java.util.Arrays;\n" +
            "class World {\n" +
            "    public static void main(String... args) {\n" +
            "        System.out.println(\"Hello World! \" + Arrays.toString(args));\n" +
            "    }\n" +
            "}");
        testSuccess(base.resolve("hello").resolve("World.java"), "Hello World! [1, 2, 3]\n");
    }

    @Test
    public void testHelloWorldInPackageWithStaticImport(Path base) throws IOException {
        tb.writeJavaFiles(base,
                """
                package hello;
                import static hello.Helper.*;
                import java.util.Arrays;
                class World {
                    public static void main(String... args) {
                        m(args);
                    }
                }
                class Helper {
                    static void m(String... args) {
                        System.out.println("Hello World! " + Arrays.toString(args));
                    }
                }
                """);
        testSuccess(base.resolve("hello").resolve("World.java"), "Hello World! [1, 2, 3]\n");
    }

    @Test
    public void testHelloWorldWithAux(Path base) throws IOException {
        tb.writeJavaFiles(base,
            "import java.util.Arrays;\n" +
            "class HelloWorld {\n" +
            "    public static void main(String... args) {\n" +
            "        Aux.write(args);\n" +
            "    }\n" +
            "}\n" +
            "class Aux {\n" +
            "    static void write(String... args) {\n" +
            "        System.out.println(\"Hello World! \" + Arrays.toString(args));\n" +
            "    }\n" +
            "}");
        testSuccess(base.resolve("HelloWorld.java"), "Hello World! [1, 2, 3]\n");
    }

    @Test
    public void testHelloWorldWithShebang(Path base) throws IOException {
        tb.writeJavaFiles(base,
            "#!/usr/bin/java --source " + thisVersion + "\n" +
            "import java.util.Arrays;\n" +
            "class HelloWorld {\n" +
            "    public static void main(String... args) {\n" +
            "        System.out.println(\"Hello World! \" + Arrays.toString(args));\n" +
            "    }\n" +
            "}");
        Files.copy(base.resolve("HelloWorld.java"), base.resolve("HelloWorld"));
        testSuccess(base.resolve("HelloWorld"), "Hello World! [1, 2, 3]\n");
    }

    @Test
    public void testNoAnnoProcessing(Path base) throws IOException {
        Path annoSrc = base.resolve("annoSrc");
        tb.writeJavaFiles(annoSrc,
            "import java.util.*;\n" +
            "import javax.annotation.processing.*;\n" +
            "import javax.lang.model.element.*;\n" +
            "@SupportedAnnotationTypes(\"*\")\n" +
            "public class AnnoProc extends AbstractProcessor {\n" +
            "    public boolean process(Set<? extends TypeElement> annos, RoundEnvironment rEnv) {\n" +
            "        throw new Error(\"Annotation processor should not be invoked\");\n" +
            "    }\n" +
            "}\n");
        Path annoClasses = Files.createDirectories(base.resolve("classes"));
        new JavacTask(tb)
                .outdir(annoClasses)
                .files(annoSrc.resolve("AnnoProc.java").toString())
                .run();
        Path serviceFile = annoClasses.resolve("META-INF").resolve("services")
                .resolve("javax.annotation.processing.Processor");
        tb.writeFile(serviceFile, "AnnoProc");

        Path mainSrc = base.resolve("mainSrc");
        tb.writeJavaFiles(mainSrc,
            "import java.util.Arrays;\n" +
            "class HelloWorld {\n" +
            "    public static void main(String... args) {\n" +
            "        System.out.println(\"Hello World! \" + Arrays.toString(args));\n" +
            "    }\n" +
            "}");

        List<String> javacArgs = List.of("-classpath", annoClasses.toString());
        List<String> classArgs = List.of("1", "2", "3");
        String expect = "Hello World! [1, 2, 3]\n";
        Result r = run(mainSrc.resolve("HelloWorld.java"), javacArgs, classArgs);
        checkEqual("stdout", r.stdOut, expect);
        checkEmpty("stderr", r.stdErr);
        checkNull("exception", r.exception);
    }

    @Test
    public void testEnablePreview(Path base) throws IOException {
        tb.writeJavaFiles(base,
            "import java.util.Arrays;\n" +
            "class HelloWorld {\n" +
            "    public static void main(String... args) {\n" +
            "        System.out.println(\"Hello World! \" + Arrays.toString(args));\n" +
            "    }\n" +
            "}");

        String log = new JavaTask(tb)
                .vmOptions("--enable-preview", "--source", thisVersion)
                .className(base.resolve("HelloWorld.java").toString())
                .classArgs("1", "2", "3")
                .run(Task.Expect.SUCCESS)
                .getOutput(Task.OutputKind.STDOUT);
        checkEqual("stdout", log.trim(), "Hello World! [1, 2, 3]");
    }

    @Test
    public void testCodeSource(Path base) throws IOException {
        tb.writeJavaFiles(base,
            "import java.net.URL;\n" +
            "class ShowCodeSource {\n" +
            "    public static void main(String... args) {\n" +
            "        URL u = ShowCodeSource.class.getProtectionDomain().getCodeSource().getLocation();\n" +
            "        System.out.println(u);\n" +
            "    }\n" +
            "}");

        Path file = base.resolve("ShowCodeSource.java");
        String log = new JavaTask(tb)
                .className(file.toString())
                .run(Task.Expect.SUCCESS)
                .getOutput(Task.OutputKind.STDOUT);
        checkEqual("stdout", log.trim(), file.toAbsolutePath().toUri().toURL().toString());
    }

    @Test
    public void testSystemProperty(Path base) throws IOException {
        tb.writeJavaFiles(base,
            "class ShowProperty {\n" +
            "    public static void main(String... args) {\n" +
            "        System.out.println(System.getProperty(\"jdk.launcher.sourcefile\"));\n" +
            "    }\n" +
            "}");

        Path file = base.resolve("ShowProperty.java");
        String log = new JavaTask(tb)
                .className(file.toString())
                .run(Task.Expect.SUCCESS)
                .getOutput(Task.OutputKind.STDOUT);
        checkEqual("stdout", log.trim(), file.toAbsolutePath().toString());
    }

    @Test
    public void testThreadContextClassLoader(Path base) throws IOException {
        tb.writeJavaFiles(base, //language=java
                """
                class ThreadContextClassLoader {
                    public static void main(String... args) {
                        var expected = ThreadContextClassLoader.class.getClassLoader();
                        var actual = Thread.currentThread().getContextClassLoader();
                        System.out.println(expected == actual);
                    }
                }
                """);

        Path file = base.resolve("ThreadContextClassLoader.java");
        String log = new JavaTask(tb)
                .className(file.toString())
                .run(Task.Expect.SUCCESS)
                .getOutput(Task.OutputKind.STDOUT);
        checkEqual("stdout", log.trim(), "true");
    }

    void testSuccess(Path file, String expect) throws IOException {
        Result r = run(file, Collections.emptyList(), List.of("1", "2", "3"));
        checkEqual("stdout", r.stdOut, expect);
        checkEmpty("stderr", r.stdErr);
        checkNull("exception", r.exception);
    }


    @Test
    public void testMainNoParams(Path base) throws IOException {
        tb.writeJavaFiles(base,
            "package hello;\n" +
            "import java.util.Arrays;\n" +
            "class World {\n" +
            "    public static void main(String... args) {\n" +
            "        System.out.println(\"Hello World! \" + Arrays.toString(args));\n" +
            "    }\n" +
            "}");
        testSuccess(base.resolve("hello").resolve("World.java"), "Hello World! [1, 2, 3]\n");
    }

    @Test
    public void testMainNotPublic(Path base) throws IOException {
        tb.writeJavaFiles(base,
            "package hello;\n" +
            "import java.util.Arrays;\n" +
            "class World {\n" +
            "    static void main(String... args) {\n" +
            "        System.out.println(\"Hello World! \" + Arrays.toString(args));\n" +
            "    }\n" +
            "}");
        testSuccess(base.resolve("hello").resolve("World.java"), "Hello World! [1, 2, 3]\n");
    }

    @Test
    public void testMainNotStatic(Path base) throws IOException {
        tb.writeJavaFiles(base,
            "package hello;\n" +
            "import java.util.Arrays;\n" +
            "class World {\n" +
            "    public void main(String... args) {\n" +
            "        System.out.println(\"Hello World! \" + Arrays.toString(args));\n" +
            "    }\n" +
            "}");
        testSuccess(base.resolve("hello").resolve("World.java"), "Hello World! [1, 2, 3]\n");
    }

    /*
     * Negative tests: such as cannot find or execute main method.
     */

    @Test
    public void testHelloWorldWithShebangJava(Path base) throws IOException {
        tb.writeJavaFiles(base,
            "#!/usr/bin/java --source " + thisVersion + "\n" +
            "import java.util.Arrays;\n" +
            "class HelloWorld {\n" +
            "    public static void main(String... args) {\n" +
            "        System.out.println(\"Hello World! \" + Arrays.toString(args));\n" +
            "    }\n" +
            "}");
        Path file = base.resolve("HelloWorld.java");
        testError(file,
            file + ":1: error: illegal character: '#'\n" +
            "#!/usr/bin/java --source " + thisVersion + "\n" +
            "^\n" +
            file + ":1: error: class, interface, annotation type, enum, record, method or field expected\n" +
            "#!/usr/bin/java --source " + thisVersion + "\n" +
            "  ^\n" +
            "2 errors\n",
            "error: compilation failed");
    }

    @Test
    public void testNoClass(Path base) throws IOException {
        var path = Files.createDirectories(base.resolve("p"));
        Path file = path.resolve("NoClass.java");
        Files.write(file, List.of("package p;"));
        testError(file, "", "error: no class declared in source file");
    }

    @Test
    public void testMismatchOfPathAndPackage(Path base) throws IOException {
        Files.createDirectories(base);
        Path file = base.resolve("MismatchOfPathAndPackage.java");
        Files.write(file, List.of("package p; class MismatchOfPathAndPackage {}"));
        testError(file, "", "error: end of path to source file does not match its package name p: " + file);
    }

    @Test
    public void testLoadClass(Path base) throws IOException {
        Path src1 = base.resolve("src1");
        Path file1 = src1.resolve("LoadClass.java");
        tb.writeJavaFiles(src1,
                "class LoadClass {\n"
                + "    public static void main(String... args) {\n"
                + "        System.out.println(\"on classpath\");\n"
                + "    };\n"
                + "}\n");
        Path classes1 = Files.createDirectories(base.resolve("classes"));
        new JavacTask(tb)
                .outdir(classes1)
                .files(file1)
                .run();
        String log1 = new JavaTask(tb)
                .classpath(classes1.toString())
                .className("LoadClass")
                .run(Task.Expect.SUCCESS)
                .getOutput(Task.OutputKind.STDOUT);
        checkEqual("stdout", log1.trim(),
                "on classpath");

        Path src2 = base.resolve("src2");
        Path file2 = src2.resolve("LoadClass.java");
        tb.writeJavaFiles(src2,
                "class LoadClass {\n"
                + "    public static void main(String... args) {\n"
                + "        System.out.println(\"in source file\");\n"
                + "    };\n"
                + "}\n");
        String log2 = new JavaTask(tb)
                .classpath(classes1.toString())
                .className(file2.toString())
                .run(Task.Expect.SUCCESS)
                .getOutput(Task.OutputKind.STDOUT);
        checkEqual("stdout", log2.trim(),
                "in source file");
    }

    @Test
    public void testGetResource(Path base) throws IOException {
        Path src = base.resolve("src");
        Path file = src.resolve("GetResource.java");
        tb.writeJavaFiles(src,
                "class GetResource {\n"
                + "    public static void main(String... args) {\n"
                + "        System.out.println(GetResource.class.getClassLoader().getResource(\"GetResource.class\"));\n"
                + "    };\n"
                + "}\n");
        Path classes = Files.createDirectories(base.resolve("classes"));
        new JavacTask(tb)
                .outdir(classes)
                .files(file)
                .run();

        String log = new JavaTask(tb)
                .classpath(classes.toString())
                .className(file.toString())
                .run(Task.Expect.SUCCESS)
                .getOutput(Task.OutputKind.STDOUT);
        checkMatch("stdout", log.trim(),
                Pattern.compile("sourcelauncher-memoryclassloader[0-9]+:GetResource.class"));
    }

    @Test
    public void testGetResources(Path base) throws IOException {
        Path src = base.resolve("src");
        Path file = src.resolve("GetResources.java");
        tb.writeJavaFiles(src,
                "import java.io.*; import java.net.*; import java.util.*;\n"
                + "class GetResources {\n"
                + "    public static void main(String... args) throws IOException {\n"
                + "        Enumeration<URL> e =\n"
                + "            GetResources.class.getClassLoader().getResources(\"GetResources.class\");\n"
                + "        while (e.hasMoreElements()) System.out.println(e.nextElement());\n"
                + "    };\n"
                + "}\n");
        Path classes = Files.createDirectories(base.resolve("classes"));
        new JavacTask(tb)
                .outdir(classes)
                .files(file)
                .run();

        List<String> log = new JavaTask(tb)
                .classpath(classes.toString())
                .className(file.toString())
                .run(Task.Expect.SUCCESS)
                .getOutputLines(Task.OutputKind.STDOUT);
        checkMatch("stdout:0", log.get(0).trim(),
                Pattern.compile("sourcelauncher-memoryclassloader[0-9]+:GetResources.class"));
        checkMatch("stdout:1", log.get(1).trim(),
                Pattern.compile("file:/.*/testGetResources/classes/GetResources.class"));
    }

    @Test
    public void testSyntaxErr(Path base) throws IOException {
        tb.writeJavaFiles(base, "class SyntaxErr {");
        Path file = base.resolve("SyntaxErr.java");
        testError(file,
                file + ":1: error: reached end of file while parsing\n" +
                "class SyntaxErr {\n" +
                "                 ^\n" +
                "1 error\n",
                "error: compilation failed");
    }

    @Test
    public void testNoSourceOnClassPath(Path base) throws IOException {
        Path extraSrc = base.resolve("extraSrc");
        tb.writeJavaFiles(extraSrc,
            "public class Extra {\n" +
            "    static final String MESSAGE = \"Hello World\";\n" +
            "}\n");

        Path mainSrc = base.resolve("mainSrc");
        tb.writeJavaFiles(mainSrc,
            "import java.util.Arrays;\n" +
            "class HelloWorld {\n" +
            "    public static void main(String... args) {\n" +
            "        System.out.println(Extra.MESSAGE + Arrays.toString(args));\n" +
            "    }\n" +
            "}");

        List<String> javacArgs = List.of("-classpath", extraSrc.toString());
        List<String> classArgs = List.of("1", "2", "3");
        String FS = File.separator;
        String expectStdErr =
            "testNoSourceOnClassPath" + FS + "mainSrc" + FS + "HelloWorld.java:4: error: cannot find symbol\n" +
            "        System.out.println(Extra.MESSAGE + Arrays.toString(args));\n" +
            "                           ^\n" +
            "  symbol:   variable Extra\n" +
            "  location: class HelloWorld\n" +
            "1 error\n";
        Result r = run(mainSrc.resolve("HelloWorld.java"), javacArgs, classArgs);
        checkEmpty("stdout", r.stdOut);
        checkEqual("stderr", r.stdErr, expectStdErr);
        checkFault("exception", r.exception, "error: compilation failed");
    }

    @Test
    public void testClassNotFound(Path base) throws IOException {
        Path src = base.resolve("src");
        Path file = src.resolve("ClassNotFound.java");
        tb.writeJavaFiles(src,
                "class ClassNotFound {\n"
                + "    public static void main(String... args) {\n"
                + "        try {\n"
                + "            Class.forName(\"NoSuchClass\");\n"
                + "            System.out.println(\"no exception\");\n"
                + "            System.exit(1);\n"
                + "        } catch (ClassNotFoundException e) {\n"
                + "            System.out.println(\"Expected exception thrown: \" + e);\n"
                + "        }\n"
                + "    };\n"
                + "}\n");
        Path classes = Files.createDirectories(base.resolve("classes"));
        new JavacTask(tb)
                .outdir(classes)
                .files(file)
                .run();

        String log = new JavaTask(tb)
                .classpath(classes.toString())
                .className(file.toString())
                .run(Task.Expect.SUCCESS)
                .getOutput(Task.OutputKind.STDOUT);
        checkEqual("stdout", log.trim(),
                "Expected exception thrown: java.lang.ClassNotFoundException: NoSuchClass");
    }

    // For any source file that is invoked through the OS shebang mechanism, invalid shebang
    // lines will be caught and handled by the OS, before the launcher is even invoked.
    // However, if such a file is passed directly to the launcher, perhaps using the --source
    // option, a well-formed shebang line will be removed but a badly-formed one will be not be
    // removed and will cause compilation errors.
    @Test
    public void testBadShebang(Path base) throws IOException {
        tb.writeJavaFiles(base,
            "#/usr/bin/java --source " + thisVersion + "\n" +
            "import java.util.Arrays;\n" +
            "class HelloWorld {\n" +
            "    public static void main(String... args) {\n" +
            "        System.out.println(\"Hello World! \" + Arrays.toString(args));\n" +
            "    }\n" +
            "}");
        Path file = base.resolve("HelloWorld.java");
        testError(file,
            file + ":1: error: illegal character: '#'\n" +
            "#/usr/bin/java --source " + thisVersion + "\n" +
            "^\n" +
            file + ":1: error: class, interface, annotation type, enum, record, method or field expected\n" +
            "#/usr/bin/java --source " + thisVersion + "\n" +
            "  ^\n" +
            "2 errors\n",
            "error: compilation failed");
    }

    @Test
    public void testBadSourceOpt(Path base) throws IOException {
        Files.createDirectories(base);
        Path file = base.resolve("DummyClass.java");
        Files.write(file, List.of("class DummyClass { }"));
        Properties sysProps = System.getProperties();
        Properties p = new Properties(sysProps);
        p.setProperty("jdk.internal.javac.source", "<BAD>");
        System.setProperties(p);
        try {
            testError(file, "", "error: invalid value for --source option: <BAD>");
        } finally {
            System.setProperties(sysProps);
        }
    }

    @Test
    public void testEnablePreviewNoSource(Path base) throws IOException {
        tb.writeJavaFiles(base,
            "import java.util.Arrays;\n" +
            "class HelloWorld {\n" +
            "    public static void main(String... args) {\n" +
            "        System.out.println(\"Hello World! \" + Arrays.toString(args));\n" +
            "    }\n" +
            "}");

        List<String> log = new JavaTask(tb)
                .vmOptions("--enable-preview")
                .className(base.resolve("HelloWorld.java").toString())
                .run(Task.Expect.SUCCESS)
                .getOutputLines(Task.OutputKind.STDOUT);
        checkEqual("stdout", log, List.of("Hello World! []"));
    }

    @Test
    public void testNoMain(Path base) throws IOException {
        tb.writeJavaFiles(base, "class NoMain { }");
        testError(base.resolve("NoMain.java"), "",
                "error: can't find main(String[]) or main() method in class: NoMain");
    }

    @Test
    public void testMainBadParams(Path base) throws IOException {
        tb.writeJavaFiles(base,
                "class BadParams { public static void main(int n) { } }");
        testError(base.resolve("BadParams.java"), "",
                "error: can't find main(String[]) or main() method in class: BadParams");
    }

    @Test
    public void testMainNotVoid(Path base) throws IOException {
        tb.writeJavaFiles(base,
                "class NotVoid { public static int main(String... args) { return 0; } }");
        testError(base.resolve("NotVoid.java"), "",
                "error: can't find main(String[]) or main() method in class: NotVoid");
    }

    @Test
    public void testClassInModule(Path base) throws IOException {
        tb.writeJavaFiles(base, "package java.net; class InModule { }");
        Path file = base.resolve("java").resolve("net").resolve("InModule.java");
        testError(file,
                file + ":1: error: package exists in another module: java.base\n" +
                "package java.net; class InModule { }\n" +
                "^\n" +
                "1 error\n",
                "error: compilation failed");
    }

    @Test
    public void testNoRecompileWithSuggestions(Path base) throws IOException {
        tb.writeJavaFiles(base,
            "class NoRecompile {\n" +
            "    void use(String s) {}\n" +
            "    void test() {\n" +
            "        use(1);\n" +
            "    }\n" +
            "    <T> void test(T t, Object o) {\n" +
            "        T t1 = (T) o;\n" +
            "    }\n" +
            "    static class Generic<T> {\n" +
            "        T t;\n" +
            "        void raw(Generic raw) {\n" +
            "            raw.t = \"\";\n" +
            "        }\n" +
            "    }\n" +
            "    void deprecation() {\n" +
            "        Thread.currentThread().stop();\n" +
            "    }\n" +
            "    void preview(Object o) {\n" +
            "      if (o instanceof String s) {\n" +
            "          System.out.println(s);\n" +
            "      }\n" +
            "    }\n" +
            "}");
        Result r = run(base.resolve("NoRecompile.java"), Collections.emptyList(), Collections.emptyList());
        if (r.stdErr.contains("recompile with")) {
            error("Unexpected recompile suggestions in error output: " + r.stdErr);
        }
    }

    @Test
    public void testNoOptionsWarnings(Path base) throws IOException {
        tb.writeJavaFiles(base, "public class Main { public static void main(String... args) {}}");
        String log = new JavaTask(tb)
                .vmOptions("--source", "21")
                .className(base.resolve("Main.java").toString())
                .run(Task.Expect.SUCCESS)
                .getOutput(Task.OutputKind.STDERR);

        if (log.contains("warning: [options]")) {
            error("Unexpected options warning in error output: " + log);
        }
    }

    void testError(Path file, String expectStdErr, String expectFault) throws IOException {
        Result r = run(file, Collections.emptyList(), List.of("1", "2", "3"));
        checkEmpty("stdout", r.stdOut);
        checkEqual("stderr", r.stdErr, expectStdErr);
        checkFault("exception", r.exception, expectFault);
    }

    /*
     * Tests in which main throws an exception.
     */
    @Test
    public void testTargetException1(Path base) throws IOException {
        tb.writeJavaFiles(base,
            "import java.util.Arrays;\n" +
            "class Thrower {\n" +
            "    public static void main(String... args) {\n" +
            "        throwWhenZero(Integer.parseInt(args[0]));\n" +
            "    }\n" +
            "    static void throwWhenZero(int arg) {\n" +
            "        if (arg == 0) throw new Error(\"zero!\");\n" +
            "        throwWhenZero(arg - 1);\n" +
            "    }\n" +
            "}");
        Path file = base.resolve("Thrower.java");
        Result r = run(file, Collections.emptyList(), List.of("3"));
        checkEmpty("stdout", r.stdOut);
        checkEmpty("stderr", r.stdErr);
        checkTrace("exception", r.exception,
                "java.lang.Error: zero!",
                "at Thrower.throwWhenZero(Thrower.java:7)",
                "at Thrower.throwWhenZero(Thrower.java:8)",
                "at Thrower.throwWhenZero(Thrower.java:8)",
                "at Thrower.throwWhenZero(Thrower.java:8)",
                "at Thrower.main(Thrower.java:4)");
    }

    @Test
    public void testNoDuplicateIncubatorWarning(Path base) throws Exception {
        Path module = base.resolve("lib");
        Path moduleSrc = module.resolve("src");
        Path moduleClasses = module.resolve("classes");
        Files.createDirectories(moduleClasses);
        tb.cleanDirectory(moduleClasses);
        tb.writeJavaFiles(moduleSrc, "module test {}");
        new JavacTask(tb)
                .outdir(moduleClasses)
                .files(tb.findJavaFiles(moduleSrc))
                .run()
                .writeAll();
        markModuleAsIncubator(moduleClasses.resolve("module-info.class"));
        tb.writeJavaFiles(base, "public class Main { public static void main(String... args) {}}");
        String log = new JavaTask(tb)
                .vmOptions("--module-path", moduleClasses.toString(),
                           "--add-modules", "test")
                .className(base.resolve("Main.java").toString())
                .run(Task.Expect.SUCCESS)
                .writeAll()
                .getOutput(Task.OutputKind.STDERR);

        int numberOfWarnings = log.split("WARNING").length - 1;

        if (log.contains("warning:") || numberOfWarnings != 1) {
            error("Unexpected warning in error output: " + log);
        }

        List<String> compileLog = new JavacTask(tb)
                .options("--module-path", moduleClasses.toString(),
                         "--add-modules", "test",
                         "-XDrawDiagnostics",
                         "-XDsourceLauncher",
                         "-XDshould-stop.at=FLOW")
                .files(base.resolve("Main.java").toString())
                .run(Task.Expect.SUCCESS)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        List<String> expectedOutput = List.of(
                "- compiler.warn.incubating.modules: test",
                "1 warning"
        );

        if (!expectedOutput.equals(compileLog)) {
            error("Unexpected options : " + compileLog);
        }
    }
        //where:
        private static void markModuleAsIncubator(Path moduleInfoFile) throws Exception {
            ClassModel cf = ClassFile.of().parse(moduleInfoFile);
            ModuleResolutionAttribute newAttr = ModuleResolutionAttribute.of(WARN_INCUBATING);
            byte[] newBytes = ClassFile.of().transformClass(cf,
                    ClassTransform.endHandler(classBuilder -> classBuilder.with(newAttr)));
            try (OutputStream out = Files.newOutputStream(moduleInfoFile)) {
                out.write(newBytes);
            }
        }

    @Test
    public void testAbstractClassInstanceMain(Path base) throws IOException {
        tb.writeJavaFiles(base,
                          """
                          public abstract class AbstractMain {
                              void main(String[] args) {}
                          }
                          """);
        testError(base.resolve("AbstractMain.java"), "",
                "error: abstract class: AbstractMain can not be instantiated");
    }

    @Test
    public void testWrongMainPrivate(Path base) throws IOException {
        tb.writeJavaFiles(base,
                          """
                          public class WrongMainPrivate {
                              private static void main(String[] args) {}
                              void main() {
                                  System.out.println("correct");
                              }
                          }
                          """);
        testSuccess(base.resolve("WrongMainPrivate.java"),
                    "correct\n");
    }

    @Test
    public void testWrongMainPrivateInstance(Path base) throws IOException {
        tb.writeJavaFiles(base,
                          """
                          public class WrongMainPrivate {
                              private void main(String[] args) {}
                              void main() {
                                  System.out.println("correct");
                              }
                          }
                          """);
        testSuccess(base.resolve("WrongMainPrivate.java"),
                    "correct\n");
    }

    @Test
    public void testWrongMainReturnType(Path base) throws IOException {
        tb.writeJavaFiles(base,
                          """
                          public class WrongMainReturnType {
                              public static int main(String[] args) {
                                  return -1;
                              }
                              void main() {
                                  System.out.println("correct");
                              }
                          }
                          """);
        testSuccess(base.resolve("WrongMainReturnType.java"),
                    "correct\n");
    }

    @Test
    public void testWrongMainReturnTypeInstance(Path base) throws IOException {
        tb.writeJavaFiles(base,
                          """
                          public class WrongMainReturnType {
                              public int main(String[] args) {
                                  return -1;
                              }
                              void main() {
                                  System.out.println("correct");
                              }
                          }
                          """);
        testSuccess(base.resolve("WrongMainReturnType.java"),
                    "correct\n");
    }

    Result run(Path file, List<String> runtimeArgs, List<String> appArgs) {
        List<String> args = new ArrayList<>();
        args.add(file.toString());
        args.addAll(appArgs);

        PrintStream prev = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(baos, true)) {
            System.setOut(out);
            StringWriter sw = new StringWriter();
            try (PrintWriter err = new PrintWriter(sw, true)) {
                SourceLauncher m = new SourceLauncher(err);
                m.run(toArray(runtimeArgs), toArray(args));
                return new Result(baos.toString(), sw.toString(), null);
            } catch (Throwable t) {
                return new Result(baos.toString(), sw.toString(), t);
            }
        } finally {
            System.setOut(prev);
        }
    }

    void checkEqual(String name, String found, String expect) {
        expect = expect.replace("\n", tb.lineSeparator);
        out.println(name + ": " + found);
        if (!expect.equals(found)) {
            error("Unexpected output; expected: " + expect);
        }
    }

    void checkContains(String name, String found, String expect) {
        expect = expect.replace("\n", tb.lineSeparator);
        out.println(name + ": " + found);
        if (!found.contains(expect)) {
            error("Expected output not found: " + expect);
        }
    }

    void checkEqual(String name, List<String> found, List<String> expect) {
        out.println(name + ": " + found);
        tb.checkEqual(expect, found);
    }

    void checkMatch(String name, String found, Pattern expect) {
        out.println(name + ": " + found);
        if (!expect.matcher(found).matches()) {
            error("Unexpected output; expected match for: " + expect);
        }
    }

    void checkEmpty(String name, String found) {
        out.println(name + ": " + found);
        if (!found.isEmpty()) {
            error("Unexpected output; expected empty string");
        }
    }

    void checkNull(String name, Throwable found) {
        out.println(name + ": " + found);
        if (found != null) {
            error("Unexpected exception; expected null");
        }
    }

    void checkFault(String name, Throwable found, String expect) {
        expect = expect.replace("\n", tb.lineSeparator);
        out.println(name + ": " + found);
        if (found == null) {
            error("No exception thrown; expected Fault");
        } else {
            if (!(found instanceof Fault)) {
                error("Unexpected exception; expected Fault");
            }
            if (!(found.getMessage().equals(expect))) {
                error("Unexpected detail message; expected: " + expect);
            }
        }
    }

    void checkTrace(String name, Throwable found, String... expect) {
        if (!(found instanceof InvocationTargetException)) {
            error("Unexpected exception; expected InvocationTargetException");
            out.println("Found:");
            found.printStackTrace(out);
        }
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            ((InvocationTargetException) found).getTargetException().printStackTrace(pw);
        }
        String trace = sw.toString();
        out.println(name + ":\n" + trace);
        String[] traceLines = trace.trim().split("[\r\n]+\\s+");
        try {
            tb.checkEqual(List.of(traceLines), List.of(expect));
        } catch (Error e) {
            error(e.getMessage());
        }
    }

    String[] toArray(List<String> list) {
        return list.toArray(new String[list.size()]);
    }

    record Result(String stdOut, String stdErr, Throwable exception) {}
}
