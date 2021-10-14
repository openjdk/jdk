/*
 * Copyright (c) 2021, Google LLC. All rights reserved.
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

import com.sun.tools.classfile.*;
import com.sun.tools.classfile.ConstantPool.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/*
 * @test
 * @bug     8273914
 * @summary Indy string concat changes order of operations
 * @modules jdk.jdeps/com.sun.tools.classfile
 *
 * @clean *
 * @compile -XDstringConcat=indy              WellKnownTypeSignatures.java
 * @run main WellKnownTypeSignatures
 *
 * @clean *
 * @compile -XDstringConcat=indyWithConstants WellKnownTypeSignatures.java
 * @run main WellKnownTypeSignatures
 */

public class WellKnownTypeSignatures {
    static List<String> actualTypes;

    static int idx = 0;

    static boolean z = true;
    static char c = (char) 42;
    static short s = (short) 42;
    static byte b = (byte) 42;
    static int i = 42;
    static long l = 42L;
    static float f = 42.0f;
    static double d = 42.0;

    public static void main(String[] argv) throws Exception {
        readIndyTypes();

        test("" + WellKnownTypeSignatures.class, idx++, "(Ljava/lang/String;)Ljava/lang/String;");
        test("" + Boolean.valueOf(z), idx++, "(Ljava/lang/Boolean;)Ljava/lang/String;");
        test("" + Character.valueOf(c), idx++, "(Ljava/lang/Character;)Ljava/lang/String;");
        test("" + Byte.valueOf(b), idx++, "(Ljava/lang/Byte;)Ljava/lang/String;");
        test("" + Short.valueOf(s), idx++, "(Ljava/lang/Short;)Ljava/lang/String;");
        test("" + Integer.valueOf(i), idx++, "(Ljava/lang/Integer;)Ljava/lang/String;");
        test("" + Long.valueOf(l), idx++, "(Ljava/lang/Long;)Ljava/lang/String;");
        test("" + Double.valueOf(d), idx++, "(Ljava/lang/Double;)Ljava/lang/String;");
        test("" + Float.valueOf(f), idx++, "(Ljava/lang/Float;)Ljava/lang/String;");
        test("" + z, idx++, "(Z)Ljava/lang/String;");
        test("" + c, idx++, "(C)Ljava/lang/String;");
        test("" + b, idx++, "(B)Ljava/lang/String;");
        test("" + s, idx++, "(S)Ljava/lang/String;");
        test("" + i, idx++, "(I)Ljava/lang/String;");
        test("" + l, idx++, "(J)Ljava/lang/String;");
        test("" + d, idx++, "(D)Ljava/lang/String;");
        test("" + f, idx++, "(F)Ljava/lang/String;");
    }

    public static void test(String actual, int index, String expectedType) {
        String actualType = actualTypes.get(index);
        if (!actualType.equals(expectedType)) {
            throw new IllegalStateException(
                    index
                            + " Unexpected type: expected = "
                            + expectedType
                            + ", actual = "
                            + actualType);
        }
    }

    public static void readIndyTypes() throws Exception {
        actualTypes = new ArrayList<String>();

        ClassFile classFile =
                ClassFile.read(
                        new File(
                                System.getProperty("test.classes", "."),
                                WellKnownTypeSignatures.class.getName() + ".class"));
        ConstantPool constantPool = classFile.constant_pool;

        for (Method method : classFile.methods) {
            if (method.getName(constantPool).equals("main")) {
                Code_attribute code = (Code_attribute) method.attributes.get(Attribute.Code);
                for (Instruction i : code.getInstructions()) {
                    if (i.getOpcode() == Opcode.INVOKEDYNAMIC) {
                        CONSTANT_InvokeDynamic_info indyInfo =
                                (CONSTANT_InvokeDynamic_info)
                                        constantPool.get(i.getUnsignedShort(1));
                        CONSTANT_NameAndType_info natInfo = indyInfo.getNameAndTypeInfo();
                        actualTypes.add(natInfo.getType());
                    }
                }
            }
        }
    }
}
