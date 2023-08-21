/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8268748
 * @summary Javac generates error opcodes when using nest pattern variables
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          java.base/jdk.internal.classfile
 *          java.base/jdk.internal.classfile.attribute
 *          java.base/jdk.internal.classfile.constantpool
 *          java.base/jdk.internal.classfile.instruction
 *          java.base/jdk.internal.classfile.components
 *          java.base/jdk.internal.classfile.impl
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main NestedPatternVariablesBytecode
 */

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.StreamSupport;

import jdk.internal.classfile.*;
import jdk.internal.classfile.attribute.CodeAttribute;

import toolbox.JavacTask;
import toolbox.TestRunner;
import toolbox.ToolBox;

public class NestedPatternVariablesBytecode extends TestRunner {
    private static final String JAVA_VERSION = System.getProperty("java.specification.version");
    private static final String TEST_METHOD = "test";

    ToolBox tb;
    ClassModel cf;

    public NestedPatternVariablesBytecode() {
        super(System.err);
        tb = new ToolBox();
    }

    public static void main(String[] args) throws Exception {
        NestedPatternVariablesBytecode t = new NestedPatternVariablesBytecode();
        t.runTests();
    }

    @Test
    public void testNestedPatternVariablesBytecode() throws Exception {
        String code = """
                class NestedPatterVariablesTest {
                    String test(Object o) {
                        if (o instanceof CharSequence cs && cs instanceof String s) {
                            return s;
                        }
                        return null;
                    }
                }""";
        Path curPath = Path.of(".");
        new JavacTask(tb)
                .sources(code)
                .outdir(curPath)
                .run();

        cf = Classfile.of().parse(curPath.resolve("NestedPatterVariablesTest.class"));
        MethodModel testMethod = cf.methods().stream()
                                  .filter(this::isTestMethod)
                                  .findAny()
                                  .orElseThrow();
        CodeAttribute code_attribute = testMethod.findAttribute(Attributes.CODE).orElseThrow();

        List<String> actualCode = getCodeInstructions(code_attribute);
        List<String> expectedCode = Arrays.asList(
                "ALOAD_1", "INSTANCEOF", "IFEQ", "ALOAD_1", "CHECKCAST", "ASTORE_2", "ALOAD_2", "INSTANCEOF",
                "IFEQ", "ALOAD_2", "CHECKCAST", "ASTORE_3", "ALOAD_3", "ARETURN", "ACONST_NULL", "ARETURN");
        tb.checkEqual(expectedCode, actualCode);
    }

    boolean isTestMethod(MethodModel m) {
        return m.methodName().equalsString(TEST_METHOD);
    }

    List<String> getCodeInstructions(CodeAttribute code) {
        return code.elementList().stream()
                .filter(ce -> ce instanceof Instruction)
                .map(ins -> ((Instruction) ins).opcode().name())
                .toList();
    }
}
