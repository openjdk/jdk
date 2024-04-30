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
 * @bug 8263642 8268885
 * @summary javac should not emit duplicate checkcast for first bound of intersection type in cast
 *          duplicate checkcast when destination type is not first type of intersection type
 * @library /tools/lib
 * @enablePreview
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          java.base/jdk.internal.classfile.impl
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main DuplicatedCheckcastTest
 */

import java.nio.file.Path;
import java.util.ArrayList;

import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.instruction.*;

import toolbox.JavacTask;
import toolbox.TestRunner;
import toolbox.ToolBox;

public class DuplicatedCheckcastTest extends TestRunner {
    ToolBox tb;
    ClassModel cf;

    public DuplicatedCheckcastTest() {
        super(System.err);
        tb = new ToolBox();
    }

    public static void main(String[] args) throws Exception {
        DuplicatedCheckcastTest t = new DuplicatedCheckcastTest();
        t.runTests();
    }

    static final String Code1 =
            """
            class IntersectionTypeTest {
                interface I1 { }
                static class C1 { }
                static Object test(Object o) {
                    return (C1 & I1) o;
                }
            }
            """;

    static final String Code2 =
            """
            class IntersectionTypeTest {
                interface I1 {}
                interface I2 {}
                static class C {}
                void test() {
                    I2 i = (I1 & I2) new C();
                }
            }
            """;

    @Test
    public void testDuplicatedCheckcast() throws Exception {
        duplicateCheckCastHelper(Code1, "IntersectionTypeTest$I1", "IntersectionTypeTest$C1");
        duplicateCheckCastHelper(Code2, "IntersectionTypeTest$I1", "IntersectionTypeTest$I2");
    }

    private void duplicateCheckCastHelper(String source, String expected1, String expected2) throws Exception {
        Path curPath = Path.of(".");
        new JavacTask(tb)
                .sources(source)
                .outdir(curPath)
                .run();
        cf = ClassFile.of().parse(curPath.resolve("IntersectionTypeTest.class"));
        ArrayList<Instruction> checkCastList = new ArrayList<>();
        for (MethodModel method : cf.methods()) {
            if (method.methodName().equalsString("test")) {
                CodeAttribute code_attribute = method.findAttribute(Attributes.CODE).orElseThrow();
                for (CodeElement ce : code_attribute.elementList()) {
                    if (ce instanceof Instruction instruction && Opcode.CHECKCAST == instruction.opcode()) {
                        checkCastList.add(instruction);
                    }
                }
            }
        }
        if (checkCastList.size() != 2) {
            throw new AssertionError("The number of the instruction 'checkcast' is not right. " +
                    "Expected number: 2, actual number: " + checkCastList.size());
        }
        checkClassName(checkCastList.get(0), expected1);
        checkClassName(checkCastList.get(1), expected2);
    }

    public void checkClassName(Instruction ins, String expected) {
        String className = "";
        if (ins instanceof TypeCheckInstruction typeCheckInstruction) {
            ClassEntry classInfo = typeCheckInstruction.type();
            className = classInfo.asInternalName();
        }
        if (!expected.equals(className)) {
            throw new AssertionError("The type of the 'checkcast' is not right. Expected: " +
                    expected + ", actual: " + className);
        }
    }
}
