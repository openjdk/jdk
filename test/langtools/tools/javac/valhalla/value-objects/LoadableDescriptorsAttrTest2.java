/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8331461
 * @summary [lworld] javac is generating a class file with the LoadableDescriptors attribute but with minor version '0'
 * @library /tools/lib
 * @enablePreview
 * @modules
 *      jdk.compiler/com.sun.tools.javac.code
 *      jdk.compiler/com.sun.tools.javac.util
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.file
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main LoadableDescriptorsAttrTest2
 */

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import com.sun.tools.javac.util.Assert;

import toolbox.Task.OutputKind;
import toolbox.TestRunner;
import toolbox.ToolBox;

public class LoadableDescriptorsAttrTest2 extends TestRunner {
    ToolBox tb = new ToolBox();

    public LoadableDescriptorsAttrTest2() {
        super(System.err);
    }

    protected void runTests() throws Exception {
        runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    Path[] findJavaFiles(Path... paths) throws Exception {
        return tb.findJavaFiles(paths);
    }

    public static void main(String... args) throws Exception {
        new LoadableDescriptorsAttrTest2().runTests();
    }

    @Test
    public void testLoadableDescField(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                value class Val {}
                """,
                """
                class Ident {
                    Val val;
                }
                """);
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);

        List<String> log;
        List<String> expected;

        expected = List.of(
            "Ident.java:2:5: compiler.warn.declared.using.preview: kindname.class, Val",
            "Val.java:1:1: compiler.warn.preview.feature.use.plural: (compiler.misc.feature.value.classes)",
            "2 warnings"
        );

        log = new toolbox.JavacTask(tb)
                .options("--enable-preview",
                         "-source", Integer.toString(Runtime.version().feature()),
                         "-XDrawDiagnostics",
                         "-Xlint:preview")
                .outdir(classes)
                .files(findJavaFiles(src))
                .run()
                .writeAll()
                .getOutputLines(OutputKind.DIRECT);

        tb.checkEqual(log, expected);

        Path classFilePath = classes.resolve("Ident.class");
        var classFile = ClassFile.of().parse(classFilePath);
        Assert.check(classFile.minorVersion() == 65535);
        Assert.check(classFile.findAttribute(Attributes.loadableDescriptors()).isPresent());

        expected = List.of(
            "- compiler.warn.preview.feature.use.classfile: Val.class, 28",
            "Ident.java:2:5: compiler.warn.declared.using.preview: kindname.class, Val",
            "2 warnings"
        );

        // now with the value class in the classpath
        log = new toolbox.JavacTask(tb)
                .options("--enable-preview",
                         "-source", Integer.toString(Runtime.version().feature()),
                         "-cp", classes.toString(),
                         "-XDrawDiagnostics",
                         "-Xlint:preview")
                .outdir(classes)
                .files(src.resolve("Ident.java"))
                .run()
                .writeAll()
                .getOutputLines(OutputKind.DIRECT);

        tb.checkEqual(log, expected);

        classFilePath = classes.resolve("Ident.class");
        classFile = ClassFile.of().parse(classFilePath);
        Assert.check(classFile.minorVersion() == 65535);
        Assert.check(classFile.findAttribute(Attributes.loadableDescriptors()).isPresent());
    }

    @Test
    public void testLoadableDescMethodArg(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                value class Val {}
                """,
                """
                class Ident {
                    void m(Val val) {}
                }
                """);
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);

        List<String> log;
        List<String> expected;

        expected = List.of(
            "Ident.java:2:12: compiler.warn.declared.using.preview: kindname.class, Val",
            "Val.java:1:1: compiler.warn.preview.feature.use.plural: (compiler.misc.feature.value.classes)",
            "2 warnings"
        );

        log = new toolbox.JavacTask(tb)
                .options("--enable-preview",
                         "-source", Integer.toString(Runtime.version().feature()),
                         "-XDrawDiagnostics",
                         "-Xlint:preview")
                .outdir(classes)
                .files(findJavaFiles(src))
                .run()
                .writeAll()
                .getOutputLines(OutputKind.DIRECT);

        tb.checkEqual(log, expected);

        Path classFilePath = classes.resolve("Ident.class");
        var classFile = ClassFile.of().parse(classFilePath);
        Assert.check(classFile.minorVersion() == 65535);
        Assert.check(classFile.findAttribute(Attributes.loadableDescriptors()).isPresent());

        expected = List.of(
            "- compiler.warn.preview.feature.use.classfile: Val.class, 28",
            "Ident.java:2:12: compiler.warn.declared.using.preview: kindname.class, Val",
            "2 warnings"
        );

        // now with the value class in the classpath
        log = new toolbox.JavacTask(tb)
                .options("--enable-preview",
                         "-source", Integer.toString(Runtime.version().feature()),
                         "-cp", classes.toString(),
                         "-XDrawDiagnostics",
                         "-Xlint:preview")
                .outdir(classes)
                .files(src.resolve("Ident.java"))
                .run()
                .writeAll()
                .getOutputLines(OutputKind.DIRECT);

        tb.checkEqual(log, expected);

        classFilePath = classes.resolve("Ident.class");
        classFile = ClassFile.of().parse(classFilePath);
        Assert.check(classFile.minorVersion() == 65535);
        Assert.check(classFile.findAttribute(Attributes.loadableDescriptors()).isPresent());

    }

    @Test
    public void testLoadableDescReturnType(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                value class Val {}
                """,
                """
                class Ident {
                    Val m() {
                        return null;
                    }
                }
                """);
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);

        List<String> log;
        List<String> expected;

        expected = List.of(
            "Ident.java:2:5: compiler.warn.declared.using.preview: kindname.class, Val",
            "Val.java:1:1: compiler.warn.preview.feature.use.plural: (compiler.misc.feature.value.classes)",
            "2 warnings"
        );

        log = new toolbox.JavacTask(tb)
                .options("--enable-preview",
                         "-source", Integer.toString(Runtime.version().feature()),
                         "-XDrawDiagnostics",
                         "-Xlint:preview")
                .outdir(classes)
                .files(findJavaFiles(src))
                .run()
                .writeAll()
                .getOutputLines(OutputKind.DIRECT);

        tb.checkEqual(log, expected);

        Path classFilePath = classes.resolve("Ident.class");
        var classFile = ClassFile.of().parse(classFilePath);
        Assert.check(classFile.minorVersion() == 65535);
        Assert.check(classFile.findAttribute(Attributes.loadableDescriptors()).isPresent());

        expected = List.of(
            "Ident.java:2:5: compiler.warn.declared.using.preview: kindname.class, Val",
            "Val.java:1:1: compiler.warn.preview.feature.use.plural: (compiler.misc.feature.value.classes)",
            "2 warnings"
        );

        // now with the value class in the classpath
        new toolbox.JavacTask(tb)
                .options("--enable-preview",
                         "-source", Integer.toString(Runtime.version().feature()), "-cp", classes.toString(),
                         "-XDrawDiagnostics",
                         "-Xlint:preview")
                .outdir(classes)
                .files(src.resolve("Ident.java"))
                .run()
                .writeAll()
                .getOutputLines(OutputKind.DIRECT);

        tb.checkEqual(log, expected);

        classFilePath = classes.resolve("Ident.class");
        classFile = ClassFile.of().parse(classFilePath);
        Assert.check(classFile.minorVersion() == 65535);
        Assert.check(classFile.findAttribute(Attributes.loadableDescriptors()).isPresent());

    }
}
