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
 * @bug 8133884 8162711 8133896
 * @summary Verify that annotation processing works.
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask ModuleTestBase
 * @run main AnnotationProcessing
 */

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.ModuleElement.ProvidesDirective;
import javax.lang.model.element.ModuleElement.UsesDirective;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.ElementScanner9;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import toolbox.JavacTask;
import toolbox.Task;
import toolbox.Task.Mode;
import toolbox.Task.OutputKind;

public class AnnotationProcessing extends ModuleTestBase {

    public static void main(String... args) throws Exception {
        new AnnotationProcessing().runTests();
    }

    @Test
    public void testAPSingleModule(Path base) throws Exception {
        Path moduleSrc = base.resolve("module-src");
        Path m1 = moduleSrc.resolve("m1x");

        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        tb.writeJavaFiles(m1,
                          "module m1x { }",
                          "package impl; public class Impl { }");

        String log = new JavacTask(tb)
                .options("--module-source-path", moduleSrc.toString(),
                         "-processor", AP.class.getName(),
                         "-AexpectedEnclosedElements=m1x=>impl")
                .outdir(classes)
                .files(findJavaFiles(moduleSrc))
                .run()
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!log.isEmpty())
            throw new AssertionError("Unexpected output: " + log);
    }

    @Test
    public void testAPMultiModule(Path base) throws Exception {
        Path moduleSrc = base.resolve("module-src");
        Path m1 = moduleSrc.resolve("m1x");
        Path m2 = moduleSrc.resolve("m2x");

        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        tb.writeJavaFiles(m1,
                          "module m1x { }",
                          "package impl1; public class Impl1 { }");

        tb.writeJavaFiles(m2,
                          "module m2x { }",
                          "package impl2; public class Impl2 { }");

        String log = new JavacTask(tb)
                .options("--module-source-path", moduleSrc.toString(),
                         "-processor", AP.class.getName(),
                         "-AexpectedEnclosedElements=m1x=>impl1,m2x=>impl2")
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
        private Set<String> seenModules = new HashSet<>();

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

                String moduleName = module.getQualifiedName().toString();

                assertEquals(module2ExpectedEnclosedElements.get(moduleName),
                             actualElements);

                seenModules.add(moduleName);
            }

            if (roundEnv.processingOver()) {
                assertEquals(module2ExpectedEnclosedElements.keySet(), seenModules);
            }

            return false;
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latest();
        }

    }

    @Test
    public void testVerifyUsesProvides(Path base) throws Exception {
        Path moduleSrc = base.resolve("module-src");
        Path m1 = moduleSrc.resolve("m1x");

        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        tb.writeJavaFiles(m1,
                          "module m1x { exports api; uses api.Api; provides api.Api with impl.Impl; }",
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
            assertEquals("impl.Impl", provides.iterator().next().getImplementations().get(0).getQualifiedName().toString());

            return false;
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latest();
        }

    }

    @Test
    public void testPackageNoModule(Path base) throws Exception {
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

    @Test
    public void testQualifiedClassForProcessing(Path base) throws Exception {
        Path moduleSrc = base.resolve("module-src");
        Path m1 = moduleSrc.resolve("m1x");
        Path m2 = moduleSrc.resolve("m2x");

        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        tb.writeJavaFiles(m1,
                          "module m1x { }",
                          "package impl; public class Impl { int m1x; }");

        tb.writeJavaFiles(m2,
                          "module m2x { }",
                          "package impl; public class Impl { int m2x; }");

        new JavacTask(tb)
            .options("--module-source-path", moduleSrc.toString())
            .outdir(classes)
            .files(findJavaFiles(moduleSrc))
            .run()
            .writeAll()
            .getOutput(Task.OutputKind.DIRECT);

        List<String> expected = Arrays.asList("Note: field: m1x");

        for (Mode mode : new Mode[] {Mode.API, Mode.CMDLINE}) {
            List<String> log = new JavacTask(tb, mode)
                    .options("-processor", QualifiedClassForProcessing.class.getName(),
                             "--module-path", classes.toString())
                    .classes("m1x/impl.Impl")
                    .outdir(classes)
                    .run()
                    .writeAll()
                    .getOutputLines(Task.OutputKind.DIRECT);

            if (!expected.equals(log))
                throw new AssertionError("Unexpected output: " + log);
        }
    }

    @SupportedAnnotationTypes("*")
    public static final class QualifiedClassForProcessing extends AbstractProcessor {

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (processingEnv.getElementUtils().getModuleElement("m1x") == null) {
                throw new AssertionError("No m1x module found.");
            }

            Messager messager = processingEnv.getMessager();

            for (TypeElement clazz : ElementFilter.typesIn(roundEnv.getRootElements())) {
                for (VariableElement field : ElementFilter.fieldsIn(clazz.getEnclosedElements())) {
                    messager.printMessage(Kind.NOTE, "field: " + field.getSimpleName());
                }
            }

            return false;
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latest();
        }

    }

    @Test
    public void testModuleInRootElements(Path base) throws Exception {
        Path moduleSrc = base.resolve("module-src");
        Path m1 = moduleSrc.resolve("m1");

        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        tb.writeJavaFiles(m1,
                          "module m1x { exports api; }",
                          "package api; public class Api { }");

        List<String> log = new JavacTask(tb)
                .options("-processor", ModuleInRootElementsAP.class.getName())
                .outdir(classes)
                .files(findJavaFiles(moduleSrc))
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.STDERR);

        assertEquals(Arrays.asList("module: m1x"), log);
    }

    @SupportedAnnotationTypes("*")
    public static final class ModuleInRootElementsAP extends AbstractProcessor {

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            roundEnv.getRootElements()
                    .stream()
                    .filter(el -> el.getKind() == ElementKind.MODULE)
                    .forEach(mod -> System.err.println("module: " + mod.getSimpleName()));

            return false;
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latest();
        }

    }

    @Test
    public void testAnnotationsInModuleInfo(Path base) throws Exception {
        Path moduleSrc = base.resolve("module-src");
        Path m1 = moduleSrc.resolve("m1");

        tb.writeJavaFiles(m1,
                          "@Deprecated module m1x { }");

        Path m2 = moduleSrc.resolve("m2x");

        tb.writeJavaFiles(m2,
                          "@SuppressWarnings(\"\") module m2x { }");

        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        List<String> log = new JavacTask(tb)
                .options("-processor", AnnotationsInModuleInfoPrint.class.getName())
                .outdir(classes)
                .files(findJavaFiles(m1))
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        List<String> expectedLog = Arrays.asList("Note: AP Invoked",
                                                 "Note: AP Invoked");

        assertEquals(expectedLog, log);

        new JavacTask(tb)
            .options("-processor", AnnotationsInModuleInfoFail.class.getName())
            .outdir(classes)
            .files(findJavaFiles(m2))
            .run()
            .writeAll();
    }

    @SupportedAnnotationTypes("java.lang.Deprecated")
    public static final class AnnotationsInModuleInfoPrint extends AbstractProcessor {

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            processingEnv.getMessager().printMessage(Kind.NOTE, "AP Invoked");
            return false;
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latest();
        }

    }

    @SupportedAnnotationTypes("java.lang.Deprecated")
    public static final class AnnotationsInModuleInfoFail extends AbstractProcessor {

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            throw new AssertionError();
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latest();
        }

    }

    @Test
    public void testGenerateInMultiModeAPI(Path base) throws Exception {
        Path moduleSrc = base.resolve("module-src");
        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        Path m1 = moduleSrc.resolve("m1x");

        tb.writeJavaFiles(m1,
                          "module m1x { exports api1; }",
                          "package api1; public class Api { GenApi ga; impl.Impl i; }");

        writeFile("1", m1, "api1", "api");
        writeFile("1", m1, "impl", "impl");

        Path m2 = moduleSrc.resolve("m2x");

        tb.writeJavaFiles(m2,
                          "module m2x { requires m1x; exports api2; }",
                          "package api2; public class Api { api1.GenApi ga1; GenApi qa2; impl.Impl i;}");

        writeFile("2", m2, "api2", "api");
        writeFile("2", m2, "impl", "impl");

        for (FileType fileType : FileType.values()) {
            if (Files.isDirectory(classes)) {
                tb.cleanDirectory(classes);
            } else {
                Files.createDirectories(classes);
            }

            new JavacTask(tb)
              .options("-processor", MultiModeAPITestAP.class.getName(),
                       "--module-source-path", moduleSrc.toString(),
                       "-Afiletype=" + fileType.name())
              .outdir(classes)
              .files(findJavaFiles(moduleSrc))
              .run()
              .writeAll();

            assertFileExists(classes, "m1x", "api1", "GenApi.class");
            assertFileExists(classes, "m1x", "impl", "Impl.class");
            assertFileExists(classes, "m1x", "api1", "gen1");
            assertFileExists(classes, "m2x", "api2", "GenApi.class");
            assertFileExists(classes, "m2x", "impl", "Impl.class");
            assertFileExists(classes, "m2x", "api2", "gen1");
        }
    }

    enum FileType {
        SOURCE,
        CLASS;
    }

    public static abstract class GeneratingAP extends AbstractProcessor {

        void createSource(CreateFileObject file, String name, String content) {
            try (Writer out = file.create().openWriter()) {
                out.write(content);
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }

        void createClass(CreateFileObject file, String name, String content) {
            String fileNameStub = name.replace(".", File.separator);

            try (OutputStream out = file.create().openOutputStream()) {
                Path scratch = Files.createDirectories(Paths.get(""));
                Path scratchSrc = scratch.resolve(fileNameStub + ".java").toAbsolutePath();

                Files.createDirectories(scratchSrc.getParent());

                try (Writer w = Files.newBufferedWriter(scratchSrc)) {
                    w.write(content);
                }

                Path scratchClasses = scratch.resolve("classes");

                Files.createDirectories(scratchClasses);

                JavaCompiler comp = ToolProvider.getSystemJavaCompiler();
                try (StandardJavaFileManager fm = comp.getStandardFileManager(null, null, null)) {
                    List<String> options = Arrays.asList("-d", scratchClasses.toString());
                    Iterable<? extends JavaFileObject> files = fm.getJavaFileObjects(scratchSrc);
                    CompilationTask task = comp.getTask(null, fm, null, options, null, files);

                    if (!task.call()) {
                        throw new AssertionError("compilation failed");
                    }
                }

                Path classfile = scratchClasses.resolve(fileNameStub + ".class");

                Files.copy(classfile, out);
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }

        void doReadResource(CreateFileObject file, String expectedContent) {
            try {
                StringBuilder actualContent = new StringBuilder();

                try (Reader r = file.create().openReader(true)) {
                    int read;

                    while ((read = r.read()) != (-1)) {
                        actualContent.append((char) read);
                    }

                }

                assertEquals(expectedContent, actualContent.toString());
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }

        public interface CreateFileObject {
            public FileObject create() throws IOException;
        }

        void expectFilerException(Callable<Object> c) {
            try {
                c.call();
                throw new AssertionError("Expected exception not thrown");
            } catch (FilerException ex) {
                //expected
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latest();
        }

    }

    @SupportedAnnotationTypes("*")
    @SupportedOptions({"filetype", "modulename"})
    public static final class MultiModeAPITestAP extends GeneratingAP {

        int round;

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (round++ != 0)
                return false;

            createClass("m1x", "api1.GenApi", "package api1; public class GenApi {}");
            createClass("m1x", "impl.Impl", "package impl; public class Impl {}");
            createClass("m2x", "api2.GenApi", "package api2; public class GenApi {}");
            createClass("m2x", "impl.Impl", "package impl; public class Impl {}");

            createResource("m1x", "api1", "gen1");
            createResource("m2x", "api2", "gen1");

            readResource("m1x", "api1", "api", "1");
            readResource("m1x", "impl", "impl", "1");
            readResource("m2x", "api2", "api", "2");
            readResource("m2x", "impl", "impl", "2");

            Filer filer = processingEnv.getFiler();

            expectFilerException(() -> filer.createSourceFile("fail.Fail"));
            expectFilerException(() -> filer.createClassFile("fail.Fail"));
            expectFilerException(() -> filer.createResource(StandardLocation.CLASS_OUTPUT, "fail", "fail"));
            expectFilerException(() -> filer.getResource(StandardLocation.MODULE_SOURCE_PATH, "fail", "fail"));

            //must not generate to unnamed package:
            expectFilerException(() -> filer.createSourceFile("m1/Fail"));
            expectFilerException(() -> filer.createClassFile("m1/Fail"));

            //cannot generate resources to modules that are not root modules:
            expectFilerException(() -> filer.createSourceFile("java.base/fail.Fail"));
            expectFilerException(() -> filer.createClassFile("java.base/fail.Fail"));
            expectFilerException(() -> filer.createResource(StandardLocation.CLASS_OUTPUT, "java.base/fail", "Fail"));

            return false;
        }

        void createClass(String expectedModule, String name, String content) {
            Filer filer = processingEnv.getFiler();
            FileType filetype = FileType.valueOf(processingEnv.getOptions().getOrDefault("filetype", ""));

            switch (filetype) {
                case SOURCE:
                    createSource(() -> filer.createSourceFile(expectedModule + "/" + name), name, content);
                    break;
                case CLASS:
                    createClass(() -> filer.createClassFile(expectedModule + "/" + name), name, content);
                    break;
                default:
                    throw new AssertionError("Unexpected filetype: " + filetype);
            }
        }

        void createResource(String expectedModule, String pkg, String relName) {
            try {
                Filer filer = processingEnv.getFiler();

                filer.createResource(StandardLocation.CLASS_OUTPUT, expectedModule + "/" + pkg, relName)
                     .openOutputStream()
                     .close();
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }

        void readResource(String expectedModule, String pkg, String relName, String expectedContent) {
            Filer filer = processingEnv.getFiler();

            doReadResource(() -> filer.getResource(StandardLocation.MODULE_SOURCE_PATH, expectedModule + "/" + pkg, relName),
                           expectedContent);
        }

    }

    @Test
    public void testGenerateInSingleNameModeAPI(Path base) throws Exception {
        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        Path m1 = base.resolve("module-src");

        tb.writeJavaFiles(m1,
                          "module m1x { }");

        writeFile("3", m1, "impl", "resource");

        new JavacTask(tb)
          .options("-processor", SingleNameModeAPITestAP.class.getName(),
                   "-sourcepath", m1.toString())
          .outdir(classes)
          .files(findJavaFiles(m1))
          .run()
          .writeAll();

        assertFileExists(classes, "impl", "Impl1.class");
        assertFileExists(classes, "impl", "Impl2.class");
        assertFileExists(classes, "impl", "Impl3");
        assertFileExists(classes, "impl", "Impl4.class");
        assertFileExists(classes, "impl", "Impl5.class");
        assertFileExists(classes, "impl", "Impl6");
        assertFileExists(classes, "impl", "Impl7.class");
        assertFileExists(classes, "impl", "Impl8.class");
        assertFileExists(classes, "impl", "Impl9");
    }


    @SupportedAnnotationTypes("*")
    public static final class SingleNameModeAPITestAP extends GeneratingAP {

        int round;

        @Override
        public synchronized void init(ProcessingEnvironment processingEnv) {
            super.init(processingEnv);
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (round++ != 0)
                return false;

            Filer filer = processingEnv.getFiler();

            createSource(() -> filer.createSourceFile("impl.Impl1"), "impl.Impl1", "package impl; class Impl1 {}");
            createClass(() -> filer.createClassFile("impl.Impl2"), "impl.Impl2", "package impl; class Impl2 {}");
            createSource(() -> filer.createResource(StandardLocation.CLASS_OUTPUT, "impl", "Impl3"), "impl.Impl3", "");
            doReadResource(() -> filer.getResource(StandardLocation.SOURCE_PATH, "impl", "resource"), "3");

            createSource(() -> filer.createSourceFile("m1x/impl.Impl4"), "impl.Impl4", "package impl; class Impl4 {}");
            createClass(() -> filer.createClassFile("m1x/impl.Impl5"), "impl.Impl5", "package impl; class Impl5 {}");
            createSource(() -> filer.createResource(StandardLocation.CLASS_OUTPUT, "m1x/impl", "Impl6"), "impl.Impl6", "");
            doReadResource(() -> filer.getResource(StandardLocation.SOURCE_PATH, "m1x/impl", "resource"), "3");

            TypeElement jlObject = processingEnv.getElementUtils().getTypeElement("java.lang.Object");

            //"broken" originating element:
            createSource(() -> filer.createSourceFile("impl.Impl7", jlObject), "impl.Impl7", "package impl; class Impl7 {}");
            createClass(() -> filer.createClassFile("impl.Impl8", jlObject), "impl.Impl8", "package impl; class Impl8 {}");
            createSource(() -> filer.createResource(StandardLocation.CLASS_OUTPUT, "impl", "Impl9", jlObject), "impl.Impl9", "");

            //must not generate to unnamed package:
            expectFilerException(() -> filer.createSourceFile("Fail"));
            expectFilerException(() -> filer.createClassFile("Fail"));
            expectFilerException(() -> filer.createSourceFile("m1x/Fail"));
            expectFilerException(() -> filer.createClassFile("m1x/Fail"));

            //cannot generate resources to modules that are not root modules:
            expectFilerException(() -> filer.createSourceFile("java.base/fail.Fail"));
            expectFilerException(() -> filer.createClassFile("java.base/fail.Fail"));
            expectFilerException(() -> filer.createResource(StandardLocation.CLASS_OUTPUT, "java.base/fail", "Fail"));

            return false;
        }

    }

    @Test
    public void testGenerateInUnnamedModeAPI(Path base) throws Exception {
        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        Path src = base.resolve("src");

        tb.writeJavaFiles(src,
                          "class T {}");

        new JavacTask(tb)
          .options("-processor", UnnamedModeAPITestAP.class.getName(),
                   "-sourcepath", src.toString())
          .outdir(classes)
          .files(findJavaFiles(src))
          .run()
          .writeAll();

        assertFileExists(classes, "Impl1.class");
        assertFileExists(classes, "Impl2.class");
    }

    @Test
    public void testGenerateInNoModeAPI(Path base) throws Exception {
        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        Path src = base.resolve("src");

        tb.writeJavaFiles(src,
                          "class T {}");

        new JavacTask(tb)
          .options("-processor", UnnamedModeAPITestAP.class.getName(),
                   "-source", "8", "-target", "8",
                   "-sourcepath", src.toString())
          .outdir(classes)
          .files(findJavaFiles(src))
          .run()
          .writeAll();

        assertFileExists(classes, "Impl1.class");
        assertFileExists(classes, "Impl2.class");
    }

    @SupportedAnnotationTypes("*")
    public static final class UnnamedModeAPITestAP extends GeneratingAP {

        int round;

        @Override
        public synchronized void init(ProcessingEnvironment processingEnv) {
            super.init(processingEnv);
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (round++ != 0)
                return false;

            Filer filer = processingEnv.getFiler();

            //must not generate to unnamed package:
            createSource(() -> filer.createSourceFile("Impl1"), "Impl1", "class Impl1 {}");
            createClass(() -> filer.createClassFile("Impl2"), "Impl2", "class Impl2 {}");

            return false;
        }

    }

    @Test
    public void testDisambiguateAnnotations(Path base) throws Exception {
        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        Path src = base.resolve("src");
        Path m1 = src.resolve("m1x");

        tb.writeJavaFiles(m1,
                          "module m1x { exports api; }",
                          "package api; public @interface A {}",
                          "package api; public @interface B {}");

        Path m2 = src.resolve("m2x");

        tb.writeJavaFiles(m2,
                          "module m2x { exports api; }",
                          "package api; public @interface A {}",
                          "package api; public @interface B {}");

        Path m3 = src.resolve("m3x");

        tb.writeJavaFiles(m3,
                          "module m3x { requires m1x; }",
                          "package impl; import api.*; @A @B public class T {}");

        Path m4 = src.resolve("m4x");

        tb.writeJavaFiles(m4,
                          "module m4x { requires m2x; }",
                          "package impl; import api.*; @A @B public class T {}");

        List<String> log;
        List<String> expected;

        log = new JavacTask(tb)
            .options("-processor", SelectAnnotationATestAP.class.getName() + "," + SelectAnnotationBTestAP.class.getName(),
                     "--module-source-path", src.toString(),
                     "-m", "m1x,m2x")
            .outdir(classes)
            .run()
            .writeAll()
            .getOutputLines(OutputKind.STDERR);

        expected = Arrays.asList("");

        if (!expected.equals(log)) {
            throw new AssertionError("Output does not match; output: " + log);
        }

        log = new JavacTask(tb)
            .options("-processor", SelectAnnotationATestAP.class.getName() + "," + SelectAnnotationBTestAP.class.getName(),
                     "--module-source-path", src.toString(),
                     "-m", "m3x")
            .outdir(classes)
            .run()
            .writeAll()
            .getOutputLines(OutputKind.STDERR);

        expected = Arrays.asList("SelectAnnotationBTestAP",
                                 "SelectAnnotationBTestAP");

        if (!expected.equals(log)) {
            throw new AssertionError("Output does not match; output: " + log);
        }

        log = new JavacTask(tb)
            .options("-processor", SelectAnnotationATestAP.class.getName() + "," + SelectAnnotationBTestAP.class.getName(),
                     "--module-source-path", src.toString(),
                     "-m", "m4x")
            .outdir(classes)
            .run()
            .writeAll()
            .getOutputLines(OutputKind.STDERR);

        expected = Arrays.asList("SelectAnnotationATestAP",
                                 "SelectAnnotationBTestAP",
                                 "SelectAnnotationATestAP",
                                 "SelectAnnotationBTestAP");

        if (!expected.equals(log)) {
            throw new AssertionError("Output does not match; output: " + log);
        }
    }

    @SupportedAnnotationTypes("m2x/api.A")
    public static final class SelectAnnotationATestAP extends AbstractProcessor {

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            System.err.println("SelectAnnotationATestAP");

            return false;
        }

    }

    @SupportedAnnotationTypes("api.B")
    public static final class SelectAnnotationBTestAP extends AbstractProcessor {

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            System.err.println("SelectAnnotationBTestAP");

            return false;
        }

    }

    private static void writeFile(String content, Path base, String... pathElements) throws IOException {
        Path file = resolveFile(base, pathElements);

        Files.createDirectories(file.getParent());

        try (Writer out = Files.newBufferedWriter(file)) {
            out.append(content);
        }
    }

    @Test
    public void testUnboundLookup(Path base) throws Exception {
        Path src = base.resolve("src");

        tb.writeJavaFiles(src,
                          "package impl.conflict.src; public class Impl { }");

        Path moduleSrc = base.resolve("module-src");
        Path m1 = moduleSrc.resolve("m1x");
        Path m2 = moduleSrc.resolve("m2x");

        Path classes = base.resolve("classes");
        Path cpClasses = base.resolve("cpClasses");

        Files.createDirectories(classes);
        Files.createDirectories(cpClasses);

        tb.writeJavaFiles(m1,
                          "module m1x { }",
                          "package impl1; public class Impl { }",
                          "package impl.conflict.module; class Impl { }",
                          "package impl.conflict.clazz; public class pkg { public static class I { } }",
                          "package impl.conflict.src; public class Impl { }");

        tb.writeJavaFiles(m2,
                          "module m2x { }",
                          "package impl2; public class Impl { }",
                          "package impl.conflict.module; class Impl { }",
                          "package impl.conflict; public class clazz { public static class pkg { } }");

        //from source:
        new JavacTask(tb)
            .options("--module-source-path", moduleSrc.toString(),
                     "--source-path", src.toString(),
                     "-processorpath", System.getProperty("test.class.path"),
                     "-processor", UnboundLookup.class.getName())
            .outdir(classes)
            .files(findJavaFiles(moduleSrc))
            .run()
            .writeAll();

        new JavacTask(tb)
            .options("--source-path", src.toString())
            .outdir(cpClasses)
            .files(findJavaFiles(src))
            .run()
            .writeAll();

        //from classfiles:
        new JavacTask(tb)
            .options("--module-path", classes.toString(),
                     "--class-path", cpClasses.toString(),
                     "--add-modules", "m1x,m2x",
                     "-processorpath", System.getProperty("test.class.path"),
                     "-processor", UnboundLookup.class.getName(),
                     "-proc:only")
            .classes("java.lang.Object")
            .run()
            .writeAll();

        //source 8:
        new JavacTask(tb)
            .options("--source-path", src.toString(),
                     "-source", "8",
                     "-processorpath", System.getProperty("test.class.path"),
                     "-processor", UnboundLookup8.class.getName())
            .outdir(cpClasses)
            .files(findJavaFiles(src))
            .run()
            .writeAll();

    }

    @SupportedAnnotationTypes("*")
    public static final class UnboundLookup extends AbstractProcessor {

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            assertTypeElementExists("impl1.Impl", "m1x");
            assertPackageElementExists("impl1", "m1x");
            assertTypeElementExists("impl2.Impl", "m2x");
            assertTypeElementExists("impl.conflict.clazz.pkg.I", "m1x");
            assertTypeElementExists("impl.conflict.clazz", "m2x");
            assertPackageElementExists("impl.conflict.clazz", "m1x");
            assertPackageElementExists("impl2", "m2x");
            assertTypeElementNotFound("impl.conflict.module.Impl");
            assertPackageElementNotFound("impl.conflict.module");
            assertTypeElementNotFound("impl.conflict.src.Impl");
            assertPackageElementNotFound("impl.conflict.src");
            assertTypeElementNotFound("impl.conflict.clazz.pkg");

            return false;
        }

        private void assertTypeElementExists(String name, String expectedModule) {
            assertElementExists(name, "class", processingEnv.getElementUtils() :: getTypeElement, expectedModule);
        }

        private void assertPackageElementExists(String name, String expectedModule) {
            assertElementExists(name, "package", processingEnv.getElementUtils() :: getPackageElement, expectedModule);
        }

        private void assertElementExists(String name, String type, Function<String, Element> getter, String expectedModule) {
            Element clazz = getter.apply(name);

            if (clazz == null) {
                throw new AssertionError("No " + name + " " + type + " found.");
            }

            ModuleElement mod = processingEnv.getElementUtils().getModuleOf(clazz);

            if (!mod.getQualifiedName().contentEquals(expectedModule)) {
                throw new AssertionError(name + " found in an unexpected module: " + mod.getQualifiedName());
            }
        }

        private void assertTypeElementNotFound(String name) {
            assertElementNotFound(name, processingEnv.getElementUtils() :: getTypeElement);
        }

        private void assertPackageElementNotFound(String name) {
            assertElementNotFound(name, processingEnv.getElementUtils() :: getPackageElement);
        }

        private void assertElementNotFound(String name, Function<String, Element> getter) {
            Element found = getter.apply(name);

            if (found != null) {
                fail("Element found unexpectedly: " + found);
            }
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latest();
        }

    }

    @SupportedAnnotationTypes("*")
    public static final class UnboundLookup8 extends AbstractProcessor {

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (processingEnv.getElementUtils().getTypeElement("impl.conflict.src.Impl") == null) {
                throw new AssertionError("impl.conflict.src.Impl.");
            }

            if (processingEnv.getElementUtils().getModuleElement("java.base") != null) {
                throw new AssertionError("getModuleElement != null for -source 8");
            }

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

    private static void assertFileExists(Path base, String... pathElements) {
        Path file = resolveFile(base, pathElements);

        if (!Files.exists(file)) {
            throw new AssertionError("Expected file: " + file + " exist, but it does not.");
        }
    }

    static Path resolveFile(Path base, String... pathElements) {
        Path file = base;

        for (String el : pathElements) {
            file = file.resolve(el);
        }

        return file;
    }

    private static void fail(String msg) {
        throw new AssertionError(msg);
    }

}
