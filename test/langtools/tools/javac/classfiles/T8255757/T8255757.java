/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8255757
 * @summary Javac shouldn't emit duplicate pool entries on array::clone
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          java.base/jdk.internal.classfile
 *          java.base/jdk.internal.classfile.attribute
 *          java.base/jdk.internal.classfile.constantpool
 *          java.base/jdk.internal.classfile.instruction
 *          java.base/jdk.internal.classfile.components
 *          java.base/jdk.internal.classfile.impl
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main T8255757
 */

import java.nio.file.Path;

import jdk.internal.classfile.*;
import jdk.internal.classfile.constantpool.*;

import toolbox.JavacTask;
import toolbox.ToolBox;
import toolbox.TestRunner;

public class T8255757 extends TestRunner {
    ToolBox tb;

    T8255757() {
        super(System.err);
        tb = new ToolBox();
    }

    public static void main(String[] args) throws Exception {
        T8255757 t = new T8255757();
        t.runTests();
    }

    @Test
    public void testDuplicatePoolEntries() throws Exception {
        String code = """
                public class Test {
                    void test(Object[] o) {
                        o.clone();
                        o.clone();
                    }
                    void test2(Object[] o) {
                        o.clone();
                        o.clone();
                    }
                }""";
        Path curPath = Path.of(".");
        new JavacTask(tb)
                .sources(code)
                .outdir(curPath)
                .run();

        ClassModel cf = Classfile.of().parse(curPath.resolve("Test.class"));
        int num = 0;
        for (PoolEntry pe : cf.constantPool()) {
            if (pe instanceof MethodRefEntry methodRefEntry) {
                String class_name = methodRefEntry.owner().asInternalName();
                String method_name = methodRefEntry.name().stringValue();
                String method_type = methodRefEntry.type().stringValue();
                if ("[Ljava/lang/Object;".equals(class_name) &&
                        "clone".equals(method_name) &&
                        "()Ljava/lang/Object;".equals(method_type)) {
                    ++num;
                }
            }
        }
        if (num != 1) {
            throw new AssertionError("The number of the pool entries on array::clone is not right. " +
                    "Expected number: 1, actual number: " + num);
        }
    }
}
