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

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.ConstantDescs.*;

/*
 * @test TestMultiANewArray
 * @enablePreview
 * @bug 8038076
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @compile -XDignore.symbol.file TestMultiANewArray.java
 * @run main/othervm --enable-preview TestMultiANewArray 49
 * @run main/othervm --enable-preview TestMultiANewArray 50
 * @run main/othervm --enable-preview TestMultiANewArray 51
 * @run main/othervm --enable-preview TestMultiANewArray 52
 */

public class TestMultiANewArray {
    public static void main(String... args) throws Exception {
        int cfv = Integer.parseInt(args[0]);
        writeClassFile(cfv);
        System.err.println("Running with cfv: " + cfv);
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder("-cp", ".", "ClassFile");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("VerifyError");
        output.shouldHaveExitValue(1);
    }

    public static void writeClassFile(int cfv) throws Exception {
        byte[] bytes;

        bytes = ClassFile.of().build(ClassDesc.of("ClassFile"),
                clb -> clb
                        .withVersion(cfv, 0)
                        .withSuperclass(CD_Object)
                        .withFlags(ACC_PUBLIC | ACC_SUPER)
                        .withMethodBody(INIT_NAME, MTD_void, ACC_PUBLIC,
                                cob -> cob
                                        .aload(0)
                                        .invokespecial(CD_Object, INIT_NAME, MTD_void)
                                        .return_())
                        .withMethodBody("main", MethodTypeDesc.of(CD_void, CD_String.arrayType()), ACC_PUBLIC | ACC_STATIC,
                                cob -> cob
                                        .iconst_1()
                                        .iconst_2()
                                        .multianewarray(CD_int.arrayType(), 2)
                                        .astore(1)
                                        .return_()
                        )
        );

        try (FileOutputStream fos = new FileOutputStream(new File("ClassFile.class"))) {
            fos.write(bytes);
        }
    }
}
