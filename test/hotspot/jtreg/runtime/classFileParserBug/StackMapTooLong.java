/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test Very large StackMapTable should cause OutOfMemoryError and not VM crash.
 * @bug 8386562
 * @library /test/lib /test/hotspot/jtreg/testlibrary/asm
 * @run main StackMapTooLong
 */

import java.lang.invoke.MethodHandles;
import static org.objectweb.asm.Opcodes.*;
import org.objectweb.asm.*;

public class StackMapTooLong {
    private static String BadClass = "BadClass";

    public static void main(String[] args) throws Exception {
        byte[] classFile = dumpBadClass();

        try {
            MethodHandles.lookup().defineClass(classFile);
            throw new RuntimeException("OutOfMemoryError expected but not thrown!");
        } catch (OutOfMemoryError expected) {}
    }

    static class LargeStackMapTable extends Attribute {
        LargeStackMapTable() {
            super("StackMapTable");
        }

        @Override
        public boolean isCodeAttribute() {
            return true;
        }

        @Override
        protected ByteVector write(ClassWriter cw, byte[] code,
                                   int codeLength, int maxStack, int maxLocals) {
            int len = 16 * 1024 * 1024 + 1; // Too large to be allocated by Metaspace::allocate()
            ByteVector bv = new ByteVector();
            bv.putByteArray(new byte[len], 0, len);
            return bv;
        }
    }

    private static byte[] dumpBadClass() throws Exception {
        ClassWriter classWriter = new ClassWriter(0);
        MethodVisitor methodVisitor;

        classWriter.visit(51, ACC_SUPER, BadClass, null, "java/lang/Object",
                          null);

        {
            methodVisitor =
                classWriter.visitMethod(ACC_PUBLIC | ACC_STATIC, "main",
                                        "([Ljava/lang/String;)V", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitAttribute(new LargeStackMapTable());
            methodVisitor.visitMaxs(0, 1);
            methodVisitor.visitEnd();
        }
        classWriter.visitEnd();

        return classWriter.toByteArray();
    }
}
