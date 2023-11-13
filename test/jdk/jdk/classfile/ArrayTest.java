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
 * @summary Testing Classfile arrays.
 * @run junit ArrayTest
 */
import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.ClassModel;
import jdk.internal.classfile.CodeElement;
import jdk.internal.classfile.MethodModel;
import jdk.internal.classfile.Opcode;
import jdk.internal.classfile.TypeKind;
import jdk.internal.classfile.instruction.NewMultiArrayInstruction;
import jdk.internal.classfile.instruction.NewPrimitiveArrayInstruction;
import jdk.internal.classfile.instruction.NewReferenceArrayInstruction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

class ArrayTest {
    static final String testClassName = "ArrayTest$TestClass";
    static final Path testClassPath = Paths.get(URI.create(ArrayTest.class.getResource(testClassName + ".class").toString()));


    @Test
    void testArrayNew() throws Exception {
        ClassModel cm = Classfile.of().parse(testClassPath);

        for (MethodModel mm : cm.methods()) {
            mm.code().ifPresent(code -> {
                Iterator<CodeElement> it = code.iterator();
                int arrayCreateCount = 1;
                while (it.hasNext()) {
                    CodeElement im = it.next();
                    if (im instanceof NewReferenceArrayInstruction
                        || im instanceof NewPrimitiveArrayInstruction
                        || im instanceof NewMultiArrayInstruction) {
                        switch (arrayCreateCount++) {
                            case 1: {
                                NewMultiArrayInstruction nai = (NewMultiArrayInstruction) im;
                                assertEquals(nai.opcode(), Opcode.MULTIANEWARRAY);
                                assertEquals(nai.arrayType().asInternalName(), "[[[I");
                                assertEquals(nai.dimensions(), 3);
                                break;
                            }
                            case 2: {
                                NewMultiArrayInstruction nai = (NewMultiArrayInstruction) im;
                                assertEquals(nai.opcode(), Opcode.MULTIANEWARRAY);
                                assertEquals(nai.arrayType().asInternalName(),
                                             "[[[Ljava/lang/String;");
                                assertEquals(nai.dimensions(), 2);
                                break;
                            }
                            case 3: {
                                NewReferenceArrayInstruction nai = (NewReferenceArrayInstruction) im;
                                assertEquals(nai.opcode(), Opcode.ANEWARRAY);
                                assertEquals(nai.componentType().asInternalName(),
                                             "java/lang/String");
                                break;
                            }
                            case 4: {
                                NewPrimitiveArrayInstruction nai = (NewPrimitiveArrayInstruction) im;
                                assertEquals(nai.opcode(), Opcode.NEWARRAY);
                                assertEquals(nai.typeKind(), TypeKind.DoubleType);
                                break;
                            }
                        }
                    }
                }
                if (arrayCreateCount > 1) {
                    assertEquals(arrayCreateCount, 5);
                }
            });
        }
    }

    public static class TestClass {
        public static void makeArrays() {
            int[][][] ma = new int[10][20][30];
            String[][][] pa = new String[10][20][];
            String[] sa = new String[5];
            double[] da = new double[3];
        }
    }
}
