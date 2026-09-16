/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.valueclass;

/*
 * @test
 * @bug 8384068
 * @summary Tests ValueClassPlugin
 * @enablePreview
 * @library /test/lib /test/langtools/tools/lib
 * @run junit ${test.main.class}
 */

import jdk.test.lib.ByteCodeLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import toolbox.JarTask;
import toolbox.JavacTask;
import toolbox.ToolBox;

import java.lang.classfile.ClassFile;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A reusable value class helper for Collections tests.
 * Wraps an int and an int array (x, arr), supports equality, ordering, and hashing.
 * When compiled with -Xplugin:ValueClassPlugin --enable-preview this class
 * is treated as a value class; otherwise it is a plain identity class,
 * allowing the same tests to exercise both modes.
 */
public class ValueClassPluginTest {
    static final ToolBox tb = new ToolBox();
    static Path pluginJar;
    static Path realAnnotation;

    @BeforeAll
    static void setup() throws Throwable {
        Path pluginSrc = tb.findFromTestRoot("../jtreg_value_class_plugin");
        requireNonNull(pluginSrc);
        Path pluginClasses = Files.createDirectories(Path.of("plugin-classes"));
        new JavacTask(tb)
                .outdir(pluginClasses)
                .options("-d", pluginSrc.toString(), "--add-exports", "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
                        "--add-exports", "jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
                        "--add-exports", "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
                        "--add-exports", "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
                        "--enable-preview")
                .run()
                .writeAll();
        new JarTask(tb, pluginJar)
                .baseDir(pluginClasses)
                .run()
                .writeAll();
        pluginJar = pluginClasses;

        Path annoSrc = tb.findFromTestRoot("../lib/jdk/test/lib/valueclass/AsValueClass.java");
        Path annoClasses = Files.createDirectories(Path.of("annotation-classes"));
        new JavacTask(tb)
                .outdir(annoClasses)
                .files(annoSrc)
                .run()
                .writeAll();
    }

    static Arguments[] positiveTests() {
        return new Arguments[] {
                Arguments.of("fully-qualified", """
                        @jdk.test.lib.valueclass.AsValueClass
                        class Test {}
                        """),
                Arguments.of("explicit-import", """
                        import jdk.test.lib.valueclass.AsValueClass;

                        @AsValueClass
                        class Test {}
                        """),
                Arguments.of("star-import", """
                        import jdk.test.lib.valueclass.*;

                        @AsValueClass
                        class Test {}
                        """),
        };
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("positiveTests")
    void positiveTest(String caseName, String body) throws Throwable {
        Path path = Path.of(caseName);
        tb.writeJavaFiles(path, body);
        new JavacTask(tb)
                .outdir(path)
                .files(tb.findJavaFiles(path))
                .options("--processor-path", pluginJar.toString(),
                        "-XDaccessInternalAPI",
                        "-Xplugin:ValueClassPlugin",
                        "--enable-preview",
                        "-source", Integer.toString(Runtime.version().feature()))
                .run()
                .writeAll();
        byte[] bytes = Files.readAllBytes(path.resolve("Test.class"));
        var classModel = ClassFile.of().parse(bytes);
        assertSame(ClassFile.PREVIEW_MINOR_VERSION, classModel.minorVersion());
        assertSame(ClassFile.latestMajorVersion(), classModel.majorVersion());
        assertSame(0, classModel.flags().flagsMask() & ClassFile.ACC_IDENTITY);
        var loaded = ByteCodeLoader.load("Test", bytes);
        MethodHandles.lookup().ensureInitialized(loaded); // Ensure no format error
    }
}
