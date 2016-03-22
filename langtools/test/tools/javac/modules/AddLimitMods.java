/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @summary Test -addmods and -limitmods; also test the "enabled" modules.
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.code
 *      jdk.compiler/com.sun.tools.javac.main
 *      jdk.compiler/com.sun.tools.javac.model
 *      jdk.compiler/com.sun.tools.javac.processing
 *      jdk.compiler/com.sun.tools.javac.util
 *      jdk.jdeps/com.sun.tools.javap
 * @build ToolBox ModuleTestBase
 * @run main AddLimitMods
 */

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.TypeElement;

import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.util.Context;

public class AddLimitMods extends ModuleTestBase {

    public static void main(String... args) throws Exception {
        AddLimitMods t = new AddLimitMods();
        t.runTests();
    }

    @Test
    void testManual(Path base) throws Exception {
        Path moduleSrc = base.resolve("module-src");
        Path m1 = moduleSrc.resolve("m1");

        tb.writeJavaFiles(m1,
                          "module m1 { requires m2; requires m3; }");

        Path m2 = moduleSrc.resolve("m2");

        tb.writeJavaFiles(m2,
                          "module m2 { requires m3; exports m2; }",
                          "package m2; public class M2 {}");

        Path m3 = moduleSrc.resolve("m3");

        tb.writeJavaFiles(m3,
                          "module m3 { exports m3; }",
                          "package m3; public class M3 {}");

        Path modulePath = base.resolve("module-path");

        Files.createDirectories(modulePath);

        tb.new JavacTask()
                .options("-modulesourcepath", moduleSrc.toString())
                .outdir(modulePath)
                .files(findJavaFiles(m3))
                .run()
                .writeAll();

        tb.new JavacTask()
                .options("-modulesourcepath", moduleSrc.toString())
                .outdir(modulePath)
                .files(findJavaFiles(m2))
                .run()
                .writeAll();

        //real test
        tb.new JavacTask()
                .options("-modulepath", modulePath.toString(),
                         "-XDshouldStopPolicyIfNoError=FLOW",
                         "-limitmods", "java.base")
                .outdir(modulePath)
                .files(findJavaFiles(m1))
                .run(ToolBox.Expect.FAIL)
                .writeAll();

        tb.new JavacTask()
                .options("-modulepath", modulePath.toString(),
                         "-XDshouldStopPolicyIfNoError=FLOW",
                         "-limitmods", "java.base",
                         "-addmods", "m2")
                .outdir(modulePath)
                .files(findJavaFiles(m1))
                .run(ToolBox.Expect.FAIL)
                .writeAll();

        tb.new JavacTask()
                .options("-modulepath", modulePath.toString(),
                         "-XDshouldStopPolicyIfNoError=FLOW",
                         "-limitmods", "java.base",
                         "-addmods", "m2,m3")
                .outdir(modulePath)
                .files(findJavaFiles(m1))
                .run()
                .writeAll();

        tb.new JavacTask()
                .options("-modulepath", modulePath.toString(),
                         "-XDshouldStopPolicyIfNoError=FLOW",
                         "-limitmods", "m2")
                .outdir(modulePath)
                .files(findJavaFiles(m1))
                .run()
                .writeAll();

        tb.new JavacTask()
                .options("-modulepath", modulePath.toString(),
                         "-XDshouldStopPolicyIfNoError=FLOW",
                         "-limitmods", "m3")
                .outdir(modulePath)
                .files(findJavaFiles(m1))
                .run(ToolBox.Expect.FAIL)
                .writeAll();

        tb.new JavacTask()
                .options("-modulepath", modulePath.toString(),
                         "-XDshouldStopPolicyIfNoError=FLOW",
                         "-limitmods", "m3",
                         "-addmods", "m2")
                .outdir(modulePath)
                .files(findJavaFiles(m1))
                .run()
                .writeAll();
    }

