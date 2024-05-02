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

import java.lang.classfile.*;
import java.lang.classfile.instruction.InvokeInstruction;
import static java.lang.constant.ConstantDescs.INIT_NAME;
import static java.lang.classfile.ClassFile.*;

public class ClassToInterfaceConverter implements ClassFilePreprocessor {

    private final String whichClass;

    public ClassToInterfaceConverter(String className) {
        this.whichClass = className;
    }

    private byte[] convertToInterface(ClassModel classModel) {
        //  Convert method tag. Find Methodref which is only invoked by other methods
        //  in the interface, convert it to InterfaceMethodref.  If opcode is invokevirtual,
        //  convert it to invokeinterface
        CodeTransform ct = (b, e) -> {
            if (e instanceof InvokeInstruction i && i.owner() == classModel.thisClass()) {
                Opcode opcode = i.opcode() == Opcode.INVOKEVIRTUAL ? Opcode.INVOKEINTERFACE : i.opcode();
                b.invoke(opcode, i.owner().asSymbol(),
                        i.name().stringValue(), i.typeSymbol(), true);
            } else {
                b.with(e);
            }
        };

        return ClassFile.of().transform(classModel,
            ClassTransform.dropping(ce -> ce instanceof MethodModel mm && mm.methodName().equalsString(INIT_NAME))
                          .andThen(ClassTransform.transformingMethodBodies(ct))
                          .andThen(ClassTransform.endHandler(b -> b.withFlags(ACC_INTERFACE | ACC_ABSTRACT | ACC_PUBLIC)))
        );
    }

    public byte[] preprocess(String classname, byte[] bytes) {
        ClassModel classModel = ClassFile.of().parse(bytes);
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
