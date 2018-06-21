/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test source launcher
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.launcher
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.JavaTask toolbox.JavacTask toolbox.TestRunner toolbox.ToolBox
 * @run main SourceLauncherTest
 */

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
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

import com.sun.tools.javac.launcher.Main;

import toolbox.JavaTask;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.TestRunner;
import toolbox.TestRunner;
import toolbox.ToolBox;

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

    void testSuccess(Path file, String expect) throws IOException {
        Result r = run(file, Collections.emptyList(), List.of("1", "2", "3"));
        checkEqual("stdout", r.stdOut, expect);
        checkEmpty("stderr", r.stdErr);
        checkNull("exception", r.exception);
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
            file + ":1: error: class, interface, or enum expected\n" +
            "#!/usr/bin/java --source " + thisVersion + "\n" +
            "  ^\n" +
            "2 errors\n",
            "error: compilation failed");
    }

    @Test
    public void testNoClass(Path base) throws IOException {
        Files.createDirectories(base);
        Path file = base.resolve("NoClass.java");
        Files.write(file, List.of("package p;"));
        testError(file, "", "error: no class declared in file");
    }

    @Test
    public void testWrongClass(Path base) throws IOException {
        Path src = base.resolve("src");
        Path file = src.resolve("WrongClass.java");
        tb.writeJavaFiles(src, "class WrongClass { }");
        Path classes = Files.createDirectories(base.resolve("classes"));
        new JavacTask(tb)
                .outdir(classes)
                .files(file)
                .run();
        String log = new JavaTask(tb)
                .classpath(classes.toString())
                .className(file.toString())
                .run(Task.Expect.FAIL)
                .getOutput(Task.OutputKind.STDERR);
        checkEqual("stderr", log.trim(),
                "error: class found on application class path: WrongClass");
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
        Path auxSrc = base.resolve("auxSrc");
        tb.writeJavaFiles(auxSrc,
            "public class Aux {\n" +
            "    static final String MESSAGE = \"Hello World\";\n" +
            "}\n");

        Path mainSrc = base.resolve("mainSrc");
        tb.writeJavaFiles(mainSrc,
            "import java.util.Arrays;\n" +
            "class HelloWorld {\n" +
            "    public static void main(String... args) {\n" +
            "        System.out.println(Aux.MESSAGE + Arrays.toString(args));\n" +
            "    }\n" +
            "}");

        List<String> javacArgs = List.of("-classpath", auxSrc.toString());
        List<String> classArgs = List.of("1", "2", "3");
        String FS = File.separator;
        String expectStdErr =
            "testNoSourceOnClassPath" + FS + "mainSrc" + FS + "HelloWorld.java:4: error: cannot find symbol\n" +
            "        System.out.println(Aux.MESSAGE + Arrays.toString(args));\n" +
            "                           ^\n" +
            "  symbol:   variable Aux\n" +
            "  location: class HelloWorld\n" +
            "1 error\n";
        Result r = run(mainSrc.resolve("HelloWorld.java"), javacArgs, classArgs);
        checkEmpty("stdout", r.stdOut);
        checkEqual("stderr", r.stdErr, expectStdErr);
        checkFault("exception", r.exception, "error: compilation failed");

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
            file + ":1: error: class, interface, or enum expected\n" +
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

        String log = new JavaTask(tb)
                .vmOptions("--enable-preview")
                .className(base.resolve("HelloWorld.java").toString())
                .run(Task.Expect.FAIL)
                .getOutput(Task.OutputKind.STDERR);
        checkEqual("stderr", log.trim(),
                "error: --enable-preview must be used with --source");
    }

    @Test
    public void testNoMain(Path base) throws IOException {
        tb.writeJavaFiles(base, "class NoMain { }");
        testError(base.resolve("NoMain.java"), "",
                "error: can't find main(String[]) method in class: NoMain");
    }

    @Test
    public void testMainBadParams(Path base) throws IOException {
        tb.writeJavaFiles(base,
                "class BadParams { public static void main() { } }");
        testError(base.resolve("BadParams.java"), "",
                "error: can't find main(String[]) method in class: BadParams");
    }

    @Test
    public void testMainNotPublic(Path base) throws IOException {
        tb.writeJavaFiles(base,
                "class NotPublic { static void main(String... args) { } }");
        testError(base.resolve("NotPublic.java"), "",
                "error: 'main' method is not declared 'public static'");
    }

    @Test
    public void testMainNotStatic(Path base) throws IOException {
        tb.writeJavaFiles(base,
                "class NotStatic { public void main(String... args) { } }");
        testError(base.resolve("NotStatic.java"), "",
                "error: 'main' method is not declared 'public static'");
    }

    @Test
    public void testMainNotVoid(Path base) throws IOException {
        tb.writeJavaFiles(base,
                "class NotVoid { public static int main(String... args) { return 0; } }");
        testError(base.resolve("NotVoid.java"), "",
                "error: 'main' method is not declared with a return type of 'void'");
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
                Main m = new Main(err);
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
        out.println(name + ": " + found);
        if (!expect.equals(found)) {
            error("Unexpected output; expected: " + expect);
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
            error("No exception thrown; expected Main.Fault");
        } else {
            if (!(found instanceof Main.Fault)) {
                error("Unexpected exception; expected Main.Fault");
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

    class Result {
        private final String stdOut;
        private final String stdErr;
        private final Throwable exception;

        Result(String stdOut, String stdErr, Throwable exception) {
            this.stdOut = stdOut;
            this.stdErr = stdErr;
            this.exception = exception;
        }
    }
}
