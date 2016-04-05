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
 * @summary Verify that annotation processing works.
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask ModuleTestBase
 * @run main AnnotationProcessing
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.ModuleElement.ProvidesDirective;
import javax.lang.model.element.ModuleElement.UsesDirective;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.ElementScanner9;

import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class AnnotationProcessing extends ModuleTestBase {

    public static void main(String... args) throws Exception {
        new AnnotationProcessing().runTests();
    }

    @Test
    void testAPSingleModule(Path base) throws Exception {
        Path moduleSrc = base.resolve("module-src");
        Path m1 = moduleSrc.resolve("m1");

        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        tb.writeJavaFiles(m1,
                          "module m1 { }",
                          "package impl; public class Impl { }");

        String log = new JavacTask(tb)
                .options("-modulesourcepath", moduleSrc.toString(),
                         "-processor", AP.class.getName(),
                         "-AexpectedEnclosedElements=m1=>impl")
                .outdir(classes)
                .files(findJavaFiles(moduleSrc))
                .run()
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!log.isEmpty())
            throw new AssertionError("Unexpected output: " + log);
    }

    @Test
    void testAPMultiModule(Path base) throws Exception {
        Path moduleSrc = base.resolve("module-src");
        Path m1 = moduleSrc.resolve("m1");
        Path m2 = moduleSrc.resolve("m2");

        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        tb.writeJavaFiles(m1,
                          "module m1 { }",
                          "package impl1; public class Impl1 { }");

        tb.writeJavaFiles(m2,
                          "module m2 { }",
                          "package impl2; public class Impl2 { }");

        String log = new JavacTask(tb)
                .options("-modulesourcepath", moduleSrc.toString(),
                         "-processor", AP.class.getName(),
                         "-AexpectedEnclosedElements=m1=>impl1,m2=>impl2")
                .outdir(classes)
                .files(findJavaFiles(moduleSrc))
                .run()
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!log.isEmpty())
            throw new AssertionError("Unexpected output: " + log);
    }

    @SupportedAnnotationTypes("*")
    @SupportedOptions("expectedEnclosedElements")
    public static final class AP extends AbstractProcessor {

        private Map<String, List<String>> module2ExpectedEnclosedElements;

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (module2ExpectedEnclosedElements == null) {
                module2ExpectedEnclosedElements = new HashMap<>();

                String expectedEnclosedElements =
                        processingEnv.getOptions().get("expectedEnclosedElements");

                for (String moduleDef : expectedEnclosedElements.split(",")) {
                    String[] module2Packages = moduleDef.split("=>");

                    module2ExpectedEnclosedElements.put(module2Packages[0],
                                                        Arrays.asList(module2Packages[1].split(":")));
                }
            }

            //verify ModuleType and ModuleSymbol behavior:
            for (Element root : roundEnv.getRootElements()) {
                ModuleElement module = processingEnv.getElementUtils().getModuleOf(root);

                assertEquals(TypeKind.MODULE, module.asType().getKind());

                boolean[] seenModule = new boolean[1];

                module.accept(new ElementScanner9<Void, Void>() {
                    @Override
                    public Void visitModule(ModuleElement e, Void p) {
                        seenModule[0] = true;
                        return null;
                    }
                    @Override
                    public Void scan(Element e, Void p) {
                        throw new AssertionError("Shouldn't get here.");
                    }
                }, null);

                assertEquals(true, seenModule[0]);

                List<String> actualElements =
                        module.getEnclosedElements()
                              .stream()
                              .map(s -> (PackageElement) s)
                              .map(p -> p.getQualifiedName().toString())
                              .collect(Collectors.toList());

                assertEquals(module2ExpectedEnclosedElements.remove(module.getQualifiedName().toString()),
                             actualElements);
            }

            if (roundEnv.processingOver()) {
                assertEquals(true, module2ExpectedEnclosedElements.isEmpty());
            }

            return false;
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latest();
        }

    }

    @Test
    void testVerifyUsesProvides(Path base) throws Exception {
        Path moduleSrc = base.resolve("module-src");
        Path m1 = moduleSrc.resolve("m1");

        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        tb.writeJavaFiles(m1,
                          "module m1 { exports api; uses api.Api; provides api.Api with impl.Impl; }",
                          "package api; public class Api { }",
                          "package impl; public class Impl extends api.Api { }");

        String log = new JavacTask(tb)
                .options("-doe", "-processor", VerifyUsesProvidesAP.class.getName())
                .outdir(classes)
                .files(findJavaFiles(moduleSrc))
                .run()
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!log.isEmpty())
            throw new AssertionError("Unexpected output: " + log);
    }

    @SupportedAnnotationTypes("*")
    public static final class VerifyUsesProvidesAP extends AbstractProcessor {

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            TypeElement api = processingEnv.getElementUtils().getTypeElement("api.Api");

            assertNonNull("Cannot find api.Api", api);

            ModuleElement modle = (ModuleElement) processingEnv.getElementUtils().getPackageOf(api).getEnclosingElement();

            assertNonNull("modle is null", modle);

            List<? extends UsesDirective> uses = ElementFilter.usesIn(modle.getDirectives());
            assertEquals(1, uses.size());
            assertEquals("api.Api", uses.iterator().next().getService().getQualifiedName().toString());

            List<? extends ProvidesDirective> provides = ElementFilter.providesIn(modle.getDirectives());
            assertEquals(1, provides.size());
            assertEquals("api.Api", provides.iterator().next().getService().getQualifiedName().toString());
            assertEquals("impl.Impl", provides.iterator().next().getImplementation().getQualifiedName().toString());

            return false;
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latest();
        }

    }

    @Test
    void testPackageNoModule(Path base) throws Exception {
        Path src = base.resolve("src");
        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        tb.writeJavaFiles(src,
                          "package api; public class Api { }");

        String log = new JavacTask(tb)
                .options("-processor", VerifyPackageNoModule.class.getName(),
                         "-source", "8",
                         "-Xlint:-options")
                .outdir(classes)
                .files(findJavaFiles(src))
                .run()
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!log.isEmpty())
            throw new AssertionError("Unexpected output: " + log);
    }

    @SupportedAnnotationTypes("*")
    public static final class VerifyPackageNoModule extends AbstractProcessor {

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            TypeElement api = processingEnv.getElementUtils().getTypeElement("api.Api");

            assertNonNull("Cannot find api.Api", api);

            ModuleElement modle = (ModuleElement) processingEnv.getElementUtils().getPackageOf(api).getEnclosingElement();

            assertNull("modle is not null", modle);

            return false;
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latest();
        }

    }

    private static void assertNonNull(String msg, Object val) {
        if (val == null) {
            throw new AssertionError(msg);
        }
    }

    private static void assertNull(String msg, Object val) {
        if (val != null) {
            throw new AssertionError(msg);
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("expected: " + expected + "; actual=" + actual);
        }
    }

}
