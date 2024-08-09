/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8194246
 * @enablePreview
 * @summary JVM crashes on stack trace for large number of methods.
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @run main LargeClassTest
 */

import java.io.File;
import java.io.FileOutputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.ConstantDescs.*;

public class LargeClassTest{
    public static void main(String... args) throws Exception {
        writeClassFile();
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder("-cp", ".",  "Large");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
    }

    // Writes a Large class with > signed 16 bit int methods
    public static void writeClassFile() throws Exception {

        ClassDesc CD_Large = ClassDesc.of("Large");
        ClassDesc CD_Random = ClassDesc.ofInternalName("java/util/Random");
        ClassDesc CD_System = ClassDesc.ofInternalName("java/lang/System");
        ClassDesc CD_PrintStream = ClassDesc.ofInternalName("java/io/PrintStream");
        ClassDesc CD_Thread = ClassDesc.ofInternalName("java/lang/Thread");
        ClassDesc CD_StackTraceElement = ClassDesc.ofInternalName("java/lang/StackTraceElement");
        ClassDesc CD_Arrays = ClassDesc.ofInternalName("java/util/Arrays");

        byte[] bytes = ClassFile.of().build(CD_Large,
                clb -> {
                    clb.withVersion(JAVA_11_VERSION, 0);
                            clb.withSuperclass(CD_Object);
                            clb.withFlags(ACC_PUBLIC | ACC_SUPER);
                            clb.withMethodBody(INIT_NAME, MTD_void, ACC_PUBLIC,
                                    cob -> cob
                                            .aload(0)
                                            .invokespecial(CD_Object, INIT_NAME, MTD_void)
                                            .return_());
                    // public static void main(String[] args) {
                    //     Large large = new Large();
                    //     large.f_1(55);
                    // }
                    clb.withMethodBody("main", MethodTypeDesc.of(CD_void, CD_String.arrayType()), ACC_PUBLIC | ACC_STATIC,
                                    cob -> cob
                                            .new_(CD_Large)
                                            .dup()
                                            .invokespecial(CD_Large, INIT_NAME, MTD_void)
                                            .astore(1)
                                            .aload(1)
                                            .bipush(55)
                                            .invokevirtual(CD_Large, "f_1", MethodTypeDesc.of(CD_int, CD_int))
                                            .pop()
                                            .return_());
                    // Write 34560 methods called f_$i
                    for (int i = 1000; i < 34560 ; i++) {
                        clb.withMethodBody("f_" + i, MethodTypeDesc.of(CD_void), ACC_PUBLIC,
                                CodeBuilder::return_);

                    }
                    // public int f_1(int prior) {
                    //   int total = prior + new java.util.Random(1).nextInt();
                    //   return total + f_2(total);
                    // }
                    clb.withMethodBody("f_1", MethodTypeDesc.of(CD_int, CD_int), ACC_PUBLIC,
                            cob -> cob
                                    .iload(1)
                                    .new_(CD_Random)
                                    .dup()
                                    .lconst_1()
                                    .invokespecial(CD_Random, INIT_NAME, MethodTypeDesc.of(CD_void, CD_long))
                                    .invokevirtual(CD_Random, "nextInt", MethodTypeDesc.of(CD_int))
                                    .iadd()
                                    .istore(2)
                                    .iload(2)
                                    .aload(0)
                                    .iload(2)
                                    .invokevirtual(CD_Large, "f_2", MethodTypeDesc.of(CD_int, CD_int))
                                    .iadd()
                                    .ireturn());
                    // public int f_2(int total) {
                    //   System.out.println(java.util.Arrays.toString(Thread.currentThread().getStackTrace()));
                    //   return 10;
                    // }
                    clb.withMethodBody("f_2", MethodTypeDesc.of(CD_int, CD_int), ACC_PUBLIC,
                            cob -> cob
                                    .getstatic(CD_System, "out", CD_PrintStream)
                                    .invokestatic(CD_Thread, "currentThread", MethodTypeDesc.of(CD_Thread))
                                    .invokevirtual(CD_Thread, "getStackTrace", MethodTypeDesc.of(CD_StackTraceElement.arrayType()))
                                    .invokestatic(CD_Arrays, "toString", MethodTypeDesc.of(CD_String, CD_Object.arrayType()))
                                    .invokevirtual(CD_PrintStream, "println", MethodTypeDesc.of(CD_void, CD_String))
                                    .bipush(10)
                                    .ireturn());
                }
        );
        try (FileOutputStream fos = new FileOutputStream(new File("Large.class"))) {
          fos.write(bytes);
        }
    }
}
