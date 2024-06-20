/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8022186 8271254
 * @summary javac generates dead code if a try with an empty body has a finalizer
 * @enablePreview
 * @modules java.base/jdk.internal.classfile.impl
 *          jdk.compiler/com.sun.tools.javac.util
 */

import java.lang.classfile.*;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.constantpool.*;
import java.lang.classfile.instruction.InvokeInstruction;
import com.sun.tools.javac.util.Assert;
import java.io.BufferedInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DeadCodeGeneratedForEmptyTryTest {

    public static void main(String[] args) throws Exception {
        new DeadCodeGeneratedForEmptyTryTest().run();
    }

    void run() throws Exception {
        for (int i = 1; i <= 8; i++) {
            checkClassFile(Paths.get(System.getProperty("test.classes"),
                    this.getClass().getName() + "$Test" + i + ".class"));
        }
    }

    int utf8Index;
    int numberOfRefToStr = 0;
    ConstantPool constantPool;

    void checkClassFile(final Path path) throws Exception {
        numberOfRefToStr = 0;
        ClassModel classFile = ClassFile.of().parse(
                new BufferedInputStream(Files.newInputStream(path)).readAllBytes());
        constantPool = classFile.constantPool();
        for (MethodModel method: classFile.methods()) {
            if (method.methodName().equalsString("methodToLookFor")) {
                CodeAttribute codeAtt = method.findAttribute(Attributes.code()).orElseThrow();
                codeAtt.elementList().stream()
                        .filter(ce -> ce instanceof Instruction)
                        .forEach(ins -> checkIndirectRefToString((Instruction) ins));
            }
        }
        Assert.check(numberOfRefToStr == 1,
                "There should only be one reference to a CONSTANT_String_info structure in the generated code");
    }
    void checkIndirectRefToString(Instruction instruction) {
        if (instruction instanceof InvokeInstruction invokeInstruction) {
            MemberRefEntry refEntry = invokeInstruction.method();
            if (constantPool.entryByIndex(refEntry.type().index()) instanceof Utf8Entry) {
                numberOfRefToStr++;
            }
        }
    }

    public class Test1 {
        void methodToLookFor() {
            try {
                // empty intentionally
            } finally {
                System.out.println("STR_TO_LOOK_FOR");
            }
        }
    }

    public class Test2 {
        void methodToLookFor() {
            try {
                // empty intentionally
            } catch (Exception e) {
                System.out.println("EXCEPTION");
            } finally {
                System.out.println("STR_TO_LOOK_FOR");
            }
        }
    }

    public class Test3 {
        void methodToLookFor() {
            try {
                ;  // skip statement intentionally
            } finally {
                System.out.println("STR_TO_LOOK_FOR");
            }
        }
    }

    public class Test4 {
        void methodToLookFor() {
            try {
                ;  // skip statement intentionally
            } catch (Exception e) {
                System.out.println("EXCEPTION");
            } finally {
                System.out.println("STR_TO_LOOK_FOR");
            }
        }
    }

    public class Test5 {
        void methodToLookFor() {
            try {
                // empty try statement
                try { } finally { }
            } finally {
                System.out.println("STR_TO_LOOK_FOR");
            }
        }
    }

    public class Test6 {
        void methodToLookFor() {
            try {
                // empty try statement
                try { } catch (Exception e) { } finally { }
            } catch (Exception e) {
                System.out.println("EXCEPTION");
            } finally {
                System.out.println("STR_TO_LOOK_FOR");
            }
        }
    }

    public class Test7 {
        void methodToLookFor() {
            try {
                // empty try statement with skip statement
                try { ; } finally { }
            } finally {
                System.out.println("STR_TO_LOOK_FOR");
            }
        }
    }

    public class Test8 {
        void methodToLookFor() {
            try {
                // empty try statement with skip statement
                try { ; } catch (Exception e) { } finally { }
            } catch (Exception e) {
                System.out.println("EXCEPTION");
            } finally {
                System.out.println("STR_TO_LOOK_FOR");
            }
        }
    }
}
