/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8009170
 * @summary Regression: javac generates redundant bytecode in assignop involving
 * arrays
 * @enablePreview
 * @run main RedundantByteCodeInArrayTest
 */

import java.lang.classfile.*;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.constantpool.ConstantPool;
import java.io.File;
import java.io.IOException;

public class RedundantByteCodeInArrayTest {
    public static void main(String[] args)
            throws IOException {
        new RedundantByteCodeInArrayTest()
                .checkClassFile(new File(System.getProperty("test.classes", "."),
                    RedundantByteCodeInArrayTest.class.getName() + ".class"));
    }

    void arrMethod(int[] array, int p, int inc) {
        array[p] += inc;
    }

    void checkClassFile(File file)
            throws IOException {
        ClassModel classFile = ClassFile.of().parse(file.toPath());
        ConstantPool constantPool = classFile.constantPool();

        //lets get all the methods in the class file.
        for (MethodModel method : classFile.methods()) {
            if (method.methodName().equalsString("arrMethod")) {
                CodeAttribute code = method.findAttribute(Attributes.CODE).orElse(null);
                assert code != null;
                if (code.maxLocals() > 4)
                    throw new AssertionError("Too many locals for method arrMethod");
            }
        }
    }
}