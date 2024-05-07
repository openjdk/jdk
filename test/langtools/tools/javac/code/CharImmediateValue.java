/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8280067
 * @summary Verify constant/immediate char values are correctly enhanced to ints when used in unary
 *          operators
 * @library /tools/lib
 * @enablePreview
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.code
 *          jdk.compiler/com.sun.tools.javac.comp
 *          jdk.compiler/com.sun.tools.javac.jvm
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.tree
 *          jdk.compiler/com.sun.tools.javac.util
 *          java.base/jdk.internal.classfile.impl
 *          jdk.jdeps/com.sun.tools.javap
 * @build toolbox.JarTask toolbox.JavacTask toolbox.JavapTask toolbox.ToolBox
 * @compile CharImmediateValue.java
 * @run main CharImmediateValue
 */

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;

import java.lang.classfile.*;
import java.lang.classfile.attribute.CodeAttribute;

import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.TreeScanner;

import toolbox.JarTask;
import toolbox.ToolBox;


public class CharImmediateValue implements Plugin {
    public static void main(String... args) throws Exception {
        new CharImmediateValue().runSourceTest();
        new CharImmediateValue().runReplacementTest();
    }

    void runSourceTest() throws Exception {
        int param = 0;
        Character var = (char) -(false ? (char) param : (char) 2);
    }

    void runReplacementTest() throws Exception {
        ToolBox tb = new ToolBox();
        Path pluginClasses = Path.of("plugin-classes");
        tb.writeFile(pluginClasses.resolve("META-INF").resolve("services").resolve(Plugin.class.getName()),
                CharImmediateValue.class.getName() + System.lineSeparator());
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(Path.of(ToolBox.testClasses))) {
            for (Path p : ds) {
                if (p.getFileName().toString().startsWith("CharImmediateValue") ||
                    p.getFileName().toString().endsWith(".class")) {
                    Files.copy(p, pluginClasses.resolve(p.getFileName()));
                }
            }
        }

        Path pluginJar = Path.of("plugin.jar");
        new JarTask(tb, pluginJar)
                .baseDir(pluginClasses)
                .files(".")
                .run();

        Path src = Path.of("src");
            tb.writeJavaFiles(src,
                    """
                    public class Test{
                        private static char replace; //this will be replace with a constant "1" after constant folding is done
                        public static String run() {
                            char c = (char) - replace;
                            if (c < 0) {
                                throw new AssertionError("Incorrect value!");
                            } else {
                                return Integer.toString(c);
                            }
                        }
                    }
                    """);
        Path classes = Files.createDirectories(Path.of("classes"));

        new toolbox.JavacTask(tb)
                .classpath(pluginJar)
                .options("-XDaccessInternalAPI")
                .outdir(classes)
                .files(tb.findJavaFiles(src))
                .run()
                .writeAll();

        URLClassLoader cl = new URLClassLoader(new URL[] {classes.toUri().toURL()});

        String actual = (String) cl.loadClass("Test")
                                   .getMethod("run")
                                   .invoke(null);
        String expected = "65535";
        if (!Objects.equals(actual, expected)) {
            throw new AssertionError("expected: " + expected + "; but got: " + actual);
        }

        Path testClass = classes.resolve("Test.class");
        ClassModel cf = ClassFile.of().parse(testClass);
        CodeAttribute codeAttr = cf.methods().get(1).findAttribute(Attributes.CODE).orElseThrow();
        boolean seenCast = false;
        for (CodeElement i : codeAttr.elementList()) {
            if (i instanceof Instruction ins && ins.opcode() == Opcode.I2C) {
                seenCast = true;
            }
        }
        if (!seenCast) {
            throw new AssertionError("Missing cast!");
        }
    }

    // Plugin impl...

    @Override
    public String getName() { return "CharImmediateValue"; }

    @Override
    public void init(JavacTask task, String... args) {
        task.addTaskListener(new TaskListener() {
            @Override
            public void started(TaskEvent e) {
                if (e.getKind() == TaskEvent.Kind.GENERATE) {
                    convert((JCCompilationUnit) e.getCompilationUnit());
                }
            }
        });
    }

    @Override
    public boolean autoStart() {
        return true;
    }

    private void convert(JCCompilationUnit toplevel) {
        new TreeScanner() {
            @Override
            public void visitIdent(JCIdent tree) {
                if (tree.name.contentEquals("replace")) {
                    tree.type = tree.type.constType(1);
                }
                super.visitIdent(tree);
            }
        }.scan(toplevel);
    }

}
