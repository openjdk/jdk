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

import java.io.File;
import java.io.FileOutputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.ConstantDescs.*;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

/*
 * @test OverriderMsg
 * @bug 8026894
 * @enablePreview
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @compile -XDignore.symbol.file OverriderMsg.java
 * @run main/othervm --enable-preview OverriderMsg
 */

// This test checks that the super class name is included in the message when
// a method is detected overriding a final method in its super class.  The
// ClassFile part of the test creates these two classes:
//
//     public class HasFinal {
//         public final void m(String s) { }
//     }
//
//     public class Overrider extends HasFinal {
//         public void m(String s) { }
//         public static void main(String[] args) { }
//     }
//
public class OverriderMsg {

    public static void dump_HasFinal () throws Exception {

        byte[] bytes;

        bytes = ClassFile.of().build(ClassDesc.of("HasFinal"),
                    clb -> clb
                            .withVersion(JAVA_7_VERSION, 0)
                            .withFlags(ACC_PUBLIC | ACC_SUPER)
                            .withSuperclass(CD_Object)

                            .withMethodBody(INIT_NAME, MTD_void, ACC_PUBLIC,
                                    cob -> cob
                                            .aload(0)
                                            .invokespecial(CD_Object, INIT_NAME, MTD_void)
                                            .return_())

                            .withMethodBody("m", MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)V"), ACC_PUBLIC | ACC_FINAL, CodeBuilder::return_)

        );

        try (FileOutputStream fos = new FileOutputStream(new File("HasFinal.class"))) {
             fos.write(bytes);
        }
    }


    public static void dump_Overrider () throws Exception {

        byte[] bytes;

        bytes = ClassFile.of().build(ClassDesc.of("Overrider"),
                clb -> clb
                        .withVersion(JAVA_7_VERSION, 0)
                        .withFlags(ACC_PUBLIC | ACC_SUPER)
                        .withSuperclass(ClassDesc.of("HasFinal"))

                        .withMethodBody(INIT_NAME, MTD_void, ACC_PUBLIC,
                                cob -> cob
                                        .aload(0)
                                        .invokespecial(ClassDesc.ofInternalName("HasFinal"), INIT_NAME, MTD_void)
                                        .return_())

                        .withMethodBody("m", MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)V"), ACC_PUBLIC, CodeBuilder::return_)

                        .withMethodBody("main", MethodTypeDesc.ofDescriptor("([Ljava/lang/String;)V"), ACC_PUBLIC | ACC_STATIC, CodeBuilder::return_)

        );

        try (FileOutputStream fos = new FileOutputStream(new File("Overrider.class"))) {
             fos.write(bytes);
        }
    }


    public static void main(String... args) throws Exception {
        dump_HasFinal();
        dump_Overrider();
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder("-cp", ".",  "Overrider");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain(
            "java.lang.IncompatibleClassChangeError: class Overrider overrides final method HasFinal.m(Ljava/lang/String;)V");
        output.shouldHaveExitValue(1);
    }

}
