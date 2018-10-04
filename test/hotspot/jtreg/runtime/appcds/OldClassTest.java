/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test
 * @summary classes with major version < JDK_1.5 (48) should not be included in CDS
 * @requires vm.cds
 * @library /test/lib
 * @modules java.base/jdk.internal.org.objectweb.asm
 *          java.base/jdk.internal.misc
 *          java.management
 *          jdk.jartool/sun.tools.jar
 * @compile test-classes/Hello.java
 * @run build TestCommon JarBuilder
 * @run driver OldClassTest
 */

import java.io.File;
import java.io.FileOutputStream;
import jdk.test.lib.process.OutputAnalyzer;
import java.nio.file.Files;

import java.util.*;
import jdk.internal.org.objectweb.asm.*;

public class OldClassTest implements Opcodes {

  public static void main(String[] args) throws Exception {
    File jarSrcFile = new File(JarBuilder.getOrCreateHelloJar());

    File dir = new File(System.getProperty("test.classes", "."));
    File jarFile = new File(dir, "OldClassTest_old.jar");
    String jar = jarFile.getPath();

    if (!jarFile.exists() || jarFile.lastModified() < jarSrcFile.lastModified()) {
      createTestJarFile(jarSrcFile, jarFile);
    } else {
      System.out.println("Already up-to-date: " + jarFile);
    }

    String appClasses[] = TestCommon.list("Hello");

    // CASE 1: pre-JDK 1.5 compiled classes should be excluded from the dump
    OutputAnalyzer output = TestCommon.dump(jar, appClasses);
    TestCommon.checkExecReturn(output, 0, true, "Pre JDK 1.5 class not supported by CDS");

    TestCommon.run(
        "-cp", jar,
        "Hello")
      .assertNormalExit("Hello Unicode world (Old)");

    // CASE 2: if we exlcude old version of this class, we should not pick up
    //         the newer version of this class in a subsequent classpath element.
    String classpath = jar + File.pathSeparator + jarSrcFile.getPath();
    output = TestCommon.dump(classpath, appClasses);
    TestCommon.checkExecReturn(output, 0, true, "Pre JDK 1.5 class not supported by CDS");

    TestCommon.run(
        "-cp", classpath,
        "Hello")
      .assertNormalExit("Hello Unicode world (Old)");
  }

  static void createTestJarFile(File jarSrcFile, File jarFile) throws Exception {
    jarFile.delete();
    Files.copy(jarSrcFile.toPath(), jarFile.toPath());

    File dir = new File(System.getProperty("test.classes", "."));
    File outdir = new File(dir, "old_class_test_classes");
    outdir.delete();
    outdir.mkdir();

    writeClassFile(new File(outdir, "Hello.class"), makeOldHello());

    JarBuilder.update(jarFile.getPath(), outdir.getPath());
  }

  static void writeClassFile(File file, byte bytecodes[]) throws Exception {
    try (FileOutputStream fos = new FileOutputStream(file)) {
        fos.write(bytecodes);
      }
  }

/* makeOldHello() was obtained using JDK8. We use a method name > 128 that would
   trigger a call to java.lang.Character.isJavaIdentifierStart() during class
   file parsing.

cat > Hello.java <<EOF
public class Hello {
    public static void main(String args[]) {
        System.out.println(\u1234());
    }
    static String \u1234() {
        return "Hello Unicode world (Old)";
    }
}
EOF
javac Hello.java
java jdk.internal.org.objectweb.asm.util.ASMifier Hello.class

 */

  static byte[] makeOldHello() throws Exception {
    ClassWriter cw = new ClassWriter(0);
    FieldVisitor fv;
    MethodVisitor mv;
    AnnotationVisitor av0;

//WAS cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "Hello", null, "java/lang/Object", null);
      cw.visit(V1_4, ACC_PUBLIC + ACC_SUPER, "Hello", null, "java/lang/Object", null);

    {
      mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(1, 1);
      mv.visitEnd();
    }
    {
      mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
      mv.visitCode();
      mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      mv.visitMethodInsn(INVOKESTATIC, "Hello", "\u1234", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(2, 1);
      mv.visitEnd();
    }
    {
      mv = cw.visitMethod(ACC_STATIC, "\u1234", "()Ljava/lang/String;", null, null);
      mv.visitCode();
      mv.visitLdcInsn("Hello Unicode world (Old)");
      mv.visitInsn(ARETURN);
      mv.visitMaxs(1, 0);
      mv.visitEnd();
    }
    cw.visitEnd();

    return cw.toByteArray();
  }
}
