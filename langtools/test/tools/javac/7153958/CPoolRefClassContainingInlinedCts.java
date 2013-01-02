/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * @bug 7153958
 * @summary add constant pool reference to class containing inlined constants
 * @compile pkg/ClassToBeStaticallyImported.java CPoolRefClassContainingInlinedCts.java
 * @run main CPoolRefClassContainingInlinedCts
 */

import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.ConstantPool.CONSTANT_Class_info;
import com.sun.tools.classfile.ConstantPool.CPInfo;
import com.sun.tools.classfile.ConstantPoolException;
import java.io.File;
import java.io.IOException;

import static pkg.ClassToBeStaticallyImported.staticField;

public class CPoolRefClassContainingInlinedCts {

    public static void main(String args[]) throws Exception {
        new CPoolRefClassContainingInlinedCts().run();
    }

    void run() throws Exception {
        checkReferences();
    }

    int numberOfReferencedClassesToBeChecked = 0;

    void checkClassName(String className) {
        switch (className) {
            case "SimpleAssignClass" : case "BinaryExpClass":
            case "UnaryExpClass" : case "CastClass":
            case "ParensClass" : case "CondClass":
            case "IfClass" : case "pkg/ClassToBeStaticallyImported":
                numberOfReferencedClassesToBeChecked++;
        }
    }

    void checkReferences() throws IOException, ConstantPoolException {
        File testClasses = new File(System.getProperty("test.classes"));
        File file = new File(testClasses,
                CPoolRefClassContainingInlinedCts.class.getName() + ".class");
        ClassFile classFile = ClassFile.read(file);
        int i = 1;
        CPInfo cpInfo;
        while (i < classFile.constant_pool.size()) {
            cpInfo = classFile.constant_pool.get(i);
            if (cpInfo instanceof CONSTANT_Class_info) {
                checkClassName(((CONSTANT_Class_info)cpInfo).getName());
            }
            i += cpInfo.size();
        }
        if (numberOfReferencedClassesToBeChecked != 8) {
            throw new AssertionError("Class reference missing in the constant pool");
        }
    }

    private int assign = SimpleAssignClass.x;
    private int binary = BinaryExpClass.x + 1;
    private int unary = -UnaryExpClass.x;
    private int cast = (int)CastClass.x;
    private int parens = (ParensClass.x);
    private int cond = (CondClass.x == 1) ? 1 : 2;
    private static int ifConstant;
    private static int importStatic;
    static {
        if (IfClass.x == 1) {
            ifConstant = 1;
        } else {
            ifConstant = 2;
        }
    }
    static {
        if (staticField == 1) {
            importStatic = 1;
        } else {
            importStatic = 2;
        }
    }
}

class SimpleAssignClass {
    public static final int x = 1;
}

class BinaryExpClass {
    public static final int x = 1;
}

class UnaryExpClass {
    public static final int x = 1;
}

class CastClass {
    public static final int x = 1;
}

class ParensClass {
    public static final int x = 1;
}

class CondClass {
    public static final int x = 1;
}

class IfClass {
    public static final int x = 1;
}