    @Test
    void testAllModulePath(Path base) throws Exception {
        if (Files.isDirectory(base))
            tb.cleanDirectory(base);

        Path moduleSrc = base.resolve("module-src");
        Path m1 = moduleSrc.resolve("m1");

        tb.writeJavaFiles(m1,
                          "module m1 { exports api; }",
                          "package api; public class Api { }");

        Path modulePath = base.resolve("module-path");

        Files.createDirectories(modulePath);

        tb.new JavacTask()
                .options("-modulesourcepath", moduleSrc.toString())
                .outdir(modulePath)
                .files(findJavaFiles(moduleSrc))
                .run()
                .writeAll();

        Path cpSrc = base.resolve("cp-src");
        tb.writeJavaFiles(cpSrc, "package test; public class Test { api.Api api; }");

        Path cpOut = base.resolve("cp-out");

        Files.createDirectories(cpOut);

        tb.new JavacTask()
                .options("-modulepath", modulePath.toString())
                .outdir(cpOut)
                .files(findJavaFiles(cpSrc))
                .run(ToolBox.Expect.FAIL)
                .writeAll();

        tb.new JavacTask()
                .options("-modulepath", modulePath.toString(),
                         "-addmods", "ALL-MODULE-PATH")
                .outdir(cpOut)
                .files(findJavaFiles(cpSrc))
                .run()
                .writeAll();

        List<String> actual;
        List<String> expected = Arrays.asList(
                "- compiler.err.addmods.all.module.path.invalid",
                "1 error");

        actual = tb.new JavacTask()
                   .options("-modulesourcepath", moduleSrc.toString(),
                            "-XDrawDiagnostics",
                            "-addmods", "ALL-MODULE-PATH")
                   .outdir(modulePath)
                   .files(findJavaFiles(moduleSrc))
                   .run(ToolBox.Expect.FAIL)
                   .writeAll()
                   .getOutputLines(ToolBox.OutputKind.DIRECT);

        if (!Objects.equals(actual, expected)) {
            throw new IllegalStateException("incorrect errors; actual=" + actual + "; expected=" + expected);
        }

        actual = tb.new JavacTask()
                   .options("-Xmodule:java.base",
                            "-XDrawDiagnostics",
                            "-addmods", "ALL-MODULE-PATH")
                   .outdir(cpOut)
                   .files(findJavaFiles(cpSrc))
                   .run(ToolBox.Expect.FAIL)
                   .writeAll()
                   .getOutputLines(ToolBox.OutputKind.DIRECT);

        if (!Objects.equals(actual, expected)) {
            throw new IllegalStateException("incorrect errors; actual=" + actual + "; expected=" + expected);
        }

        actual = tb.new JavacTask(ToolBox.Mode.CMDLINE)
                   .options("-source", "8", "-target", "8",
                            "-XDrawDiagnostics",
                            "-addmods", "ALL-MODULE-PATH")
                   .outdir(cpOut)
                   .files(findJavaFiles(cpSrc))
                   .run(ToolBox.Expect.FAIL)
                   .writeAll()
                   .getOutputLines(ToolBox.OutputKind.DIRECT);

        if (!actual.contains("javac: option -addmods not allowed with target 1.8")) {
            throw new IllegalStateException("incorrect errors; actual=" + actual);
        }

        tb.writeJavaFiles(cpSrc, "module m1 {}");

        actual = tb.new JavacTask()
                   .options("-XDrawDiagnostics",
                            "-addmods", "ALL-MODULE-PATH")
                   .outdir(cpOut)
                   .files(findJavaFiles(cpSrc))
                   .run(ToolBox.Expect.FAIL)
                   .writeAll()
                   .getOutputLines(ToolBox.OutputKind.DIRECT);

        if (!Objects.equals(actual, expected)) {
            throw new IllegalStateException("incorrect errors; actual=" + actual + "; expected=" + expected);
        }
    }

