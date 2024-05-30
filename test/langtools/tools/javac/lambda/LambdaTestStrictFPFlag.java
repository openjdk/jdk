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
 * @bug 8046060
 * @summary Different results of floating point multiplication for lambda code block
 * @library /tools/lib /test/lib
 * @enablePreview
 * @run main LambdaTestStrictFPFlag
 */

import jdk.test.lib.compiler.CompilerUtils;
import toolbox.ToolBox;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.nio.file.Path;

public class LambdaTestStrictFPFlag {
    private static final String SOURCE = """
            class Test {
                strictfp void test() {
                    Face itf = () -> { };
                }
            }

            interface Face {
                void m();
            }
            """;

    public static void main(String[] args) throws Exception {
        new LambdaTestStrictFPFlag().run();
    }

    void run() throws Exception {
        Path src = Path.of("src");
        Path out = Path.of("out");

        ToolBox toolBox = new ToolBox();
        toolBox.writeJavaFiles(src, SOURCE);
        CompilerUtils.compile(src, out, "--release", "16");

        ClassModel cm = ClassFile.of().parse(out.resolve("Test.class"));
        boolean found = false;
        for (MethodModel meth: cm.methods()) {
            if (meth.methodName().stringValue().startsWith("lambda$")) {
                if ((meth.flags().flagsMask() & ClassFile.ACC_STRICT) == 0){
                    throw new Exception("strict flag missing from lambda");
                }
                found = true;
            }
        }
        if (!found) {
            throw new Exception("did not find lambda method");
        }
    }
}
