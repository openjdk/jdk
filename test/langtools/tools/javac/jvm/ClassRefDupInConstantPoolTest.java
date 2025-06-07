/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.JavacTask toolbox.ToolBox
 * @run main ClassRefDupInConstantPoolTest
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeSet;

import toolbox.JavacTask;
import toolbox.ToolBox;

import java.lang.classfile.*;
import java.lang.classfile.constantpool.*;

public class ClassRefDupInConstantPoolTest {

    private static final String DUPLICATE_REFS_CLASS =
            """
            class Duplicates {
                String concat(String s1, String s2) {
                    return s1 + (s2 == s1 ? " " : s2);
                }
            }""";

    public static void main(String[] args) throws Exception {
        new JavacTask(new ToolBox()).sources(DUPLICATE_REFS_CLASS).run();

        ClassModel cls = ClassFile.of().parse(Files.readAllBytes(Path.of("Duplicates.class")));
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
