/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.compiler.CompilerUtils;
import toolbox.ToolBox;

import java.lang.classfile.BootstrapMethodEntry;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.MethodHandleEntry;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.nio.file.Path;

/*
 * @test
 * @bug     8148483 8151516 8151223
 * @summary Test that StringConcat is working for JDK >= 9
 * @library /tools/lib /test/lib
 * @enablePreview
 * @run main TestIndyStringConcat
 */
public class TestIndyStringConcat {

    private static final String SOURCE = """
            public class IndyStringConcat {
                static String other;

                public static String test() {
                    return "Foo" + other;
                }
            }
            """;

    public static void main(String[] args) throws Exception {
        Path src = Path.of("src");
        ToolBox toolBox = new ToolBox();
        toolBox.writeJavaFiles(src, SOURCE);

        int errors = 0;
        errors += test(false, "java8", "-source", "8", "-target", "8");
        errors += test(false, "inline", "-XDstringConcat=inline");
        errors += test(true, "indy", "-XDstringConcat=indy");
        errors += test(true, "indyWithConstants", "-XDstringConcat=indyWithConstants");

        if (errors > 0) {
            throw new AssertionError(errors + " cases failed");
        }
    }

    public static int test(boolean expected, String label, String... args) throws Exception {
        Path src = Path.of("src");
        Path out = Path.of(label);
        CompilerUtils.compile(src, out, args);
        if (hasStringConcatFactoryCall(out.resolve("IndyStringConcat.class"), "test") != expected) {
            System.err.println("Expected " + expected + " failed for case " + label);
            return 1;
        }
        return 0;
    }

    public static boolean hasStringConcatFactoryCall(Path file, String methodName) throws Exception {
        ClassModel classFile = ClassFile.of().parse(file);

        for (MethodModel method : classFile.methods()) {
            if (method.methodName().equalsString(methodName)) {
                CodeModel code = method.code().orElseThrow();
                for (CodeElement i : code.elementList()) {
                    if (i instanceof InvokeDynamicInstruction indy) {
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
