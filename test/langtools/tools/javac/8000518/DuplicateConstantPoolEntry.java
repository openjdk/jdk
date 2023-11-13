/*
 * Copyright (c) 2002, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8000518
 * @summary Javac generates duplicate name_and_type constant pool entry for
 * class BinaryOpValueExp.java
 * @modules java.base/jdk.internal.classfile
 *          java.base/jdk.internal.classfile.attribute
 *          java.base/jdk.internal.classfile.constantpool
 *          java.base/jdk.internal.classfile.instruction
 *          java.base/jdk.internal.classfile.components
 *          java.base/jdk.internal.classfile.impl
 * @run main DuplicateConstantPoolEntry
 */

import com.sun.source.util.JavacTask;
import jdk.internal.classfile.*;
import jdk.internal.classfile.constantpool.ConstantPool;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import jdk.internal.classfile.constantpool.PoolEntry;

/*
 * This bug was reproduced having two classes B and C referenced from a class A
 * class C should be compiled and generated in advance. Later class A and B should
 * be compiled like this: javac A.java B.java
 */

public class DuplicateConstantPoolEntry {

    public static void main(String args[]) throws Exception {
        new DuplicateConstantPoolEntry().run();
    }

    void run() throws Exception {
        generateFilesNeeded();
        checkReference();
    }

    void generateFilesNeeded() throws Exception {

        StringJavaFileObject[] CSource = new StringJavaFileObject[] {
            new StringJavaFileObject("C.java",
                "class C {C(String s) {}}"),
        };

        List<StringJavaFileObject> AandBSource = Arrays.asList(
                new StringJavaFileObject("A.java",
                    "class A {void test() {new B(null);new C(null);}}"),
                new StringJavaFileObject("B.java",
                    "class B {B(String s) {}}")
        );

        final JavaCompiler tool = ToolProvider.getSystemJavaCompiler();
        JavacTask compileC = (JavacTask)tool.getTask(null, null, null, null, null,
                Arrays.asList(CSource));
        if (!compileC.call()) {
            throw new AssertionError("Compilation error while compiling C.java sources");
        }
        JavacTask compileAB = (JavacTask)tool.getTask(null, null, null,
                Arrays.asList("-cp", "."), null, AandBSource);
        if (!compileAB.call()) {
            throw new AssertionError("Compilation error while compiling A and B sources");
        }
    }

    void checkReference() throws IOException {
        File file = new File("A.class");
        ClassModel classFile = Classfile.of().parse(file.toPath());
        ConstantPool constantPool = classFile.constantPool();
        for (PoolEntry pe1 : constantPool) {
            for (PoolEntry pe2 : constantPool) {
                if (pe2.index() > pe1.index() && pe1.equals(pe2)) {
                    throw new AssertionError(
                            "Duplicate entries in the constant pool at positions " +
                            pe1.index() + " and " + pe2.index());
                }
            }
        }
    }

    private static class StringJavaFileObject extends SimpleJavaFileObject {
        StringJavaFileObject(String name, String text) {
            super(URI.create(name), JavaFileObject.Kind.SOURCE);
            this.text = text;
        }
        @Override
        public CharSequence getCharContent(boolean b) {
            return text;
        }
        private String text;
    }
}
