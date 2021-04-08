/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8263642
 * @summary javac should not emit duplicate checkcast for first bound of intersection type in cast
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jdeps/com.sun.tools.classfile
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main DuplicatedCheckcastTest
 */

import java.nio.file.Path;

import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.Method;
import com.sun.tools.classfile.Attribute;
import com.sun.tools.classfile.Code_attribute;
import com.sun.tools.classfile.Instruction;

import toolbox.JavacTask;
import toolbox.TestRunner;
import toolbox.ToolBox;

public class DuplicatedCheckcastTest extends TestRunner {
    ToolBox tb;

    public DuplicatedCheckcastTest() {
        super(System.err);
        tb = new ToolBox();
    }

    public static void main(String[] args) throws Exception {
        DuplicatedCheckcastTest t = new DuplicatedCheckcastTest();
        t.runTests();
    }

    @Test
    public void testDuplicatedCheckcast() throws Exception {
        String code = """
                class IntersectionTypeTest {
                    interface I1 { }
                    static class C1 { }
                    static Object test(Object o) {
                        return (C1 & I1) o;
                    }
                }""";
        Path curPath = Path.of(".");
        new JavacTask(tb)
                .sources(code)
                .outdir(curPath)
                .run();
        ClassFile cf = ClassFile.read(curPath.resolve("IntersectionTypeTest.class"));
        int checkcastNumber = 0;
        for (Method method : cf.methods) {
            if ("test".equals(method.getName(cf.constant_pool))) {
                Code_attribute code_attribute = (Code_attribute) method.attributes.get(Attribute.Code);
                for (Instruction instruction : code_attribute.getInstructions()) {
                    if ("checkcast".equals(instruction.getMnemonic())) {
                        checkcastNumber++;
                    }
                }
            }
        }
        if (checkcastNumber != 2) {
            throw new AssertionError("The number of the instruction 'checkcast' is not right. " +
                    "Expected number: 2, actual number: " + checkcastNumber);
        }
    }
}
