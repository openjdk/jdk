/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8051045
 * @summary Test that exceptions from invokedynamic are wrapped in BootstrapMethodError
 * @modules java.base/jdk.internal.org.objectweb.asm
 * @run main BootstrapMethodErrorTest
 */

import java.lang.reflect.Method;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.*;

import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Handle;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

public class BootstrapMethodErrorTest extends ClassLoader implements Opcodes {

  @Override
  public Class findClass(String name) throws ClassNotFoundException {
    byte[] b;
    try {
      b = loadClassData(name);
    } catch (Throwable th) {
      throw new ClassNotFoundException("Loading error", th);
    }
    return defineClass(name, b, 0, b.length);
  }

  private byte[] loadClassData(String name) throws Exception {
    ClassWriter cw = new ClassWriter(0);
    MethodVisitor mv;

    if (name.equals("C")) {
      cw.visit(52, ACC_SUPER | ACC_PUBLIC, "C", null, "java/lang/Object", null);
      {
        mv = cw.visitMethod(ACC_PRIVATE | ACC_STATIC, "m", "()V", null, null);
        mv.visitCode();
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 1);
        mv.visitEnd();
      }
      cw.visitEnd();
      return cw.toByteArray();
    } else if (name.equals("Exec")) {
      cw.visit(52, ACC_SUPER | ACC_PUBLIC, "Exec", null, "java/lang/Object", null);
      {
        mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "invokeRef", "()V", null, null);
        mv.visitCode();
        Handle h = new Handle(H_INVOKESTATIC, "C", "m", "()V");
        mv.visitInvokeDynamicInsn("C", "()V", h);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
      }
      cw.visitEnd();
      return cw.toByteArray();
    }
    return null;
  }

  public static void main(String[] args) throws ClassNotFoundException, IllegalAccessException, NoSuchMethodException {
    new BootstrapMethodErrorTest().test();
  }

  public void test() throws ClassNotFoundException, IllegalAccessException, NoSuchMethodException {
    Class.forName("C", true, this);
    Class<?> exec = Class.forName("Exec", true, this);

    try {
      exec.getMethod("invokeRef").invoke(null);
    } catch (Throwable e) {
      Throwable c = e.getCause();
      if (c == null) {
        throw new RuntimeException(
            "Expected BootstrapMethodError wrapped in an InvocationTargetException but it wasn't wrapped", e);
      } else if (c instanceof BootstrapMethodError) {
        // Only way to pass test, all else should throw
        return;
      } else {
        throw new RuntimeException(
            "Expected BootstrapMethodError but got another Error: "
            + c.getClass().getName(),
            c);
      }
    }
    throw new RuntimeException("Expected BootstrapMethodError but no Error at all was thrown");
  }
}
