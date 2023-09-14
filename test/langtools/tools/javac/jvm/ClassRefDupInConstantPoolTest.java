/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8015927
 * @summary Class reference duplicates in constant pool
 * @modules java.base/jdk.internal.classfile
 *          java.base/jdk.internal.classfile.attribute
 *          java.base/jdk.internal.classfile.constantpool
 *          java.base/jdk.internal.classfile.instruction
 *          java.base/jdk.internal.classfile.components
 * @clean ClassRefDupInConstantPoolTest$Duplicates
 * @run main ClassRefDupInConstantPoolTest
 */

import java.util.TreeSet;

import jdk.internal.classfile.*;
import jdk.internal.classfile.constantpool.*;

public class ClassRefDupInConstantPoolTest {
    public static void main(String[] args) throws Exception {
        ClassModel cls = Classfile.of().parse(ClassRefDupInConstantPoolTest.class.
                                       getResourceAsStream("ClassRefDupInConstantPoolTest$Duplicates.class").readAllBytes());
        ConstantPool pool = cls.constantPool();

        int duplicates = 0;
        TreeSet<String> set = new TreeSet<>();
        for (PoolEntry pe : pool) {
            if (pe instanceof ClassEntry ce) {
                if (!set.add(ce.asInternalName())) {
                    duplicates++;
                    System.out.println("DUPLICATE CLASS REF " + ce.asInternalName());
                }
            }
        }
        if (duplicates > 0)
            throw new Exception("Test Failed");
    }

    class Duplicates {
        String concat(String s1, String s2) {
            return s1 + (s2 == s1 ? " " : s2);
        }
    }
}
