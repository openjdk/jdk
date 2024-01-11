/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8237528
 * @summary Verify there are no unnecessary checkcasts and conditions generated
 *          for the pattern matching in instanceof.
 * @enablePreview
 * @modules java.base/jdk.internal.classfile.impl
 * @compile NoUnnecessaryCast.java
 * @run main NoUnnecessaryCast
 */

import java.lang.classfile.*;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.constantpool.ConstantPool;
import java.io.File;
import java.io.IOException;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class NoUnnecessaryCast {
    public static void main(String[] args) throws IOException {
        new NoUnnecessaryCast()
                .checkClassFile(new File(System.getProperty("test.classes", "."),
                    NoUnnecessaryCast.class.getName() + ".class"));
    }

    void checkClassFile(File file) throws IOException {
        ClassModel classFile = ClassFile.of().parse(file.toPath());

        MethodModel method = classFile.methods().stream()
                              .filter(m -> getName(m).equals("test"))
                              .findAny()
                              .get();
        String expectedInstructions = """
                                      ALOAD_1
                                      INSTANCEOF
                                      IFEQ
                                      ALOAD_1
                                      CHECKCAST
                                      ASTORE_2
                                      ALOAD_2
                                      INVOKEVIRTUAL
                                      IFEQ
                                      ICONST_1
                                      GOTO
                                      ICONST_0
                                      IRETURN
                                      """;
        CodeAttribute code = method.findAttribute(Attributes.CODE).orElseThrow();
        String actualInstructions = printCode(code);
        if (!expectedInstructions.equals(actualInstructions)) {
            throw new AssertionError("Unexpected instructions found:\n" +
                                     actualInstructions);
        }
    }

    String printCode(CodeAttribute code) {
        return code.elementList().stream()
                            .filter(e -> e instanceof Instruction)
                            .map(ins -> ((Instruction) ins).opcode().name())
                            .collect(Collectors.joining("\n", "", "\n"));
    }

    String getName(MethodModel m) {
        return m.methodName().stringValue();
    }

    boolean test(Object o) {
        return o instanceof String s && s.isEmpty();
    }
}
