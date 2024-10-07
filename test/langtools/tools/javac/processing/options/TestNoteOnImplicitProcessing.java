/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8310061 8315534 8306819
 * @summary Verify behavior around implicit annotation processing
 *
 * @library /tools/lib /tools/javac/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask JavacTestingAbstractProcessor
 * @run main TestNoteOnImplicitProcessing
 */

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import javax.annotation.processing.Processor;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import toolbox.JavacTask;
import toolbox.Task;
import toolbox.Task.Expect;
import toolbox.TestRunner;
import toolbox.ToolBox;
import toolbox.JarTask;

/*
 * Does not generates a note and the processor does not run:
 * $ javac -cp ImplicitProcTestProc.jar                                     HelloWorldTest.java
 *
 * Does _not_ generate a note and the processor does run:
 * $ javac -processorpath ImplicitProcTestProc.jar                          HelloWorldTest.java
 * $ javac -cp ImplicitProcTestProc.jar -processor ImplicitProcTestProc.jar HelloWorldTest.java
 * $ javac -cp ImplicitProcTestProc.jar -proc:full                          HelloWorldTest.java
 * $ javac -cp ImplicitProcTestProc.jar -proc:only                          HelloWorldTest.java
 *
 * Does _not_ generate a note and the processor does _not_run:
 * $ javac -cp ImplicitProcTestProc.jar -Xlint:-options                     HelloWorldTest.java
 * $ javac -cp ImplicitProcTestProc.jar -Xlint:none                         HelloWorldTest.java
 *
 * Does _not_ generate a note and the processor _doesn't_ run.
 * $ javac -cp ImplicitProcTestProc.jar -proc:none                          HelloWorldTest.java
 *
 * (Previously, annotation processing was implicitly enabled and the
 * the class path was searched for processors. This test was
 * originally written to probe around a note warning of a potential
 * future policy change to disable such implicit processing, a policy
 * change now implemented and this test has been updated accordingly.)
 */

public class TestNoteOnImplicitProcessing extends TestRunner {
    public static void main(String... args) throws Exception {

        var self  = new TestNoteOnImplicitProcessing();
        Path jarFilePath = self.createProcessorJarFile();
        self.runTests(m -> new Object[] { Paths.get(m.getName()), jarFilePath });
    }

    private ToolBox tb = new ToolBox();
    private String processorName = "ImplicitProcTestProc";

    public TestNoteOnImplicitProcessing() {
        super(System.err);
    }

    private Path createProcessorJarFile() throws Exception {
        Path apDir = Paths.get(".");

        // Write out shared-use source file
        tb.writeJavaFiles(apDir,
                          """
                          public class HelloWorldTest {
                              public static void main(String... args) {
                                  System.out.println("Hello world test.");
                              }
                          }
                          """);

        JarTask jarTask = new JarTask(tb, processorName + ".jar");

        // write out META-INF/services file for the processor
        Path servicesFile =
            apDir
            .resolve("META-INF")
            .resolve("services")
            .resolve(Processor.class.getCanonicalName());
        tb.writeFile(servicesFile, processorName);

        // write out processor source file
        tb.writeJavaFiles(apDir,
                          """
                          import java.util.Set;
                          import javax.annotation.processing.*;
                          import javax.lang.model.SourceVersion;
                          import javax.lang.model.element.TypeElement;

                          @SupportedAnnotationTypes("*")
                          public class ImplicitProcTestProc extends AbstractProcessor {
                              public ImplicitProcTestProc() {super();}

                              @Override
                              public boolean process(Set<? extends TypeElement> annotations,
                                                     RoundEnvironment roundEnv) {
                                  if (roundEnv.processingOver()) {
                                      System.out.println("ImplicitProcTestProc run");
                                  }
                                  return true;
                              }

                              @Override
                              public SourceVersion getSupportedSourceVersion() {
                                  return SourceVersion.latest();
                              }
                          }
                          """);

        // Compile the processor
        new JavacTask(tb)
            .files(processorName + ".java")
            .run(Expect.SUCCESS)
            .writeAll();

        // Create jar file
        jarTask
            .files(servicesFile.toString(),
                   apDir.resolve(processorName + ".class").toString())
            .run();

        return Paths.get(processorName + ".jar");
    }

