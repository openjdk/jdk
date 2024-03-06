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
 * @bug 8028504
 * @summary javac generates LocalVariableTable even with -g:none
 * @enablePreview
 * @compile -g:none DontGenerateLVTForGNoneOpTest.java
 * @run main DontGenerateLVTForGNoneOpTest
 */

import java.io.File;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.nio.file.Paths;

import java.lang.classfile.*;
import java.lang.classfile.attribute.CodeAttribute;

public class DontGenerateLVTForGNoneOpTest {

    public static void main(String[] args) throws Exception {
        new DontGenerateLVTForGNoneOpTest().run();
    }

    void run() throws Exception {
        checkClassFile(new File(Paths.get(System.getProperty("test.classes"),
                this.getClass().getName() + ".class").toUri()));
    }

    void checkClassFile(final File cfile) throws Exception {
        ClassModel classFile = ClassFile.of().parse(cfile.toPath());
        for (MethodModel method : classFile.methods()) {
            CodeAttribute code = method.findAttribute(Attributes.CODE).orElse(null);
            if (code != null) {
                if (code.findAttribute(Attributes.LOCAL_VARIABLE_TABLE).orElse(null) != null) {
                    throw new AssertionError("LVT shouldn't be generated for g:none");
                }
            }
        }
    }

    public void bar() {
        try {
            System.out.println();
        } catch(@TA Exception e) {
        } catch(Throwable t) {}
    }

    @Target(ElementType.TYPE_USE)
    @interface TA {}
}
