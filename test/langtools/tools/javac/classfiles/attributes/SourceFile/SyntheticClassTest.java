/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary sourcefile attribute test for synthetic class.
 * @bug 8040129
 * @library /tools/lib /tools/javac/lib /test/lib ../lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          java.base/jdk.internal.classfile.impl
 * @build toolbox.ToolBox InMemoryFileManager TestBase SourceFileTestBase
 * @enablePreview
 * @run main SyntheticClassTest
 */

import jdk.test.lib.compiler.CompilerUtils;
import toolbox.ToolBox;

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public class SyntheticClassTest extends SourceFileTestBase {
    public static void main(String[] args) throws Exception {
        String sourceCode = """
                public class SyntheticClass {
                    static class Inner {
                        private Inner() {
                        }
                    }

                    public SyntheticClass() {
                        new Inner();
                    }
                }
                """;
        Path srcDir = Path.of("src");
        Path v10Dir = Path.of("out10");
        Path modernDir = Path.of("out");
        ToolBox toolBox = new ToolBox();
        toolBox.writeJavaFiles(srcDir, sourceCode);
        CompilerUtils.compile(srcDir, v10Dir, "--release", "10");
        CompilerUtils.compile(srcDir, modernDir);
        test(v10Dir, true);
        test(modernDir, false);
    }

    private static void test(Path path, boolean expectSynthetic) throws Exception {
        try {
            new SyntheticClassTest().test(path.resolve("SyntheticClass$1.class"), "SyntheticClass.java");
            if (!expectSynthetic) {
                throw new AssertionError("Synthetic class should not have been emitted!");
            }
        } catch (NoSuchFileException ex) {
            if (expectSynthetic) {
                throw new AssertionError("Synthetic class should have been emitted!");
            }
        }
    }
}
