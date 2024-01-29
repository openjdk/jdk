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
 * @summary Testing ClassFile bootstrap methods.
 * @run junit BSMTest
 */
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import java.lang.classfile.*;
import helpers.ByteArrayClassLoader;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.constantpool.ConstantDynamicEntry;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.LoadableConstantEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.MethodHandleEntry;
import org.junit.jupiter.api.Test;

import static java.lang.constant.ConstantDescs.CD_String;
import static org.junit.jupiter.api.Assertions.*;

public class BSMTest {
    static final String testClassName = "BSMTest$SomeClass";
    static final Path testClassPath = Paths.get(URI.create(ArrayTest.class.getResource(testClassName + ".class").toString()));
    private static final String THIRTEEN = "BlahBlahBlahBlahBlahBlahBlahBlahBlahBlahBlahBlahBlah";
    private static final String SEVEN = "BlahBlahBlahBlahBlahBlahBlah";
    private static final String TWENTY = "BlahBlahBlahBlahBlahBlahBlahBlahBlahBlahBlahBlahBlahBlahBlahBlahBlahBlahBlahBlah";
    private static final String TYPE = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/String;";

    @Test
    void testSevenOfThirteenIterator() throws Exception {
        var cc = ClassFile.of();
        ClassModel cm = cc.parse(testClassPath);
        byte[] newBytes = cc.transform(cm, (cb, ce) -> {
            if (ce instanceof MethodModel mm) {
                cb.transformMethod(mm, (mb, me) -> {
                    if (me instanceof CodeModel xm) {
                        mb.transformCode(xm, (codeB, codeE) -> {
                            switch (codeE) {
                                case ConstantInstruction ci -> {
                                    ConstantPoolBuilder cpb = codeB.constantPool();

                                    List<LoadableConstantEntry> staticArgs = new ArrayList<>(2);
                                    staticArgs.add(cpb.stringEntry(SEVEN));
                                    staticArgs.add(cpb.stringEntry(THIRTEEN));

                                    MemberRefEntry memberRefEntry = cpb.methodRefEntry(ClassDesc.of("BSMTest"), "bootstrap", MethodTypeDesc.ofDescriptor(TYPE));
                                    MethodHandleEntry methodHandleEntry = cpb.methodHandleEntry(6, memberRefEntry);
                                    BootstrapMethodEntry bme = cpb.bsmEntry(methodHandleEntry, staticArgs);
                                    ConstantDynamicEntry cde = cpb.constantDynamicEntry(bme, cpb.nameAndTypeEntry("name", CD_String));

                                    codeB.constantInstruction(Opcode.LDC, cde.constantValue());
                                }
                                default -> codeB.with(codeE);
                            }
                        });
                    }
                    else
                        mb.with(me);
                });
            }
            else
                cb.with(ce);
        });
        String result = (String)
                new ByteArrayClassLoader(BSMTest.class.getClassLoader(), testClassName, newBytes)
                        .getMethod(testClassName, "many")
                        .invoke(null, new Object[0]);
        assertEquals(result, TWENTY);
    }

    public static String bootstrap(MethodHandles.Lookup lookup, String name, Class<?> clz, Object arg1, Object arg2) {
        return (String)arg1 + (String)arg2;
    }

    public static class SomeClass {
        public static String many() {
            String s = "Foo";
            return s;
        }
    }
}