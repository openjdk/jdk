/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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

package separate;

import jdk.internal.classfile.*;
import jdk.internal.classfile.instruction.InvokeInstruction;
import static jdk.internal.classfile.Opcode.*;

public class ClassToInterfaceConverter implements ClassFilePreprocessor {

    private final String whichClass;

    public ClassToInterfaceConverter(String className) {
        this.whichClass = className;
    }

    private byte[] convertToInterface(ClassModel classModel) {
        return Classfile.of().build(classModel.thisClass().asSymbol(),
                classBuilder ->  {
                    for (ClassElement ce : classModel) {
                        if (ce instanceof AccessFlags accessFlags) {
                            classBuilder.withFlags(0x0601); // ACC_INTERFACE | ACC_ABSTRACT | ACC_PUBLIC);
                        } else if (ce instanceof MethodModel mm) {
                            // Find <init> method and delete it
                            if (mm.methodName().stringValue().equals("<init>")) {
                                continue;
                            }
                            //  Convert method tag. Find Methodref, which is not "<init>" and only invoked
                            //  by other methods in the interface, convert it to InterfaceMethodref and
                            //  if opcode is invokevirtual, convert it to invokeinterface
                            classBuilder.withMethod(mm.methodName().stringValue(),
                                    mm.methodTypeSymbol(),
                                    mm.flags().flagsMask(),
                                    methodBuilder -> {
                                        for (MethodElement me : mm) {
                                            if (me instanceof CodeModel xm) {
                                                methodBuilder.withCode(codeBuilder -> {
                                                    for (CodeElement e : xm) {
                                                        if (e instanceof InvokeInstruction i && i.owner() == classModel.thisClass()) {
                                                            Opcode opcode = i.opcode() == INVOKEVIRTUAL ? INVOKEINTERFACE : i.opcode();
                                                            codeBuilder.invokeInstruction(opcode, i.owner().asSymbol(),
                                                                    i.name().stringValue(), i.typeSymbol(), true);
                                                        } else {
                                                            codeBuilder.with(e);
                                                        }
                                                    }});
                                            } else {
                                                methodBuilder.with(me);
                                            }
                                        }
                                    });
                        } else {
                            classBuilder.with(ce);
                        }
                    }
                });
    }

    public byte[] preprocess(String classname, byte[] bytes) {
        ClassModel classModel = Classfile.of().parse(bytes);
        if (classModel.thisClass().asInternalName().equals(whichClass)) {
            return convertToInterface(classModel);
        } else {
            return bytes; // unmodified
        }
    }

/*
    public static void main(String argv[]) throws Exception {
        File input = new File(argv[0]);
        byte[] buffer = new byte[(int)input.length()];
        new FileInputStream(input).read(buffer);

        ClassFilePreprocessor cfp = new ClassToInterfaceConverter("Hello");
        byte[] cf = cfp.preprocess(argv[0], buffer);
        new FileOutputStream(argv[0] + ".mod").write(cf);
    }
*/
}
