/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8171355
 * @summary Test behavior of javax.lang.model.util.Elements.getOrigin.
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          java.base/jdk.internal.classfile
 *          java.base/jdk.internal.classfile.attribute
 *          java.base/jdk.internal.classfile.constantpool
 *          java.base/jdk.internal.classfile.instruction
 *          java.base/jdk.internal.classfile.components
 *          java.base/jdk.internal.classfile.impl
 * @build toolbox.ToolBox toolbox.JavacTask toolbox.TestRunner
 * @build TestOrigin
 * @run main TestOrigin
 */

import java.io.OutputStream;
import java.lang.instrument.ClassFileTransformer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.element.ModuleElement.Directive;
import javax.lang.model.element.ModuleElement.ExportsDirective;
import javax.lang.model.element.ModuleElement.OpensDirective;
import javax.lang.model.element.ModuleElement.RequiresDirective;
import javax.lang.model.util.Elements;

import jdk.internal.classfile.*;
import jdk.internal.classfile.attribute.*;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.TestRunner;
import toolbox.ToolBox;

public class TestOrigin extends TestRunner {

    private final ToolBox tb;

    TestOrigin() {
        super(System.err);
        tb = new ToolBox();
    }

    public static void main(String... args) throws Exception {
        new TestOrigin().runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    @Test
    public void testGeneratedConstr(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                          "package test; public class Test { private void test() { } }",
                          "class Dummy {}");
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);

        List<String> log;
        List<String> expected;

        //from source:
        log = new JavacTask(tb)
            .options("-processor", ListMembersAP.class.getName())
            .outdir(classes)
            .files(tb.findJavaFiles(src))
            .run()
            .writeAll()
            .getOutputLines(Task.OutputKind.STDOUT);

        expected = Arrays.asList(
                "<init>:MANDATED",
                "test:EXPLICIT");

        if (!expected.equals(log))
            throw new AssertionError("expected output not found: " + log);

        //from class:
        log = new JavacTask(tb)
            .options("-classpath", classes.toString(),
                     "-processorpath", System.getProperty("test.classes"),
                     "-processor", ListMembersAP.class.getName())
            .outdir(classes)
            .files(src.resolve("Dummy.java"))
            .run()
            .writeAll()
            .getOutputLines(Task.OutputKind.STDOUT);

        expected = Arrays.asList(
                "<init>:EXPLICIT",
                "test:EXPLICIT");

