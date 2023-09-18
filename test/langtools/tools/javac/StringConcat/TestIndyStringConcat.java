/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.classfile.*;
import jdk.internal.classfile.attribute.*;
import jdk.internal.classfile.constantpool.InvokeDynamicEntry;
import jdk.internal.classfile.constantpool.MethodHandleEntry;
import jdk.internal.classfile.instruction.InvokeDynamicInstruction;

import java.io.File;

/*
 * @test
 * @bug     8148483 8151516 8151223
 * @summary Test that StringConcat is working for JDK >= 9
 * @modules java.base/jdk.internal.classfile
 *          java.base/jdk.internal.classfile.attribute
 *          java.base/jdk.internal.classfile.constantpool
 *          java.base/jdk.internal.classfile.instruction
 *          java.base/jdk.internal.classfile.components
 *          java.base/jdk.internal.classfile.impl
 *
 * @clean *
 * @compile -source 8 -target 8 TestIndyStringConcat.java
 * @run main TestIndyStringConcat false
 *
 * @clean *
 * @compile -XDstringConcat=inline TestIndyStringConcat.java
 * @run main TestIndyStringConcat false
 *
 * @clean *
 * @compile -XDstringConcat=indy TestIndyStringConcat.java
 * @run main TestIndyStringConcat true
 *
 * @clean *
 * @compile -XDstringConcat=indyWithConstants TestIndyStringConcat.java
 * @run main TestIndyStringConcat true
 */
public class TestIndyStringConcat {

    static String other;

    public static String test() {
        return "Foo" + other;
    }

    public static void main(String[] args) throws Exception {
        boolean expected = Boolean.parseBoolean(args[0]);
        boolean actual = hasStringConcatFactoryCall("test");
        if (expected != actual) {
            throw new AssertionError("expected = " + expected + ", actual = " + actual);
        }
    }

    public static boolean hasStringConcatFactoryCall(String methodName) throws Exception {
        ClassModel classFile = Classfile.of().parse(new File(System.getProperty("test.classes", "."),
                TestIndyStringConcat.class.getName() + ".class").toPath());

        for (MethodModel method : classFile.methods()) {
            if (method.methodName().equalsString(methodName)) {
                CodeAttribute code = method.findAttribute(Attributes.CODE).orElseThrow();
                for (CodeElement i : code.elementList()) {
                    if (i instanceof InvokeDynamicInstruction) {
                        InvokeDynamicInstruction indy = (InvokeDynamicInstruction) i;
                        BootstrapMethodEntry bsmSpec = indy.invokedynamic().bootstrap();
                        MethodHandleEntry bsmInfo = bsmSpec.bootstrapMethod();
                        if (bsmInfo.reference().owner().asInternalName().equals("java/lang/invoke/StringConcatFactory")) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

}