    @Test
    public void generateWarning(Path base, Path jarFile) {
        Task.Result javacResult =
            new JavacTask(tb)
            .options("-classpath", jarFile.toString(),
                     "-XDrawDiagnostics")
            .files("HelloWorldTest.java")
            .run(Expect.SUCCESS)
            .writeAll();

        checkForProcessorMessage(javacResult, false);
        checkForCompilerNote(javacResult, false);
    }

    @Test
    public void processorPath(Path base, Path jarFile) {
        Task.Result javacResult =
            new JavacTask(tb)
            .options("-processorpath", jarFile.toString(),
                     "-XDrawDiagnostics")
            .files("HelloWorldTest.java")
            .run(Expect.SUCCESS)
            .writeAll();

        checkForProcessorMessage(javacResult, true);
        checkForCompilerNote(javacResult, false);
    }

    @Test
    public void processor(Path base, Path jarFile) {
        Task.Result javacResult =
            new JavacTask(tb)
            .options("-classpath", jarFile.toString(),
                     "-processor", processorName,
                     "-XDrawDiagnostics")
            .files("HelloWorldTest.java")
            .run(Expect.SUCCESS)
            .writeAll();

        checkForProcessorMessage(javacResult, true);
        checkForCompilerNote(javacResult, false);
    }

    @Test
    public void procFull(Path base, Path jarFile) {
        Task.Result javacResult =
            new JavacTask(tb)
            .options("-classpath", jarFile.toString(),
                     "-proc:full",
                     "-XDrawDiagnostics")
            .files("HelloWorldTest.java")
            .run(Expect.SUCCESS)
            .writeAll();

        checkForProcessorMessage(javacResult, true);
        checkForCompilerNote(javacResult, false);
    }

    @Test
    public void procOnly(Path base, Path jarFile) {
        Task.Result javacResult =
            new JavacTask(tb)
            .options("-classpath", jarFile.toString(),
                     "-proc:only",
                     "-XDrawDiagnostics")
            .files("HelloWorldTest.java")
            .run(Expect.SUCCESS)
            .writeAll();

        checkForProcessorMessage(javacResult, true);
        checkForCompilerNote(javacResult, false);
    }

    @Test
    public void lintOptions(Path base, Path jarFile) {
        Task.Result javacResult =
            new JavacTask(tb)
            .options("-classpath", jarFile.toString(),
                     "-Xlint:-options",
                     "-XDrawDiagnostics")
            .files("HelloWorldTest.java")
            .run(Expect.SUCCESS)
            .writeAll();

        checkForProcessorMessage(javacResult, false);
        checkForCompilerNote(javacResult, false);
    }

    @Test
    public void lintNone(Path base, Path jarFile) {
        Task.Result javacResult =
            new JavacTask(tb)
            .options("-classpath", jarFile.toString(),
                     "-Xlint:none",
                     "-XDrawDiagnostics")
            .files("HelloWorldTest.java")
            .run(Expect.SUCCESS)
            .writeAll();

        checkForProcessorMessage(javacResult, false);
        checkForCompilerNote(javacResult, false);
    }

    @Test
    public void procNone(Path base, Path jarFile) {
        Task.Result javacResult =
            new JavacTask(tb)
            .options("-classpath", jarFile.toString(),
                     "-proc:none",
                     "-XDrawDiagnostics")
            .files("HelloWorldTest.java")
            .run(Expect.SUCCESS)
            .writeAll();

        checkForProcessorMessage(javacResult, false);
        checkForCompilerNote(javacResult, false);
    }

