/*
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.base/jdk.internal.classfile
 *          java.base/jdk.internal.classfile.attribute
 *          java.base/jdk.internal.classfile.constantpool
 *          java.base/jdk.internal.classfile.instruction
 *          java.base/jdk.internal.classfile.components
 * @compile -source 16 -target 16 LambdaTestStrictFPFlag.java
 * @run main LambdaTestStrictFPFlag
 */

import java.io.*;
import java.net.URL;
import jdk.internal.classfile.*;

public class LambdaTestStrictFPFlag {
    public static void main(String[] args) throws Exception {
        new LambdaTestStrictFPFlag().run();
    }

    void run() throws Exception {
        ClassModel cm = getClassFile("LambdaTestStrictFPFlag$Test.class");
        boolean found = false;
        for (MethodModel meth: cm.methods()) {
            if (meth.methodName().stringValue().startsWith("lambda$")) {
                if ((meth.flags().flagsMask() & Classfile.ACC_STRICT) == 0){
                    throw new Exception("strict flag missing from lambda");
                }
                found = true;
            }
        }
        if (!found) {
            throw new Exception("did not find lambda method");
        }
    }

    ClassModel getClassFile(String name) throws IOException {
        URL url = getClass().getResource(name);
        assert url != null;
        try (InputStream in = url.openStream()) {
            return Classfile.of().parse(in.readAllBytes());
        }
    }

    class Test {
        strictfp void test() {
            Face itf = () -> { };
        }
    }

    interface Face {
        void m();
    }
}
