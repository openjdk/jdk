/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @summary tests for module resolution
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 *      jdk.jdeps/com.sun.tools.javap
 * @build ToolBox ModuleTestBase
 * @run main QueryBeforeEnter
 */

import java.io.File;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.Main;

public class QueryBeforeEnter extends ModuleTestBase {
    public static void main(String... args) throws Exception {
        QueryBeforeEnter t = new QueryBeforeEnter();
        t.runTests();
    }

    @Test
    void testEmpty(Path base) throws Exception {
        JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
        JavacTask task = (JavacTask) javaCompiler.getTask(null, null, null, null, null, null);
        TypeElement jlString = task.getElements().getTypeElement("java.lang.String");

        assertNotNull(jlString);
    }

    @Test
    void testUnnamed(Path base) throws Exception {
        Path moduleSrc = base.resolve("module-src");
        Path m1 = moduleSrc.resolve("m1");

        tb.writeJavaFiles(m1,
                          "module m1 { exports m1; }",
                          "package m1; public class M1 {}");

        Path m2 = moduleSrc.resolve("m2");

        tb.writeJavaFiles(m2,
                          "module m2 { exports m2; }",
                          "package m2; public class M2 {}");

        Path modulePath = base.resolve("module-path");

        Files.createDirectories(modulePath);

        tb.new JavacTask()
                .options("-modulesourcepath", moduleSrc.toString())
                .outdir(modulePath)
                .files(findJavaFiles(moduleSrc))
                .run()
                .writeAll();

        Path cpSrc = base.resolve("cp-src");

        tb.writeJavaFiles(cpSrc,
                          "package cp; public class CP {}");

        Path cp = base.resolve("cp");

        Files.createDirectories(cp);

        tb.new JavacTask()
                .outdir(cp)
                .files(findJavaFiles(cpSrc))
                .run()
                .writeAll();

        Path src = base.resolve("src");

        tb.writeJavaFiles(src,
                          "package test; public class Test1 {}",
                          "package test; public class Test2 {}");

        Path out = base.resolve("out");

        Files.createDirectories(out);

        JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fm = javaCompiler.getStandardFileManager(null, null, null)) {
            JavacTask task = (JavacTask) javaCompiler.getTask(null,
                                                              null,
                                                              d -> { throw new IllegalStateException(d.toString()); },
                                                              Arrays.asList("-modulepath", modulePath.toString(),
                                                                            "-classpath", cp.toString(),
                                                                            "-sourcepath", src.toString()),
                                                              null,
                                                              fm.getJavaFileObjects(src.resolve("test").resolve("Test2.java")));
            assertNotNull(task.getElements().getTypeElement("java.lang.String"));
            assertNotNull(task.getElements().getTypeElement("javax.tools.ToolProvider"));
            assertNull(task.getElements().getTypeElement("m1.M1"));
            assertNull(task.getElements().getTypeElement("m2.M2"));
            assertNotNull(task.getElements().getTypeElement("cp.CP"));
            assertNotNull(task.getElements().getTypeElement("test.Test1"));
            assertNotNull(task.getElements().getTypeElement("test.Test2"));
            assertNotNull(task.getElements().getModuleElement("java.base"));
            assertNotNull(task.getElements().getModuleElement("java.compiler"));
            assertNull(task.getElements().getModuleElement("m1"));
            assertNull(task.getElements().getModuleElement("m2"));
        }
    }

    @Test
    void testSingleNamed(Path base) throws Exception {
        Path moduleSrc = base.resolve("module-src");
        Path m1 = moduleSrc.resolve("m1");

        tb.writeJavaFiles(m1,
                          "module m1 { exports m1; }",
                          "package m1; public class M1 {}");

        Path m2 = moduleSrc.resolve("m2");

        tb.writeJavaFiles(m2,
                          "module m2 { exports m2; }",
                          "package m2; public class M2 {}");

        Path modulePath = base.resolve("module-path");

        Files.createDirectories(modulePath);

        tb.new JavacTask()
                .options("-modulesourcepath", moduleSrc.toString())
                .outdir(modulePath)
                .files(findJavaFiles(moduleSrc))
                .run()
                .writeAll();

        Path cpSrc = base.resolve("cp-src");

        tb.writeJavaFiles(cpSrc,
                          "package cp; public class CP {}");

        Path cp = base.resolve("cp");

        Files.createDirectories(cp);

        tb.new JavacTask()
                .outdir(cp)
                .files(findJavaFiles(cpSrc))
                .run()
                .writeAll();

        Path src = base.resolve("src");

        tb.writeJavaFiles(src,
                          "module test { requires java.base; requires m1; } ",
                          "package test; public class Test {}");

        Path out = base.resolve("out");

        Files.createDirectories(out);

        JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fm = javaCompiler.getStandardFileManager(null, null, null)) {
            JavacTask task = (JavacTask) javaCompiler.getTask(null,
                                                              null,
                                                              d -> { throw new IllegalStateException(d.toString()); },
                                                              Arrays.asList("-modulepath", modulePath.toString(),
                                                                            "-classpath", cp.toString(),
                                                                            "-sourcepath", src.toString()),
                                                              null,
                                                              fm.getJavaFileObjects(findJavaFiles(src)));
            assertNotNull(task.getElements().getTypeElement("java.lang.String"));
            assertNull(task.getElements().getTypeElement("javax.tools.ToolProvider"));
            assertNotNull(task.getElements().getTypeElement("m1.M1"));
            assertNull(task.getElements().getTypeElement("m2.M2"));
            assertNotNull(task.getElements().getTypeElement("test.Test"));
            assertNotNull(task.getElements().getModuleElement("java.base"));
            assertNull(task.getElements().getModuleElement("java.compiler"));
            assertNotNull(task.getElements().getModuleElement("m1"));
            assertNull(task.getElements().getModuleElement("m2"));
            assertNotNull(task.getElements().getModuleElement("test"));
        }
    }

    @Test
    void testMultiModule(Path base) throws Exception {
        Path modulePathSrc = base.resolve("module-path-src");
        Path m1 = modulePathSrc.resolve("m1");

        tb.writeJavaFiles(m1,
                          "module m1 { exports m1; }",
                          "package m1; public class M1 {}");

        Path m2 = modulePathSrc.resolve("m2");

        tb.writeJavaFiles(m2,
                          "module m2 { exports m2; }",
                          "package m2; public class M2 {}");

        Path modulePath = base.resolve("module-path");

        Files.createDirectories(modulePath);

        tb.new JavacTask()
                .options("-modulesourcepath", modulePathSrc.toString())
                .outdir(modulePath)
                .files(findJavaFiles(modulePathSrc))
                .run()
                .writeAll();

        Path cpSrc = base.resolve("cp-src");

        tb.writeJavaFiles(cpSrc,
                          "package cp; public class CP {}");

        Path cp = base.resolve("cp");

        Files.createDirectories(cp);

        tb.new JavacTask()
                .outdir(cp)
                .files(findJavaFiles(cpSrc))
                .run()
                .writeAll();

        Path moduleSrc = base.resolve("module-src");
        Path m3 = moduleSrc.resolve("m3");

        tb.writeJavaFiles(m3,
                          "module m3 { requires m1; exports m3; }",
                          "package m3; public class M3 {  }");

        Path m4 = moduleSrc.resolve("m4");

        tb.writeJavaFiles(m4,
                          "module m4 { exports m4; }",
                          "package m4; public class M4 {}");

        Path out = base.resolve("out");

        Files.createDirectories(out);

        JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fm = javaCompiler.getStandardFileManager(null, null, null)) {
            JavacTask task = (JavacTask) javaCompiler.getTask(null,
                                                              null,
                                                              d -> { throw new IllegalStateException(d.toString()); },
                                                              Arrays.asList("-modulepath", modulePath.toString(),
                                                                            "-classpath", cp.toString(),
                                                                            "-modulesourcepath", moduleSrc.toString(),
                                                                            "-d", out.toString()),
                                                              null,
                                                              fm.getJavaFileObjects(findJavaFiles(moduleSrc)));
            assertNotNull(task.getElements().getTypeElement("java.lang.String"));
            assertNull(task.getElements().getTypeElement("javax.tools.ToolProvider"));
            assertNotNull(task.getElements().getTypeElement("m1.M1"));
            assertNull(task.getElements().getTypeElement("m2.M2"));
            assertNotNull(task.getElements().getTypeElement("m3.M3"));
            assertNotNull(task.getElements().getTypeElement("m4.M4"));
            assertNotNull(task.getElements().getModuleElement("java.base"));
            assertNull(task.getElements().getModuleElement("java.compiler"));
            assertNotNull(task.getElements().getModuleElement("m1"));
            assertNull(task.getElements().getModuleElement("m2"));
            assertNotNull(task.getElements().getModuleElement("m3"));
            assertNotNull(task.getElements().getModuleElement("m4"));
        }
    }

    @Test
    void testTooSoon(Path base) throws Exception {
        Path src = base.resolve("src");

        tb.writeJavaFiles(src,
                          "package test; public class Test {}");

        Path out = base.resolve("out");

        Files.createDirectories(out);

        Path reg = base.resolve("reg");
        Path regFile = reg.resolve("META-INF").resolve("services").resolve(Plugin.class.getName());

        Files.createDirectories(regFile.getParent());

        try (OutputStream regOut = Files.newOutputStream(regFile)) {
            regOut.write(PluginImpl.class.getName().getBytes());
        }

        String processorPath = System.getProperty("test.class.path") + File.pathSeparator + reg.toString();

        JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
        Path testSource = src.resolve("test").resolve("Test.java");
        try (StandardJavaFileManager fm = javaCompiler.getStandardFileManager(null, null, null)) {
            JavacTask task = (JavacTask) javaCompiler.getTask(null,
                                                              null,
                                                              d -> { throw new IllegalStateException(d.toString()); },
                                                              Arrays.asList("-processorpath", processorPath,
                                                                            "-processor", AP.class.getName(),
                                                                            "-Xplugin:test"),
                                                              null,
                                                              fm.getJavaFileObjects(testSource));
            task.call();
        }

        Main.compile(new String[] {"-processorpath", processorPath,
                                   "-Xplugin:test",
                                   testSource.toString()});
    }

    public static class PluginImpl implements Plugin {

        @Override
        public String getName() {
            return "test";
        }

        @Override
        public void init(JavacTask task, String... args) {
            task.addTaskListener(new TaskListener() {
                boolean wasEntered;
                @Override
                public void started(TaskEvent e) {
                    switch (e.getKind()) {
                        case COMPILATION: case PARSE:
                            shouldFail(e.getKind());
                            break;
                        case ANNOTATION_PROCESSING: case ENTER:
                            if (wasEntered) {
                                shouldPass(e.getKind());
                            } else {
                                shouldFail(e.getKind());
                            }
                            break;
                        default:
                            shouldPass(e.getKind());
                            break;
                    }
                }
                @Override
                public void finished(TaskEvent e) {
                    switch (e.getKind()) {
                        case PARSE:
                            shouldFail(e.getKind());
                            break;
                        case ENTER:
                            wasEntered = true;
                            //fall-through:
                        default:
                            shouldPass(e.getKind());
                            break;
                    }
                }
                private void shouldFail(TaskEvent.Kind kind) {
                    try {
                        task.getElements().getTypeElement("java.lang.String");
                        throw new AssertionError("Expected exception not observed; kind=" + kind.name());
                    } catch (IllegalStateException ex) {
                        //correct
                    }
                }
                private void shouldPass(TaskEvent.Kind kind) {
                    assertNotNull(task.getElements().getTypeElement("java.lang.String"));
                }
            });

        }

    }

    @SupportedAnnotationTypes("*")
    public static final class AP extends AbstractProcessor {

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            return false;
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latest();
        }

    }

    private static void assertNotNull(Object actual) {
        if (actual == null) {
            throw new AssertionError("unexpected null!");
        }
    }

    private static void assertNull(Object actual) {
        if (actual != null) {
            throw new AssertionError("unexpected non null!");
        }
    }

}