    private void checkForProcessorMessage(Task.Result javacResult, boolean expectedPresent) {
        List<String> outputLines = javacResult.getOutputLines(Task.OutputKind.STDOUT);

        if (!expectedPresent && outputLines.isEmpty()) {
            return;
        }

        if (expectedPresent ^ outputLines.get(0).contains("ImplicitProcTestProc run")) {
            throw new RuntimeException("Expected processor message not printed");
        }
    }

    private void checkForCompilerNote(Task.Result javacResult, boolean expectedPresent) {
        List<String> outputLines = javacResult.getOutputLines(Task.OutputKind.DIRECT);

        if (!expectedPresent && outputLines.isEmpty()) {
            return;
        }

        if (expectedPresent ^
            outputLines.get(0).contains("- compiler.note.implicit.annotation.processing")) {
            throw new RuntimeException("Expected note not printed");
        }
    }

    @Test
    public void processorsViaAPI(Path base, Path jarFile) throws Exception {
        ClassLoader cl = new URLClassLoader(new URL[] {jarFile.toUri().toURL()});
        Class<?> processorClass = Class.forName(processorName, true, cl);
        StringWriter compilerOut = new StringWriter();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JavaCompiler provider = ToolProvider.getSystemJavaCompiler();
        PrintStream oldOut = System.out;

        try (StandardJavaFileManager jfm = provider.getStandardFileManager(null, null, null)) {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            Iterable<? extends JavaFileObject> inputFile = jfm.getJavaFileObjects("HelloWorldTest.java");

            {
                List<String> options = List.of("-classpath", jarFile.toString(), "-XDrawDiagnostics");
                CompilationTask task = provider.getTask(compilerOut, null, null, options, null, inputFile);

                task.call();

                verifyMessages(out, compilerOut, false, false);
            }

            {
                List<String> options = List.of("-classpath", jarFile.toString(), "-XDrawDiagnostics");
                CompilationTask task = provider.getTask(compilerOut, null, null, options, null, inputFile);
                Processor processor =
                        (Processor) processorClass.getDeclaredConstructor().newInstance();

                task.setProcessors(List.of(processor));
                task.call();

                verifyMessages(out, compilerOut, false, true);
            }

            {
                List<String> options = List.of("-classpath", jarFile.toString(), "-XDrawDiagnostics");
                com.sun.source.util.JavacTask task =
                        (com.sun.source.util.JavacTask) provider.getTask(compilerOut, null, null, options, null, inputFile);

                task.analyze();

                verifyMessages(out, compilerOut, false, false);
            }

            {
                List<String> options = List.of("-classpath", jarFile.toString(), "-XDrawDiagnostics");
                com.sun.source.util.JavacTask task =
                        (com.sun.source.util.JavacTask) provider.getTask(compilerOut, null, null, options, null, inputFile);

                Processor processor =
                        (Processor) processorClass.getDeclaredConstructor().newInstance();

                task.setProcessors(List.of(processor));
                task.analyze();

                verifyMessages(out, compilerOut, false, true);
            }
        } finally {
            System.setOut(oldOut);
        }
    }

    private void verifyMessages(ByteArrayOutputStream out, StringWriter compilerOut, boolean expectedNotePresent,
                                boolean processorRunExpected) {
        boolean processorRun = out.toString(StandardCharsets.UTF_8).contains("ImplicitProcTestProc run");

        if (processorRun != processorRunExpected) {
            throw new RuntimeException(processorRunExpected ?
                                       "Expected processor message not printed" :
                                       "Unexpected processor message printed");
        }

        out.reset();

        boolean printed = compilerOut.toString().contains("- compiler.note.implicit.annotation.processing");

        if (!expectedNotePresent && printed) {
            throw new RuntimeException("Unexpected note printed");
        }

        if (expectedNotePresent && !printed) {
            throw new RuntimeException("Expected note not printed");
        }

        StringBuffer compilerOutData = compilerOut.getBuffer();

        compilerOutData.delete(0, compilerOutData.length());
    }

}
