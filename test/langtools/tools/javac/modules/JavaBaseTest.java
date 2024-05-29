/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8193125 8196623
 * @summary test modifiers with java.base
 * @library /tools/lib
 * @enablePreview
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.jvm
 *      jdk.compiler/com.sun.tools.javac.main
 *      jdk.compiler/com.sun.tools.javac.platform
 *      java.base/jdk.internal.classfile.impl
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main JavaBaseTest
 */

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.StreamSupport;

import java.lang.classfile.*;
import java.lang.classfile.attribute.*;

import com.sun.tools.javac.jvm.Target;
import com.sun.tools.javac.platform.JDKPlatformProvider;

import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class JavaBaseTest {

    public static void main(String... args) throws Exception {
        JavaBaseTest t = new JavaBaseTest();
        t.run();
    }

    final List<List<String>> modifiers = List.of(
        List.of("static"),
        List.of("transitive"),
        List.of("static", "transitive")
    );

    enum Mode { SOURCE, CLASS };

    ToolBox tb = new ToolBox();
    int testCount = 0;
    int errorCount = 0;

    void run() throws Exception {
        Set<String> targets = new LinkedHashSet<>();
        StreamSupport.stream(new JDKPlatformProvider().getSupportedPlatformNames()
                                                      .spliterator(),
                             false)
                     .filter(p -> Integer.parseInt(p) >= 9)
                     .forEach(targets::add);
        //run without --release:
        targets.add("current");
        for (List<String> mods : modifiers) {
            for (String target : targets) {
                for (Mode mode : Mode.values()) {
                    test(mods, target, mode);
                }
            }
        }

        System.err.println(testCount + " tests");
        if (errorCount > 0) {
            throw new Exception(errorCount + " errors found");
        }
    }

    void test(List<String> mods, String target, Mode mode) {
        System.err.println("Test " + mods + " " + target + " " + mode);
        testCount++;
        try {
            Path base = Paths.get(String.join("-", mods))
                    .resolve(target).resolve(mode.name().toLowerCase());
            Files.createDirectories(base);
            switch (mode) {
                case SOURCE -> testSource(base, mods, target);
                case CLASS -> testClass(base, mods, target);
            }
        } catch (Exception e) {
            error("Exception: " + e);
            e.printStackTrace();
        }
        System.err.println();
    }

    void testSource(Path base, List<String> mods, String target) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "module m { requires " + String.join(" ", mods) + " java.base; }");
        Path modules = Files.createDirectories(base.resolve("modules"));
        boolean expectOK = target.equals("9");

        JavacTask jct = new JavacTask(tb)
            .outdir(modules);

        if (target.equals("current"))
            jct.options("-XDrawDiagnostics");
        else
            jct.options("-XDrawDiagnostics", "--release", target);

        String log = jct.files(tb.findJavaFiles(src))
            .run(expectOK ? Task.Expect.SUCCESS : Task.Expect.FAIL)
            .writeAll()
            .getOutput(Task.OutputKind.DIRECT);

        if (!expectOK) {
            boolean foundErrorMessage = false;
            for (String mod : mods) {
                String key = mod.equals("static")
                    ? "compiler.err.mod.not.allowed.here"
                    : "compiler.err.modifier.not.allowed.here";
                String message = "module-info.java:1:12: " + key + ": " + mod;
                if (log.contains(message)) {
                    foundErrorMessage = true;
                }
            }
            if (!foundErrorMessage) {
                throw new Exception("expected error message not found");
            }
        }
    }

    void testClass(Path base, List<String> mods, String target) throws Exception {
        createClass(base, mods, target);

        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "module mx { requires m; }");
        Path modules = Files.createDirectories(base.resolve("modules"));

        boolean expectOK = target.equals("9");
        String log = new JavacTask(tb)
                .outdir(modules)
                .options("-XDrawDiagnostics",
                        "--module-path", base.resolve("test-modules").toString())
                .files(tb.findJavaFiles(src))
                .run(expectOK ? Task.Expect.SUCCESS : Task.Expect.FAIL)
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!expectOK) {
            boolean foundErrorMessage = false;
            for (String mod : mods) {
                String flag = mod.equals("static")
                    ? "ACC_STATIC_PHASE (0x0040)"
                    : "ACC_TRANSITIVE (0x0020)";
                String message = "- compiler.err.cant.access: m.module-info,"
                        + " (compiler.misc.bad.class.file.header: module-info.class,"
                        + " (compiler.misc.bad.requires.flag: " + flag + ")";
                if (log.contains(message)) {
                    foundErrorMessage = true;
                }
            }
            if (!foundErrorMessage) {
                throw new Exception("expected error message not found");
            }
        }
    }

    void createClass(Path base, List<String> mods, String target) throws Exception {
        Path src1 = base.resolve("interim-src");
        tb.writeJavaFiles(src1,
                "module m { requires java.base; }");
        Path modules1 = Files.createDirectories(base.resolve("interim-modules"));

        JavacTask jct = new JavacTask(tb)
            .outdir(modules1);

        if (!target.equals("current")) {
            jct.options("--release", target);
        }

        jct.files(tb.findJavaFiles(src1))
            .run(Task.Expect.SUCCESS);

        ClassModel cm1 = ClassFile.of().parse(modules1.resolve("module-info.class"));

        ModuleAttribute modAttr1 = cm1.findAttribute(Attributes.module()).orElseThrow();
        List<ModuleRequireInfo> requires = Arrays.asList(new ModuleRequireInfo[modAttr1.requires().size()]);
        for (int i = 0; i < modAttr1.requires().size(); ++i) {
            ModuleRequireInfo e1 = modAttr1.requires().get(i);
            int flags = e1.requiresFlagsMask();
            ModuleRequireInfo e2;
            if (e1.requires().name().equalsString("java.base")) {
                for (String mod : mods) {
                    switch (mod) {
                        case "static" -> flags |= ClassFile.ACC_STATIC_PHASE;
                        case "transitive" -> flags |= ClassFile.ACC_TRANSITIVE;
                    }
                }
                e2 = ModuleRequireInfo.of(e1.requires(), flags, e1.requiresVersion().orElse(null));
            } else {
                e2 = e1;
            }
            requires.set(i, e2);
        }

        ModuleAttribute modAttr2 = ModuleAttribute.of(
                modAttr1.moduleName(),
                modAttr1.moduleFlagsMask(),
                modAttr1.moduleVersion().orElse(null),
                requires,
                modAttr1.exports(),
                modAttr1.opens(),
                modAttr1.uses(),
                modAttr1.provides());
        Path modInfo = base.resolve("test-modules").resolve("module-info.class");
        Files.createDirectories(modInfo.getParent());
        byte[] newBytes = ClassFile.of().transform(cm1, ClassTransform.dropping(ce -> ce instanceof ModuleAttribute).
                andThen(ClassTransform.endHandler(classBuilder -> classBuilder.with(modAttr2))));
        try (OutputStream out = Files.newOutputStream(modInfo)) {
            out.write(newBytes);
        }
    }

    private void error(String message) {
        System.err.println("Error: " + message);
        errorCount++;
    }
}

