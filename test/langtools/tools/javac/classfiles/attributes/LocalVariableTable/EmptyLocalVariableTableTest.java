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

/*
 * @test
 * @bug 8389868
 * @summary Test that empty LocalVariableTable attribute is not generated.
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run junit ${test.main.class}
 */

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.CodeAttribute;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import toolbox.JavacTask;
import toolbox.ToolBox;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

public class EmptyLocalVariableTableTest {

    Path base;
    ToolBox tb = new ToolBox();

    @Test
    void testNoEmptyLVT() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        new JavacTask(tb)
                .options("-d", classes.toString(), "-g")
                .sources("""
                         public class UnusedVariable {
                             static void test() {
                                 {
                                     Class<?> unused = null;
                                 }
                             }
                             static {
                                 Class<?> unused = null;
                             }
                         }
                         """)
                .run()
                .writeAll();
        ClassFile.of().parse(classes.resolve("UnusedVariable.class")).methods().stream()
                .forEach(mm -> {
                    CodeAttribute codeAttribute = mm.findAttribute(Attributes.code()).orElse(null);
                    Assertions.assertNotNull(codeAttribute);
                    codeAttribute.findAttributes(Attributes.localVariableTable()).stream()
                            .forEach(attr -> {
                                Assertions.assertFalse(attr.localVariables().isEmpty(), "Empty LocalVariableTableAttribute found");
                            });
                });
    }

    @BeforeEach
    public void setUp(TestInfo info) {
        base = Paths.get(".")
                    .resolve(info.getTestMethod()
                                 .orElseThrow()
                                 .getName());
    }
}
