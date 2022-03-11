/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8144992
 * @requires vm.jvmti
 * @modules java.base/jdk.internal.org.objectweb.asm
 * @run main/othervm/native -agentlib:TestManyBreakpoints
 *                          -Xlog:gc+metaspace
 *                          -Xint
 *                          -XX:MetaspaceSize=16K -XX:MaxMetaspaceSize=64M
 *                          TestManyBreakpoints
 */

import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Label;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

public class TestManyBreakpoints {

  static final int BATCHES = 50;
  static final int METHODS = 1000;

  public static void main(String[] args) throws Exception {
    for (int c = 0; c < BATCHES; c++) {
      System.out.println("Batch " + c);
      TestClassLoader loader = new TestClassLoader();
      Class.forName("Target", true, loader);
    }
  }

  private static class TestClassLoader extends ClassLoader implements Opcodes {
    static byte[] TARGET_BYTES = generateTarget();

    private static byte[] generateTarget() {
      ClassWriter cw = new ClassWriter(0);

      cw.visit(52, ACC_SUPER | ACC_PUBLIC, "Target", null, "java/lang/Object", null);
      for (int m = 0; m < METHODS; m++) {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "m" + m, "()V", null, null);
        mv.visitCode();
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
      }
      cw.visitEnd();
      return cw.toByteArray();
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
      if (name.equals("Target")) {
        return defineClass(name, TARGET_BYTES, 0, TARGET_BYTES.length);
      } else {
        return super.findClass(name);
      }
    }
  }

}