        if (!expected.equals(log))
            throw new AssertionError("expected output not found: " + log);
    }

    @SupportedAnnotationTypes("*")
    public static final class ListMembersAP extends AbstractProcessor {

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (!roundEnv.processingOver())
                return false;

            Elements elements = processingEnv.getElementUtils();
            TypeElement test = elements.getTypeElement("test.Test");
            List<? extends Element> members = new ArrayList<>(test.getEnclosedElements());

            members.sort((e1, e2) -> e1.getSimpleName().toString().compareTo(e2.getSimpleName().toString()));

            for (Element el : members) {
                System.out.println(el.getSimpleName() + ":" + elements.getOrigin(el));
            }

            return false;
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

    }

    @Test
    public void testRepeatableAnnotations(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                          "package test; @A @A public class Test { }",
                          "package test;" +
                          "import java.lang.annotation.*;" +
                          "@Repeatable(Container.class)" +
                          "@Retention(RetentionPolicy.CLASS)" +
                          "@interface A {}",
                          "package test; @interface Container { A[] value(); }",
                          "class Dummy {}");
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);

        List<String> log;
        List<String> expected;

        //from source:
        log = new JavacTask(tb)
            .options("-processor", ListAnnotationsAP.class.getName())
            .outdir(classes)
            .files(tb.findJavaFiles(src))
            .run()
            .writeAll()
            .getOutputLines(Task.OutputKind.STDOUT);

        expected = List.of("test.Container:MANDATED");

        if (!expected.equals(log))
            throw new AssertionError("expected output not found: " + log);

        //from class:
        log = new JavacTask(tb)
            .options("-classpath", classes.toString(),
                     "-processorpath", System.getProperty("test.classes"),
                     "-processor", ListAnnotationsAP.class.getName())
            .outdir(classes)
            .files(src.resolve("Dummy.java"))
            .run()
            .writeAll()
            .getOutputLines(Task.OutputKind.STDOUT);

        expected = List.of("test.Container:EXPLICIT");

        if (!expected.equals(log))
            throw new AssertionError("expected output not found: " + log);
    }

    @SupportedAnnotationTypes("*")
    public static final class ListAnnotationsAP extends AbstractProcessor {

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (!roundEnv.processingOver())
                return false;

            Elements elements = processingEnv.getElementUtils();
            TypeElement test = elements.getTypeElement("test.Test");

            for (AnnotationMirror am : test.getAnnotationMirrors()) {
                System.out.println(am.getAnnotationType() + ":" + elements.getOrigin(test, am));
            }

            return false;
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

    }

    @Test
    public void testModuleDirectives(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                          "module m {}",
                          "package p1; class Test {}",
                          "package p2; class Test {}",
                          "package p3; class Test {}",
                          "package test; class Dummy {}");
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);

        List<String> log;
        List<String> expected;

        //from source:
        log = new JavacTask(tb)
            .options("-processor", ListModuleAP.class.getName())
            .outdir(classes)
            .files(tb.findJavaFiles(src))
            .run()
            .writeAll()
            .getOutputLines(Task.OutputKind.STDOUT);

        expected = List.of("REQUIRES:java.base:MANDATED");

        if (!expected.equals(log))
            throw new AssertionError("expected output not found: " + log);

        tb.writeJavaFiles(src,
                          "module m {" +
                          "    requires java.base;" +
                          "    requires java.compiler;" +
                          "    requires jdk.compiler;" +
                          "    exports p1;" +
                          "    exports p2;" +
                          "    exports p3;" +
                          "    opens p1;" +
                          "    opens p2;" +
                          "    opens p3;" +
                          "}");

        new JavacTask(tb)
            .outdir(classes)
            .files(src.resolve("module-info.java"))
            .run()
            .writeAll();

        Path moduleInfo = classes.resolve("module-info.class");
        ClassModel cf = Classfile.of().parse(moduleInfo);
        ModuleAttribute module = cf.findAttribute(Attributes.MODULE).orElseThrow();

        List<ModuleRequireInfo> newRequires = new ArrayList<>(3);
        newRequires.add(ModuleRequireInfo.of(module.requires().get(0).requires(), Classfile.ACC_MANDATED, module.requires().get(0).requiresVersion().orElse(null)));
        newRequires.add(ModuleRequireInfo.of(module.requires().get(1).requires(), Classfile.ACC_SYNTHETIC, module.requires().get(1).requiresVersion().orElse(null)));
        newRequires.add(module.requires().get(2));

        List<ModuleExportInfo> newExports = new ArrayList<>(3);
        newExports.add(ModuleExportInfo.of(module.exports().get(0).exportedPackage(), Classfile.ACC_MANDATED, module.exports().get(0).exportsTo()));
        newExports.add(ModuleExportInfo.of(module.exports().get(1).exportedPackage(), Classfile.ACC_SYNTHETIC, module.exports().get(1).exportsTo()));
        newExports.add(module.exports().get(2));

        List<ModuleOpenInfo> newOpens = new ArrayList<>(3);
        newOpens.add(ModuleOpenInfo.of(module.opens().get(0).openedPackage(), Classfile.ACC_MANDATED, module.opens().get(0).opensTo()));
        newOpens.add(ModuleOpenInfo.of(module.opens().get(1).openedPackage(), Classfile.ACC_SYNTHETIC, module.opens().get(1).opensTo()));
        newOpens.add(module.opens().get(2));


        ModuleAttribute newModule = ModuleAttribute.of(module.moduleName(),
                                                          module.moduleFlagsMask(),
                                                          module.moduleVersion().orElse(null),
                                                          newRequires,
                                                          newExports,
                                                          newOpens,
                                                          module.uses(),
                                                          module.provides());
        byte[] newClassFileBytes = Classfile.of().transform(cf, ClassTransform.dropping(ce -> ce instanceof ModuleAttribute)
                                                 .andThen(ClassTransform.endHandler(classBuilder -> classBuilder.with(newModule))));
        try (OutputStream out = Files.newOutputStream(moduleInfo)) {
            out.write(newClassFileBytes);
        }

        //from class:
        log = new JavacTask(tb)
            .options("-processor", ListModuleAP.class.getName())
            .outdir(classes)
            .files(src.resolve("test").resolve("Dummy.java"))
            .run()
            .writeAll()
            .getOutputLines(Task.OutputKind.STDOUT);

        expected = Arrays.asList(
                "REQUIRES:java.base:MANDATED",
                "REQUIRES:java.compiler:SYNTHETIC",
                "REQUIRES:jdk.compiler:EXPLICIT",
                "EXPORTS:p1:MANDATED",
                "EXPORTS:p2:SYNTHETIC",
                "EXPORTS:p3:EXPLICIT",
                "OPENS:p1:MANDATED",
                "OPENS:p2:SYNTHETIC",
                "OPENS:p3:EXPLICIT");

        if (!expected.equals(log))
            throw new AssertionError("expected output not found: " + log);
    }

    @SupportedAnnotationTypes("*")
    public static final class ListModuleAP extends AbstractProcessor {

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (!roundEnv.processingOver())
                return false;

            Elements elements = processingEnv.getElementUtils();
            ModuleElement m = elements.getModuleElement("m");

            for (Directive d : m.getDirectives()) {
                switch (d.getKind()) {
                    case REQUIRES -> {
                        RequiresDirective rd = (RequiresDirective) d;
                        System.out.println(rd.getKind() + ":" +
                                rd.getDependency().getQualifiedName() + ":" +
                                elements.getOrigin(m, rd));
                    }
                    case EXPORTS -> {
                        ExportsDirective ed = (ExportsDirective) d;
                        System.out.println(ed.getKind() + ":" +
                                ed.getPackage() + ":" +
                                elements.getOrigin(m, ed));
                    }
                    case OPENS -> {
                        OpensDirective od = (OpensDirective) d;
                        System.out.println(od.getKind() + ":" +
                                od.getPackage() + ":" +
                                elements.getOrigin(m, od));
                    }
                }
            }

            return false;
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

    }

}