    @Test
    void testRuntime2Compile(Path base) throws Exception {
        Path classpathSrc = base.resolve("classpath-src");
        Path classpathOut = base.resolve("classpath-out");

        tb.writeJavaFiles(classpathSrc,
                          generateCheckAccessibleClass("cp.CP"));

        Files.createDirectories(classpathOut);

        tb.new JavacTask()
                .outdir(classpathOut)
                .files(findJavaFiles(classpathSrc))
                .run()
                .writeAll()
                .getOutput(ToolBox.OutputKind.DIRECT);

        Path automaticSrc = base.resolve("automatic-src");
        Path automaticOut = base.resolve("automatic-out");

        tb.writeJavaFiles(automaticSrc,
                          generateCheckAccessibleClass("automatic.Automatic"));

        Files.createDirectories(automaticOut);

        tb.new JavacTask()
                .outdir(automaticOut)
                .files(findJavaFiles(automaticSrc))
                .run()
                .writeAll()
                .getOutput(ToolBox.OutputKind.DIRECT);

        Path modulePath = base.resolve("module-path");

        Files.createDirectories(modulePath);

        Path automaticJar = modulePath.resolve("automatic.jar");

        tb.new JarTask(automaticJar)
          .baseDir(automaticOut)
          .files("automatic/Automatic.class")
          .run();

        Path moduleSrc = base.resolve("module-src");
        Path m1 = moduleSrc.resolve("m1");

        tb.writeJavaFiles(m1,
                          "module m1 { exports api; }",
                          "package api; public class Api { public void test() { } }");

        tb.new JavacTask()
                .options("-modulesourcepath", moduleSrc.toString())
                .outdir(modulePath)
                .files(findJavaFiles(moduleSrc))
                .run()
                .writeAll()
                .getOutput(ToolBox.OutputKind.DIRECT);

        int index = 0;

        for (String moduleInfo : MODULE_INFO_VARIANTS) {
            for (String[] options : OPTIONS_VARIANTS) {
                index++;

                System.err.println("running check: " + moduleInfo + "; " + Arrays.asList(options));

                Path m2Runtime = base.resolve(index + "-runtime").resolve("m2");
                Path out = base.resolve(index + "-runtime").resolve("out").resolve("m2");

                Files.createDirectories(out);

                StringBuilder testClassNamed = new StringBuilder();

                testClassNamed.append("package test;\n" +
                                      "public class Test {\n" +
                                      "    public static void main(String... args) throws Exception {\n");

                for (Entry<String, String> e : MODULES_TO_CHECK_TO_SAMPLE_CLASS.entrySet()) {
                    testClassNamed.append("        System.err.println(\"visible:" + e.getKey() + ":\" + java.lang.reflect.Layer.boot().findModule(\"" + e.getKey() + "\").isPresent());\n");
                }

                testClassNamed.append("        Class<?> cp = Class.forName(Test.class.getClassLoader().getUnnamedModule(), \"cp.CP\");\n");
                testClassNamed.append("        cp.getDeclaredMethod(\"runMe\").invoke(null);\n");

                testClassNamed.append("        Class<?> automatic = Class.forName(java.lang.reflect.Layer.boot().findModule(\"automatic\").get(), \"automatic.Automatic\");\n");
                testClassNamed.append("        automatic.getDeclaredMethod(\"runMe\").invoke(null);\n");

                testClassNamed.append("    }\n" +
                                      "}");

                tb.writeJavaFiles(m2Runtime, moduleInfo, testClassNamed.toString());

                tb.new JavacTask()
                   .options("-modulepath", modulePath.toString())
                   .outdir(out)
                   .files(findJavaFiles(m2Runtime))
                   .run()
                   .writeAll();

                boolean success;
                String output;

                try {
                    output = tb.new JavaTask()
                       .vmOptions(augmentOptions(options,
                                                 Collections.emptyList(),
                                                 "-modulepath", modulePath.toString() + File.pathSeparator + out.getParent().toString(),
                                                 "-classpath", classpathOut.toString(),
                                                 "-XaddReads:m2=ALL-UNNAMED,m2=automatic",
                                                 "-m", "m2/test.Test"))
                       .run()
                       .writeAll()
                       .getOutput(ToolBox.OutputKind.STDERR);

                    success = true;
                } catch (ToolBox.TaskError err) {
                    success = false;
                    output = "";
                }

                Path m2 = base.resolve(String.valueOf(index)).resolve("m2");

                tb.writeJavaFiles(m2,
                                  moduleInfo,
                                  "package test;\n" +
                                  "public class Test {}\n");

                List<String> auxOptions = success ? Arrays.asList(
                    "-processorpath", System.getProperty("test.class.path"),
                    "-processor", CheckVisibleModule.class.getName(),
                    "-Aoutput=" + output,
                    "-XDaccessInternalAPI=true"
                ) : Collections.emptyList();
                tb.new JavacTask()
                   .options(augmentOptions(options,
                                           auxOptions,
                                           "-modulepath", modulePath.toString(),
                                           "-classpath", classpathOut.toString(),
                                           "-XDshouldStopPolicyIfNoError=FLOW"))
                   .outdir(modulePath)
                   .files(findJavaFiles(m2))
                   .run(success ? ToolBox.Expect.SUCCESS : ToolBox.Expect.FAIL)
                   .writeAll();
            }
        }
    }

    private String generateCheckAccessibleClass(String fqn) {
        String packageName = fqn.substring(0, fqn.lastIndexOf('.'));
        String simpleName = fqn.substring(fqn.lastIndexOf('.') + 1);
        StringBuilder checkClassesAccessible = new StringBuilder();
        checkClassesAccessible.append("package " + packageName + ";" +
                                      "public class " + simpleName + " {" +
                                      "    public static void runMe() throws Exception {");
        for (Entry<String, String> e : MODULES_TO_CHECK_TO_SAMPLE_CLASS.entrySet()) {
            checkClassesAccessible.append("try {");
            checkClassesAccessible.append("Class.forName(\"" + e.getValue() + "\").newInstance();");
            checkClassesAccessible.append("System.err.println(\"" + fqn + ":" + e.getKey() + ":true\");");
            checkClassesAccessible.append("} catch (Exception ex) {");
            checkClassesAccessible.append("System.err.println(\"" + fqn + ":" + e.getKey() + ":false\");");
            checkClassesAccessible.append("}");
        }

        checkClassesAccessible.append("    }\n" +
                                      "}");

        return checkClassesAccessible.toString();
    }

    private static final Map<String, String> MODULES_TO_CHECK_TO_SAMPLE_CLASS = new LinkedHashMap<>();

    static {
        MODULES_TO_CHECK_TO_SAMPLE_CLASS.put("m1", "api.Api");
        MODULES_TO_CHECK_TO_SAMPLE_CLASS.put("m2", "test.Test");
        MODULES_TO_CHECK_TO_SAMPLE_CLASS.put("java.base", "java.lang.Object");
        MODULES_TO_CHECK_TO_SAMPLE_CLASS.put("java.compiler", "javax.tools.ToolProvider");
        MODULES_TO_CHECK_TO_SAMPLE_CLASS.put("jdk.compiler", "com.sun.tools.javac.Main");
    };

    @SupportedAnnotationTypes("*")
    @SupportedOptions("output")
    public static final class CheckVisibleModule extends AbstractProcessor {

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            String expected = processingEnv.getOptions().get("output");
            Set<String> expectedElements = new HashSet<>(Arrays.asList(expected.split(System.getProperty("line.separator"))));
            Context context = ((JavacProcessingEnvironment) processingEnv).getContext();
            Symtab syms = Symtab.instance(context);

            for (Entry<String, String> e : MODULES_TO_CHECK_TO_SAMPLE_CLASS.entrySet()) {
                String module = e.getKey();
                ModuleElement mod = processingEnv.getElementUtils().getModuleElement(module);
                String visible = "visible:" + module + ":" + (mod != null);

                if (!expectedElements.contains(visible)) {
                    throw new AssertionError("actual: " + visible + "; expected: " + expected);
                }

                JavacElements javacElements = JavacElements.instance(context);
                ClassSymbol unnamedClass = javacElements.getTypeElement(syms.unnamedModule, e.getValue());
                String unnamed = "cp.CP:" + module + ":" + (unnamedClass != null);

                if (!expectedElements.contains(unnamed)) {
                    throw new AssertionError("actual: " + unnamed + "; expected: " + expected);
                }

                ModuleElement automaticMod = processingEnv.getElementUtils().getModuleElement("automatic");
                ClassSymbol automaticClass = javacElements.getTypeElement(automaticMod, e.getValue());
                String automatic = "automatic.Automatic:" + module + ":" + (automaticClass != null);

                if (!expectedElements.contains(automatic)) {
                    throw new AssertionError("actual: " + automatic + "; expected: " + expected);
                }
            }

            return false;
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latest();
        }

    }

    public String[] augmentOptions(String[] options, List<String> auxOptions, String... baseOptions) {
        List<String> all = new ArrayList<>();

        all.addAll(Arrays.asList(options));
        all.addAll(Arrays.asList(baseOptions));
        all.addAll(auxOptions);

        return all.toArray(new String[0]);
    }

    private static final String[] MODULE_INFO_VARIANTS = {
        "module m2 { exports test; }",
        "module m2 { requires m1; exports test; }",
        "module m2 { requires jdk.compiler; exports test; }",
    };

    private static final String[][] OPTIONS_VARIANTS = {
        {"-addmods", "automatic"},
        {"-addmods", "m1,automatic"},
        {"-addmods", "jdk.compiler,automatic"},
        {"-addmods", "m1,jdk.compiler,automatic"},
        {"-addmods", "ALL-SYSTEM,automatic"},
        {"-limitmods", "java.base", "-addmods", "automatic"},
        {"-limitmods", "java.base", "-addmods", "ALL-SYSTEM,automatic"},
        {"-limitmods", "m2", "-addmods", "automatic"},
        {"-limitmods", "jdk.compiler", "-addmods", "automatic"},
    };
}
